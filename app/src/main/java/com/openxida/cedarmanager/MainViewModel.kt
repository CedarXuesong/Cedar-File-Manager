package com.openxida.cedarmanager

import android.app.Application
import android.os.Environment
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openxida.cedarmanager.data.AppPreferences
import com.openxida.cedarmanager.data.ArchiveExtractItem
import com.openxida.cedarmanager.data.ArchiveInput
import com.openxida.cedarmanager.data.ArchiveService
import com.openxida.cedarmanager.data.FileSystemRouter
import com.openxida.cedarmanager.data.LocalFileSystem
import com.openxida.cedarmanager.data.RootFileSystem
import com.openxida.cedarmanager.data.RootShell
import com.openxida.cedarmanager.domain.AppSettings
import com.openxida.cedarmanager.domain.ArchiveLocation
import com.openxida.cedarmanager.domain.ConflictPolicy
import com.openxida.cedarmanager.domain.FileEntry
import com.openxida.cedarmanager.domain.OperationProgress
import com.openxida.cedarmanager.domain.PaneId
import com.openxida.cedarmanager.domain.PaneState
import com.openxida.cedarmanager.domain.PathTools
import com.openxida.cedarmanager.domain.PermissionSpec
import com.openxida.cedarmanager.domain.RootState
import com.openxida.cedarmanager.domain.ShellEscaper
import com.openxida.cedarmanager.domain.SortMode
import com.openxida.cedarmanager.domain.ThemeMode
import com.openxida.cedarmanager.domain.TransferRequest
import com.openxida.cedarmanager.nativebridge.NativeBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface AppEvent {
    data class Message(val text: String) : AppEvent
    data class EditText(val entry: FileEntry, val content: String) : AppEvent
    data class ShowPermissions(val entry: FileEntry) : AppEvent
    data class OpenExternal(val file: File, val share: Boolean) : AppEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)
    private val rootShell = RootShell()
    private val localFileSystem = LocalFileSystem()
    private val rootFileSystem = RootFileSystem(rootShell, application.cacheDir)
    private val router = FileSystemRouter(localFileSystem, rootFileSystem)
    private val archiveService = ArchiveService()

    private val mutablePaneA = MutableStateFlow(
        PaneState(PaneId.A, Environment.getExternalStorageDirectory().absolutePath),
    )
    private val mutablePaneB = MutableStateFlow(
        PaneState(PaneId.B, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath),
    )
    private val mutableActivePane = MutableStateFlow(PaneId.A)
    private val mutableRootState = MutableStateFlow(RootState.CHECKING)
    private val mutableOperation = MutableStateFlow<OperationProgress?>(null)
    private val mutablePendingTransfer = MutableStateFlow<TransferRequest?>(null)
    private val mutableEvents = MutableSharedFlow<AppEvent>(extraBufferCapacity = 16)

    val paneA: StateFlow<PaneState> = mutablePaneA.asStateFlow()
    val paneB: StateFlow<PaneState> = mutablePaneB.asStateFlow()
    val activePane: StateFlow<PaneId> = mutableActivePane.asStateFlow()
    val rootState: StateFlow<RootState> = mutableRootState.asStateFlow()
    val operation: StateFlow<OperationProgress?> = mutableOperation.asStateFlow()
    val pendingTransfer: StateFlow<TransferRequest?> = mutablePendingTransfer.asStateFlow()
    val settings: StateFlow<AppSettings> = preferences.settings
    val events: SharedFlow<AppEvent> = mutableEvents.asSharedFlow()

    val nativeArchitecture: String = runCatching { NativeBridge.architecture() }.getOrDefault("unavailable")
    private var operationJob: Job? = null
    private var rootToggleJob: Job? = null

    init {
        checkRoot()
        refresh(PaneId.A)
        refresh(PaneId.B)
    }

    fun checkRoot() {
        viewModelScope.launch {
            if (refreshRootState() != RootState.GRANTED && preferences.settings.value.rootFileAccess) {
                preferences.setRootFileAccess(false)
                listOf(PaneId.A, PaneId.B).forEach(::refresh)
            }
        }
    }

    fun setActivePane(id: PaneId) {
        mutableActivePane.value = id
    }

    fun refresh(id: PaneId) {
        val snapshot = pane(id)
        val readAsRoot = preferences.settings.value.rootFileAccess
        updatePane(id) { copy(loading = true, error = null) }
        viewModelScope.launch {
            val archiveLocation = snapshot.archive
            runCatching {
                if (archiveLocation != null) {
                    archiveService.listZip(
                        archiveLocation.archivePath,
                        archiveLocation.innerPath,
                        rootShell.takeIf { readAsRoot },
                        cacheDirectory,
                    )
                } else {
                    router.backend(readAsRoot).list(snapshot.path)
                }
            }
                .onSuccess { entries ->
                    updatePane(id) {
                        if (path == snapshot.path && archiveLocation == snapshot.archive &&
                            preferences.settings.value.rootFileAccess == readAsRoot
                        ) {
                            copy(entries = entries, loading = false, error = null)
                        } else this
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    updatePane(id) {
                        if (path == snapshot.path && archiveLocation == snapshot.archive &&
                            preferences.settings.value.rootFileAccess == readAsRoot
                        ) {
                            copy(loading = false, error = error.userMessage())
                        } else this
                    }
                }
        }
    }

    fun navigate(id: PaneId, path: String) {
        val resolvedPath = PathTools.resolve(path, pane(id).path)
        val currentArchive = pane(id).archive
        if (currentArchive != null && currentArchive.contains(resolvedPath)) {
            openArchive(id, currentArchive.copy(innerPath = currentArchive.relativePath(resolvedPath)))
        } else {
            navigateDirectory(id, resolvedPath)
        }
    }

    private fun navigateDirectory(id: PaneId, path: String) {
        mutableActivePane.value = id
        updatePane(id) {
            copy(
                path = path,
                archive = null,
                entries = emptyList(),
                selected = emptySet(),
                selectionAnchor = null,
                query = "",
                error = null,
            )
        }
        refresh(id)
    }

    fun navigateUp(id: PaneId) {
        val state = pane(id)
        val archive = state.archive
        if (archive != null) {
            val parent = archive.parent()
            if (parent != null) {
                openArchive(id, parent)
            } else {
                val directory = PathTools.parent(
                    archive.archivePath,
                    stopAtSharedStorageRoot = false,
                )
                if (directory != null) navigateDirectory(id, directory)
            }
        } else {
            PathTools.parent(
                state.path,
                stopAtSharedStorageRoot = !preferences.settings.value.rootFileAccess,
            )?.let { navigateDirectory(id, it) }
        }
    }

    private fun openArchive(id: PaneId, archive: ArchiveLocation) {
        mutableActivePane.value = id
        updatePane(id) {
            copy(
                path = archive.virtualPath,
                archive = archive,
                entries = emptyList(),
                selected = emptySet(),
                selectionAnchor = null,
                query = "",
                error = null,
            )
        }
        refresh(id)
    }

    fun openEntry(id: PaneId, entry: FileEntry) {
        mutableActivePane.value = id
        if (pane(id).selected.isNotEmpty()) {
            toggleSelection(id, entry.path)
            return
        }
        when {
            entry.isDirectory -> navigate(id, entry.path)
            entry.archive == null && entry.isArchive -> openArchive(id, ArchiveLocation(entry.path))
            entry.isText -> openText(id, entry)
            entry.isApk && entry.archive == null -> installApk(id, entry)
            entry.isApk -> emitMessage("请先将 APK 解压到文件夹后再安装")
            else -> emitMessage("暂不支持直接打开 ${entry.name}")
        }
    }

    fun toggleSelection(id: PaneId, path: String) {
        mutableActivePane.value = id
        updatePane(id) {
            val next = selected.toMutableSet().apply { if (!add(path)) remove(path) }
            copy(selected = next, selectionAnchor = if (next.isEmpty()) null else selectionAnchor)
        }
    }

    fun selectPaths(id: PaneId, paths: Collection<String>) {
        mutableActivePane.value = id
        updatePane(id) {
            val visiblePaths = visibleEntries.mapTo(mutableSetOf()) { it.path }
            val next = paths.filterTo(mutableSetOf()) { it in visiblePaths }
            copy(selected = next, selectionAnchor = paths.firstOrNull { it in next })
        }
    }

    fun selectSwipe(id: PaneId, path: String) {
        mutableActivePane.value = id
        updatePane(id) { selectSwipe(path) }
    }

    fun invertSelection(id: PaneId) = updatePane(id) { invertSelection() }

    fun selectSimilar(id: PaneId) = updatePane(id) { selectSimilar() }

    fun clearSelection(id: PaneId) = updatePane(id) { copy(selected = emptySet(), selectionAnchor = null) }

    fun selectAll(id: PaneId) = updatePane(id) { copy(selected = visibleEntries.mapTo(mutableSetOf()) { it.path }, selectionAnchor = null) }

    fun setQuery(value: String) = updatePane(mutableActivePane.value) { copy(query = value) }

    fun setSort(mode: SortMode) {
        val id = mutableActivePane.value
        updatePane(id) {
            if (sortMode == mode) copy(ascending = !ascending) else copy(sortMode = mode, ascending = true)
        }
    }

    fun setRootFileAccess(enabled: Boolean) {
        rootToggleJob?.cancel()
        if (!enabled) {
            preferences.setRootFileAccess(false)
            listOf(PaneId.A, PaneId.B).forEach { id -> updatePane(id) { copy(selected = emptySet()) } }
            listOf(PaneId.A, PaneId.B).forEach(::refresh)
            return
        }
        rootToggleJob = viewModelScope.launch {
            mutableRootState.value = RootState.CHECKING
            if (refreshRootState() == RootState.GRANTED) {
                preferences.setRootFileAccess(true)
                listOf(PaneId.A, PaneId.B).forEach { id -> updatePane(id) { copy(selected = emptySet()) } }
                listOf(PaneId.A, PaneId.B).forEach(::refresh)
            } else {
                emitMessage("未获得 Root 授权，无法启用 Root 文件访问")
            }
        }
    }

    fun swapPanes() {
        val a = mutablePaneA.value
        val b = mutablePaneB.value
        mutablePaneA.value = b.copy(id = PaneId.A)
        mutablePaneB.value = a.copy(id = PaneId.B)
        mutableActivePane.value = if (mutableActivePane.value == PaneId.A) PaneId.B else PaneId.A
    }

    fun createDirectory(id: PaneId, name: String) {
        val state = pane(id)
        val archive = state.archive
        if (archive != null) {
            mutateArchive(id, "新建目录") {
                require(name.isSafeName()) { "目录名无效" }
                require(state.entries.none { it.name == name }) { "已存在同名项目" }
                archiveService.createEntry(archive.archivePath, archive.innerPath, name, true, rootShellForArchives(), cacheDirectory)
            }
            return
        }
        mutate(id, "新建目录") { backend, current ->
            require(name.isSafeName()) { "目录名无效" }
            backend.mkdir(PathTools.join(current.path, name))
        }
    }

    fun createFile(id: PaneId, name: String) {
        val state = pane(id)
        val archive = state.archive
        if (archive != null) {
            mutateArchive(id, "新建文件") {
                require(name.isSafeName()) { "文件名无效" }
                require(state.entries.none { it.name == name }) { "已存在同名项目" }
                archiveService.createEntry(archive.archivePath, archive.innerPath, name, false, rootShellForArchives(), cacheDirectory)
            }
            return
        }
        mutate(id, "新建文件") { backend, current ->
            require(name.isSafeName()) { "文件名无效" }
            backend.writeText(PathTools.join(current.path, name), "")
        }
    }

    fun rename(id: PaneId, entry: FileEntry, name: String) {
        val archive = entry.archive
        if (archive != null) {
            viewModelScope.launch {
                runCatching {
                    require(name.isSafeName()) { "文件名无效" }
                    archiveService.renameEntry(
                        archive.archivePath,
                        archive.relativePath(entry.path),
                        name,
                        rootShellForArchives(),
                        cacheDirectory,
                    )
                }
                    .onSuccess {
                        updatePane(id) { copy(selected = emptySet(), selectionAnchor = null) }
                        refresh(id)
                        emitMessage("重命名完成")
                    }
                    .onFailure { emitMessage(it.userMessage()) }
            }
            return
        }
        mutate(id, "重命名") { backend, _ ->
            require(name.isSafeName()) { "文件名无效" }
            backend.rename(entry.path, PathTools.join(PathTools.parent(entry.path) ?: "/", name))
            updatePane(id) { copy(selected = emptySet()) }
        }
    }

    fun deleteSelected(id: PaneId) {
        val state = pane(id)
        val selected = state.entries.filter { it.path in state.selected }
        if (selected.isEmpty()) return
        val archive = state.archive
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            mutableOperation.value = OperationProgress("永久删除", "准备删除", 0, selected.size, true)
            var failures = 0
            if (archive != null) {
                runCatching {
                    archiveService.deleteEntries(
                        archive.archivePath,
                        selected.map { archive.relativePath(it.path) },
                        rootShellForArchives(),
                        cacheDirectory,
                    )
                }.onFailure { error ->
                    failures = selected.size
                    emitMessage(error.userMessage())
                }
                mutableOperation.value = OperationProgress(
                    "永久删除",
                    if (failures == 0) "已更新压缩包" else "写入压缩包失败",
                    1,
                    1,
                    false,
                    failures,
                )
            } else {
                selected.forEachIndexed { index, entry ->
                    runCatching { router.backend(preferences.settings.value.rootFileAccess).delete(entry.path) }
                        .onFailure { failures++ }
                    mutableOperation.value = OperationProgress(
                        "永久删除",
                        entry.name,
                        index + 1,
                        selected.size,
                        index != selected.lastIndex,
                        failures,
                    )
                }
            }
            updatePane(id) { copy(selected = emptySet(), selectionAnchor = null) }
            refresh(id)
            emitMessage(if (failures == 0) "删除完成" else "删除完成，$failures 项失败")
        }
    }

    fun prepareTransfer(move: Boolean) {
        val sourceId = mutableActivePane.value
        val targetId = sourceId.other()
        val source = pane(sourceId)
        val target = pane(targetId)
        val selected = source.entries.filter { it.path in source.selected }
        if (selected.isEmpty()) {
            emitMessage("请先选择文件")
            return
        }
        if (source.archive != null && target.archive != null) {
            emitMessage("暂不支持在两个压缩包之间直接传输")
            return
        }
        mutablePendingTransfer.value = TransferRequest(
            sourcePane = sourceId,
            targetPane = targetId,
            sources = selected,
            targetDirectory = target.path,
            move = move,
            sourceArchive = source.archive,
            targetArchive = target.archive,
        )
    }

    fun cancelTransfer() {
        mutablePendingTransfer.value = null
    }

    fun executeTransfer(policy: ConflictPolicy) {
        val request = mutablePendingTransfer.value ?: return
        mutablePendingTransfer.value = null
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            when {
                request.targetArchive != null -> addToArchive(request, policy)
                request.sourceArchive != null -> extractFromArchive(request, policy)
                else -> transferBetweenDirectories(request, policy)
            }
        }
    }

    private suspend fun transferBetweenDirectories(request: TransferRequest, policy: ConflictPolicy) {
        val rootMode = preferences.settings.value.rootFileAccess
        var failures = 0
        mutableOperation.value = OperationProgress(
            if (request.move) "移动文件" else "复制文件",
            "准备执行",
            0,
            request.sources.size,
            true,
        )
        request.sources.forEachIndexed { index, entry ->
            try {
                val preferred = PathTools.join(request.targetDirectory, entry.name)
                val target = resolveConflict(preferred, rootMode, policy)
                if (target != null) {
                    require(!(entry.isDirectory && target.startsWith(entry.path.trimEnd('/') + '/'))) {
                        "不能把目录移动到自身子目录"
                    }
                    router.copyOrMove(entry.path, target, rootMode, request.move)
                }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (_: Throwable) {
                failures++
            }
            mutableOperation.value = OperationProgress(
                if (request.move) "移动文件" else "复制文件",
                entry.name,
                index + 1,
                request.sources.size,
                index != request.sources.lastIndex,
                failures,
            )
        }
        updatePane(request.sourcePane) { copy(selected = emptySet(), selectionAnchor = null) }
        refresh(request.sourcePane)
        refresh(request.targetPane)
        emitMessage(if (failures == 0) "文件任务完成" else "任务完成，$failures 项失败")
    }

    private suspend fun addToArchive(request: TransferRequest, policy: ConflictPolicy) {
        val archive = request.targetArchive ?: return
        if (request.sources.any { source ->
                archive.archivePath == source.path ||
                    archive.archivePath.startsWith(source.path.trimEnd('/') + "/")
            }
        ) {
            emitMessage("不能把压缩包添加到它自身或所在目录")
            return
        }
        val rootMode = preferences.settings.value.rootFileAccess
        mutableOperation.value = OperationProgress("添加到压缩包", "正在写入压缩包", 0, request.sources.size, true)
        var failures = 0
        try {
            val result = archiveService.addToArchive(
                archive.archivePath,
                archive.innerPath,
                request.sources.map { ArchiveInput(it.path, it.name) },
                policy,
                rootShellForArchives(),
                cacheDirectory,
            )
            if (request.move) {
                val addedSources = result.addedSources.toSet()
                request.sources
                    .filter { it.path in addedSources }
                    .forEach { source ->
                        runCatching { router.backend(rootMode).delete(source.path) }
                            .onFailure { failures++ }
                    }
            }
            mutableOperation.value = OperationProgress(
                "添加到压缩包",
                "完成",
                request.sources.size,
                request.sources.size,
                false,
                failures,
            )
            emitMessage(
                buildString {
                    append("已添加 ${result.added} 项")
                    if (result.skipped > 0) append("，跳过 ${result.skipped} 项")
                    if (failures > 0) append("，$failures 项源文件删除失败")
                },
            )
        } catch (canceled: CancellationException) {
            throw canceled
        } catch (error: Throwable) {
            mutableOperation.value = OperationProgress(
                "添加到压缩包",
                error.userMessage(),
                0,
                request.sources.size,
                false,
                1,
            )
            emitMessage(error.userMessage())
        }
        updatePane(request.sourcePane) { copy(selected = emptySet(), selectionAnchor = null) }
        refresh(request.sourcePane)
        refresh(request.targetPane)
    }

    private suspend fun extractFromArchive(request: TransferRequest, policy: ConflictPolicy) {
        val archive = request.sourceArchive ?: return
        val rootMode = preferences.settings.value.rootFileAccess
        mutableOperation.value = OperationProgress(
            if (request.move) "移动文件" else "解压文件",
            "正在读取压缩包",
            0,
            request.sources.size,
            true,
        )
        var failures = 0
        try {
            val movedEntries = ArrayList<String>()
            val items = ArrayList<ArchiveExtractItem>()
            request.sources.forEach { source ->
                val preferred = PathTools.join(request.targetDirectory, source.name)
                val target = resolveConflict(preferred, rootMode, policy)
                if (target != null) {
                    movedEntries += archive.relativePath(source.path)
                    items += ArchiveExtractItem(
                        entryPath = archive.relativePath(source.path),
                        targetName = File(target).name,
                    )
                }
            }
            if (items.isNotEmpty()) {
                archiveService.extractEntries(
                    archive.archivePath,
                    items,
                    request.targetDirectory,
                    rootShellForArchives(),
                    cacheDirectory,
                )
            }
            if (request.move && movedEntries.isNotEmpty()) {
                archiveService.deleteEntries(
                    archive.archivePath,
                    movedEntries,
                    rootShellForArchives(),
                    cacheDirectory,
                )
            }
            mutableOperation.value = OperationProgress(
                if (request.move) "移动文件" else "解压文件",
                "完成",
                request.sources.size,
                request.sources.size,
                false,
            )
            emitMessage(
                buildString {
                    append(if (request.move) "已移出 " else "已解压 ")
                    append(items.size)
                    append(" 项")
                    if (items.size < request.sources.size) {
                        append("，跳过 ${request.sources.size - items.size} 项")
                    }
                },
            )
        } catch (canceled: CancellationException) {
            throw canceled
        } catch (error: Throwable) {
            failures = 1
            mutableOperation.value = OperationProgress(
                if (request.move) "移动文件" else "解压文件",
                error.userMessage(),
                0,
                request.sources.size,
                false,
                failures,
            )
            emitMessage(error.userMessage())
        }
        updatePane(request.sourcePane) { copy(selected = emptySet(), selectionAnchor = null) }
        refresh(request.sourcePane)
        refresh(request.targetPane)
    }

    fun cancelOperation() {
        operationJob?.cancel()
        operationJob = null
        mutableOperation.value = mutableOperation.value?.copy(running = false, detail = "已取消")
    }

    fun requestPermissions(id: PaneId, entry: FileEntry) {
        if (entry.archive != null) {
            emitMessage("压缩包内条目不支持修改权限")
            return
        }
        mutableEvents.tryEmit(AppEvent.ShowPermissions(entry))
    }

    fun savePermissions(entry: FileEntry, spec: PermissionSpec) {
        if (entry.archive != null) {
            emitMessage("压缩包内条目不支持修改权限")
            return
        }
        viewModelScope.launch {
            runCatching { router.backend(preferences.settings.value.rootFileAccess).setPermissions(entry.path, spec) }
                .onSuccess {
                    emitMessage("权限已更新为 ${spec.octal}")
                    refresh(mutableActivePane.value)
                }
                .onFailure { emitMessage(it.userMessage()) }
        }
    }

    fun openText(id: PaneId, entry: FileEntry) {
        val readAsRoot = preferences.settings.value.rootFileAccess
        val archive = entry.archive
        viewModelScope.launch {
            runCatching {
                if (archive != null) {
                    archiveService.readZipText(
                        archive.archivePath,
                        archive.relativePath(entry.path),
                        rootShell.takeIf { readAsRoot },
                        cacheDirectory,
                    )
                } else {
                    router.backend(readAsRoot).readText(entry.path)
                }
            }
                .onSuccess { mutableEvents.emit(AppEvent.EditText(entry, it)) }
                .onFailure { emitMessage(it.userMessage()) }
        }
    }

    fun saveText(entry: FileEntry, content: String) {
        val archive = entry.archive
        viewModelScope.launch {
            runCatching {
                if (archive != null) {
                    archiveService.writeZipText(
                        archive.archivePath,
                        archive.relativePath(entry.path),
                        content,
                        rootShellForArchives(),
                        cacheDirectory,
                    )
                } else {
                    router.backend(preferences.settings.value.rootFileAccess).writeText(entry.path, content)
                }
            }
                .onSuccess {
                    emitMessage("已保存 ${entry.name}")
                    refresh(mutableActivePane.value)
                }
                .onFailure { emitMessage(it.userMessage()) }
        }
    }

    fun archiveSelected(id: PaneId) {
        val state = pane(id)
        if (state.archive != null) {
            emitMessage("压缩包内暂不支持再次创建压缩包")
            return
        }
        val selected = state.entries.filter { it.path in state.selected }
        if (selected.isEmpty()) return
        operationJob = viewModelScope.launch {
            val rootMode = preferences.settings.value.rootFileAccess
            val output = PathTools.join(state.path, "Archive-${System.currentTimeMillis()}.zip")
            mutableOperation.value = OperationProgress("创建压缩包", File(output).name, 0, 1, true)
            runCatching {
                if (rootMode) archiveService.createRootZip(selected.map { it.path }, output, rootShell, getApplication<Application>().cacheDir)
                else archiveService.createZip(selected.map { it.path }, output)
            }
                .onSuccess {
                    mutableOperation.value = OperationProgress("创建压缩包", File(output).name, 1, 1, false)
                    clearSelection(id)
                    refresh(id)
                    emitMessage("压缩完成")
                }
                .onFailure {
                    mutableOperation.value = OperationProgress("创建压缩包", it.userMessage(), 1, 1, false, 1)
                    emitMessage(it.userMessage())
                }
        }
    }

    fun extractArchive(id: PaneId, entry: FileEntry) {
        val state = pane(id)
        if (state.archive != null) {
            emitMessage("请将压缩包解压到另一侧目录")
            return
        }
        if (entry.extension != "zip") {
            emitMessage("当前仅支持 ZIP 解压")
            return
        }
        viewModelScope.launch {
            val rootMode = preferences.settings.value.rootFileAccess
            val target = PathTools.join(state.path, entry.name.substringBeforeLast('.'))
            runCatching {
                if (rootMode) archiveService.extractRootZip(entry.path, target, rootShell, getApplication<Application>().cacheDir)
                else archiveService.extractZip(entry.path, target)
            }
                .onSuccess { refresh(id); emitMessage("解压完成") }
                .onFailure { emitMessage(it.userMessage()) }
        }
    }

    fun installApk(id: PaneId, entry: FileEntry) {
        if (entry.archive != null) {
            emitMessage("请先将 APK 解压到文件夹后再安装")
            return
        }
        if (mutableRootState.value != RootState.GRANTED) {
            emitMessage("Root 安装需要先授予 Root 权限")
            return
        }
        val sourceRoot = preferences.settings.value.rootFileAccess
        operationJob = viewModelScope.launch {
            val temp = "/data/local/tmp/cedar-${System.currentTimeMillis()}.apk"
            mutableOperation.value = OperationProgress("Root 安装 APK", entry.name, 0, 3, true)
            runCatching {
                if (sourceRoot) {
                    rootShell.runOrThrow("cp -f -- ${ShellEscaper.quote(entry.path)} ${ShellEscaper.quote(temp)}")
                } else {
                    val bytes = withContext(Dispatchers.IO) { File(entry.path).readBytes() }
                    require(bytes.size <= 512L * 1024 * 1024) { "APK 超过 512 MiB" }
                    val staged = File(getApplication<Application>().cacheDir, "install.apk")
                    withContext(Dispatchers.IO) { staged.writeBytes(bytes) }
                    rootShell.runOrThrow("cp -f -- ${ShellEscaper.quote(staged.absolutePath)} ${ShellEscaper.quote(temp)}")
                    withContext(Dispatchers.IO) { staged.delete() }
                }
                mutableOperation.value = OperationProgress("Root 安装 APK", "正在调用系统包管理器", 1, 3, true)
                rootShell.runOrThrow("chmod 0644 ${ShellEscaper.quote(temp)} && pm install -r -d ${ShellEscaper.quote(temp)}")
            }.onSuccess {
                mutableOperation.value = OperationProgress("Root 安装 APK", "安装成功", 3, 3, false)
                emitMessage("${entry.name} 安装成功")
            }.onFailure {
                mutableOperation.value = OperationProgress("Root 安装 APK", it.userMessage(), 3, 3, false, 1)
                emitMessage("安装失败：${it.userMessage()}")
            }
            rootShell.run("rm -f -- ${ShellEscaper.quote(temp)}")
        }
    }

    fun setThemeMode(value: ThemeMode) = preferences.setThemeMode(value)
    fun setHaptics(value: Boolean) = preferences.setHaptics(value)
    fun setConfirmSystemWrites(value: Boolean) = preferences.setConfirmSystemWrites(value)
    fun setTerminalFontSize(value: Int) = preferences.setTerminalFontSize(value)
    fun setSafTreeUri(value: String?) = preferences.setSafTreeUri(value)
    fun setAnimationSpeed(value: Float) = preferences.setAnimationSpeed(value)
    fun setTiltStrength(value: Float) = preferences.setTiltStrength(value)

    private fun mutate(
        id: PaneId,
        title: String,
        block: suspend (com.openxida.cedarmanager.data.FileSystemBackend, PaneState) -> Unit,
    ) {
        val state = pane(id)
        viewModelScope.launch {
            runCatching { block(router.backend(preferences.settings.value.rootFileAccess), state) }
                .onSuccess { refresh(id); emitMessage("${title}完成") }
                .onFailure { emitMessage(it.userMessage()) }
        }
    }

    private fun mutateArchive(id: PaneId, title: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess {
                    updatePane(id) { copy(selected = emptySet(), selectionAnchor = null) }
                    refresh(id)
                    emitMessage("${title}完成")
                }
                .onFailure { emitMessage(it.userMessage()) }
        }
    }

    private val cacheDirectory: File
        get() = getApplication<Application>().cacheDir

    private fun rootShellForArchives(): RootShell? =
        rootShell.takeIf { preferences.settings.value.rootFileAccess }

    private suspend fun resolveConflict(
        preferred: String,
        rootMode: Boolean,
        policy: ConflictPolicy,
    ): String? {
        if (!router.exists(preferred, rootMode)) return preferred
        return when (policy) {
            ConflictPolicy.SKIP -> null
            ConflictPolicy.REPLACE -> {
                router.backend(rootMode).delete(preferred)
                preferred
            }
            ConflictPolicy.KEEP_BOTH -> {
                val directory = PathTools.parent(preferred) ?: "/"
                val name = File(preferred).name
                val dot = name.lastIndexOf('.')
                val base = if (dot > 0) name.substring(0, dot) else name
                val extension = if (dot > 0) name.substring(dot) else ""
                var index = 1
                var candidate: String
                do {
                    candidate = PathTools.join(directory, "$base ($index)$extension")
                    index++
                } while (router.exists(candidate, rootMode))
                candidate
            }
        }
    }

    private fun pane(id: PaneId): PaneState = if (id == PaneId.A) mutablePaneA.value else mutablePaneB.value

    private suspend fun refreshRootState(): RootState {
        val granted = rootShell.requestRoot()
        val state = when {
            granted -> RootState.GRANTED
            File("/system/bin/su").exists() || File("/system/xbin/su").exists() -> RootState.DENIED
            else -> RootState.UNAVAILABLE
        }
        mutableRootState.value = state
        return state
    }

    fun openExternal(entry: FileEntry, share: Boolean) {
        viewModelScope.launch {
            val archive = entry.archive
            if (archive != null) {
                if (entry.isDirectory) {
                    emitMessage("暂不支持直接分享压缩包内的目录")
                    return@launch
                }
                val cache = cacheDirectory
                val suffix = ".${entry.extension.ifBlank { "bin" }}"
                val staged = withContext(Dispatchers.IO) {
                    File.createTempFile("cedar-export-", suffix, cache)
                }
                runCatching {
                    archiveService.extractFile(
                        archive.archivePath,
                        archive.relativePath(entry.path),
                        staged,
                        rootShellForArchives(),
                        cache,
                    )
                }
                    .onSuccess { mutableEvents.emit(AppEvent.OpenExternal(staged, share)) }
                    .onFailure {
                        staged.delete()
                        emitMessage("无法准备文件：${it.userMessage()}")
                    }
                return@launch
            }
            val cache = getApplication<Application>().cacheDir
            val rootMode = preferences.settings.value.rootFileAccess
            val suffix = if (entry.isDirectory) ".zip" else ".${entry.extension.ifBlank { "bin" }}"
            val staged = withContext(Dispatchers.IO) { File.createTempFile("cedar-export-", suffix, cache) }
            runCatching {
                if (entry.isDirectory) {
                    if (rootMode) archiveService.createRootZip(listOf(entry.path), staged.path, rootShell, cache)
                    else archiveService.createZip(listOf(entry.path), staged.path)
                } else if (rootMode) {
                    rootShell.runOrThrow("cp -- ${ShellEscaper.quote(entry.path)} ${ShellEscaper.quote(staged.path)}")
                } else {
                    withContext(Dispatchers.IO) {
                        Files.copy(File(entry.path).toPath(), staged.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
                if (rootMode) {
                    rootShell.runOrThrow("chown ${Process.myUid()}:${Process.myUid()} -- ${ShellEscaper.quote(staged.path)} && chmod 0600 -- ${ShellEscaper.quote(staged.path)}")
                }
                mutableEvents.emit(AppEvent.OpenExternal(staged, share))
            }.onFailure {
                staged.delete()
                emitMessage("无法准备文件：${it.userMessage()}")
            }
        }
    }

    private fun updatePane(id: PaneId, block: PaneState.() -> PaneState) {
        if (id == PaneId.A) mutablePaneA.value = mutablePaneA.value.block()
        else mutablePaneB.value = mutablePaneB.value.block()
    }

    private fun emitMessage(text: String) {
        mutableEvents.tryEmit(AppEvent.Message(text))
    }

    private fun PaneId.other(): PaneId = if (this == PaneId.A) PaneId.B else PaneId.A
    private fun String.isSafeName(): Boolean = isNotBlank() && this != "." && this != ".." && '/' !in this && '\u0000' !in this
    private fun Throwable.userMessage(): String = message?.lineSequence()?.lastOrNull()?.take(400) ?: javaClass.simpleName
}

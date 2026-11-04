@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.openxida.cedarmanager.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.MimeTypeMap
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowInsetsControllerCompat
import com.openxida.cedarmanager.AppEvent
import com.openxida.cedarmanager.MainViewModel
import com.openxida.cedarmanager.domain.AppSettings
import com.openxida.cedarmanager.domain.ConflictPolicy
import com.openxida.cedarmanager.domain.FileEntry
import com.openxida.cedarmanager.domain.FileKind
import com.openxida.cedarmanager.domain.PaneId
import com.openxida.cedarmanager.domain.PaneState
import com.openxida.cedarmanager.domain.PermissionSpec
import com.openxida.cedarmanager.domain.PathTools
import com.openxida.cedarmanager.domain.RootState
import com.openxida.cedarmanager.domain.ShellEscaper
import com.openxida.cedarmanager.domain.ShellCommandBuilder
import com.openxida.cedarmanager.domain.SortMode
import com.openxida.cedarmanager.domain.ThemeMode
import com.openxida.cedarmanager.domain.TransferRequest
import com.openxida.cedarmanager.ui.effects.LiquidGlassPopupSurface
import com.openxida.cedarmanager.ui.effects.LocalPopupTiltStrength
import com.openxida.cedarmanager.ui.effects.SampledLayerBackdrop
import com.openxida.cedarmanager.ui.effects.BackdropCapture
import com.openxida.cedarmanager.ui.effects.rememberSampledLayerBackdrop
import com.openxida.cedarmanager.ui.effects.sampledLayerBackdrop
import com.openxida.cedarmanager.ui.effects.verticalGradientBlur
import com.openxida.cedarmanager.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import kotlin.math.roundToInt

private enum class MainSection(val label: String, val icon: String) {
    HOME("主页", "home"),
    FILES("文件", "files"),
    SETTINGS("设置", "settings"),
}

private sealed interface WorkScreen {
    data object Main : WorkScreen
    data object Debug : WorkScreen
    data class Terminal(val directory: String, val root: Boolean, val command: String? = null) : WorkScreen
    data class Editor(val entry: FileEntry, val initialContent: String) : WorkScreen
}

private sealed interface ModalState {
    data class Create(val pane: PaneId, val directory: Boolean) : ModalState
    data class Rename(val pane: PaneId, val entry: FileEntry) : ModalState
    data class Delete(val pane: PaneId, val count: Int) : ModalState
    data class Permissions(val entry: FileEntry) : ModalState
    data class Transfer(val request: TransferRequest) : ModalState
    data class InstallApk(val pane: PaneId, val entry: FileEntry) : ModalState
    data class Script(val pane: PaneId, val entry: FileEntry) : ModalState
    data class FileActions(
        val pane: PaneId,
        val entry: FileEntry,
        val targets: List<FileEntry>,
        val selectionCount: Int,
    ) : ModalState
    data class FileTools(val pane: PaneId, val entry: FileEntry) : ModalState
    data class Properties(val entry: FileEntry) : ModalState
    data class SystemWrite(
        val operation: String,
        val paths: List<String>,
        val onConfirm: () -> Unit,
    ) : ModalState
}

private enum class FileAction {
    COPY,
    MOVE,
    DELETE,
    RENAME,
    TOOLS,
    ARCHIVE,
    EXTRACT,
    PROPERTIES,
    SHARE,
    OPEN_WITH,
    BOOKMARK,
}

private enum class SelectionAction { SELECT_ALL, INVERT, CLEAR, SIMILAR }

@Composable
fun CedarManagerApp(
    viewModel: MainViewModel,
    hasAllFilesAccess: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onRequestSafTree: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val controller = ThemeController(
        when (settings.themeMode) {
            ThemeMode.SYSTEM -> ColorSchemeMode.System
            ThemeMode.LIGHT -> ColorSchemeMode.Light
            ThemeMode.DARK -> ColorSchemeMode.Dark
        },
        keyColor = null,
        isDark = dark,
        paletteStyle = ThemePaletteStyle.TonalSpot,
        colorSpec = ThemeColorSpec.Spec2025,
    )

    MiuixTheme(controller = controller, textStyles = CedarTextStyles) {
        CompositionLocalProvider(
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,
            LocalPopupTiltStrength provides settings.tiltStrength,
        ) {
        var section by rememberSaveable { mutableStateOf(MainSection.HOME) }
        var secondaryScreen by remember { mutableStateOf<WorkScreen?>(null) }
        var secondaryVisible by remember { mutableStateOf(false) }
        var modal by remember { mutableStateOf<ModalState?>(null) }
        var message by remember { mutableStateOf<String?>(null) }

        fun openSecondary(screen: WorkScreen) {
            secondaryScreen = screen
            secondaryVisible = true
        }

        val secondaryProgress by animateFloatAsState(
            targetValue = if (secondaryVisible) 1f else 0f,
            animationSpec = tween(
                durationMillis = 360,
                easing = FastOutSlowInEasing,
            ),
            label = "secondary screen progress",
        )
        LaunchedEffect(secondaryVisible, secondaryProgress) {
            if (!secondaryVisible && secondaryProgress == 0f) secondaryScreen = null
        }
        LaunchedEffect(dark, secondaryScreen is WorkScreen.Terminal) {
            val window = (context as? Activity)?.window ?: return@LaunchedEffect
            val lightIcons = !dark && secondaryScreen !is WorkScreen.Terminal
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = lightIcons
                isAppearanceLightNavigationBars = lightIcons
            }
        }

        fun runSystemWrite(operation: String, paths: List<String>, action: () -> Unit) {
            val protectedPaths = paths.distinct().filter(PathTools::isProtectedSystemPath)
            if (settings.confirmSystemWrites && protectedPaths.isNotEmpty()) {
                modal = ModalState.SystemWrite(operation, protectedPaths, action)
            } else {
                modal = null
                action()
            }
        }

        val paneA by viewModel.paneA.collectAsStateWithLifecycle()
        val paneB by viewModel.paneB.collectAsStateWithLifecycle()
        val activePane by viewModel.activePane.collectAsStateWithLifecycle()
        val rootState by viewModel.rootState.collectAsStateWithLifecycle()
        val operation by viewModel.operation.collectAsStateWithLifecycle()
        val pendingTransfer by viewModel.pendingTransfer.collectAsStateWithLifecycle()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is AppEvent.Message -> message = event.text
                    is AppEvent.EditText -> openSecondary(WorkScreen.Editor(event.entry, event.content))
                    is AppEvent.ShowPermissions -> modal = ModalState.Permissions(event.entry)
                    is AppEvent.OpenExternal -> launchPreparedFile(context, event.file, event.share) { message = it }
                }
            }
        }
        LaunchedEffect(pendingTransfer) {
            pendingTransfer?.let { modal = ModalState.Transfer(it) }
        }
        LaunchedEffect(message) {
            if (message != null) {
                kotlinx.coroutines.delay(2800)
                message = null
            }
        }

        // The provider records live page content; glass overlays are rendered outside it so they
        // never sample themselves and can safely use the RuntimeShader blur path.
        val backdropSurface = MiuixTheme.colorScheme.surface
        val backdrop = rememberSampledLayerBackdrop(
            maxFps = 30,
        ) {
            drawRect(backdropSurface)
            drawContent()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val radius = 16.dp.toPx() * secondaryProgress
                        renderEffect = if (radius > 0f) BlurEffect(radius, radius) else null
                    },
            ) {
                MainShell(
                    section = section,
                    dark = dark,
                    onSectionChange = { section = it },
                    settings = settings,
                    backdrop = backdrop,
                    paneA = paneA,
                    paneB = paneB,
                    activePane = activePane,
                    rootState = rootState,
                    hasAllFilesAccess = hasAllFilesAccess,
                    nativeArchitecture = viewModel.nativeArchitecture,
                    onOpenTerminal = {
                        openSecondary(WorkScreen.Terminal(
                            paneFor(activePane, paneA, paneB).path,
                            settings.rootFileAccess && rootState == RootState.GRANTED,
                        ))
                    },
                    onRequestAllFilesAccess = onRequestAllFilesAccess,
                    onRequestSafTree = onRequestSafTree,
                    onRootCheck = viewModel::checkRoot,
                    onActivePane = viewModel::setActivePane,
                    onNavigate = viewModel::navigate,
                    onNavigateUp = viewModel::navigateUp,
                    onRefresh = viewModel::refresh,
                    onOpenEntry = { pane, entry ->
                        val paneState = paneFor(pane, paneA, paneB)
                        when {
                            paneState.selected.isNotEmpty() -> viewModel.openEntry(pane, entry)
                            entry.archive == null && entry.isShellScript -> modal = ModalState.Script(pane, entry)
                            entry.archive == null && entry.isApk -> modal = ModalState.InstallApk(pane, entry)
                            else -> viewModel.openEntry(pane, entry)
                        }
                    },
                    onSelectSwipe = viewModel::selectSwipe,
                    onLongPress = { pane, entry ->
                        val state = paneFor(pane, paneA, paneB)
                        val selected = state.visibleEntries.filter { it.path in state.selected }
                        modal = ModalState.FileActions(pane, entry, selected.ifEmpty { listOf(entry) }, state.selected.size)
                    },
                    onSetQuery = viewModel::setQuery,
                    onSetSort = viewModel::setSort,
                    onSwapPanes = viewModel::swapPanes,
                    onCreateDirectory = { modal = ModalState.Create(activePane, true) },
                    onCreateFile = { modal = ModalState.Create(activePane, false) },
                    onThemeMode = viewModel::setThemeMode,
                    onHaptics = viewModel::setHaptics,
                    onConfirmSystemWrites = viewModel::setConfirmSystemWrites,
                    onRootFileAccess = viewModel::setRootFileAccess,
                    onTerminalFontSize = viewModel::setTerminalFontSize,
                    onAnimationSpeed = viewModel::setAnimationSpeed,
                    onOpenDebug = { openSecondary(WorkScreen.Debug) },
                )
            }

            secondaryScreen?.let { screen ->
                var terminalViewReady by remember(screen) { mutableStateOf(false) }
                LaunchedEffect(secondaryVisible, screen) {
                    if (screen is WorkScreen.Terminal && secondaryVisible) {
                        snapshotFlow { secondaryProgress }.first { it >= 0.999f }
                        terminalViewReady = true
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = size.width * (1f - secondaryProgress) },
                ) {
                    when (screen) {
                        is WorkScreen.Terminal -> TerminalScreen(
                            rootAvailable = rootState == RootState.GRANTED,
                            fontSize = settings.terminalFontSize,
                            workingDirectory = screen.directory,
                            initialRoot = screen.root,
                            initialCommand = screen.command,
                            showTerminalView = terminalViewReady,
                            onBack = { secondaryVisible = false },
                        )

                        is WorkScreen.Editor -> EditorScreen(
                            screen = screen,
                            onBack = { secondaryVisible = false },
                            onSave = { content ->
                                runSystemWrite("保存文本", listOf(screen.entry.path)) {
                                    viewModel.saveText(screen.entry, content)
                                    secondaryVisible = false
                                }
                            },
                        )
                        WorkScreen.Debug -> CedarDebugSettingsScreen(
                            animationSpeed = settings.animationSpeed,
                            onAnimationSpeed = viewModel::setAnimationSpeed,
                            tiltStrength = settings.tiltStrength,
                            onTiltStrength = viewModel::setTiltStrength,
                            onBack = { secondaryVisible = false },
                        )
                        WorkScreen.Main -> Unit
                    }
                }
            }

            CedarTopNotificationHost(
                operation = operation,
                message = message,
                dark = dark,
                backdrop = backdrop,
                onCancelOperation = viewModel::cancelOperation,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        when (val current = modal) {
            null -> Unit
            is ModalState.Create -> NameDialog(
                title = if (current.directory) "新建目录" else "新建文件",
                initial = "",
                confirmLabel = "创建",
                onDismiss = { modal = null },
                onConfirm = { name ->
                    val target = PathTools.join(paneFor(current.pane, paneA, paneB).path, name)
                    runSystemWrite(if (current.directory) "新建目录" else "新建文件", listOf(target)) {
                        if (current.directory) viewModel.createDirectory(current.pane, name)
                        else viewModel.createFile(current.pane, name)
                    }
                },
            )
            is ModalState.Rename -> NameDialog(
                title = "重命名",
                initial = current.entry.name,
                confirmLabel = "保存",
                onDismiss = { modal = null },
                onConfirm = { name ->
                    val target = PathTools.join(PathTools.parent(current.entry.path) ?: "/", name)
                    runSystemWrite("重命名", listOf(current.entry.path, target)) {
                        viewModel.rename(current.pane, current.entry, name)
                    }
                },
            )
            is ModalState.Delete -> ConfirmDialog(
                title = "永久删除 ${current.count} 项？",
                message = "删除后无法在 Cedar Manager 中撤销。",
                destructive = true,
                onDismiss = { modal = null },
                onConfirm = {
                    val selected = paneFor(current.pane, paneA, paneB).selected.toList()
                    runSystemWrite("永久删除", selected) { viewModel.deleteSelected(current.pane) }
                },
            )
            is ModalState.Permissions -> PermissionsDialog(
                entry = current.entry,
                onDismiss = { modal = null },
                onConfirm = { spec ->
                    runSystemWrite("修改权限与所有者", listOf(current.entry.path)) {
                        viewModel.savePermissions(current.entry, spec)
                    }
                },
            )
            is ModalState.Transfer -> TransferDialog(
                request = current.request,
                onDismiss = { viewModel.cancelTransfer(); modal = null },
                onConfirm = { policy ->
                    val writtenPaths = buildList {
                        add(current.request.targetDirectory)
                        if (current.request.move) addAll(current.request.sources.map { it.path })
                    }
                    runSystemWrite(if (current.request.move) "移动文件" else "复制文件", writtenPaths) {
                        viewModel.executeTransfer(policy)
                    }
                },
            )
            is ModalState.InstallApk -> ConfirmDialog(
                title = "使用 Root 安装 APK？",
                message = buildString {
                    append(current.entry.path)
                    append("\n\nRoot 状态：")
                    append(rootState.displayName())
                },
                destructive = false,
                confirmLabel = "Root 安装",
                onDismiss = { modal = null },
                onConfirm = {
                    modal = null
                    viewModel.installApk(current.pane, current.entry)
                },
            )
            is ModalState.Script -> ScriptLaunchDialog(
                scriptPath = current.entry.path,
                rootAvailable = rootState == RootState.GRANTED,
                initialRoot = settings.rootFileAccess,
                onDismiss = { modal = null },
                onEdit = {
                    modal = null
                    viewModel.openText(current.pane, current.entry)
                },
                onRun = { root, args ->
                    val path = current.entry.path
                    val command = ShellCommandBuilder.launch(path, args)
                    modal = null
                    openSecondary(WorkScreen.Terminal(
                        PathTools.parent(path, stopAtSharedStorageRoot = false) ?: "/",
                        root,
                        command,
                    ))
                },
            )
            is ModalState.FileActions -> FileActionsDialog(
                entry = current.entry,
                targetCount = current.targets.size,
                selectionCount = current.selectionCount,
                archiveEntry = current.targets.firstOrNull()?.archive != null,
                archiveFile = current.targets.singleOrNull()
                    ?.let { it.archive == null && it.isArchive } == true,
                onDismiss = { modal = null },
                onSelectionAction = { action ->
                    modal = null
                    when (action) {
                        SelectionAction.SELECT_ALL -> viewModel.selectAll(current.pane)
                        SelectionAction.INVERT -> viewModel.invertSelection(current.pane)
                        SelectionAction.CLEAR -> viewModel.clearSelection(current.pane)
                        SelectionAction.SIMILAR -> viewModel.selectSimilar(current.pane)
                    }
                },
                onAction = { action ->
                    val targets = current.targets
                    val entry = targets.singleOrNull() ?: current.entry
                    val selectTargets = { viewModel.selectPaths(current.pane, targets.map { it.path }) }
                    modal = null
                    when (action) {
                        FileAction.COPY -> {
                            selectTargets()
                            viewModel.prepareTransfer(false)
                        }
                        FileAction.MOVE -> {
                            selectTargets()
                            viewModel.prepareTransfer(true)
                        }
                        FileAction.DELETE -> {
                            selectTargets()
                            modal = ModalState.Delete(current.pane, targets.size)
                        }
                        FileAction.RENAME -> if (targets.size == 1) modal = ModalState.Rename(current.pane, entry)
                        FileAction.TOOLS -> if (targets.size == 1) modal = ModalState.FileTools(current.pane, entry)
                        FileAction.ARCHIVE -> {
                            selectTargets()
                            runSystemWrite("创建压缩包", targets.map { it.path }) {
                                viewModel.archiveSelected(current.pane)
                            }
                        }
                        FileAction.EXTRACT -> if (targets.size == 1) {
                            runSystemWrite("解压", listOf(entry.path)) {
                                viewModel.extractArchive(current.pane, entry)
                            }
                        }
                        FileAction.PROPERTIES -> if (targets.size == 1) modal = ModalState.Properties(entry)
                        FileAction.SHARE -> if (targets.size == 1) viewModel.openExternal(entry, share = true)
                        FileAction.OPEN_WITH -> if (targets.size == 1) viewModel.openExternal(entry, share = false)
                        FileAction.BOOKMARK -> Unit
                    }
                },
            )
            is ModalState.FileTools -> CedarDialogSurface(onDismiss = { modal = null }) {
                Text(current.entry.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                CedarMenuItem("permissions", "修改权限") {
                    modal = ModalState.Permissions(current.entry)
                }
                CedarMenuItem("terminal", "在目录中打开终端") {
                    val directory = if (current.entry.isDirectory) current.entry.path
                        else PathTools.parent(current.entry.path, stopAtSharedStorageRoot = false) ?: "/"
                    modal = null
                    openSecondary(WorkScreen.Terminal(directory, settings.rootFileAccess && rootState == RootState.GRANTED))
                }
                if (current.entry.isText) {
                    CedarMenuItem("edit", "编辑文本") {
                        modal = null
                        viewModel.openText(current.pane, current.entry)
                    }
                }
                if (current.entry.extension == "zip") {
                    CedarMenuItem("archive", "解压") {
                        modal = null
                        runSystemWrite("解压", listOf(current.entry.path)) {
                            viewModel.extractArchive(current.pane, current.entry)
                        }
                    }
                }
            }
            is ModalState.Properties -> CedarDialogSurface(onDismiss = { modal = null }) {
                Text("属性", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                KeyValue("名称", current.entry.name)
                KeyValue("路径", current.entry.path)
                KeyValue(
                    "类型",
                    when {
                        current.entry.archive != null && current.entry.isDirectory -> "压缩包内文件夹"
                        current.entry.archive != null -> "压缩包内文件"
                        current.entry.isArchive -> "ZIP 压缩包"
                        current.entry.isDirectory -> "文件夹"
                        else -> "文件"
                    },
                )
                KeyValue("大小", formatBytes(current.entry.size))
                if (current.entry.archive != null) {
                    KeyValue("所属压缩包", current.entry.archive.archivePath)
                    KeyValue("包内路径", "/" + current.entry.archive.relativePath(current.entry.path))
                } else {
                    KeyValue("权限", current.entry.mode.toString(8).padStart(3, '0'))
                    KeyValue("所有者", "${current.entry.owner}:${current.entry.group}")
                }
                KeyValue("修改时间", java.text.DateFormat.getDateTimeInstance().format(java.util.Date(current.entry.modifiedAt)))
                CedarDialogActions({ modal = null }, { modal = null }, "完成", true)
            }
            is ModalState.SystemWrite -> ConfirmDialog(
                title = "确认写入系统路径？",
                message = buildString {
                    append(current.operation)
                    append("将影响：\n")
                    append(current.paths.joinToString("\n"))
                },
                destructive = false,
                confirmLabel = "继续",
                onDismiss = { modal = null },
                onConfirm = {
                    val action = current.onConfirm
                    modal = null
                    action()
                },
            )
        }
        }
    }
}

@Composable
private fun MainShell(
    section: MainSection,
    dark: Boolean,
    onSectionChange: (MainSection) -> Unit,
    settings: AppSettings,
    backdrop: SampledLayerBackdrop,
    paneA: PaneState,
    paneB: PaneState,
    activePane: PaneId,
    rootState: RootState,
    hasAllFilesAccess: Boolean,
    nativeArchitecture: String,
    onOpenTerminal: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onRequestSafTree: () -> Unit,
    onRootCheck: () -> Unit,
    onActivePane: (PaneId) -> Unit,
    onNavigate: (PaneId, String) -> Unit,
    onNavigateUp: (PaneId) -> Unit,
    onRefresh: (PaneId) -> Unit,
    onOpenEntry: (PaneId, FileEntry) -> Unit,
    onSelectSwipe: (PaneId, String) -> Unit,
    onLongPress: (PaneId, FileEntry) -> Unit,
    onSetQuery: (String) -> Unit,
    onSetSort: (SortMode) -> Unit,
    onSwapPanes: () -> Unit,
    onCreateDirectory: () -> Unit,
    onCreateFile: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onConfirmSystemWrites: (Boolean) -> Unit,
    onRootFileAccess: (Boolean) -> Unit,
    onTerminalFontSize: (Int) -> Unit,
    onAnimationSpeed: (Float) -> Unit,
    onOpenDebug: () -> Unit,
) {
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val pagerState = rememberPagerState(initialPage = section.ordinal) { MainSection.entries.size }
    var commandedPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(section) {
        if (pagerState.currentPage != section.ordinal) {
            commandedPage = section.ordinal
            try {
                pagerState.animateScrollToPage(section.ordinal)
            } finally {
                commandedPage = null
            }
        }
    }
    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress, commandedPage) {
        if (!pagerState.isScrollInProgress && commandedPage == null) {
            MainSection.entries.getOrNull(pagerState.settledPage)?.let { page ->
            if (page != section) onSectionChange(page)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .sampledLayerBackdrop(backdrop, active = pagerState.isScrollInProgress),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
            ) { page ->
                when (MainSection.entries[page]) {
                MainSection.HOME -> HomePage(
                    rootState = rootState,
                    hasAllFilesAccess = hasAllFilesAccess,
                    onOpenTerminal = onOpenTerminal,
                    onRootCheck = onRootCheck,
                    onStorageAccess = onRequestAllFilesAccess,
                )
                MainSection.FILES -> FilesPage(
                    paneA = paneA,
                    paneB = paneB,
                    activePane = activePane,
                    rootFileAccess = settings.rootFileAccess,
                    dark = dark,
                    onActivePane = onActivePane,
                    onNavigate = onNavigate,
                    onNavigateUp = onNavigateUp,
                    onRefresh = onRefresh,
                    onOpenEntry = onOpenEntry,
                    onSelectSwipe = onSelectSwipe,
                    onLongPress = onLongPress,
                    onSetQuery = onSetQuery,
                    onSetSort = onSetSort,
                    onSwapPanes = onSwapPanes,
                    onCreateDirectory = onCreateDirectory,
                    onCreateFile = onCreateFile,
                )
                MainSection.SETTINGS -> SettingsPage(
                    settings = settings,
                    rootState = rootState,
                    hasAllFilesAccess = hasAllFilesAccess,
                    nativeArchitecture = nativeArchitecture,
                    dark = dark,
                    onRootCheck = onRootCheck,
                    onRequestAllFilesAccess = onRequestAllFilesAccess,
                    onRequestSafTree = onRequestSafTree,
                    onThemeMode = onThemeMode,
                    onHaptics = onHaptics,
                    onConfirmSystemWrites = onConfirmSystemWrites,
                    onRootFileAccess = onRootFileAccess,
                    onTerminalFontSize = onTerminalFontSize,
                    onAnimationSpeed = onAnimationSpeed,
                    onOpenDebug = onOpenDebug,
                )
                }
            }
        }

        if (!imeVisible) {
            CedarNavigationDock(
                items = MainSection.entries.map { CedarNavigationItem(it.label, it.icon) },
                selectedIndex = section.ordinal,
                dark = dark,
                backdrop = backdrop,
                onSelect = { index -> MainSection.entries.getOrNull(index)?.let(onSectionChange) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun HomePage(
    rootState: RootState,
    hasAllFilesAccess: Boolean,
    onOpenTerminal: () -> Unit,
    onRootCheck: () -> Unit,
    onStorageAccess: () -> Unit,
) {
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val safeTop = safeInsets.calculateTopPadding()
    val safeBottom = safeInsets.calculateBottomPadding()
    val headerHeight = safeTop + 80.dp
    val storageState = rememberHomeStorage()
    val deviceDetails = rememberDeviceDetails()
    val scrollState = rememberScrollState()
    val backdropSurface = MiuixTheme.colorScheme.surface
    val titleBackdrop = rememberSampledLayerBackdrop(
        maxFps = 30,
        capture = BackdropCapture.Top(headerHeight),
    ) {
        drawRect(backdropSurface)
        drawContent()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val topHeight = if (maxWidth < 380.dp) 168.dp else 188.dp
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .sampledLayerBackdrop(titleBackdrop, active = scrollState.isScrollInProgress)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp),
            ) {
                Spacer(Modifier.height(headerHeight))
                HomeSummaryRow(
                    rootState = rootState,
                    hasAllFilesAccess = hasAllFilesAccess,
                    storage = storageState.snapshot,
                    storageLoading = storageState.loading,
                    onRootCheck = onRootCheck,
                    onStorageAccess = onStorageAccess,
                    height = topHeight,
                )
                Spacer(Modifier.height(10.dp))
                HomeTerminalEntry(onOpenTerminal = onOpenTerminal)
                Spacer(Modifier.height(10.dp))
                HomeDeviceInfoCard(details = deviceDetails)
                Spacer(Modifier.height(safeBottom + 200.dp))
            }
            CedarHomeHeader(
                height = headerHeight,
                backdrop = titleBackdrop,
                scrollState = scrollState,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

@Composable
private fun HomeSummaryRow(
    rootState: RootState,
    hasAllFilesAccess: Boolean,
    storage: StorageSnapshot?,
    storageLoading: Boolean,
    onRootCheck: () -> Unit,
    onStorageAccess: () -> Unit,
    height: Dp,
) {
    Row(
        Modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HomeStorageCard(
            storage = storage,
            loading = storageLoading,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomeStatusMiniCard(
                icon = "root",
                label = "Root 权限",
                value = rootAvailability(rootState),
                accent = rootState == RootState.GRANTED,
                onClick = onRootCheck,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            HomeStatusMiniCard(
                icon = "permissions",
                label = "文件访问",
                value = if (hasAllFilesAccess) "已开启" else "未开启",
                accent = hasAllFilesAccess,
                onClick = if (hasAllFilesAccess) null else onStorageAccess,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FilesPage(
    paneA: PaneState,
    paneB: PaneState,
    activePane: PaneId,
    rootFileAccess: Boolean,
    dark: Boolean,
    onActivePane: (PaneId) -> Unit,
    onNavigate: (PaneId, String) -> Unit,
    onNavigateUp: (PaneId) -> Unit,
    onRefresh: (PaneId) -> Unit,
    onOpenEntry: (PaneId, FileEntry) -> Unit,
    onSelectSwipe: (PaneId, String) -> Unit,
    onLongPress: (PaneId, FileEntry) -> Unit,
    onSetQuery: (String) -> Unit,
    onSetSort: (SortMode) -> Unit,
    onSwapPanes: () -> Unit,
    onCreateDirectory: () -> Unit,
    onCreateFile: () -> Unit,
) {
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var moreVisible by remember { mutableStateOf(false) }
    var morePopupPresented by remember { mutableStateOf(false) }
    var pathDialogFor by remember { mutableStateOf<PaneId?>(null) }
    val activePath = paneFor(activePane, paneA, paneB).path
    val statusBarHeight = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val pathBarHeight = 76.dp
    val topBarHeight = statusBarHeight + pathBarHeight
    val backdropSurface = MiuixTheme.colorScheme.surface
    val topBackdrop = rememberSampledLayerBackdrop(
        maxFps = 30,
        capture = BackdropCapture.Top(topBarHeight),
    ) {
        drawRect(backdropSurface)
        drawContent()
    }
    var paneAMoving by remember { mutableStateOf(false) }
    var paneBMoving by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .sampledLayerBackdrop(topBackdrop, active = paneAMoving || paneBMoving),
        ) {
            if (searchVisible) {
                Spacer(Modifier.height(topBarHeight))
                TextField(
                    value = paneFor(activePane, paneA, paneB).query,
                    onValueChange = onSetQuery,
                    label = "搜索当前目录",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val fontScale = LocalDensity.current.fontScale
                val vertical = maxWidth < 360.dp || fontScale >= 1.5f
                val topInset = if (searchVisible) 0.dp else topBarHeight
                if (vertical) {
                    Column(Modifier.fillMaxSize()) {
                        FilePane(
                            state = paneA,
                            active = activePane == PaneId.A,
                            rootFileAccess = rootFileAccess,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(top = topInset, bottom = 76.dp),
                            onActivate = { onActivePane(PaneId.A) },
                            onNavigateUp = { onNavigateUp(PaneId.A) },
                            onRefresh = { onRefresh(PaneId.A) },
                            onOpen = { onOpenEntry(PaneId.A, it) },
                            onLongPress = { onLongPress(PaneId.A, it) },
                            onSwipeRight = { onSelectSwipe(PaneId.A, it.path) },
                            onScrollingChanged = { paneAMoving = it },
                        )
                        Spacer(Modifier.fillMaxWidth().height(1.dp).background(MiuixTheme.colorScheme.dividerLine))
                        FilePane(
                            state = paneB,
                            active = activePane == PaneId.B,
                            rootFileAccess = rootFileAccess,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 76.dp),
                            onActivate = { onActivePane(PaneId.B) },
                            onNavigateUp = { onNavigateUp(PaneId.B) },
                            onRefresh = { onRefresh(PaneId.B) },
                            onOpen = { onOpenEntry(PaneId.B, it) },
                            onLongPress = { onLongPress(PaneId.B, it) },
                            onSwipeRight = { onSelectSwipe(PaneId.B, it.path) },
                            onScrollingChanged = { paneBMoving = it },
                        )
                    }
                } else {
                    Row(Modifier.fillMaxSize()) {
                        FilePane(
                            state = paneA,
                            active = activePane == PaneId.A,
                            rootFileAccess = rootFileAccess,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(top = topInset, bottom = 76.dp),
                            onActivate = { onActivePane(PaneId.A) },
                            onNavigateUp = { onNavigateUp(PaneId.A) },
                            onRefresh = { onRefresh(PaneId.A) },
                            onOpen = { onOpenEntry(PaneId.A, it) },
                            onLongPress = { onLongPress(PaneId.A, it) },
                            onSwipeRight = { onSelectSwipe(PaneId.A, it.path) },
                            onScrollingChanged = { paneAMoving = it },
                        )
                        Spacer(Modifier.width(1.dp).fillMaxHeight().background(MiuixTheme.colorScheme.dividerLine))
                        FilePane(
                            state = paneB,
                            active = activePane == PaneId.B,
                            rootFileAccess = rootFileAccess,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(top = topInset, bottom = 76.dp),
                            onActivate = { onActivePane(PaneId.B) },
                            onNavigateUp = { onNavigateUp(PaneId.B) },
                            onRefresh = { onRefresh(PaneId.B) },
                            onOpen = { onOpenEntry(PaneId.B, it) },
                            onLongPress = { onLongPress(PaneId.B, it) },
                            onSwipeRight = { onSelectSwipe(PaneId.B, it.path) },
                            onScrollingChanged = { paneBMoving = it },
                        )
                    }
                }
            }
        }
        CedarFilePathBar(
            path = activePath,
            height = topBarHeight,
            topInset = statusBarHeight,
            backdrop = topBackdrop,
            onPathClick = {
                if (moreVisible) moreVisible = false else pathDialogFor = activePane
            },
        )

        LiquidGlassPopupSurface(
            visible = moreVisible,
            onDismiss = { moreVisible = false },
            onPresentationChanged = { morePopupPresented = it },
            backdrop = topBackdrop,
            dark = dark,
            expandedWidth = 204.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusBarHeight + 14.dp, end = 12.dp),
        ) {
            MoreToolsMenu(
                onSearch = { searchVisible = !searchVisible; moreVisible = false },
                onSort = { onSetSort(nextSort(paneFor(activePane, paneA, paneB).sortMode)); moreVisible = false },
                onRefresh = { onRefresh(activePane); moreVisible = false },
                onSwap = { onSwapPanes(); moreVisible = false },
                onNewDirectory = { onCreateDirectory(); moreVisible = false },
                onNewFile = { onCreateFile(); moreVisible = false },
            )
        }

        if (!moreVisible && !morePopupPresented) {
            CedarFileMoreButton(
                backdrop = topBackdrop,
                onClick = { moreVisible = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusBarHeight + 14.dp, end = 12.dp),
            )
        }
    }

    pathDialogFor?.let { id ->
        NameDialog(
            title = "跳转路径",
            initial = paneFor(id, paneA, paneB).path,
            confirmLabel = "打开",
            onDismiss = { pathDialogFor = null },
            onConfirm = { onNavigate(id, it); pathDialogFor = null },
        )
    }
}

@Composable
private fun MoreToolsMenu(
    onSearch: () -> Unit,
    onSort: () -> Unit,
    onRefresh: () -> Unit,
    onSwap: () -> Unit,
    onNewDirectory: () -> Unit,
    onNewFile: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        CedarMenuItem("search", "搜索", onClick = onSearch)
        CedarMenuItem("sort", "排序", onClick = onSort)
        CedarMenuItem("refresh", "刷新当前栏", onClick = onRefresh)
        CedarMenuItem("swap", "交换两栏", onClick = onSwap)
        CedarMenuItem("plus", "新建目录", onClick = onNewDirectory)
        CedarMenuItem("file", "新建文件", onClick = onNewFile)
    }
}

private fun launchPreparedFile(context: android.content.Context, file: File, share: Boolean, onError: (String) -> Unit) {
    runCatching {
        val authority = "${context.packageName}.files"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        val intent = if (share) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uri); type = mime }
        } else {
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime) }
        }.apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(if (share) Intent.createChooser(intent, "分享文件") else intent)
    }.onFailure { error ->
        if (error is ActivityNotFoundException) onError("没有可用的应用打开此文件")
        else onError(error.message ?: "无法打开文件")
    }
}

@Composable
private fun FileActionsDialog(
    entry: FileEntry,
    targetCount: Int,
    selectionCount: Int,
    archiveEntry: Boolean,
    archiveFile: Boolean,
    onDismiss: () -> Unit,
    onSelectionAction: (SelectionAction) -> Unit,
    onAction: (FileAction) -> Unit,
) {
    val singleArchive = archiveFile && targetCount == 1
    val allActions = listOf(
        Triple(FileAction.COPY, "复制 →", "copy"),
        Triple(FileAction.MOVE, "移动 →", "move"),
        Triple(FileAction.DELETE, "删除", "trash"),
        Triple(FileAction.RENAME, "重命名", "edit"),
        Triple(FileAction.TOOLS, "工具", "settings"),
        Triple(if (singleArchive) FileAction.EXTRACT else FileAction.ARCHIVE, if (singleArchive) "解压" else "压缩", "archive"),
        Triple(FileAction.PROPERTIES, "属性", "permissions"),
        Triple(FileAction.SHARE, "分享", "copy"),
        Triple(FileAction.OPEN_WITH, "打开方式", "file"),
        Triple(FileAction.BOOKMARK, "添加书签", "plus"),
    )
    val actions = if (archiveEntry) {
        allActions.filterNot { (action, _, _) ->
            action in setOf(FileAction.TOOLS, FileAction.ARCHIVE, FileAction.EXTRACT, FileAction.BOOKMARK)
        }
    } else {
        allActions
    }
    CedarDialogSurface(onDismiss) {
        Text(
            when {
                targetCount > 1 -> "$targetCount 项已选"
                selectionCount > 0 -> "$selectionCount 项已选"
                else -> entry.name
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (targetCount > 1) "长按：${entry.name}" else entry.path,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
        )
        actions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (action, label, icon) ->
                    val singleItemOnly = action in setOf(
                        FileAction.RENAME,
                        FileAction.TOOLS,
                        FileAction.PROPERTIES,
                        FileAction.SHARE,
                        FileAction.OPEN_WITH,
                    )
                    val enabled = action != FileAction.BOOKMARK && !(targetCount > 1 && singleItemOnly)
                    Row(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = enabled) { onAction(action) }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CedarSvgIcon(
                            icon,
                            label,
                            size = 20.dp,
                            tint = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface,
                        )
                    }
                }
            }
        }
        if (selectionCount > 0) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MiuixTheme.colorScheme.outline.copy(alpha = 0.25f)))
            listOf(
                SelectionAction.SELECT_ALL to ("check" to "全选"),
                SelectionAction.INVERT to ("swap" to "反选"),
                SelectionAction.CLEAR to ("close" to "取消"),
                SelectionAction.SIMILAR to ("file" to "相同文件"),
            ).chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (action, item) ->
                        val (icon, label) = item
                        Row(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelectionAction(action) }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CedarSvgIcon(icon, label, size = 19.dp, tint = MiuixTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilePane(
    state: PaneState,
    active: Boolean,
    rootFileAccess: Boolean,
    modifier: Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onActivate: () -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (FileEntry) -> Unit,
    onLongPress: (FileEntry) -> Unit,
    onSwipeRight: (FileEntry) -> Unit,
    onScrollingChanged: (Boolean) -> Unit,
) {
    val visibleEntries = remember(state.entries, state.query, state.sortMode, state.ascending) {
        state.visibleEntries
    }
    val listState = remember(state.path, rootFileAccess, state.query, state.sortMode, state.ascending) {
        LazyListState()
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect(onScrollingChanged)
    }
    DisposableEffect(listState) {
        onDispose { onScrollingChanged(false) }
    }
    val revealProgress = remember(state.path, rootFileAccess, state.entries) {
        Animatable(if (state.entries.isEmpty()) 1f else 0f)
    }
    val revealFinished by remember(revealProgress) { derivedStateOf { revealProgress.value >= 0.999f } }
    LaunchedEffect(revealProgress) {
        if (shouldStartFileReveal(state.entries.isNotEmpty(), revealFinished)) {
            revealProgress.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
        }
    }
    val configuration = LocalConfiguration.current
    val dateFormatter = remember(configuration) {
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
    }
    Column(
        modifier
            .pointerInput(onActivate) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    onActivate()
                }
            }
            .clipToBounds()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        val hasParent = state.archive != null ||
            PathTools.parent(state.path, stopAtSharedStorageRoot = !rootFileAccess) != null
        val statusText = when {
            state.error != null -> state.error
            state.query.isBlank() && state.archive?.innerPath.isNullOrEmpty() && state.archive != null -> "压缩包为空"
            state.query.isBlank() -> "空文件夹"
            state.archive != null -> "压缩包内没有匹配文件"
            else -> "没有匹配文件"
        }
        val showInlineStatus = state.error != null || (!state.loading && visibleEntries.isEmpty())

        if (!hasParent && showInlineStatus) {
            CenterStatus(statusText, error = state.error != null, action = onRefresh.takeIf { state.error != null })
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = contentPadding,
            ) {
                if (hasParent) {
                    item(key = "parent:${state.path}", contentType = "parent") {
                        ParentFolderRow(
                            onClick = {
                                onActivate()
                                onNavigateUp()
                            },
                        )
                    }
                }
                if (showInlineStatus) {
                    item(key = "status:${state.path}:${statusText}", contentType = "status") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                statusText,
                                fontSize = 12.sp,
                                color = if (state.error != null) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                            if (state.error != null) {
                                TextButton("重试", onRefresh, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                }
                items(
                    visibleEntries,
                    key = { entry -> "file:${entry.path}" },
                    contentType = { _ -> "file-row" },
                ) { entry ->
                    FileRevealItem(
                        progress = revealProgress,
                        finished = revealFinished,
                    ) {
                        FileRow(
                            entry = entry,
                            selected = entry.path in state.selected,
                            dateFormatter = dateFormatter,
                            onClick = { onActivate(); onOpen(entry) },
                            onLongClick = { onActivate(); onLongPress(entry) },
                            onSwipeRight = { onActivate(); onSwipeRight(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    settings: AppSettings,
    rootState: RootState,
    hasAllFilesAccess: Boolean,
    nativeArchitecture: String,
    dark: Boolean,
    onRootCheck: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onRequestSafTree: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onConfirmSystemWrites: (Boolean) -> Unit,
    onRootFileAccess: (Boolean) -> Unit,
    onTerminalFontSize: (Int) -> Unit,
    onAnimationSpeed: (Float) -> Unit,
    onOpenDebug: () -> Unit,
) {
    var themeMenuVisible by remember { mutableStateOf(false) }
    var themeMenuPresented by remember { mutableStateOf(false) }
    var themeAnchorPosition by remember { mutableStateOf(Offset.Zero) }
    var themeAnchorSize by remember { mutableStateOf(IntSize.Zero) }
    var pageCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val safeTop = safeInsets.calculateTopPadding()
    val safeBottom = safeInsets.calculateBottomPadding()
    val titleBarHeight = safeTop + 76.dp
    val backdropSurface = MiuixTheme.colorScheme.surface
    val titleBackdrop = rememberSampledLayerBackdrop(
        maxFps = 30,
        capture = BackdropCapture.Top(titleBarHeight),
    ) {
        drawRect(backdropSurface)
        drawContent()
    }
    val density = LocalDensity.current
    val themeMenuWidth = 184.dp
    val themeMenuWidthPx = with(density) { themeMenuWidth.roundToPx() }
    val themeMenuRightInsetPx = with(density) { 7.dp.roundToPx() }

    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize().onGloballyPositioned { pageCoordinates = it }) {
        Column(
            Modifier
                .fillMaxSize()
                .sampledLayerBackdrop(titleBackdrop, active = scrollState.isScrollInProgress)
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(titleBarHeight))
            SectionLabel("外观")
            MiuixGroup {
                CedarDropdownSetting(
                    title = "主题",
                    value = settings.themeMode.displayName(),
                    expanded = themeMenuVisible || themeMenuPresented,
                    onClick = { themeMenuVisible = !themeMenuVisible },
                    onPositioned = { anchor ->
                        pageCoordinates?.takeIf { it.isAttached }?.let { page ->
                            themeAnchorPosition = page.localPositionOf(anchor, Offset.Zero)
                            themeAnchorSize = anchor.size
                        }
                    },
                )
                SwitchSetting("触感反馈", "选择与确认操作使用系统触感", settings.haptics, onHaptics)
            }

            SectionLabel("开发者")
            MiuixGroup {
                ActionSetting(
                    "调试",
                    "全局动画速度 ×${formatAnimationSpeed(settings.animationSpeed)}",
                    "settings",
                    onOpenDebug,
                )
            }

            SectionLabel("文件与 Root")
            MiuixGroup {
                ActionSetting("Root 权限", rootState.displayName(), "root", onRootCheck)
                SwitchSetting(
                    "Root 文件访问",
                    "开启后文件读取、路径跳转与写入均使用 Root",
                    settings.rootFileAccess,
                    onRootFileAccess,
                )
                ActionSetting("全文件访问", if (hasAllFilesAccess) "已开启" else "未开启", "storage", onRequestAllFilesAccess)
                ActionSetting("SAF 授权目录", settings.safTreeUri ?: "未选择", "folder", onRequestSafTree)
                SwitchSetting("系统路径二次确认", "保护 /system、/vendor 与 /product", settings.confirmSystemWrites, onConfirmSystemWrites)
            }

            SectionLabel("终端")
            MiuixGroup { StepperSetting("字体大小", settings.terminalFontSize, onTerminalFontSize) }

            SectionLabel("关于")
            MiuixGroup {
                PlainRow("应用", "Cedar Manager 1.0.0", "app")
                PlainRow("包名", "com.openxida.cedarmanager", "package")
                PlainRow("原生 ABI", nativeArchitecture, "code")
                PlainRow("界面字体", "MiSans · 小米", "settings")
            }
            Spacer(Modifier.height(safeBottom + 76.dp))
        }

        CedarGradientTitleBar(
            title = "设置",
            height = titleBarHeight,
            backdrop = titleBackdrop,
        )

        LiquidGlassPopupSurface(
            visible = themeMenuVisible,
            onDismiss = { themeMenuVisible = false },
            onPresentationChanged = { themeMenuPresented = it },
            backdrop = titleBackdrop,
            dark = dark,
            expandedWidth = themeMenuWidth,
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (themeAnchorPosition.x + themeAnchorSize.width - themeMenuWidthPx - themeMenuRightInsetPx)
                            .roundToInt()
                            .coerceAtLeast(0),
                        y = (themeAnchorPosition.y + themeAnchorSize.height * (45f / 179f))
                            .roundToInt()
                            .coerceAtLeast(0),
                    )
                },
        ) {
            Column {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    CedarChoiceMenuItem(
                        label = mode.displayName(),
                        selected = mode == settings.themeMode,
                        index = index,
                        optionSize = ThemeMode.entries.size,
                        onClick = {
                            themeMenuVisible = false
                            onThemeMode(mode)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(screen: WorkScreen.Editor, onBack: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(screen.entry.path) { mutableStateOf(screen.initialContent) }
    var confirmExit by remember { mutableStateOf(false) }
    val dirty = text != screen.initialContent
    val leave = { if (dirty) confirmExit = true else onBack() }
    BackHandler { leave() }
    Column(
        Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CedarIconButton("back", "返回", onClick = leave)
            Column(Modifier.weight(1f)) {
                Text(screen.entry.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (dirty) Text("未保存", fontSize = 10.sp, color = MiuixTheme.colorScheme.primary)
            }
            CedarIconButton("save", "保存") { onSave(text) }
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(
                color = MiuixTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            ),
            modifier = Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
        )
    }
    if (confirmExit) {
        ConfirmDialog(
            title = "放弃未保存的修改？",
            message = screen.entry.name,
            destructive = true,
            onDismiss = { confirmExit = false },
            onConfirm = onBack,
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    CedarDialogSurface(onDismiss) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        TextField(value, { value = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        CedarDialogActions(onDismiss, { onConfirm(value.trim()) }, confirmLabel, value.isNotBlank())
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    destructive: Boolean,
    confirmLabel: String? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    CedarDialogSurface(onDismiss) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(message, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(top = 8.dp))
        CedarDialogActions(onDismiss, onConfirm, confirmLabel ?: if (destructive) "确认删除" else "确认", true, destructive)
    }
}

@Composable
private fun PermissionsDialog(entry: FileEntry, onDismiss: () -> Unit, onConfirm: (PermissionSpec) -> Unit) {
    var spec by remember(entry.path) { mutableStateOf(PermissionSpec(entry.mode, entry.owner, entry.group)) }
    var octal by remember(entry.path) { mutableStateOf(spec.octal) }
    CedarDialogSurface(onDismiss) {
        Text("文件权限", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(entry.path, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
        Spacer(Modifier.height(12.dp))
        TextField(
            value = octal,
            onValueChange = { value ->
                octal = value.take(4)
                PermissionSpec.fromOctal(octal)?.let { spec = it.copy(owner = spec.owner, group = spec.group, recursive = spec.recursive) }
            },
            label = "八进制权限",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default,
        )
        CedarPermissionGrid(spec) { updated -> spec = updated; octal = updated.octal }
        TextField(spec.owner.orEmpty(), { spec = spec.copy(owner = it) }, label = "UID", modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        TextField(spec.group.orEmpty(), { spec = spec.copy(group = it) }, label = "GID", modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        SwitchSetting("递归应用", "目录内所有项目", spec.recursive) { spec = spec.copy(recursive = it) }
        CedarDialogActions(onDismiss, { onConfirm(spec) }, "应用", PermissionSpec.fromOctal(octal) != null)
    }
}

@Composable
private fun TransferDialog(request: TransferRequest, onDismiss: () -> Unit, onConfirm: (ConflictPolicy) -> Unit) {
    var policy by remember { mutableStateOf(ConflictPolicy.KEEP_BOTH) }
    val toArchive = request.targetArchive != null
    val fromArchive = request.sourceArchive != null
    CedarDialogSurface(onDismiss) {
        Text(
            when {
                toArchive && request.move -> "移动到压缩包"
                toArchive -> "添加到压缩包"
                fromArchive && request.move -> "解压并移出压缩包"
                fromArchive -> "解压到${request.targetPane.directionName()}"
                request.move -> "移动到${request.targetPane.directionName()}"
                else -> "复制到${request.targetPane.directionName()}"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        KeyValue("源", "${request.sourcePane.name} · ${request.sources.firstOrNull()?.path ?: ""}")
        KeyValue(
            "目标",
            buildString {
                append(request.targetPane.name)
                append(" · ")
                append(if (toArchive) request.targetArchive.virtualPath else request.targetDirectory)
            },
        )
        KeyValue("数量", "${request.sources.size} 项")
        Text("同名文件", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                ConflictPolicy.KEEP_BOTH to "保留两者",
                ConflictPolicy.SKIP to "跳过",
                ConflictPolicy.REPLACE to "替换",
            ).forEach { (value, label) ->
                SelectChip(label, policy == value, Modifier.weight(1f)) { policy = value }
            }
        }
        CedarDialogActions(
            onDismiss,
            { onConfirm(policy) },
            when {
                toArchive -> "开始添加"
                fromArchive -> "开始解压"
                request.move -> "开始移动"
                else -> "开始复制"
            },
            true,
        )
    }
}

private fun paneFor(id: PaneId, a: PaneState, b: PaneState): PaneState = if (id == PaneId.A) a else b
private fun PaneState.singleSelected(): FileEntry? = entries.firstOrNull { it.path in selected }.takeIf { selected.size == 1 }
private fun PaneId.directionName(): String = if (this == PaneId.A) "A 栏" else "B 栏"
private fun nextSort(value: SortMode): SortMode = SortMode.entries[(value.ordinal + 1) % SortMode.entries.size]
private fun ThemeMode.displayName(): String = when (this) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色" }
private fun RootState.displayName(): String = when (this) { RootState.CHECKING -> "检查中"; RootState.GRANTED -> "已授权"; RootState.DENIED -> "授权被拒绝"; RootState.UNAVAILABLE -> "不可用" }
private fun formatBytes(value: Long): String {
    if (value < 1024) return "$value B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var size = value.toDouble()
    var index = -1
    do { size /= 1024.0; index++ } while (size >= 1024 && index < units.lastIndex)
    return "%.1f %s".format(size, units[index])
}

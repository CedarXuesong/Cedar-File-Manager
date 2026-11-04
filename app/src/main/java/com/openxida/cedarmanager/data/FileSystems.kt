package com.openxida.cedarmanager.data

import android.system.Os
import android.system.OsConstants
import android.os.Process
import com.openxida.cedarmanager.domain.ArchiveLocation
import com.openxida.cedarmanager.domain.ConflictPolicy
import com.openxida.cedarmanager.domain.FileEntry
import com.openxida.cedarmanager.domain.FileKind
import com.openxida.cedarmanager.domain.PathTools
import com.openxida.cedarmanager.domain.PermissionSpec
import com.openxida.cedarmanager.domain.ShellEscaper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

interface FileSystemBackend {
    suspend fun list(path: String): List<FileEntry>
    suspend fun stat(path: String): FileEntry
    suspend fun exists(path: String): Boolean
    suspend fun copy(source: String, target: String)
    suspend fun move(source: String, target: String)
    suspend fun delete(path: String, recursive: Boolean = true)
    suspend fun mkdir(path: String)
    suspend fun rename(source: String, target: String) = move(source, target)
    suspend fun readText(path: String): String
    suspend fun writeText(path: String, content: String)
    suspend fun setPermissions(path: String, spec: PermissionSpec)
}

class LocalFileSystem : FileSystemBackend {
    override suspend fun list(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val directory = File(path)
        require(directory.isDirectory) { "不是目录：$path" }
        directory.listFiles()?.map(::entryFor) ?: error("无法读取目录：$path")
    }

    override suspend fun stat(path: String): FileEntry = withContext(Dispatchers.IO) { entryFor(File(path)) }
    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) { File(path).exists() }

    override suspend fun copy(source: String, target: String) = withContext(Dispatchers.IO) {
        copyRecursively(File(source), File(target))
    }

    override suspend fun move(source: String, target: String) = withContext(Dispatchers.IO) {
        val src = File(source)
        val dst = File(target)
        dst.parentFile?.mkdirs()
        if (!src.renameTo(dst)) {
            copyRecursively(src, dst)
            if (!src.deleteRecursively()) error("复制成功，但无法删除源文件：$source")
        }
    }

    override suspend fun delete(path: String, recursive: Boolean) = withContext(Dispatchers.IO) {
        val file = File(path)
        val deleted = if (recursive) file.deleteRecursively() else file.delete()
        if (!deleted && file.exists()) error("无法删除：$path")
    }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        val directory = File(path)
        if (!directory.mkdirs() && !directory.isDirectory) error("无法创建目录：$path")
    }

    override suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        val file = File(path)
        require(file.length() <= MAX_TEXT_BYTES) { "文本文件超过 1 MiB" }
        file.readText()
    }

    override suspend fun writeText(path: String, content: String) = withContext(Dispatchers.IO) {
        require(content.toByteArray().size <= MAX_TEXT_BYTES) { "文本内容超过 1 MiB" }
        File(path).apply { parentFile?.mkdirs() }.writeText(content)
    }

    override suspend fun setPermissions(path: String, spec: PermissionSpec) = withContext(Dispatchers.IO) {
        applyPermissions(File(path), spec)
    }

    private fun applyPermissions(file: File, spec: PermissionSpec) {
        Os.chmod(file.absolutePath, spec.mode)
        val uid = spec.owner?.toIntOrNull() ?: -1
        val gid = spec.group?.toIntOrNull() ?: -1
        if (uid >= 0 || gid >= 0) Os.chown(file.absolutePath, uid, gid)
        if (spec.recursive && file.isDirectory) {
            file.listFiles()?.forEach { applyPermissions(it, spec.copy(recursive = true)) }
        }
    }

    private fun entryFor(file: File): FileEntry {
        require(file.exists() || Files.isSymbolicLink(file.toPath())) { "文件不存在：${file.absolutePath}" }
        val stat = Os.lstat(file.absolutePath)
        val kind = when {
            OsConstants.S_ISLNK(stat.st_mode) -> FileKind.SYMLINK
            OsConstants.S_ISDIR(stat.st_mode) -> FileKind.DIRECTORY
            OsConstants.S_ISREG(stat.st_mode) -> FileKind.FILE
            else -> FileKind.OTHER
        }
        return FileEntry(
            path = file.absolutePath,
            name = file.name.ifBlank { "/" },
            kind = kind,
            size = stat.st_size,
            modifiedAt = stat.st_mtime * 1000,
            mode = stat.st_mode and 0x1FF,
            owner = stat.st_uid.toString(),
            group = stat.st_gid.toString(),
        )
    }

    private fun copyRecursively(source: File, target: File) {
        require(source.exists()) { "源文件不存在：${source.absolutePath}" }
        if (source.isDirectory) {
            if (!target.mkdirs() && !target.isDirectory) error("无法创建目标目录：${target.absolutePath}")
            source.listFiles()?.forEach { child -> copyRecursively(child, File(target, child.name)) }
        } else {
            target.parentFile?.mkdirs()
            Files.copy(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES,
            )
        }
    }

    companion object {
        private const val MAX_TEXT_BYTES = 1024 * 1024
    }
}

class RootFileSystem(private val shell: RootShell, private val cacheDirectory: File) : FileSystemBackend {
    override suspend fun list(path: String): List<FileEntry> {
        val normalizedPath = PathTools.resolve(path, "/")
        val directory = ShellEscaper.quote(normalizedPath)
        val script = """
            set -o pipefail
            d=$directory
            if [ ! -d "${'$'}d" ]; then
              printf '不是目录：%s\n' "${'$'}d" >&2
              exit 2
            fi
            find "${'$'}d" -mindepth 1 -maxdepth 1 -printf '%f\0%M\0%s\0%T@\0%m\0%U\0%G\0' | base64
        """.trimIndent()
        val encoded = shell.runOrThrow(script).out.joinToString("")
        return parseRootListing(normalizedPath, Base64.getDecoder().decode(encoded))
    }

    override suspend fun stat(path: String): FileEntry {
        val normalized = PathTools.resolve(path, "/")
        val parent = PathTools.parent(normalized, stopAtSharedStorageRoot = false) ?: "/"
        return list(parent).firstOrNull { PathTools.resolve(it.path, "/") == normalized }
            ?: error("文件不存在：$normalized")
    }

    override suspend fun exists(path: String): Boolean {
        val q = ShellEscaper.quote(PathTools.resolve(path, "/"))
        return shell.run("[ -e $q ] || [ -L $q ]").success
    }

    override suspend fun copy(source: String, target: String) {
        shell.runOrThrow("cp -a -- ${ShellEscaper.quote(source)} ${ShellEscaper.quote(target)}")
    }

    override suspend fun move(source: String, target: String) {
        shell.runOrThrow("mv -- ${ShellEscaper.quote(source)} ${ShellEscaper.quote(target)}")
    }

    override suspend fun delete(path: String, recursive: Boolean) {
        val flag = if (recursive) "-rf" else "-f"
        shell.runOrThrow("rm $flag -- ${ShellEscaper.quote(path)}")
    }

    override suspend fun mkdir(path: String) {
        shell.runOrThrow("mkdir -p -- ${ShellEscaper.quote(path)}")
    }

    override suspend fun readText(path: String): String {
        val quoted = ShellEscaper.quote(path)
        val result = shell.runOrThrow(
            "size=$(stat -c %s -- $quoted) || exit 2; " +
                "[ \"\$size\" -le 1048576 ] || { echo '文本文件超过 1 MiB' >&2; exit 2; }; " +
                "base64 < $quoted",
        )
        val bytes = Base64.getDecoder().decode(result.out.joinToString(""))
        return bytes.toString(Charsets.UTF_8)
    }

    override suspend fun writeText(path: String, content: String) {
        val bytes = content.toByteArray()
        require(bytes.size <= 1024 * 1024) { "文本内容超过 1 MiB" }
        val staged = withContext(Dispatchers.IO) {
            File.createTempFile("cedar-write-", ".tmp", cacheDirectory).apply { writeBytes(bytes) }
        }
        try {
            shell.runOrThrow("cat -- ${ShellEscaper.quote(staged.absolutePath)} > ${ShellEscaper.quote(path)}")
        } finally {
            withContext(Dispatchers.IO) { staged.delete() }
        }
    }

    override suspend fun setPermissions(path: String, spec: PermissionSpec) {
        val recursive = if (spec.recursive) "-R " else ""
        val target = ShellEscaper.quote(path)
        shell.runOrThrow("chmod $recursive${spec.octal} -- $target")
        if (!spec.owner.isNullOrBlank() || !spec.group.isNullOrBlank()) {
            val owner = spec.owner.orEmpty()
            val group = spec.group.orEmpty()
            shell.runOrThrow("chown $recursive${ShellEscaper.quote("$owner:$group")} -- $target")
        }
    }

}

internal fun parseRootListing(parent: String, bytes: ByteArray): List<FileEntry> {
    if (bytes.isEmpty()) return emptyList()
    val fields = ArrayList<String>(7)
    val entries = ArrayList<FileEntry>()
    var start = 0
    bytes.forEachIndexed { index, byte ->
        if (byte != 0.toByte()) return@forEachIndexed
        fields += String(bytes, start, index - start, Charsets.UTF_8)
        start = index + 1
        if (fields.size == 7) {
            val name = fields[0]
            val timestamp = fields[3]
            val millis = timestamp.substringBefore('.').toLong() * 1000 +
                timestamp.substringAfter('.', "0").take(3).padEnd(3, '0').toLong()
            entries += FileEntry(
                path = PathTools.join(parent, name),
                name = name,
                kind = when (fields[1].firstOrNull()) {
                    'd' -> FileKind.DIRECTORY
                    'l' -> FileKind.SYMLINK
                    '-' -> FileKind.FILE
                    else -> FileKind.OTHER
                },
                size = fields[2].toLong(),
                modifiedAt = millis,
                mode = fields[4].toInt(8) and 0x1FF,
                owner = fields[5],
                group = fields[6],
            )
            fields.clear()
        }
    }
    check(start == bytes.size && fields.isEmpty()) { "目录元数据不完整" }
    return entries
}

class FileSystemRouter(
    private val local: LocalFileSystem,
    private val root: RootFileSystem,
) {
    fun backend(rootMode: Boolean): FileSystemBackend = if (rootMode) root else local

    suspend fun exists(path: String, rootMode: Boolean): Boolean = backend(rootMode).exists(path)

    suspend fun copyOrMove(source: String, target: String, rootMode: Boolean, move: Boolean) {
        val selected = backend(rootMode)
        if (move) selected.move(source, target) else selected.copy(source, target)
    }
}

class ArchiveService {
    private val writeMutex = Mutex()

    suspend fun createZip(inputs: List<String>, output: String) = withContext(Dispatchers.IO) {
        ZipOutputStream(File(output).outputStream().buffered()).use { zip ->
            inputs.forEach { input ->
                val root = File(input)
                addToZip(zip, root, root.name)
            }
        }
    }

    suspend fun extractZip(source: String, targetDirectory: String) = withContext(Dispatchers.IO) {
        val targetRoot = File(targetDirectory).apply { mkdirs() }.canonicalFile
        ZipInputStream(File(source).inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val output = File(targetRoot, entry.name).canonicalFile
                require(output.path.startsWith(targetRoot.path + File.separator)) { "压缩包包含不安全路径" }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    suspend fun createRootZip(inputs: List<String>, output: String, shell: RootShell, cache: File) {
        val staged = withContext(Dispatchers.IO) { Files.createTempDirectory(cache.toPath(), "cedar-archive-").toFile() }
        try {
            val sources = File(staged, "sources").apply { mkdirs() }
            inputs.forEach { input ->
                shell.runOrThrow("cp -R -- ${ShellEscaper.quote(input)} ${ShellEscaper.quote(sources.absolutePath)}")
            }
            makeReadable(staged, shell)
            val archive = File(staged, "archive.zip")
            createZip(inputs.map { File(sources, File(it).name).absolutePath }, archive.absolutePath)
            shell.runOrThrow("cp -f -- ${ShellEscaper.quote(archive.absolutePath)} ${ShellEscaper.quote(output)}")
        } finally {
            withContext(Dispatchers.IO) { staged.deleteRecursively() }
        }
    }

    suspend fun extractRootZip(source: String, targetDirectory: String, shell: RootShell, cache: File) {
        val staged = withContext(Dispatchers.IO) { Files.createTempDirectory(cache.toPath(), "cedar-extract-").toFile() }
        try {
            val archive = File(staged, "source.zip")
            val extracted = File(staged, "extracted")
            shell.runOrThrow("cp -- ${ShellEscaper.quote(source)} ${ShellEscaper.quote(archive.absolutePath)}")
            makeReadable(staged, shell)
            extractZip(archive.absolutePath, extracted.absolutePath)
            shell.runOrThrow(
                "mkdir -p -- ${ShellEscaper.quote(targetDirectory)} && " +
                    "cp -R -- ${ShellEscaper.quote(extracted.absolutePath + "/.")} ${ShellEscaper.quote(targetDirectory)}",
            )
        } finally {
            withContext(Dispatchers.IO) { staged.deleteRecursively() }
        }
    }

    private suspend fun makeReadable(staged: File, shell: RootShell) {
        val user = Process.myUid()
        shell.runOrThrow(
            "chown -R $user:$user -- ${ShellEscaper.quote(staged.absolutePath)} && " +
                "chmod -R u+rwX -- ${ShellEscaper.quote(staged.absolutePath)}",
        )
    }

    private fun addToZip(zip: ZipOutputStream, file: File, entryName: String) {
        if (file.isDirectory) {
            zip.putNextEntry(ZipEntry(entryName.trimEnd('/') + '/'))
            zip.closeEntry()
            file.listFiles()?.forEach { child -> addToZip(zip, child, "$entryName/${child.name}") }
        } else {
            zip.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    suspend fun listZip(
        source: String,
        innerPath: String,
        shell: RootShell?,
        cache: File,
    ): List<FileEntry> = withReadableArchive(source, shell, cache) { archive ->
        val prefix = zipPrefix(innerPath)
        val children = LinkedHashMap<String, ChildEntry>()
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = zipName(entry.name)
                if (name.isEmpty() || !name.startsWith(prefix)) continue
                val rest = name.removePrefix(prefix)
                if (rest.isEmpty()) continue
                val separator = rest.indexOf('/')
                val childName = if (separator >= 0) rest.substring(0, separator) else rest
                val directory = separator >= 0 || entry.isDirectory
                val existing = children[childName]
                if (existing == null) {
                    children[childName] = ChildEntry(
                        name = childName,
                        directory = directory,
                        size = if (directory) 0L else entry.size.coerceAtLeast(0L),
                        modifiedAt = entry.time.coerceAtLeast(0L),
                    )
                } else if (directory) {
                    existing.directory = true
                    existing.size = 0L
                }
            }
        }
        val location = ArchiveLocation(source, innerPath)
        children.values.map { child ->
            FileEntry(
                path = location.child(child.name).virtualPath,
                name = child.name,
                kind = if (child.directory) FileKind.DIRECTORY else FileKind.FILE,
                size = child.size,
                modifiedAt = child.modifiedAt,
                mode = if (child.directory) 0x1ED else 0x1A4,
                owner = "",
                group = "",
                archive = location,
            )
        }
    }

    suspend fun readZipText(
        source: String,
        entryPath: String,
        shell: RootShell?,
        cache: File,
    ): String = withReadableArchive(source, shell, cache) { archive ->
        ZipFile(archive).use { zip ->
            val name = zipName(entryPath)
            val entry = zip.getEntry(name) ?: zip.getEntry("$name/")
                ?: error("压缩包内找不到文件：$entryPath")
            require(!entry.isDirectory) { "不是文件：$entryPath" }
            val output = java.io.ByteArrayOutputStream()
            zip.getInputStream(entry).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_TEXT_BYTES) { "文本文件超过 1 MiB" }
                    output.write(buffer, 0, read)
                }
            }
            output.toByteArray().toString(Charsets.UTF_8)
        }
    }

    suspend fun extractFile(
        source: String,
        entryPath: String,
        target: File,
        shell: RootShell?,
        cache: File,
    ): Unit = withReadableArchive(source, shell, cache) { archive ->
        ZipFile(archive).use { zip ->
            val name = zipName(entryPath)
            val entry = zip.getEntry(name) ?: error("压缩包内找不到文件：$entryPath")
            require(!entry.isDirectory) { "不是文件：$entryPath" }
            target.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
    }

    suspend fun extractEntries(
        source: String,
        items: List<ArchiveExtractItem>,
        targetDirectory: String,
        shell: RootShell?,
        cache: File,
    ) {
        if (items.isEmpty()) return
        withReadableArchive(source, shell, cache) { archive ->
            val target = File(targetDirectory)
            val localWritable = target.isDirectory || target.canWrite() ||
                target.parentFile?.canWrite() == true
            if (localWritable) {
                extractInto(archive, items, target)
            } else {
                val root = shell ?: error("无法写入目标目录：$targetDirectory")
                val staging = Files.createTempDirectory(cache.toPath(), "cedar-extract-").toFile()
                try {
                    extractInto(archive, items, staging)
                    makeReadable(staging, root)
                    root.runOrThrow(
                        "mkdir -p -- ${ShellEscaper.quote(targetDirectory)} && " +
                            "cp -R -- ${ShellEscaper.quote(staging.absolutePath + "/.")} " +
                            ShellEscaper.quote(targetDirectory),
                    )
                } finally {
                    staging.deleteRecursively()
                }
            }
        }
    }

    suspend fun addToArchive(
        source: String,
        innerDirectory: String,
        inputs: List<ArchiveInput>,
        policy: ConflictPolicy,
        shell: RootShell?,
        cache: File,
    ): ArchiveAddResult = writeMutex.withLock {
        addToArchiveLocked(source, innerDirectory, inputs, policy, shell, cache)
    }

    private suspend fun addToArchiveLocked(
        source: String,
        innerDirectory: String,
        inputs: List<ArchiveInput>,
        policy: ConflictPolicy,
        shell: RootShell?,
        cache: File,
    ): ArchiveAddResult {
        val staging = withContext(Dispatchers.IO) {
            Files.createTempDirectory(cache.toPath(), "cedar-add-").toFile()
        }
        try {
            var staged = false
            val effective = inputs.mapIndexed { index, input ->
                val original = File(input.path)
                if (original.canRead()) {
                    EffectiveInput(original, input.name, input.path)
                } else {
                    val root = shell ?: error("无法读取：${input.path}")
                    val copy = File(staging, "item-$index")
                    root.runOrThrow(
                        "cp -R -- ${ShellEscaper.quote(input.path)} ${ShellEscaper.quote(copy.absolutePath)}",
                    )
                    staged = true
                    EffectiveInput(copy, input.name, input.path)
                }
            }
            if (staged) makeReadable(staging, shell ?: error("无法准备源文件"))
            return withReadableArchive(source, shell, cache) { archive ->
                val temp = File.createTempFile("cedar-add-", ".zip", cache)
                try {
                    val result = writeAdded(archive, temp, effective, innerDirectory, policy, source)
                    publishArchive(temp, source, shell)
                    result
                } finally {
                    temp.delete()
                }
            }
        } finally {
            withContext(Dispatchers.IO) { staging.deleteRecursively() }
        }
    }

    suspend fun deleteEntries(
        source: String,
        entryPaths: List<String>,
        shell: RootShell?,
        cache: File,
    ) = writeMutex.withLock {
        deleteEntriesLocked(source, entryPaths, shell, cache)
    }

    private suspend fun deleteEntriesLocked(
        source: String,
        entryPaths: List<String>,
        shell: RootShell?,
        cache: File,
    ) {
        val targets = entryPaths.map(::zipName).filter { it.isNotEmpty() }
        if (targets.isEmpty()) return
        rewriteArchive(source, shell, cache) { zip, output ->
            val written = HashSet<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = zipName(entry.name)
                if (targets.any { name == it || name.startsWith("$it/") }) continue
                copyEntry(zip, entry, output, null, written)
            }
        }
    }

    suspend fun createEntry(
        source: String,
        parentInner: String,
        name: String,
        directory: Boolean,
        shell: RootShell?,
        cache: File,
    ) = writeMutex.withLock {
        createEntryLocked(source, parentInner, name, directory, shell, cache)
    }

    private suspend fun createEntryLocked(
        source: String,
        parentInner: String,
        name: String,
        directory: Boolean,
        shell: RootShell?,
        cache: File,
    ) {
        require(name.isSafeEntryName()) { "名称无效" }
        val parent = zipName(parentInner)
        val entryPath = if (parent.isEmpty()) name else "$parent/$name"
        rewriteArchive(source, shell, cache) { zip, output ->
            require(zip.getEntry(entryPath) == null && zip.getEntry("$entryPath/") == null) {
                "已存在同名项目：$name"
            }
            val written = HashSet<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                copyEntry(zip, entries.nextElement(), output, null, written)
            }
            val entryName = if (directory) "$entryPath/" else entryPath
            output.putNextEntry(ZipEntry(entryName).apply { time = System.currentTimeMillis() })
            output.closeEntry()
        }
    }

    suspend fun renameEntry(
        source: String,
        entryPath: String,
        newName: String,
        shell: RootShell?,
        cache: File,
    ) = writeMutex.withLock {
        renameEntryLocked(source, entryPath, newName, shell, cache)
    }

    private suspend fun renameEntryLocked(
        source: String,
        entryPath: String,
        newName: String,
        shell: RootShell?,
        cache: File,
    ) {
        require(newName.isSafeEntryName()) { "名称无效" }
        val oldPath = zipName(entryPath)
        require(oldPath.isNotEmpty()) { "压缩包条目无效" }
        val parent = oldPath.substringBeforeLast('/', "")
        val newPath = if (parent.isEmpty()) newName else "$parent/$newName"
        require(oldPath != newPath) { "名称未改变" }
        rewriteArchive(source, shell, cache) { zip, output ->
            require(zip.getEntry(newPath) == null && zip.getEntry("$newPath/") == null) {
                "已存在同名项目：$newName"
            }
            val written = HashSet<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = zipName(entry.name)
                if (name == oldPath || name.startsWith("$oldPath/")) {
                    val suffix = name.removePrefix(oldPath)
                    copyEntry(zip, entry, output, newPath + suffix, written)
                } else {
                    copyEntry(zip, entry, output, null, written)
                }
            }
        }
    }

    suspend fun writeZipText(
        source: String,
        entryPath: String,
        content: String,
        shell: RootShell?,
        cache: File,
    ) = writeMutex.withLock {
        writeZipTextLocked(source, entryPath, content, shell, cache)
    }

    private suspend fun writeZipTextLocked(
        source: String,
        entryPath: String,
        content: String,
        shell: RootShell?,
        cache: File,
    ) {
        val bytes = content.toByteArray()
        require(bytes.size <= MAX_TEXT_BYTES) { "文本内容超过 1 MiB" }
        val name = zipName(entryPath)
        rewriteArchive(source, shell, cache) { zip, output ->
            var replaced = false
            val written = HashSet<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (zipName(entry.name) == name) {
                    replaced = true
                    continue
                }
                copyEntry(zip, entry, output, null, written)
            }
            require(replaced) { "压缩包内找不到文件：$entryPath" }
            output.putNextEntry(ZipEntry(name).apply { time = System.currentTimeMillis() })
            output.write(bytes)
            output.closeEntry()
        }
    }

    private suspend fun rewriteArchive(
        source: String,
        shell: RootShell?,
        cache: File,
        transform: (ZipFile, ZipOutputStream) -> Unit,
    ) {
        withReadableArchive(source, shell, cache) { archive ->
            val temp = File.createTempFile("cedar-rewrite-", ".zip", cache)
            try {
                ZipOutputStream(temp.outputStream().buffered()).use { output ->
                    ZipFile(archive).use { zip -> transform(zip, output) }
                }
                publishArchive(temp, source, shell)
            } finally {
                temp.delete()
            }
        }
    }

    private suspend fun <T> withReadableArchive(
        source: String,
        shell: RootShell?,
        cache: File,
        block: suspend (File) -> T,
    ): T = withContext(Dispatchers.IO) {
        val direct = File(source)
        val attempt = runCatching { block(direct) }
        if (attempt.isSuccess) return@withContext attempt.getOrThrow()
        val localFailure = attempt.exceptionOrNull()!!
        if (localFailure is CancellationException) throw localFailure
        if (shell == null) throw localFailure
        val staged = File.createTempFile("cedar-zip-", ".zip", cache)
        try {
            shell.runOrThrow(
                "cp -f -- ${ShellEscaper.quote(source)} ${ShellEscaper.quote(staged.absolutePath)}",
            )
            makeReadable(staged, shell)
            block(staged)
        } finally {
            staged.delete()
        }
    }

    private suspend fun publishArchive(temp: File, target: String, shell: RootShell?) {
        val attempt = runCatching {
            Files.copy(temp.toPath(), File(target).toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        if (attempt.isSuccess) return
        val failure = attempt.exceptionOrNull()!!
        if (failure is CancellationException) throw failure
        if (shell == null) throw failure
        shell.runOrThrow(
            "cat -- ${ShellEscaper.quote(temp.absolutePath)} > ${ShellEscaper.quote(target)}",
        )
    }

    private fun writeAdded(
        archive: File,
        output: File,
        inputs: List<EffectiveInput>,
        innerDirectory: String,
        policy: ConflictPolicy,
        archivePath: String,
    ): ArchiveAddResult {
        val targetCanonical = canonicalPath(archivePath)
        inputs.forEach { input ->
            val sourceCanonical = canonicalPath(input.sourcePath)
            require(sourceCanonical != targetCanonical) { "不能把压缩包添加到自身" }
            require(!targetCanonical.startsWith(sourceCanonical.trimEnd(File.separatorChar) + File.separator)) {
                "不能把压缩包添加到它所在的目录"
            }
        }
        val prefix = zipPrefix(innerDirectory)
        val existing = HashSet<String>()
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = zipName(entries.nextElement().name)
                if (!name.startsWith(prefix)) continue
                val rest = name.removePrefix(prefix)
                if (rest.isEmpty()) continue
                existing += rest.substringBefore('/')
            }
        }
        val taken = existing.toMutableSet()
        val accepted = ArrayList<Pair<EffectiveInput, String>>()
        var skipped = 0
        inputs.forEach { input ->
            if (input.name in taken) {
                when (policy) {
                    ConflictPolicy.SKIP -> skipped++
                    ConflictPolicy.REPLACE -> accepted += input to input.name
                    ConflictPolicy.KEEP_BOTH -> {
                        val name = keepBothName(input.name, taken)
                        taken += name
                        accepted += input to name
                    }
                }
            } else {
                taken += input.name
                accepted += input to input.name
            }
        }
        val replaced = accepted.mapTo(HashSet()) { it.second }
        ZipOutputStream(output.outputStream().buffered()).use { zipOut ->
            ZipFile(archive).use { zip ->
                val written = HashSet<String>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = zipName(entry.name)
                    val top = if (name.startsWith(prefix)) name.removePrefix(prefix).substringBefore('/') else ""
                    if (top.isNotEmpty() && top in replaced) continue
                    copyEntry(zip, entry, zipOut, null, written)
                }
            }
            accepted.forEach { (input, name) ->
                addFileTree(zipOut, input.file, prefix + name, 0)
            }
        }
        return ArchiveAddResult(
            added = accepted.size,
            skipped = skipped,
            addedSources = accepted.map { it.first.sourcePath },
        )
    }

    private fun extractInto(
        archive: File,
        items: List<ArchiveExtractItem>,
        root: File,
    ) {
        root.mkdirs()
        ZipFile(archive).use { zip ->
            items.forEach { item ->
                val entryPath = zipName(item.entryPath)
                require(entryPath.isNotEmpty() && item.targetName.isNotBlank()) { "压缩包条目无效" }
                val explicit = zip.getEntry(entryPath) ?: zip.getEntry("$entryPath/")
                var directory = explicit?.isDirectory == true
                val children = ArrayList<ZipEntry>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = zipName(entry.name)
                    if (name == entryPath) {
                        if (entry.isDirectory) directory = true
                    } else if (name.startsWith("$entryPath/")) {
                        directory = true
                        children += entry
                    }
                }
                require(explicit != null || children.isNotEmpty()) {
                    "压缩包内找不到：${item.entryPath}"
                }
                if (!directory) {
                    val source = explicit ?: error("压缩包内找不到文件：${item.entryPath}")
                    val output = resolveOutput(root, item.targetName)
                    output.parentFile?.mkdirs()
                    zip.getInputStream(source).use { input ->
                        output.outputStream().use { input.copyTo(it) }
                    }
                    if (source.time > 0) output.setLastModified(source.time)
                } else {
                    resolveOutput(root, item.targetName).mkdirs()
                    children.forEach { entry ->
                        val name = zipName(entry.name)
                        val relative = item.targetName + "/" + name.removePrefix("$entryPath/")
                        val output = resolveOutput(root, relative)
                        if (entry.isDirectory) {
                            output.mkdirs()
                        } else {
                            output.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                output.outputStream().use { input.copyTo(it) }
                            }
                            if (entry.time > 0) output.setLastModified(entry.time)
                        }
                    }
                }
            }
        }
    }

    private fun resolveOutput(root: File, relative: String): File {
        val canonicalRoot = root.canonicalFile
        val output = File(canonicalRoot, relative.replace('\\', '/')).canonicalFile
        require(
            output.path == canonicalRoot.path ||
                output.path.startsWith(canonicalRoot.path + File.separator),
        ) { "压缩包包含不安全路径" }
        return output
    }

    private fun copyEntry(
        zip: ZipFile,
        entry: ZipEntry,
        output: ZipOutputStream,
        overrideName: String?,
        written: MutableSet<String>? = null,
    ) {
        val base = overrideName ?: zipName(entry.name)
        if (base.isEmpty()) return
        val name = if (entry.isDirectory) base.trimEnd('/') + "/" else base
        if (written != null && !written.add(name)) return
        output.putNextEntry(ZipEntry(name).apply { time = entry.time })
        if (!entry.isDirectory) {
            zip.getInputStream(entry).use { it.copyTo(output) }
        }
        output.closeEntry()
    }

    private fun addFileTree(zip: ZipOutputStream, file: File, entryName: String, depth: Int) {
        require(depth <= MAX_DEPTH) { "目录层级过深：${file.path}" }
        if (Files.isSymbolicLink(file.toPath())) return
        if (file.isDirectory) {
            val directoryName = entryName.trimEnd('/') + "/"
            zip.putNextEntry(ZipEntry(directoryName).apply { time = file.lastModified() })
            zip.closeEntry()
            file.listFiles()?.sortedBy { it.name }?.forEach { child ->
                addFileTree(zip, child, "$entryName/${child.name}", depth + 1)
            }
        } else {
            zip.putNextEntry(ZipEntry(entryName).apply { time = file.lastModified() })
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun canonicalPath(path: String): String =
        runCatching { File(path).canonicalPath }.getOrDefault(path)

    private fun keepBothName(name: String, taken: Set<String>): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var index = 1
        var candidate: String
        do {
            candidate = "$base ($index)$extension"
            index++
        } while (candidate in taken)
        return candidate
    }

    private fun zipPrefix(innerPath: String): String {
        val normalized = zipName(innerPath)
        return if (normalized.isEmpty()) "" else "$normalized/"
    }

    private fun zipName(raw: String): String =
        raw.replace('\\', '/').trimStart('/').trimEnd('/')

    private fun String.isSafeEntryName(): Boolean =
        isNotBlank() && this != "." && this != ".." && '/' !in this && '\u0000' !in this

    private class ChildEntry(
        val name: String,
        var directory: Boolean,
        var size: Long,
        val modifiedAt: Long,
    )

    private class EffectiveInput(
        val file: File,
        val name: String,
        val sourcePath: String,
    )

    private companion object {
        private const val MAX_TEXT_BYTES = 1024 * 1024
        private const val MAX_DEPTH = 32
    }
}

data class ArchiveInput(val path: String, val name: String)

data class ArchiveExtractItem(val entryPath: String, val targetName: String)

data class ArchiveAddResult(
    val added: Int,
    val skipped: Int,
    val addedSources: List<String> = emptyList(),
)

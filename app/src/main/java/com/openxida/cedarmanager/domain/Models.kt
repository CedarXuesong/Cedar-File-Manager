package com.openxida.cedarmanager.domain

enum class FileKind { DIRECTORY, FILE, SYMLINK, OTHER }

/** A directory inside a ZIP archive, addressed without the archive's own path. */
data class ArchiveLocation(
    val archivePath: String,
    val innerPath: String = "",
) {
    val virtualPath: String
        get() = if (innerPath.isEmpty()) archivePath else "$archivePath/$innerPath"

    /** Maps a path below [archivePath] to the archive-relative entry path. */
    fun relativePath(path: String): String = path.removePrefix(archivePath).trim('/')

    fun child(name: String): ArchiveLocation =
        copy(innerPath = if (innerPath.isEmpty()) name else "$innerPath/$name")

    fun parent(): ArchiveLocation? =
        if (innerPath.isEmpty()) null else copy(innerPath = innerPath.substringBeforeLast('/', ""))

    fun contains(path: String): Boolean = path == archivePath || path.startsWith("$archivePath/")
}

data class FileEntry(
    val path: String,
    val name: String,
    val kind: FileKind,
    val size: Long,
    val modifiedAt: Long,
    val mode: Int,
    val owner: String,
    val group: String,
    /** Null for real files; set when this entry lives inside a ZIP archive. */
    val archive: ArchiveLocation? = null,
) {
    val isDirectory: Boolean get() = kind == FileKind.DIRECTORY
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val isApk: Boolean get() = extension == "apk"
    val isArchive: Boolean get() = kind == FileKind.FILE && extension == "zip"
    val isShellScript: Boolean get() = extension == "sh"
    val isText: Boolean
        get() = extension in setOf(
            "txt", "md", "json", "xml", "yaml", "yml", "conf", "ini", "prop", "properties",
            "log", "csv", "kt", "java", "c", "cpp", "h", "hpp", "sh", "py", "js", "ts", "html", "css",
        ) || (extension.isEmpty() && kind == FileKind.FILE && size <= 1024 * 1024)
}

enum class SortMode { NAME, SIZE, MODIFIED, TYPE }
enum class PaneId { A, B }
enum class RootState { CHECKING, GRANTED, DENIED, UNAVAILABLE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ConflictPolicy { SKIP, KEEP_BOTH, REPLACE }

data class PermissionSpec(
    val mode: Int,
    val owner: String? = null,
    val group: String? = null,
    val recursive: Boolean = false,
) {
    val octal: String get() = mode.coerceIn(0, 511).toString(8).padStart(3, '0')

    fun has(mask: Int): Boolean = mode and mask != 0
    fun toggle(mask: Int): PermissionSpec = copy(mode = mode xor mask)

    companion object {
        fun fromOctal(value: String): PermissionSpec? {
            if (!value.matches(Regex("[0-7]{3,4}"))) return null
            return PermissionSpec(value.takeLast(3).toInt(8))
        }
    }
}

data class PaneState(
    val id: PaneId,
    val path: String,
    val archive: ArchiveLocation? = null,
    val entries: List<FileEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    val selectionAnchor: String? = null,
    val sortMode: SortMode = SortMode.NAME,
    val ascending: Boolean = true,
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
) {
    val visibleEntries: List<FileEntry>
        get() {
            val filtered = entries.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            val comparator = when (sortMode) {
                SortMode.NAME -> Comparator<FileEntry> { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
                SortMode.SIZE -> compareBy { it.size }
                SortMode.MODIFIED -> compareBy { it.modifiedAt }
                SortMode.TYPE -> compareBy<FileEntry> { it.kind.ordinal }
                    .thenComparator { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
            }
            val sorted = filtered.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.then(comparator))
            return if (ascending) sorted else sorted.reversed()
        }

    fun selectSwipe(path: String): PaneState {
        val paths = visibleEntries.map { it.path }
        val end = paths.indexOf(path)
        if (end < 0) return this
        val start = paths.indexOf(selectionAnchor)
        val additions = if (start < 0) listOf(path) else paths.subList(minOf(start, end), maxOf(start, end) + 1)
        return copy(selected = selected + additions, selectionAnchor = selectionAnchor ?: path)
    }

    fun invertSelection(): PaneState {
        val visible = visibleEntries.mapTo(mutableSetOf()) { it.path }
        return copy(selected = (selected - visible) + (visible - selected), selectionAnchor = null)
    }

    fun selectSimilar(): PaneState {
        val reference = visibleEntries.firstOrNull { it.path == selectionAnchor }
            ?: visibleEntries.firstOrNull { it.path in selected } ?: return this
        val matches = visibleEntries.filter {
            if (reference.isDirectory) it.isDirectory else !it.isDirectory && it.extension == reference.extension
        }.map { it.path }
        return copy(selected = selected + matches)
    }
}

data class TransferRequest(
    val sourcePane: PaneId,
    val targetPane: PaneId,
    val sources: List<FileEntry>,
    val targetDirectory: String,
    val move: Boolean,
    val sourceArchive: ArchiveLocation? = null,
    val targetArchive: ArchiveLocation? = null,
)

data class OperationProgress(
    val title: String,
    val detail: String,
    val current: Int,
    val total: Int,
    val running: Boolean,
    val failed: Int = 0,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val haptics: Boolean = true,
    val confirmSystemWrites: Boolean = true,
    val rootFileAccess: Boolean = false,
    val terminalFontSize: Int = 13,
    val safTreeUri: String? = null,
    /** Global UI animation speed multiplier. 1.0 is the normal speed. */
    val animationSpeed: Float = 1f,
    /** Multiplier for the diagonal popup tilt. 1.0 is the standard amplitude. */
    val tiltStrength: Float = 1f,
) {
    companion object {
        const val MIN_ANIMATION_SPEED = 0.5f
        const val MAX_ANIMATION_SPEED = 2f
        const val MIN_TILT_STRENGTH = 0.5f
        const val MAX_TILT_STRENGTH = 2f
    }
}

object PathTools {
    private fun normalized(path: String): String =
        path.replace('\\', '/').trimEnd('/').ifEmpty { "/" }

    /** Android exposes the primary shared-storage root as /storage/emulated/0. */
    fun isRoot(path: String): Boolean {
        val value = normalized(path)
        return value == "/" || value == "/storage/emulated/0"
    }

    fun resolve(path: String, currentDirectory: String): String {
        val input = path.replace('\\', '/')
        require(input.isNotBlank()) { "路径不能为空" }
        require('\u0000' !in input) { "路径包含无效字符" }
        val absolute = if (input.startsWith('/')) input else join(currentDirectory, input)
        val segments = mutableListOf<String>()
        absolute.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return "/" + segments.joinToString("/")
    }

    fun join(parent: String, name: String): String = when {
        parent == "/" -> "/$name"
        parent.endsWith('/') -> parent + name
        else -> "$parent/$name"
    }

    fun parent(path: String, stopAtSharedStorageRoot: Boolean = true): String? {
        val normalized = resolve(path, "/")
        if (normalized == "/" || (stopAtSharedStorageRoot && normalized == "/storage/emulated/0")) return null
        return normalized.substringBeforeLast('/').ifEmpty { "/" }
    }

    fun isProtectedSystemPath(path: String): Boolean {
        val segments = mutableListOf<String>()
        path.replace('\\', '/').split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        val normalized = "/" + segments.joinToString("/")
        return listOf("/system", "/vendor", "/product").any { root ->
            normalized == root || normalized.startsWith("$root/")
        }
    }

    fun keepBothName(directory: String, name: String, exists: (String) -> Boolean): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var index = 1
        var candidate: String
        do {
            candidate = join(directory, "$base ($index)$extension")
            index++
        } while (exists(candidate))
        return candidate
    }
}

object ShellEscaper {
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}

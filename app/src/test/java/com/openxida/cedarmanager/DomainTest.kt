package com.openxida.cedarmanager

import com.openxida.cedarmanager.domain.PermissionSpec
import com.openxida.cedarmanager.domain.FileEntry
import com.openxida.cedarmanager.domain.FileKind
import com.openxida.cedarmanager.domain.PaneId
import com.openxida.cedarmanager.domain.PaneState
import com.openxida.cedarmanager.domain.PathTools
import com.openxida.cedarmanager.domain.ShellEscaper
import com.openxida.cedarmanager.domain.ShellArguments
import com.openxida.cedarmanager.domain.ShellCommandBuilder
import com.openxida.cedarmanager.data.parseRootListing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    @Test
    fun shellEscapingHandlesSingleQuotes() {
        assertEquals("'a'\\''b'", ShellEscaper.quote("a'b"))
    }

    @Test
    fun permissionOctalRoundTrips() {
        assertEquals("755", PermissionSpec.fromOctal("0755")?.octal)
        assertEquals("640", PermissionSpec.fromOctal("640")?.octal)
        assertNull(PermissionSpec.fromOctal("999"))
    }

    @Test
    fun protectedSystemPathsAreNormalized() {
        assertEquals(true, PathTools.isProtectedSystemPath("/system/app"))
        assertEquals(true, PathTools.isProtectedSystemPath("/vendor/../product/etc"))
        assertEquals(false, PathTools.isProtectedSystemPath("/system_ext/app"))
        assertEquals(false, PathTools.isProtectedSystemPath("/system/../data/local/tmp"))
    }

    @Test
    fun pathsResolveRelativeAndAbsoluteSegments() {
        assertEquals("/storage/emulated/0/Download", PathTools.resolve("../../../Download", "/storage/emulated/0/Android/data/child"))
        assertEquals("/system/etc", PathTools.resolve("/system/app/../etc//", "/storage/emulated/0"))
        assertEquals("/", PathTools.resolve("../../../", "/storage"))
        assertEquals("/data/name ", PathTools.resolve("name ", "/data"))
    }

    @Test
    fun rootBrowsingCanAscendPastSharedStorage() {
        assertNull(PathTools.parent("/storage/emulated/0"))
        assertEquals("/storage/emulated", PathTools.parent("/storage/emulated/0", stopAtSharedStorageRoot = false))
        assertNull(PathTools.parent("/", stopAtSharedStorageRoot = false))
    }

    @Test
    fun rootListingParsesSpecialNamesAndMetadata() {
        val raw = listOf(
            "a|b\n中文.txt", "-rw-r-----", "12", "1720000000.25", "640", "1000", "1001",
            ".hidden", "drwx------", "4096", "1720000001.00", "700", "0", "0",
        ).joinToString("\u0000", postfix = "\u0000").toByteArray(Charsets.UTF_8)
        val entries = parseRootListing("/data", raw)
        assertEquals(2, entries.size)
        assertEquals("/data/a|b\n中文.txt", entries[0].path)
        assertEquals(FileKind.FILE, entries[0].kind)
        assertEquals(12L, entries[0].size)
        assertEquals(1720000000250L, entries[0].modifiedAt)
        assertEquals(416, entries[0].mode)
        assertEquals("1001", entries[0].group)
        assertEquals(FileKind.DIRECTORY, entries[1].kind)
        assertEquals(".hidden", entries[1].name)
    }

    @Test
    fun shellArgumentsRespectQuotesAndEscapes() {
        assertEquals(
            listOf("one", "two words", "three four", "five six"),
            ShellArguments.parse("one \"two words\" 'three four' five\\ six"),
        )
        assertEquals(listOf("", "x"), ShellArguments.parse("\"\" x"))
        assertNull(runCatching { ShellArguments.parse("'unfinished") }.getOrNull())
    }

    @Test
    fun shellCommandRunsElfDirectlyAndTextFilesThroughSh() {
        val command = ShellCommandBuilder.launch("/data/adb/TIME_Driver_Loader.sh", listOf("two words"))
        assertTrue(command.contains("7f454c46"))
        assertTrue(command.contains("then '/data/adb/TIME_Driver_Loader.sh' 'two words'"))
        assertTrue(command.contains("else sh '/data/adb/TIME_Driver_Loader.sh' 'two words'"))
    }

    @Test
    fun swipeSelectionIncludesVisibleItemsBetweenAnchorAndTarget() {
        val pane = paneWithEntries()
        val selected = pane.selectSwipe("/files/c.sh")
            .selectSwipe("/files/a.txt")

        assertEquals(setOf("/files/a.txt", "/files/b.txt", "/files/c.sh"), selected.selected)
        assertEquals("/files/c.sh", selected.selectionAnchor)
    }

    @Test
    fun similarSelectionMatchesFileExtensionOrDirectoryKind() {
        val pane = paneWithEntries().selectSwipe("/files/a.txt")
        val similarFiles = pane.selectSimilar()
        assertEquals(setOf("/files/a.txt", "/files/b.txt"), similarFiles.selected)

        val similarDirectories = paneWithEntries().selectSwipe("/files/sub").selectSimilar()
        assertEquals(setOf("/files/sub", "/files/tools"), similarDirectories.selected)
    }

    private fun paneWithEntries() = PaneState(
        id = PaneId.A,
        path = "/files",
        entries = listOf(
            entry("/files/a.txt", FileKind.FILE),
            entry("/files/b.txt", FileKind.FILE),
            entry("/files/c.sh", FileKind.FILE),
            entry("/files/sub", FileKind.DIRECTORY),
            entry("/files/tools", FileKind.DIRECTORY),
        ),
    )

    private fun entry(path: String, kind: FileKind) = FileEntry(
        path = path,
        name = path.substringAfterLast('/'),
        kind = kind,
        size = 1L,
        modifiedAt = 0L,
        mode = 0,
        owner = "u0_a1",
        group = "u0_a1",
    )
}

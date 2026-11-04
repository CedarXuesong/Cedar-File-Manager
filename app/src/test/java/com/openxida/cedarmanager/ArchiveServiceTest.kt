package com.openxida.cedarmanager

import com.openxida.cedarmanager.data.ArchiveExtractItem
import com.openxida.cedarmanager.data.ArchiveInput
import com.openxida.cedarmanager.data.ArchiveService
import com.openxida.cedarmanager.domain.ArchiveLocation
import com.openxida.cedarmanager.domain.ConflictPolicy
import com.openxida.cedarmanager.domain.FileKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveServiceTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val service = ArchiveService()

    @Test
    fun listsArchiveRootAndNestedDirectories() = runBlocking {
        val cache = temp.newFolder("cache")
        val zip = temp.newFile("sample.zip")
        writeZip(
            zip,
            mapOf(
                "root.txt" to "root",
                "dir/" to "",
                "dir/a.txt" to "a",
                "dir/sub/" to "",
                "dir/sub/b.txt" to "b",
                "empty/" to "",
            ),
        )

        val root = service.listZip(zip.absolutePath, "", null, cache)
        assertEquals(setOf("root.txt", "dir", "empty"), root.map { it.name }.toSet())
        assertEquals(FileKind.DIRECTORY, root.first { it.name == "dir" }.kind)
        assertEquals(FileKind.FILE, root.first { it.name == "root.txt" }.kind)
        assertEquals(4L, root.first { it.name == "root.txt" }.size)

        val nested = service.listZip(zip.absolutePath, "dir", null, cache)
        assertEquals(setOf("a.txt", "sub"), nested.map { it.name }.toSet())
        val sub = nested.first { it.name == "sub" }
        assertEquals("${zip.absolutePath}/dir/sub", sub.path)
        assertEquals("dir", sub.archive?.innerPath)
        assertTrue(service.listZip(zip.absolutePath, "empty", null, cache).isEmpty())
    }

    @Test
    fun addingFilesHonoursConflictPolicies() = runBlocking {
        val cache = temp.newFolder("cache")
        val zip = temp.newFile("add.zip")
        writeZip(zip, mapOf("a.txt" to "old", "dir/old.txt" to "old"))
        val source = temp.newFile("new-a.txt").apply { writeText("new") }

        val replaced = service.addToArchive(
            zip.absolutePath,
            "",
            listOf(ArchiveInput(source.absolutePath, "a.txt")),
            ConflictPolicy.REPLACE,
            null,
            cache,
        )
        assertEquals(1, replaced.added)
        assertEquals(listOf(source.absolutePath), replaced.addedSources)
        assertEquals("new", service.readZipText(zip.absolutePath, "a.txt", null, cache))

        val skipped = service.addToArchive(
            zip.absolutePath,
            "",
            listOf(ArchiveInput(source.absolutePath, "a.txt")),
            ConflictPolicy.SKIP,
            null,
            cache,
        )
        assertEquals(0, skipped.added)
        assertEquals(1, skipped.skipped)
        assertTrue(skipped.addedSources.isEmpty())

        val kept = service.addToArchive(
            zip.absolutePath,
            "",
            listOf(ArchiveInput(source.absolutePath, "a.txt")),
            ConflictPolicy.KEEP_BOTH,
            null,
            cache,
        )
        assertEquals(1, kept.added)
        val names = service.listZip(zip.absolutePath, "", null, cache).map { it.name }.toSet()
        assertTrue("a (1).txt" in names)

        val folder = temp.newFolder("folder").apply { File(this, "child.txt").writeText("child") }
        val folderResult = service.addToArchive(
            zip.absolutePath,
            "",
            listOf(ArchiveInput(folder.absolutePath, "dir")),
            ConflictPolicy.REPLACE,
            null,
            cache,
        )
        assertEquals(1, folderResult.added)
        val replacedDir = service.listZip(zip.absolutePath, "dir", null, cache).map { it.name }.toSet()
        assertEquals(setOf("child.txt"), replacedDir)
    }

    @Test
    fun extractsSelectedEntriesAndRenamesTheirRoot() = runBlocking {
        val cache = temp.newFolder("cache")
        val zip = temp.newFile("extract.zip")
        writeZip(zip, mapOf("dir/a.txt" to "a", "dir/sub/b.txt" to "b", "single.txt" to "single"))
        val target = temp.newFolder("out")

        service.extractEntries(
            zip.absolutePath,
            listOf(ArchiveExtractItem("dir", "renamed")),
            target.absolutePath,
            null,
            cache,
        )
        assertEquals("a", File(target, "renamed/a.txt").readText())
        assertEquals("b", File(target, "renamed/sub/b.txt").readText())

        service.extractEntries(
            zip.absolutePath,
            listOf(ArchiveExtractItem("single.txt", "single-renamed.txt")),
            target.absolutePath,
            null,
            cache,
        )
        assertEquals("single", File(target, "single-renamed.txt").readText())
    }

    @Test
    fun renamesDeletesAndWritesEntries() = runBlocking {
        val cache = temp.newFolder("cache")
        val zip = temp.newFile("mutate.zip")
        writeZip(zip, mapOf("keep.txt" to "keep", "dir/a.txt" to "a", "dir/b.txt" to "b"))

        service.renameEntry(zip.absolutePath, "dir/a.txt", "c.txt", null, cache)
        assertEquals(setOf("c.txt", "b.txt"), service.listZip(zip.absolutePath, "dir", null, cache).map { it.name }.toSet())

        service.deleteEntries(zip.absolutePath, listOf("dir/b.txt"), null, cache)
        assertEquals(setOf("c.txt"), service.listZip(zip.absolutePath, "dir", null, cache).map { it.name }.toSet())

        service.writeZipText(zip.absolutePath, "dir/c.txt", "updated", null, cache)
        assertEquals("updated", service.readZipText(zip.absolutePath, "dir/c.txt", null, cache))
        assertEquals("keep", service.readZipText(zip.absolutePath, "keep.txt", null, cache))

        service.renameEntry(zip.absolutePath, "dir", "renamed", null, cache)
        assertTrue(service.listZip(zip.absolutePath, "", null, cache).any { it.name == "renamed" })
        assertEquals("updated", service.readZipText(zip.absolutePath, "renamed/c.txt", null, cache))

        service.deleteEntries(zip.absolutePath, listOf("renamed"), null, cache)
        assertEquals(setOf("keep.txt"), service.listZip(zip.absolutePath, "", null, cache).map { it.name }.toSet())
    }

    @Test
    fun createsFoldersAndRejectsUnsafeExtractionPaths() = runBlocking {
        val cache = temp.newFolder("cache")
        val zip = temp.newFile("create.zip")
        writeZip(zip, mapOf("existing.txt" to "x"))

        service.createEntry(zip.absolutePath, "", "empty", true, null, cache)
        val root = service.listZip(zip.absolutePath, "", null, cache)
        assertEquals(setOf("empty", "existing.txt"), root.map { it.name }.toSet())
        assertEquals(FileKind.DIRECTORY, root.first { it.name == "empty" }.kind)

        val plain = temp.newFile("plain.zip")
        writeZip(plain, mapOf("dir/" to "", "dir/../../escape.txt" to "bad"))
        val target = temp.newFolder("safe")
        val failure = runCatching {
            service.extractEntries(
                plain.absolutePath,
                listOf(ArchiveExtractItem("dir", "out")),
                target.absolutePath,
                null,
                cache,
            )
        }
        assertTrue(failure.isFailure)
        assertFalse(File(temp.root, "escape.txt").exists())
    }

    @Test
    fun listsImplicitDirectoriesWithoutExplicitEntries() = runBlocking {
        val cache = temp.newFolder("cache")
        val zip = temp.newFile("implicit.zip")
        writeZip(zip, mapOf("deep/nested/file.txt" to "value"))

        val root = service.listZip(zip.absolutePath, "", null, cache)
        assertEquals(listOf("deep"), root.map { it.name })
        assertEquals(FileKind.DIRECTORY, root.single().kind)
        val nested = service.listZip(zip.absolutePath, "deep/nested", null, cache)
        assertEquals("file.txt", nested.single().name)
        assertEquals("value", service.readZipText(zip.absolutePath, "deep/nested/file.txt", null, cache))

        val target = temp.newFolder("implicit-out")
        service.extractEntries(
            zip.absolutePath,
            listOf(ArchiveExtractItem("deep", "deep-copy")),
            target.absolutePath,
            null,
            cache,
        )
        assertEquals("value", File(target, "deep-copy/nested/file.txt").readText())
    }

    @Test
    fun addsIntoNestedDirectories() = runBlocking {
        val cache = temp.newFolder("cache")
        val zip = temp.newFile("nested-add.zip")
        writeZip(zip, mapOf("docs/a.txt" to "a"))
        val source = temp.newFile("b.txt").apply { writeText("b") }

        val result = service.addToArchive(
            zip.absolutePath,
            "docs",
            listOf(ArchiveInput(source.absolutePath, "b.txt")),
            ConflictPolicy.KEEP_BOTH,
            null,
            cache,
        )
        assertEquals(1, result.added)
        assertEquals(setOf("a.txt", "b.txt"), service.listZip(zip.absolutePath, "docs", null, cache).map { it.name }.toSet())
        assertEquals("b", service.readZipText(zip.absolutePath, "docs/b.txt", null, cache))
    }

    @Test
    fun addsIntoAnEmptyArchive() = runBlocking {
        val cache = temp.newFolder("cache")
        val zip = temp.newFile("empty.zip")
        writeZip(zip, emptyMap())
        assertEquals(emptyList<Any>(), service.listZip(zip.absolutePath, "", null, cache))

        val source = temp.newFile("first.txt").apply { writeText("first") }
        val result = service.addToArchive(
            zip.absolutePath,
            "",
            listOf(ArchiveInput(source.absolutePath, "first.txt")),
            ConflictPolicy.KEEP_BOTH,
            null,
            cache,
        )
        assertEquals(1, result.added)
        assertEquals("first", service.readZipText(zip.absolutePath, "first.txt", null, cache))
    }

    @Test
    fun archiveLocationMapsVirtualPaths() {
        val location = ArchiveLocation("/sdcard/test.zip", "dir/sub")
        assertEquals("/sdcard/test.zip/dir/sub", location.virtualPath)
        assertEquals("dir/sub", location.relativePath("/sdcard/test.zip/dir/sub"))
        assertEquals("dir", location.parent()?.innerPath)
        assertEquals("dir/sub/file.txt", location.child("file.txt").innerPath)
        assertTrue(location.contains("/sdcard/test.zip/dir"))
        assertFalse(location.contains("/sdcard/test.zip-other/dir"))
    }

    private fun writeZip(file: File, entries: Map<String, String>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                if (!name.endsWith('/')) zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }
}

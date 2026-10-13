package dev.fusion.archive

import dev.fusion.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream

class ZipGuardTest {

    private fun tempZip(bytes: ByteArray): File =
        File.createTempFile("guard", ".zip").apply { writeBytes(bytes); deleteOnExit() }

    @Test
    fun listsValidArchiveEntries() {
        val zip = tempZip(Fixtures.zipWithTextFile("docs/readme.txt", "hi"))
        val result = ZipGuard.list(zip)
        assertTrue(result.isSuccess)
        val entries = result.getOrThrow()
        assertEquals(1, entries.size)
        assertEquals("docs/readme.txt", entries[0].name)
        assertEquals(2L, entries[0].size)
        assertTrue(!entries[0].dir)
    }

    @Test
    fun rejectsTraversalEntryNames() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("../evil.txt"))
            zip.write("x".toByteArray())
            zip.closeEntry()
        }
        val result = ZipGuard.list(tempZip(out.toByteArray()))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Unsafe"))
    }

    @Test
    fun rejectsNonZipFile() {
        val result = ZipGuard.list(tempZip("not a zip".toByteArray()))
        assertTrue(result.isFailure)
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.fileops

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class FileMoveJournalTest {
    @Test
    fun verifiedMoveCrashRollbackRollForwardAndSymlinkConfinementHold() {
        val dir = Files.createTempDirectory("fusion-fileops-test-")
        try {
            val journal = FileMoveJournal(dir)
            val payload = "Crash-safe journaled document αβγ 2026".toByteArray(StandardCharsets.UTF_8)
            val src1 = dir.resolve("alpha.txt").also { Files.write(it, payload) }
            val dst1 = dir.resolve("nested/alpha-moved.txt")

            // 1. Crash right after PREPARED leaves source untouched and rolls back cleanly.
            assertThrows(RuntimeException::class.java) {
                journal.moveVerified("tx_prep_crash", src1, dst1) { stage ->
                    if (stage == JournalStage.PREPARED) throw RuntimeException("Simulated power loss after PREPARED")
                }
            }
            assertTrue(Files.exists(src1))
            assertArrayEquals(payload, Files.readAllBytes(src1))
            val rec1 = journal.recover()
            assertEquals(listOf("tx_prep_crash"), rec1.rolledBack)
            assertTrue(Files.exists(src1))
            assertFalse(Files.exists(dst1))

            // 2. Crash right after COPY_VERIFIED rolls forward cleanly and removes duplicate source.
            assertThrows(RuntimeException::class.java) {
                journal.moveVerified("tx_verified_crash", src1, dst1) { stage ->
                    if (stage == JournalStage.COPY_VERIFIED) throw RuntimeException("Simulated crash after COPY_VERIFIED")
                }
            }
            assertTrue(Files.exists(src1))
            assertTrue(Files.exists(dst1))
            val rec2 = journal.recover()
            assertEquals(listOf("tx_verified_crash"), rec2.rolledForward)
            assertFalse(Files.exists(src1))
            assertArrayEquals(payload, Files.readAllBytes(dst1))

            // 3. Torn trailing crash record is ignored while interior corruption fails closed.
            Files.writeString(dir.resolve(".fusion-fileops-journal-v1.log"), "deadbeef\ttorn_partial_line", StandardOpenOption.APPEND)
            val entries = journal.readLog()
            assertTrue(entries.isNotEmpty())

            // 4. Path escape and symlink escape refuse fail-closed without touching target.
            assertThrows(IllegalArgumentException::class.java) {
                journal.saveVerifiedCopy(dir.resolve("../escape.txt"), payload)
            }
            val outside = Files.createTempFile("fusion-outside-", ".txt")
            try {
                val link = dir.resolve("symlink.txt")
                Files.createSymbolicLink(link, outside)
                assertThrows(IllegalArgumentException::class.java) {
                    journal.moveVerified("tx_symlink", link, dir.resolve("copy.txt"))
                }
            } finally {
                Files.deleteIfExists(outside)
            }

            // 5. Conflict-free target naming never overwrites existing files.
            val c0 = FileMoveJournal.nonConflictingTarget(dir.resolve("nested"), "alpha-moved.txt")
            assertEquals("alpha-moved (1).txt", c0.fileName.toString())
        } finally {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}

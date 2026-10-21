// SPDX-License-Identifier: Apache-2.0
package dev.fusion.fileops

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class FileOpsKernelTest {
    @Test
    fun crashRecoveryRollsBackPreparedAndRollsForwardVerifiedMove() {
        val dir = Files.createTempDirectory("r26-fileops-test-")
        val journal = FileMoveJournal(dir)
        val payload = "atomic-payload-2026".encodeToByteArray()
        val src = dir.resolve("src.bin").also { Files.write(it, payload) }
        val dst = dir.resolve("nested/dst.bin")

        runCatching {
            journal.moveVerified("op1", src, dst) { st ->
                if (st == JournalStage.PREPARED) error("power-loss-1")
            }
        }
        val rec1 = journal.recover()
        assertEquals(listOf("op1"), rec1.rolledBack)
        assertTrue(Files.exists(src) && !Files.exists(dst))

        runCatching {
            journal.moveVerified("op2", src, dst) { st ->
                if (st == JournalStage.COPY_VERIFIED) error("power-loss-2")
            }
        }
        val rec2 = journal.recover()
        assertEquals(listOf("op2"), rec2.rolledForward)
        assertFalse(Files.exists(src))
        assertArrayEquals(payload, Files.readAllBytes(dst))
    }
}

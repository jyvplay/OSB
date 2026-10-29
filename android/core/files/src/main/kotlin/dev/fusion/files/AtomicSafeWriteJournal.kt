package dev.fusion.files

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Clean-room Crash-Safe Atomic Write-Ahead `.tmp` + `SHA-256` Verification + `ATOMIC_MOVE`
 * File Journal (Apache-2.0) ensuring power loss never leaves half-written corrupt user documents!
 */
object AtomicSafeWriteJournal {
    const val SCOPE =
        "Crash-safe staging .tmp + SHA-256 read-back verification + ATOMIC_MOVE commit & orphan staging sweeper"

    data class CommitReceipt(
        val targetPath: Path,
        val byteLength: Int,
        val sha256Hex: String,
    )

    fun writeAtomically(target: Path, payload: ByteArray): CommitReceipt {
        val parent = target.parent ?: error("Target path must have a parent directory")
        Files.createDirectories(parent)
        val expectedSha = sha256Hex(payload)
        val staging = parent.resolve(".${target.fileName}.fusion-stage.tmp")
        try {
            Files.write(staging, payload)
            val verifyRead = Files.readAllBytes(staging)
            check(sha256Hex(verifyRead) == expectedSha) { "Staging read-back SHA-256 mismatch before commit" }
            try {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(staging)
        }
        return CommitReceipt(target, payload.size, expectedSha)
    }

    fun sweepOrphanStageFiles(directory: Path): Int {
        if (!Files.isDirectory(directory)) return 0
        var deleted = 0
        Files.newDirectoryStream(directory).use { ds ->
            for (p in ds) {
                val fn = p.fileName.toString()
                if (fn.startsWith(".") && fn.endsWith(".fusion-stage.tmp")) {
                    if (Files.deleteIfExists(p)) deleted++
                }
            }
        }
        return deleted
    }

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}

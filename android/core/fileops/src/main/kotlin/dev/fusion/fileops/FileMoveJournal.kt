// SPDX-License-Identifier: Apache-2.0
package dev.fusion.fileops

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Collections
import java.util.zip.CRC32

/**
 * Crash-recoverable transactional file-move and safe-save journal for local filesystem & staging workspaces.
 *
 * Key invariant (proved in Z3 model `prove_r26_models.py::prove_file_move_journal`):
 * The source file is NEVER unlinked unless the destination file exists as a regular non-symlink file AND
 * its full post-write readback SHA-256 equals the pre-flight source SHA-256 (`COPY_VERIFIED`).
 * Any interruption prior to `COPY_VERIFIED` rolls back by deleting only the unverified staging/destination artifact,
 * leaving the original source bytes intact. Any interruption at or after `COPY_VERIFIED` rolls forward idempotently.
 *
 * Explicit boundary: Local NIO regular files only (`<= 64 MiB` per journaled operation). Does NOT claim cross-provider
 * Android SAF transactional atomicity over third-party cloud DocumentsProviders (gate S6 remains open).
 */
enum class JournalStage { PREPARED, COPY_VERIFIED, SOURCE_UNLINKED, COMMITTED, ROLLED_BACK }

data class JournalEntry(
    val txId: String,
    val source: String,
    val destination: String,
    val expectedBytes: Long,
    val sha256Hex: String,
    val stage: JournalStage
) {
    init {
        require(txId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid transaction identifier" }
        require(source.isNotBlank() && destination.isNotBlank() && source != destination) { "Distinct paths required" }
        require(expectedBytes in 0..FileMoveJournal.MAX_FILE_BYTES) { "File size exceeds bounded journal limit" }
        require(sha256Hex.matches(Regex("[0-9a-f]{64}"))) { "Invalid SHA-256 hex digest" }
    }
}

data class MoveReceipt(
    val txId: String,
    val source: Path,
    val destination: Path,
    val bytes: Long,
    val sha256Hex: String,
    val finalStage: JournalStage
)

data class RecoveryReport(
    val rolledBack: List<String>,
    val rolledForward: List<String>,
    val intactCommits: Int
)

class FileMoveJournal(private val workspaceRoot: Path) {
    companion object {
        const val MAX_FILE_BYTES: Long = 64L * 1024L * 1024L
        private const val JOURNAL_FILE = ".fusion-fileops-journal-v1.log"

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun sha256File(path: Path): Pair<Long, String> {
            require(Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                "Regular non-symlink file required: $path"
            }
            val size = Files.size(path)
            require(size in 0..MAX_FILE_BYTES) { "File exceeds bounded 64 MiB limit: $size" }
            val md = MessageDigest.getInstance("SHA-256")
            var total = 0L
            Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_FILE_BYTES) { "Concurrent growth beyond 64 MiB refused" }
                    md.update(buf, 0, n)
                }
            }
            require(total == size) { "Concurrent file length mutation during readback" }
            return total to md.digest().joinToString("") { "%02x".format(it) }
        }

        /** Non-clobbering conflict resolution (`report.txt` -> `report (1).txt`). */
        fun nonConflictingTarget(directory: Path, preferredName: String): Path {
            val trimmed = preferredName.trim()
            require(trimmed.length in 1..128 && '/' !in trimmed && '\\' !in trimmed && trimmed != "." && trimmed != "..") {
                "Invalid target filename"
            }
            val dot = trimmed.lastIndexOf('.')
            val stem = if (dot > 0) trimmed.substring(0, dot) else trimmed
            val ext = if (dot > 0) trimmed.substring(dot) else ""
            val direct = directory.resolve(trimmed).normalize()
            require(direct.parent == directory.normalize()) { "Path traversal refused" }
            if (!Files.exists(direct, LinkOption.NOFOLLOW_LINKS)) return direct
            for (index in 1..1024) {
                val candidate = directory.resolve("$stem ($index)$ext").normalize()
                if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) return candidate
            }
            throw IllegalStateException("Conflict namespace exhausted for $trimmed")
        }
    }

    init {
        Files.createDirectories(workspaceRoot)
        require(!Files.isSymbolicLink(workspaceRoot)) { "Workspace root must not be a symlink" }
    }

    private val root: Path = workspaceRoot.toRealPath(LinkOption.NOFOLLOW_LINKS)
    private val journalPath: Path = root.resolve(JOURNAL_FILE)

    private fun confine(candidate: Path): Path {
        val normalized = candidate.toAbsolutePath().normalize()
        require(normalized.startsWith(root) && normalized != root && normalized != journalPath) {
            "Path outside confined workspace refused: $candidate"
        }
        var cursor: Path? = normalized
        while (cursor != null && cursor != root) {
            require(!Files.isSymbolicLink(cursor)) { "Symlink component refused: $cursor" }
            cursor = cursor.parent
        }
        return normalized
    }

    private fun encodeRecord(entry: JournalEntry): ByteArray {
        val payload = listOf(
            entry.txId,
            entry.stage.name,
            entry.expectedBytes.toString(),
            entry.sha256Hex,
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(entry.source.toByteArray(StandardCharsets.UTF_8)),
            java.util.Base64.getUrlEncoder().withoutPadding().withoutPadding().encodeToString(entry.destination.toByteArray(StandardCharsets.UTF_8))
        ).joinToString("\t")
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        val crc = CRC32().apply { update(payloadBytes) }.value
        return ("%08x\t%s\n".format(crc, payload)).toByteArray(StandardCharsets.UTF_8)
    }

    private fun appendRecord(entry: JournalEntry) {
        val bytes = encodeRecord(entry)
        Files.newByteChannel(
            journalPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
            StandardOpenOption.DSYNC
        ).use { channel ->
            val buf = ByteBuffer.wrap(bytes)
            while (buf.hasRemaining()) channel.write(buf)
        }
    }

    /** Parse all intact CRC-verified records; torn trailing crash line (if any) is safely ignored after valid prefix. */
    fun readLog(): List<JournalEntry> {
        if (!Files.exists(journalPath, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        require(!Files.isSymbolicLink(journalPath)) { "Symlink journal refused" }
        val lines = Files.readAllLines(journalPath, StandardCharsets.UTF_8)
        val result = ArrayList<JournalEntry>(lines.size)
        for ((idx, line) in lines.withIndex()) {
            if (line.isEmpty()) continue
            val tab = line.indexOf('\t')
            if (tab != 8) {
                if (idx == lines.lastIndex) break // Torn final record at crash power loss
                throw IllegalStateException("Corrupt interior journal record at line ${idx + 1}")
            }
            val expectedCrc = line.substring(0, 8).toLongOrNull(16)
                ?: if (idx == lines.lastIndex) break else throw IllegalStateException("Malformed CRC at line ${idx + 1}")
            val body = line.substring(9)
            val actualCrc = CRC32().apply { update(body.toByteArray(StandardCharsets.UTF_8)) }.value
            if (expectedCrc != actualCrc) {
                if (idx == lines.lastIndex) break // Torn write at EOF only
                throw IllegalStateException("CRC mismatch in committed interior record at line ${idx + 1}")
            }
            val parts = body.split('\t')
            require(parts.size == 6) { "Invalid journal column count at line ${idx + 1}" }
            val decoder = java.util.Base64.getUrlDecoder()
            result.add(
                JournalEntry(
                    txId = parts[0],
                    source = String(decoder.decode(parts[4]), StandardCharsets.UTF_8),
                    destination = String(decoder.decode(parts[5]), StandardCharsets.UTF_8),
                    expectedBytes = parts[2].toLong(),
                    sha256Hex = parts[3],
                    stage = JournalStage.valueOf(parts[1])
                )
            )
        }
        return Collections.unmodifiableList(result)
    }

    /**
     * Execute a verified copy-then-unlink move with optional fault injection hook (`crashAfterStage`) for deterministic testing.
     */
    fun moveVerified(
        txId: String,
        sourcePath: Path,
        destinationPath: Path,
        faultHook: ((JournalStage) -> Unit)? = null
    ): MoveReceipt {
        val src = confine(sourcePath)
        val dst = confine(destinationPath)
        require(src != dst) { "Source and destination must not alias" }
        require(!Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) { "Destination already exists; refusing overwrite: $dst" }
        Files.createDirectories(dst.parent)
        val (size, digest) = sha256File(src)
        val relSrc = root.relativize(src).toString()
        val relDst = root.relativize(dst).toString()

        appendRecord(JournalEntry(txId, relSrc, relDst, size, digest, JournalStage.PREPARED))
        faultHook?.invoke(JournalStage.PREPARED)

        val tempTarget = dst.parent.resolve(".${dst.fileName}.$txId.part")
        try {
            Files.newInputStream(src, LinkOption.NOFOLLOW_LINKS).use { input ->
                Files.newOutputStream(
                    tempTarget,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.DSYNC
                ).use { output ->
                    input.copyTo(output, 16 * 1024)
                }
            }
            val (verifySize, verifyDigest) = sha256File(tempTarget)
            require(verifySize == size && verifyDigest == digest) {
                "Staging readback digest mismatch ($verifyDigest != $digest)"
            }
            val (postSrcSize, postSrcDigest) = sha256File(src)
            require(postSrcSize == size && postSrcDigest == digest) {
                "Source modified concurrently during move; refusing unlink"
            }
            Files.move(tempTarget, dst, StandardCopyOption.ATOMIC_MOVE)
        } catch (err: Throwable) {
            Files.deleteIfExists(tempTarget)
            if (!Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) {
                appendRecord(JournalEntry(txId, relSrc, relDst, size, digest, JournalStage.ROLLED_BACK))
            }
            throw err
        }

        val (dstSize, dstDigest) = sha256File(dst)
        check(dstSize == size && dstDigest == digest) { "Destination post-rename verification failed" }
        appendRecord(JournalEntry(txId, relSrc, relDst, size, digest, JournalStage.COPY_VERIFIED))
        faultHook?.invoke(JournalStage.COPY_VERIFIED)

        Files.deleteIfExists(src)
        appendRecord(JournalEntry(txId, relSrc, relDst, size, digest, JournalStage.SOURCE_UNLINKED))
        faultHook?.invoke(JournalStage.SOURCE_UNLINKED)

        appendRecord(JournalEntry(txId, relSrc, relDst, size, digest, JournalStage.COMMITTED))
        return MoveReceipt(txId, src, dst, size, digest, JournalStage.COMMITTED)
    }

    /** Safe atomic save-copy: writes bytes to staging file, fsyncs, verifies exact SHA-256 readback, then renames into place. */
    fun saveVerifiedCopy(destinationPath: Path, payload: ByteArray): MoveReceipt {
        require(payload.size.toLong() <= MAX_FILE_BYTES) { "Payload exceeds 64 MiB limit" }
        val dst = confine(destinationPath)
        require(!Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) { "Refusing to clobber existing file: $dst" }
        Files.createDirectories(dst.parent)
        val digest = sha256(payload)
        val txId = "save-${digest.take(12)}"
        val part = dst.parent.resolve(".${dst.fileName}.$txId.part")
        try {
            Files.write(part, payload, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.DSYNC)
            val (sz, actual) = sha256File(part)
            check(sz == payload.size.toLong() && actual == digest) { "Exact readback failed on staging file" }
            Files.move(part, dst, StandardCopyOption.ATOMIC_MOVE)
            return MoveReceipt(txId, dst, dst, sz, digest, JournalStage.COMMITTED)
        } finally {
            Files.deleteIfExists(part)
        }
    }

    /** Replay uncommitted transactions deterministically after crash or restart. */
    fun recover(): RecoveryReport {
        val entries = readLog()
        val latestByTx = LinkedHashMap<String, JournalEntry>()
        for (entry in entries) latestByTx[entry.txId] = entry
        val rolledBack = mutableListOf<String>()
        val rolledForward = mutableListOf<String>()
        var committed = 0
        for ((txId, last) in latestByTx) {
            val src = confine(root.resolve(last.source))
            val dst = confine(root.resolve(last.destination))
            val part = dst.parent.resolve(".${dst.fileName}.$txId.part")
            Files.deleteIfExists(part)
            when (last.stage) {
                JournalStage.COMMITTED -> committed++
                JournalStage.ROLLED_BACK -> Unit
                JournalStage.PREPARED -> {
                    val dstValid = Files.exists(dst, LinkOption.NOFOLLOW_LINKS) &&
                        runCatching { sha256File(dst) }.getOrNull() == (last.expectedBytes to last.sha256Hex)
                    if (dstValid) {
                        Files.deleteIfExists(src)
                        appendRecord(last.copy(stage = JournalStage.COMMITTED))
                        rolledForward.add(txId)
                    } else {
                        check(Files.exists(src, LinkOption.NOFOLLOW_LINKS)) { "Unrecoverable state: missing source prior to COPY_VERIFIED for $txId" }
                        Files.deleteIfExists(dst)
                        appendRecord(last.copy(stage = JournalStage.ROLLED_BACK))
                        rolledBack.add(txId)
                    }
                }
                JournalStage.COPY_VERIFIED, JournalStage.SOURCE_UNLINKED -> {
                    val (sz, digest) = sha256File(dst)
                    check(sz == last.expectedBytes && digest == last.sha256Hex) {
                        "Verified destination corrupted post-crash for $txId"
                    }
                    Files.deleteIfExists(src)
                    appendRecord(last.copy(stage = JournalStage.COMMITTED))
                    rolledForward.add(txId)
                }
            }
        }
        return RecoveryReport(
            Collections.unmodifiableList(rolledBack),
            Collections.unmodifiableList(rolledForward),
            committed
        )
    }
}

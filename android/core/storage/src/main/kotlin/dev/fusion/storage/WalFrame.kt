// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Crash-Safe Write-Ahead Log (`WAL`) binary frame codec & torn-tail recovery engine.
 *
 * Inspired by SQLite WAL (`file format §4`):
 * - Header: `8` bytes (`"FWAL\x00\x01\x00\x00"` + `4`-byte `salt`).
 * - Each frame: `[seqNo: u32][pageId: u32][committedFlag: u8][payloadLen: u32][prevCrc: u32][frameCrc: u32][payload]`.
 * - Cumulative CRC32 chains every frame to `salt` and all preceding frames, so out-of-order,
 *   bit-flipped, or stale-epoch frames from a previous WAL incarnation fail CRC verification.
 * - Crash recovery scans forward and returns ONLY pages up to the last valid `committedFlag = 1`
 *   frame, cleanly discarding uncommitted or torn trailing frames!
 */
object WalFrame {
    const val SCOPE =
        "Cumulative-CRC32 chained Write-Ahead Log codec with torn-tail and uncommitted-suffix crash recovery; OS fsync/mmap file locks out of scope."

    const val MAX_PAGE_BYTES = 65_536
    const val MAX_FRAMES = 8_192
    private val MAGIC = byteArrayOf('F'.code.toByte(), 'W'.code.toByte(), 'A'.code.toByte(), 'L'.code.toByte(), 0, 1, 0, 0)
    private const val HEADER_LEN = 12 // 8 magic + 4 salt
    private const val FRAME_HDR_LEN = 21 // 4 seq + 4 pageId + 1 commit + 4 len + 4 prevCrc + 4 frameCrc

    data class Entry(
        val pageId: Int,
        val payload: ByteArray,
        val isCommit: Boolean,
    ) {
        init {
            require(pageId >= 0) { "pageId must be >= 0" }
            require(payload.size in 1..MAX_PAGE_BYTES) { "payload size ${payload.size} outside 1..$MAX_PAGE_BYTES" }
        }
    }

    data class RecoveryReport(
        val committedPages: Map<Int, ByteArray>,
        val committedFrameCount: Int,
        val discardedTrailingFrames: Int,
        val truncatedTornBytes: Int,
    )

    fun encode(salt: Int, entries: List<Entry>): ByteArray {
        require(entries.size <= MAX_FRAMES) { "WAL exceeds $MAX_FRAMES frames" }
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(u32Be(salt))
        var runningCrc = salt
        for ((idx, e) in entries.withIndex()) {
            val seq = idx + 1
            val frameCrc = computeFrameCrc(salt, seq, e.pageId, e.isCommit, runningCrc, e.payload)
            val hdr = ByteBuffer.allocate(FRAME_HDR_LEN).order(ByteOrder.BIG_ENDIAN)
                .putInt(seq)
                .putInt(e.pageId)
                .put(if (e.isCommit) 1.toByte() else 0.toByte())
                .putInt(e.payload.size)
                .putInt(runningCrc)
                .putInt(frameCrc)
                .array()
            out.write(hdr)
            out.write(e.payload)
            runningCrc = frameCrc
        }
        return out.toByteArray()
    }

    fun recover(walBytes: ByteArray): RecoveryReport {
        require(walBytes.size >= HEADER_LEN) { "WAL shorter than $HEADER_LEN-byte header" }
        for (i in MAGIC.indices) {
            require(walBytes[i] == MAGIC[i]) { "invalid WAL magic header at byte $i" }
        }
        val salt = ByteBuffer.wrap(walBytes, 8, 4).order(ByteOrder.BIG_ENDIAN).int

        var pos = HEADER_LEN
        var expectedSeq = 1
        var runningCrc = salt
        var tornBytes = 0

        val staged = ArrayList<Pair<Int, ByteArray>>()
        val committed = LinkedHashMap<Int, ByteArray>()
        var committedFrames = 0
        var uncommittedTrailing = 0

        while (pos < walBytes.size) {
            if (pos + FRAME_HDR_LEN > walBytes.size) {
                tornBytes = walBytes.size - pos
                break
            }
            val hdr = ByteBuffer.wrap(walBytes, pos, FRAME_HDR_LEN).order(ByteOrder.BIG_ENDIAN)
            val seq = hdr.int
            val pageId = hdr.int
            val commitByte = hdr.get().toInt() and 0xFF
            val payloadLen = hdr.int
            val prevCrc = hdr.int
            val frameCrc = hdr.int

            if (seq != expectedSeq || pageId < 0 || (commitByte != 0 && commitByte != 1) ||
                payloadLen !in 1..MAX_PAGE_BYTES || prevCrc != runningCrc
            ) {
                tornBytes = walBytes.size - pos
                break
            }
            if (pos + FRAME_HDR_LEN + payloadLen > walBytes.size) {
                tornBytes = walBytes.size - pos
                break
            }
            val payload = walBytes.copyOfRange(pos + FRAME_HDR_LEN, pos + FRAME_HDR_LEN + payloadLen)
            val isCommit = commitByte == 1
            val actualCrc = computeFrameCrc(salt, seq, pageId, isCommit, runningCrc, payload)
            if (actualCrc != frameCrc) {
                tornBytes = walBytes.size - pos
                break
            }
            staged.add(pageId to payload)
            uncommittedTrailing++
            if (isCommit) {
                for ((pid, bytes) in staged) committed[pid] = bytes
                staged.clear()
                committedFrames += uncommittedTrailing
                uncommittedTrailing = 0
            }
            runningCrc = frameCrc
            expectedSeq++
            pos += FRAME_HDR_LEN + payloadLen
        }

        return RecoveryReport(
            committedPages = committed,
            committedFrameCount = committedFrames,
            discardedTrailingFrames = uncommittedTrailing,
            truncatedTornBytes = tornBytes,
        )
    }

    /** Compacts recovered pages into a minimal single-commit WAL checkpoint with a fresh salt. */
    fun checkpoint(recovered: RecoveryReport, newSalt: Int): ByteArray {
        val pages = recovered.committedPages.entries.toList()
        if (pages.isEmpty()) return encode(newSalt, emptyList())
        val entries = pages.mapIndexed { idx, (pid, bytes) ->
            Entry(pid, bytes, isCommit = idx == pages.lastIndex)
        }
        return encode(newSalt, entries)
    }

    private fun computeFrameCrc(
        salt: Int,
        seq: Int,
        pageId: Int,
        isCommit: Boolean,
        prevCrc: Int,
        payload: ByteArray,
    ): Int {
        val crc = CRC32()
        val meta = ByteBuffer.allocate(17).order(ByteOrder.BIG_ENDIAN)
            .putInt(salt)
            .putInt(seq)
            .putInt(pageId)
            .put(if (isCommit) 1.toByte() else 0.toByte())
            .putInt(prevCrc)
            .array()
        crc.update(meta)
        crc.update(payload)
        return crc.value.toInt()
    }

    private fun u32Be(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(v).array()
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Clean-room Tails `udev-watchdog`-inspired emergency RAM secret zeroization scheduler (`RamWipeSchedule`).
 *
 * Coordinates deterministic, dependency-ordered volatile buffer destruction across subsystems when a
 * USB cable pull (`UsbGuardPolicy` panic), duress PIN, or emergency wipe trigger fires:
 * - Stages execute strictly in ascending priority order (`SESSION_CRYPTO_KEYS` first so wrapped ciphertexts
 *   become immediately undecryptable even if interrupted mid-wipe!).
 * - Overwrites every registered `ByteArray` (`0x00 -> 0xFF -> 0x00`) and `CharArray` (`'\uFFFF' -> '\u0000'`)
 *   in place and verifies post-wipe all-zero state (`verifiedZeroed == true`).
 */
class RamWipeSchedule {
    companion object {
        const val SCOPE =
            "Ordered multi-stage RAM ByteArray/CharArray panic zeroizer (3-pass overwrite + post-wipe all-zero verification); JVM GC heap-copy pinning without JNI mlock/memset_s out of scope."
    }

    enum class Stage(val order: Int) {
        SESSION_CRYPTO_KEYS(1),
        CLIPBOARD_SENSITIVE(2),
        TOR_STREAM_TOKENS(3),
        DOCUMENT_SCRATCH(4),
        THUMBNAIL_CACHE(5),
    }

    private sealed interface WipeTarget {
        val label: String
        val stage: Stage
        fun scrubAndVerify(): Pair<Int, Boolean>
    }

    private class ByteTarget(
        override val label: String,
        override val stage: Stage,
        private val buf: ByteArray,
    ) : WipeTarget {
        override fun scrubAndVerify(): Pair<Int, Boolean> {
            buf.fill(0x00)
            buf.fill(0xFF.toByte())
            buf.fill(0x00)
            var nonZero = 0
            for (b in buf) nonZero = nonZero or (b.toInt() and 0xFF)
            return buf.size to (nonZero == 0)
        }
    }

    private class CharTarget(
        override val label: String,
        override val stage: Stage,
        private val buf: CharArray,
    ) : WipeTarget {
        override fun scrubAndVerify(): Pair<Int, Boolean> {
            buf.fill('\uFFFF')
            buf.fill('\u0000')
            var nonZero = 0
            for (c in buf) nonZero = nonZero or c.code
            return (buf.size * 2) to (nonZero == 0)
        }
    }

    data class StageReceipt(
        val stage: Stage,
        val label: String,
        val bytesScrubbed: Int,
        val verifiedZeroed: Boolean,
    )

    data class WipeReport(
        val triggerReason: String,
        val receipts: List<StageReceipt>,
        val totalBytesScrubbed: Int,
        val allVerifiedZero: Boolean,
    )

    private val targets = ArrayList<WipeTarget>()

    fun registerBytes(stage: Stage, label: String, buffer: ByteArray) {
        require(label.isNotBlank()) { "Wipe target label must not be blank" }
        targets.add(ByteTarget(label, stage, buffer))
    }

    fun registerChars(stage: Stage, label: String, buffer: CharArray) {
        require(label.isNotBlank()) { "Wipe target label must not be blank" }
        targets.add(CharTarget(label, stage, buffer))
    }

    fun executePanicWipe(triggerReason: String): WipeReport {
        val ordered = targets.sortedBy { it.stage.order }
        val receipts = ArrayList<StageReceipt>(ordered.size)
        var total = 0
        var allOk = true
        for (t in ordered) {
            val (nBytes, ok) = t.scrubAndVerify()
            receipts.add(StageReceipt(t.stage, t.label, nBytes, ok))
            total += nBytes
            if (!ok) allOk = false
        }
        targets.clear()
        return WipeReport(
            triggerReason = triggerReason,
            receipts = receipts,
            totalBytesScrubbed = total,
            allVerifiedZero = allOk,
        )
    }
}

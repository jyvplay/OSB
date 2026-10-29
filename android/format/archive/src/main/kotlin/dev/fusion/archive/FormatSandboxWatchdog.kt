// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

/**
 * Clean-room Isolated Format Parser Watchdog & Decompression-Bomb Budget (`FF-12`).
 *
 * Enforces deterministic step fuel, container nesting ceiling, allocation cap, and compression
 * amplification ratio (`expandedBytes / compressedBytes <= maxAmplificationRatio`) across
 * untrusted archive, Office, PDF, and image parsers.
 */
class FormatSandboxWatchdog(
    val maxSteps: Long = 250_000L,
    val maxExpandedBytes: Long = 64L * 1024L * 1024L,
    val maxNestingDepth: Int = 8,
    val maxAmplificationRatio: Double = 32.0,
    val maxEntryCount: Int = 4_096,
) {
    enum class ViolationKind {
        FUEL_EXHAUSTED,
        EXPANDED_BYTES_EXCEEDED,
        NESTING_DEPTH_EXCEEDED,
        AMPLIFICATION_RATIO_EXCEEDED,
        ENTRY_COUNT_EXCEEDED,
    }

    class SandboxAbortException(val kind: ViolationKind, message: String) : IllegalStateException(message)

    var stepsUsed: Long = 0L
        private set
    var expandedBytesUsed: Long = 0L
        private set
    var currentDepth: Int = 0
        private set
    var entriesVisited: Int = 0
        private set

    init {
        require(maxSteps > 0 && maxExpandedBytes > 0 && maxNestingDepth in 1..32 && maxAmplificationRatio >= 1.0) {
            "invalid FormatSandboxWatchdog limits"
        }
    }

    fun consumeSteps(delta: Long = 1L) {
        require(delta >= 0L) { "negative step delta $delta" }
        stepsUsed += delta
        if (stepsUsed > maxSteps) {
            throw SandboxAbortException(ViolationKind.FUEL_EXHAUSTED, "parser fuel $stepsUsed > $maxSteps")
        }
    }

    fun recordEntry(compressedBytes: Long, expandedBytes: Long) {
        require(compressedBytes >= 0L && expandedBytes >= 0L) { "negative entry sizes" }
        entriesVisited++
        if (entriesVisited > maxEntryCount) {
            throw SandboxAbortException(ViolationKind.ENTRY_COUNT_EXCEEDED, "entries $entriesVisited > $maxEntryCount")
        }
        // Guard small headers with a 4 KiB grace baseline before applying ratio ceiling
        val denom = maxOf(compressedBytes, 4_096L).toDouble()
        val ratio = expandedBytes.toDouble() / denom
        if (ratio > maxAmplificationRatio) {
            throw SandboxAbortException(
                ViolationKind.AMPLIFICATION_RATIO_EXCEEDED,
                "decompression ratio ${"%.2f".format(java.util.Locale.US, ratio)}x > ${maxAmplificationRatio}x",
            )
        }
        expandedBytesUsed += expandedBytes
        if (expandedBytesUsed > maxExpandedBytes) {
            throw SandboxAbortException(
                ViolationKind.EXPANDED_BYTES_EXCEEDED,
                "expanded bytes $expandedBytesUsed > $maxExpandedBytes",
            )
        }
    }

    inline fun <T> withNestedContainer(block: () -> T): T {
        enterNested()
        return try {
            block()
        } finally {
            leaveNested()
        }
    }

    fun enterNested() {
        currentDepth++
        if (currentDepth > maxNestingDepth) {
            currentDepth--
            throw SandboxAbortException(
                ViolationKind.NESTING_DEPTH_EXCEEDED,
                "archive/container nesting depth > $maxNestingDepth",
            )
        }
    }

    fun leaveNested() {
        if (currentDepth > 0) currentDepth--
    }
}

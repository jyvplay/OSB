// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

/**
 * Hierarchical cgroup-v2-inspired Sandbox Resource Quota & Token-Bucket Controller.
 *
 * Tracks memory bytes, open descriptor counts, and CPU instruction/tick rate tokens across a
 * parent-child sandbox tree:
 * - Every allocation checks BOTH the child quota and every ancestor quota atomically; if an
 *   ancestor rejects, zero partial charge leaks into child counters.
 * - Peak high-water marks (`peakMemoryBytes`, `peakDescriptors`) are monotonically non-decreasing.
 */
object QuotaBudget {
    const val SCOPE =
        "Hierarchical memory/descriptor/CPU token-bucket quota controller with atomic ancestor rollback and peak watermark accounting; Linux kernel cgroupfs mounts out of scope."

    data class Limits(
        val maxMemoryBytes: Long,
        val maxDescriptors: Int,
        val cpuBurstTokens: Long,
        val cpuRefillPerSec: Long,
    ) {
        init {
            require(maxMemoryBytes > 0L) { "maxMemoryBytes must be > 0" }
            require(maxDescriptors >= 0) { "maxDescriptors must be >= 0" }
            require(cpuBurstTokens > 0L && cpuRefillPerSec >= 0L) { "invalid CPU token limits" }
        }
    }

    data class Snapshot(
        val name: String,
        val currentMemoryBytes: Long,
        val peakMemoryBytes: Long,
        val openDescriptors: Int,
        val peakDescriptors: Int,
        val availableCpuTokens: Long,
    )

    class Node(
        val name: String,
        val limits: Limits,
        val parent: Node? = null,
        initialEpochMs: Long = 0L,
    ) {
        init {
            require(name.isNotBlank()) { "quota node name must not be blank" }
            require(initialEpochMs >= 0L) { "negative initialEpochMs" }
        }

        private var memBytes: Long = 0L
        private var peakMem: Long = 0L
        private var fds: Int = 0
        private var peakFds: Int = 0
        private var cpuTokens: Long = limits.cpuBurstTokens
        private var lastRefillMs: Long = initialEpochMs

        fun tryAllocateMemory(bytes: Long): Boolean {
            require(bytes >= 0L) { "cannot allocate negative bytes ($bytes)" }
            if (bytes == 0L) return true
            val chain = lineage()
            if (chain.any { it.memBytes + bytes > it.limits.maxMemoryBytes }) return false
            for (n in chain) {
                n.memBytes += bytes
                if (n.memBytes > n.peakMem) n.peakMem = n.memBytes
            }
            return true
        }

        fun releaseMemory(bytes: Long) {
            require(bytes >= 0L && bytes <= memBytes) {
                "cannot release $bytes bytes from '$name' (current=$memBytes)"
            }
            for (n in lineage()) n.memBytes -= bytes
        }

        fun tryOpenDescriptor(count: Int = 1): Boolean {
            require(count >= 1) { "descriptor count must be >= 1" }
            val chain = lineage()
            if (chain.any { it.fds + count > it.limits.maxDescriptors }) return false
            for (n in chain) {
                n.fds += count
                if (n.fds > n.peakFds) n.peakFds = n.fds
            }
            return true
        }

        fun closeDescriptor(count: Int = 1) {
            require(count in 1..fds) { "cannot close $count descriptors on '$name' (open=$fds)" }
            for (n in lineage()) n.fds -= count
        }

        fun tryConsumeCpu(tokens: Long, nowEpochMs: Long): Boolean {
            require(tokens > 0L) { "tokens must be > 0" }
            val chain = lineage()
            for (n in chain) n.refillAt(nowEpochMs)
            if (chain.any { it.cpuTokens < tokens }) return false
            for (n in chain) n.cpuTokens -= tokens
            return true
        }

        fun snapshot(): Snapshot = Snapshot(name, memBytes, peakMem, fds, peakFds, cpuTokens)

        private fun refillAt(nowMs: Long) {
            require(nowMs >= lastRefillMs) { "non-monotonic clock $nowMs < $lastRefillMs" }
            val elapsedMs = nowMs - lastRefillMs
            if (elapsedMs > 0L && limits.cpuRefillPerSec > 0L) {
                val added = (elapsedMs * limits.cpuRefillPerSec) / 1000L
                if (added > 0L) {
                    cpuTokens = (cpuTokens + added).coerceAtMost(limits.cpuBurstTokens)
                    lastRefillMs += (added * 1000L) / limits.cpuRefillPerSec
                }
            }
        }

        private fun lineage(): List<Node> {
            val out = ArrayList<Node>(4)
            var cur: Node? = this
            while (cur != null) {
                out.add(cur)
                cur = cur.parent
            }
            return out
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.together

/**
 * State-Based Last-Writer-Wins Element Map (`LWW-Element-Map` CvRDT) with Hybrid Logical Clocks.
 *
 * Satisfies the three mathematical join-semilattice laws across arbitrary P2P network reorderings:
 * 1. **Commutativity**: `a.merge(b) == b.merge(a)`
 * 2. **Associativity**: `a.merge(b).merge(c) == a.merge(b.merge(c))`
 * 3. **Idempotence**:   `a.merge(a) == a`
 *
 * Retains tombstones (`value = null`) with HLC stamps so delayed out-of-order `put` replicas from
 * before a `remove` can never resurrect deleted keys ("zombie key" anomaly closed).
 */
object CrdtLwwMap {
    const val SCOPE =
        "Join-semilattice LWW-Element-Map CvRDT over total-ordered Hybrid Logical Clocks (wallMs, logical, replicaId) with tombstone retention; delta-state anti-entropy GC watermarks out of scope."

    data class Hlc(
        val wallMs: Long,
        val logical: Int,
        val replicaId: String,
    ) : Comparable<Hlc> {
        init {
            require(wallMs >= 0L && logical >= 0) { "negative HLC component" }
            require(replicaId.isNotBlank()) { "replicaId must not be blank" }
        }

        override fun compareTo(other: Hlc): Int {
            wallMs.compareTo(other.wallMs).let { if (it != 0) return it }
            logical.compareTo(other.logical).let { if (it != 0) return it }
            return replicaId.compareTo(other.replicaId)
        }

        fun tickLocal(physicalNowMs: Long): Hlc =
            if (physicalNowMs > wallMs) Hlc(physicalNowMs, 0, replicaId)
            else Hlc(wallMs, logical + 1, replicaId)

        fun tickReceive(remote: Hlc, localReplicaId: String, physicalNowMs: Long): Hlc {
            val maxWall = maxOf(physicalNowMs, maxOf(wallMs, remote.wallMs))
            val nextLogical = when {
                maxWall == wallMs && maxWall == remote.wallMs -> maxOf(logical, remote.logical) + 1
                maxWall == wallMs -> logical + 1
                maxWall == remote.wallMs -> remote.logical + 1
                else -> 0
            }
            return Hlc(maxWall, nextLogical, localReplicaId)
        }
    }

    data class Register(
        val value: String?, // null = tombstone
        val stamp: Hlc,
    )

    class ReplicaMap private constructor(
        private val registers: Map<String, Register>,
    ) {
        fun get(key: String): String? = registers[key]?.value

        fun liveEntries(): Map<String, String> {
            val out = java.util.TreeMap<String, String>()
            for ((k, reg) in registers) {
                if (reg.value != null) out[k] = reg.value
            }
            return out
        }

        fun tombstoneCount(): Int = registers.values.count { it.value == null }

        fun put(key: String, value: String, stamp: Hlc): ReplicaMap {
            require(key.isNotEmpty()) { "CRDT key must not be empty" }
            return applyRegister(key, Register(value, stamp))
        }

        fun remove(key: String, stamp: Hlc): ReplicaMap {
            require(key.isNotEmpty()) { "CRDT key must not be empty" }
            return applyRegister(key, Register(null, stamp))
        }

        fun merge(other: ReplicaMap): ReplicaMap {
            val merged = java.util.TreeMap<String, Register>(registers)
            for ((k, theirReg) in other.registers) {
                val mine = merged[k]
                if (mine == null || chooseWinner(mine, theirReg) == theirReg) {
                    merged[k] = theirReg
                }
            }
            return ReplicaMap(merged)
        }

        private fun applyRegister(key: String, candidate: Register): ReplicaMap {
            val next = java.util.TreeMap<String, Register>(registers)
            val cur = next[key]
            if (cur == null || chooseWinner(cur, candidate) == candidate) {
                next[key] = candidate
            }
            return ReplicaMap(next)
        }

        override fun equals(other: Any?): Boolean = other is ReplicaMap && registers == other.registers
        override fun hashCode(): Int = registers.hashCode()

        companion object {
            fun empty(): ReplicaMap = ReplicaMap(emptyMap())

            private fun chooseWinner(a: Register, b: Register): Register {
                val c = a.stamp.compareTo(b.stamp)
                return when {
                    c > 0 -> a
                    c < 0 -> b
                    // Identical HLC (e.g. idempotent merge or malformed duplicate clock): deterministic bias to delete-wins (null), then lexicographic value
                    a.value == null -> a
                    b.value == null -> b
                    a.value >= b.value -> a
                    else -> b
                }
            }
        }
    }
}

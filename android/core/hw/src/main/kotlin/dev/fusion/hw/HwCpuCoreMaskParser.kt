// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Bounded Linux CPU core range list parser (`0-3,6,8-11`).
 */
class HwCpuCoreMaskParser private constructor() {
    companion object {
        const val MAX_CORES = 1024

        fun parse(spec: String): List<Int> {
            val out = sortedSetOf<Int>()
            for (part in spec.split(',')) {
                val tok = part.trim()
                if (tok.isEmpty()) continue
                if ('-' in tok) {
                    val b = tok.split('-')
                    require(b.size == 2) { "invalid range: $tok" }
                    val s = b[0].toInt()
                    val e = b[1].toInt()
                    require(s in 0..e && e < MAX_CORES) { "core out of bounds" }
                    for (c in s..e) out.add(c)
                } else {
                    val c = tok.toInt()
                    require(c in 0 until MAX_CORES) { "core out of bounds" }
                    out.add(c)
                }
            }
            return out.toList()
        }
    }
}

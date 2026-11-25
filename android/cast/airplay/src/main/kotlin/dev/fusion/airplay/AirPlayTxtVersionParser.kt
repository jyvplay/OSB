// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

/**
 * Bounded AirPlay TXT record key=value parser.
 */
class AirPlayTxtVersionParser private constructor() {
    companion object {
        fun parse(entries: List<String>): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for (e in entries) {
                val eq = e.indexOf('=')
                if (eq <= 0) continue
                out[e.substring(0, eq).trim()] = e.substring(eq + 1).trim()
            }
            return out
        }
    }
}

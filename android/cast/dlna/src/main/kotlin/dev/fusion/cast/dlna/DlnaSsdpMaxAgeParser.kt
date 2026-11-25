// SPDX-License-Identifier: Apache-2.0
package dev.fusion.cast.dlna

/**
 * Bounded SSDP `Cache-Control: max-age=<sec>` parser.
 */
class DlnaSsdpMaxAgeParser private constructor() {
    companion object {
        private val MAX_AGE_RE = Regex("max-age\\s*=\\s*(\\d{1,9})", RegexOption.IGNORE_CASE)

        fun parseSeconds(cacheControl: String): Int? {
            val m = MAX_AGE_RE.find(cacheControl) ?: return null
            return m.groupValues[1].toIntOrNull()
        }
    }
}

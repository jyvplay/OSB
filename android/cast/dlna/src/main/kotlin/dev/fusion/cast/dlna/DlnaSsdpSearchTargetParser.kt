// SPDX-License-Identifier: Apache-2.0
package dev.fusion.cast.dlna

/**
 * Bounded SSDP search target (ST/NT) parser and validator.
 */
class DlnaSsdpSearchTargetParser private constructor() {
    companion object {
        const val MAX_ST_LEN = 256
        private val URN_RE = Regex("^urn:[a-zA-Z0-9._-]+:(device|service):[a-zA-Z0-9._-]+:[1-9][0-9]*$")
        private val UUID_RE = Regex("^uuid:[a-fA-F0-9-]{8,64}$")

        fun isValid(st: String): Boolean {
            if (st.isEmpty() || st.length > MAX_ST_LEN) return false
            if (st == "ssdp:all" || st == "upnp:rootdevice") return true
            return URN_RE.matches(st) || UUID_RE.matches(st)
        }
    }
}

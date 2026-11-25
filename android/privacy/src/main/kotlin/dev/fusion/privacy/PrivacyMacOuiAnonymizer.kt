// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Bounded MAC address OUI anonymizer (zeros last 3 NIC octets).
 */
class PrivacyMacOuiAnonymizer private constructor() {
    companion object {
        private val MAC_RE = Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$")

        fun anonymize(mac: String): String {
            require(MAC_RE.matches(mac)) { "invalid MAC: $mac" }
            val parts = mac.uppercase().split(':')
            return "${parts[0]}:${parts[1]}:${parts[2]}:00:00:00"
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Bounded IPv4 CIDR anonymizer (masks host bits beyond [prefixLen]).
 */
class Ipv4CidrAnonymizer private constructor() {
    companion object {
        fun anonymize(ipv4: String, prefixLen: Int = 24): String {
            require(prefixLen in 0..32) { "prefixLen out of bounds" }
            val parts = ipv4.split('.')
            require(parts.size == 4) { "invalid IPv4: $ipv4" }
            var ip = 0L
            for (p in parts) {
                val octet = p.toInt()
                require(octet in 0..255) { "invalid octet: $p" }
                ip = (ip shl 8) or octet.toLong()
            }
            val mask = if (prefixLen == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLen)) and 0xFFFFFFFFL
            val masked = ip and mask
            return "${(masked ushr 24) and 255}.${(masked ushr 16) and 255}.${(masked ushr 8) and 255}.${masked and 255}"
        }
    }
}

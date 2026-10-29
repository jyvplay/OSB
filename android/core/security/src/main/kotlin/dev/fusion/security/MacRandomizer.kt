// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room Tails / IEEE 802.11 / RFC 8948 Locally-Administered MAC Address Randomizer.
 *
 * Prevents cross-network physical tracking on untrusted Wi-Fi / USB-Ethernet links:
 * - Derives deterministic-within-epoch yet cross-SSID unlinkable 48-bit MAC addresses via
 *   `HMAC-SHA256(bootSessionSecret, interfaceName || 0x00 || networkSsid || epochCounter)`.
 * - Enforces IEEE 802.11 locally-administered unicast bits on octet 0:
 *   - Bit 0 (`0x01`, I/G multicast bit) = `0` (strictly unicast),
 *   - Bit 1 (`0x02`, U/L local bit)     = `1` (strictly locally administered -> `x2/x6/xA/xE`).
 * - Rejects broadcast, multicast, zero, or real factory OUI leaks.
 */
object MacRandomizer {
    const val SCOPE =
        "RFC 8948 / Tails-style HMAC-SHA256 per-SSID locally-administered unicast MAC generator and validator; root netlink RTM_NEWLINK ioctl calls out of scope."

    private val MAC_RE = Regex("^[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}$")

    data class MacAddr(val octets: ByteArray) {
        init {
            require(octets.size == 6) { "MAC address must have 6 octets, got ${octets.size}" }
        }

        val isUnicast: Boolean get() = (octets[0].toInt() and 0x01) == 0
        val isLocallyAdministered: Boolean get() = (octets[0].toInt() and 0x02) != 0
        val isValidPrivateClientMac: Boolean
            get() = isUnicast && isLocallyAdministered && !octets.all { it == 0.toByte() } && !octets.all { it == 0xFF.toByte() }

        fun formatColonLower(): String = octets.joinToString(":") { "%02x".format(it.toInt() and 0xFF) }

        override fun equals(other: Any?): Boolean = other is MacAddr && octets.contentEquals(other.octets)
        override fun hashCode(): Int = octets.contentHashCode()
        override fun toString(): String = formatColonLower()
    }

    fun derivePerNetworkMac(
        sessionSecret: ByteArray,
        interfaceName: String,
        networkSsid: String,
        rotationEpoch: Long = 0L,
    ): MacAddr {
        require(sessionSecret.size >= 16) { "sessionSecret must be >= 16 bytes (got ${sessionSecret.size})" }
        require(interfaceName.isNotBlank()) { "interfaceName must not be blank" }
        require(networkSsid.isNotBlank()) { "networkSsid must not be blank" }
        require(rotationEpoch >= 0L) { "rotationEpoch must be >= 0" }

        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(sessionSecret, "HmacSHA256"))
        hmac.update(interfaceName.toByteArray(StandardCharsets.UTF_8))
        hmac.update(0.toByte())
        hmac.update(networkSsid.toByteArray(StandardCharsets.UTF_8))
        hmac.update(0.toByte())
        hmac.update(ByteBuffer.allocate(8).putLong(rotationEpoch).array())
        val digest = hmac.doFinal()

        val mac = digest.copyOfRange(0, 6)
        // Clear bit 0 (unicast) and set bit 1 (locally administered)
        mac[0] = ((mac[0].toInt() and 0xFE) or 0x02).toByte()
        // Avoid accidental all-zero payload in trailing 5 octets
        if ((1..5).all { mac[it] == 0.toByte() }) mac[5] = 0x01
        return MacAddr(mac)
    }

    fun parse(text: String): MacAddr {
        val t = text.trim()
        require(MAC_RE.matches(t)) { "malformed MAC address '$text'" }
        val bytes = t.split(':').map { it.toInt(16).toByte() }.toByteArray()
        return MacAddr(bytes)
    }

    /** Verifies a candidate MAC neither leaks the hardware factory MAC nor violates LAA unicast bits. */
    fun verifyNoHardwareLeak(candidate: MacAddr, hardwareMac: MacAddr): Boolean {
        if (!candidate.isValidPrivateClientMac) return false
        if (candidate == hardwareMac) return false
        // Also ensure first 3 OUI octets do not accidentally match the hardware vendor OUI
        val sameOui = candidate.octets[0] == hardwareMac.octets[0] &&
            candidate.octets[1] == hardwareMac.octets[1] &&
            candidate.octets[2] == hardwareMac.octets[2]
        return !sameOui
    }
}

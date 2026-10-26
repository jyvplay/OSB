package dev.fusion.privacy

/**
 * MAC address (EUI-48) analysis for the privacy surface (IEEE 802 /
 * RFC 7042 bit definitions).
 *
 * Bits of the FIRST octet: bit 0 (LSB) is I/G — 1 means group/multicast;
 * bit 1 is U/L — 1 means locally administered. Android MAC randomization
 * produces locally-administered unicast addresses, so
 * `localUnicast == true` marks an address that CAN be a randomized one.
 * That is a necessary signal, never proof — docked adapters and VMs also
 * use local addresses, and the kernel says so in [SCOPE].
 *
 * Declared narrowings: EUI-48 only (no EUI-64), three input shapes
 * (colon, hyphen, Cisco dotted-quad triple), no OUI vendor database —
 * redaction keeps the OUI block and masks the NIC-specific half.
 */
object MacAddr {
    const val SCOPE = "EUI-48 bit analysis + formats + redaction; local-unicast is a hint, NEVER proof of randomization."

    data class Mac(val octets: ByteArray) {
        val multicast: Boolean get() = (octets[0].toInt() and 0x01) != 0
        val locallyAdministered: Boolean get() = (octets[0].toInt() and 0x02) != 0
        val localUnicast: Boolean get() = locallyAdministered && !multicast
        val broadcast: Boolean get() = octets.all { (it.toInt() and 0xFF) == 0xFF }
    }

    fun parse(raw: String): Mac {
        val hex = when {
            raw.count { it == ':' } == 5 -> raw.split(':')
            raw.count { it == '-' } == 5 -> raw.split('-')
            raw.count { it == '.' } == 2 -> raw.split('.').also { parts ->
                require(parts.all { it.length == 4 }) { "Cisco format needs three 4-digit groups" }
            }.flatMap { listOf(it.substring(0, 2), it.substring(2, 4)) }
            else -> throw IllegalArgumentException("unrecognized MAC shape '$raw'")
        }
        require(hex.size == 6) { "need 6 octets, got ${hex.size}" }
        val octets = ByteArray(6)
        for (i in hex.indices) {
            val part = hex[i]
            require(part.length == 2 && part.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                "octet '$part' is not two hex digits"
            }
            octets[i] = part.toInt(16).toByte()
        }
        return Mac(octets)
    }

    fun canonical(m: Mac): String = m.octets.joinToString(":") { "%02x".format(it.toInt() and 0xFF) }

    /** OUI is only meaningful for universally administered unicast addresses. */
    fun oui(m: Mac): String? =
        if (m.locallyAdministered || m.multicast) null
        else m.octets.take(3).joinToString(":") { "%02x".format(it.toInt() and 0xFF) }

    /** Log-safe form: keep the administration-relevant OUI half, mask the device half. */
    fun redact(m: Mac): String =
        m.octets.take(3).joinToString(":") { "%02x".format(it.toInt() and 0xFF) } + ":xx:xx:xx"
}

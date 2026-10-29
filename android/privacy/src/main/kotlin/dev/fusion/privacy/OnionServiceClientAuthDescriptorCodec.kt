// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Tor v3 Onion Service (`rend-spec-v3`) 56-character `.onion` address checksum/version (`0x03`)
 * verifier + `CLIENT_AUTH` `descriptor:x25519:<52-char-base32>` credential file encoder/parser.
 */
object OnionServiceClientAuthDescriptorCodec {
    const val SCOPE =
        "Tor rend-spec-v3 56-char .onion version/structure verifier and descriptor:x25519:<base32> client-auth codec."

    private const val B32 = "abcdefghijklmnopqrstuvwxyz234567"

    data class ClientAuthRecord(val onionHost56: String, val x25519Key32Bytes: ByteArray)

    fun encodeAuthPrivateLine(onionAddress: String, x25519Key32: ByteArray): String {
        val host = onionAddress.trim().lowercase().removeSuffix(".onion")
        require(isSyntacticallyValidV3OnionHost(host)) { "Invalid 56-char v3 onion host '$onionAddress'" }
        require(x25519Key32.size == 32) { "X25519 client-auth key must be exactly 32 bytes" }
        val b32Upper = base32EncodeNoPad(x25519Key32).uppercase()
        return "$host:descriptor:x25519:$b32Upper"
    }

    fun parseAuthPrivateLine(line: String): ClientAuthRecord {
        val parts = line.trim().split(':')
        require(parts.size == 4 && parts[1] == "descriptor" && parts[2] == "x25519") {
            "Expected <56-char-onion>:descriptor:x25519:<52-char-base32>"
        }
        val host = parts[0].lowercase().removeSuffix(".onion")
        require(isSyntacticallyValidV3OnionHost(host)) { "Invalid v3 onion host in auth record" }
        require(parts[3].length == 52) { "Base32 X25519 key must be 52 unpadded chars" }
        val key = base32DecodeNoPad(parts[3].lowercase())
        require(key.size == 32)
        return ClientAuthRecord(host, key)
    }

    fun isSyntacticallyValidV3OnionHost(hostWithoutSuffix: String): Boolean {
        if (hostWithoutSuffix.length != 56) return false
        if (hostWithoutSuffix.any { it !in B32 }) return false
        val decoded = base32DecodeNoPad(hostWithoutSuffix)
        // rend-spec-v3 §6: 32-byte ed25519 pubkey || 2-byte checksum || 1-byte version (0x03) == 35 bytes
        return decoded.size == 35 && (decoded[34].toInt() and 0xFF) == 0x03
    }

    fun buildSyntheticV3OnionHost(ed25519Pubkey32: ByteArray, twoByteChecksum: ByteArray = byteArrayOf(0x4A, 0x7F)): String {
        require(ed25519Pubkey32.size == 32 && twoByteChecksum.size == 2)
        val raw35 = ByteArray(35)
        System.arraycopy(ed25519Pubkey32, 0, raw35, 0, 32)
        raw35[32] = twoByteChecksum[0]
        raw35[33] = twoByteChecksum[1]
        raw35[34] = 0x03
        return base32EncodeNoPad(raw35)
    }

    private fun base32EncodeNoPad(data: ByteArray): String {
        val sb = StringBuilder()
        var buf = 0
        var bits = 0
        for (b in data) {
            buf = (buf shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(B32[(buf ushr bits) and 0x1F])
            }
        }
        if (bits > 0) sb.append(B32[(buf shl (5 - bits)) and 0x1F])
        return sb.toString()
    }

    private fun base32DecodeNoPad(s: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var buf = 0
        var bits = 0
        for (ch in s) {
            val v = B32.indexOf(ch)
            require(v >= 0) { "Invalid RFC 4648 base32 char '$ch'" }
            buf = (buf shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buf ushr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}

package dev.fusion.vault

/**
 * Bech32 / Bech32m checksum verification (BIP-173 and BIP-350 reference
 * algorithms, reimplemented from the specification text).
 *
 * polymod over GF(2^5) with the five published generator constants;
 * bech32 verifies to constant 1, bech32m to 0x2BC830A3. The data
 * charset is "qpzry9x8gf2tvdw0s3jn54khce6mua7l". A string is all-lower
 * or all-upper, never mixed; HRP 1..83 chars of ASCII 33..126; the
 * separator is the LAST '1'; at least 6 data characters (the checksum).
 *
 * Vault use: recognizing and validating address-shaped strings before
 * redaction/storage — never wallet functionality, never key handling.
 *
 * Declared narrowings: checksum/charset layer only — segwit address
 * semantics (witness versions, program lengths, BIP-350's
 * version-dependent variant choice) are a separate, future kernel.
 */
object Bech32 {
    const val SCOPE = "Checksum/charset verification only; no addresses, no keys, no wallet semantics."
    const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    const val BECH32_CONST = 1L
    const val BECH32M_CONST = 0x2BC830A3L
    private val GEN = longArrayOf(0x3B6A57B2L, 0x26508E6DL, 0x1EA119FAL, 0x3D4233DDL, 0x2A1462B3L)

    enum class Variant { BECH32, BECH32M }

    internal fun polymod(values: IntArray): Long {
        var chk = 1L
        for (v in values) {
            val top = chk shr 25
            chk = ((chk and 0x1FFFFFFL) shl 5) xor v.toLong()
            for (i in 0..4) if ((top shr i) and 1L == 1L) chk = chk xor GEN[i]
        }
        return chk
    }

    internal fun hrpExpand(hrp: String): IntArray {
        val out = IntArray(hrp.length * 2 + 1)
        for (i in hrp.indices) out[i] = hrp[i].code shr 5
        out[hrp.length] = 0
        for (i in hrp.indices) out[hrp.length + 1 + i] = hrp[i].code and 31
        return out
    }

    data class Decoded(val hrp: String, val data: IntArray, val variant: Variant)

    fun decode(input: String): Decoded {
        require(input.length in 8..90) { "length ${input.length} outside 8..90" }
        require(input == input.lowercase() || input == input.uppercase()) { "mixed case refused by spec" }
        val s = input.lowercase()
        val sep = s.lastIndexOf('1')
        require(sep >= 1) { "missing or empty human-readable part" }
        require(s.length - sep - 1 >= 6) { "fewer than 6 checksum characters" }
        val hrp = s.substring(0, sep)
        hrp.forEach { require(it.code in 33..126) { "HRP character out of range" } }
        val data = IntArray(s.length - sep - 1)
        for (i in data.indices) {
            val idx = CHARSET.indexOf(s[sep + 1 + i])
            require(idx >= 0) { "'${s[sep + 1 + i]}' is not a bech32 data character" }
            data[i] = idx
        }
        val residue = polymod(hrpExpand(hrp) + data)
        val variant = when (residue) {
            BECH32_CONST -> Variant.BECH32
            BECH32M_CONST -> Variant.BECH32M
            else -> throw IllegalArgumentException("checksum fails both bech32 and bech32m")
        }
        return Decoded(hrp, data.copyOfRange(0, data.size - 6), variant)
    }

    fun encode(hrp: String, data: IntArray, variant: Variant): String {
        require(hrp.isNotEmpty() && hrp == hrp.lowercase()) { "HRP must be non-empty lowercase" }
        data.forEach { require(it in 0..31) { "data value $it outside 5 bits" } }
        val const = if (variant == Variant.BECH32) BECH32_CONST else BECH32M_CONST
        val values = hrpExpand(hrp) + data + IntArray(6)
        val mod = polymod(values) xor const
        val checksum = IntArray(6) { ((mod shr (5 * (5 - it))) and 31L).toInt() }
        val out = hrp + "1" + (data + checksum).map { CHARSET[it] }.joinToString("")
        require(out.length <= 90) { "encoded string exceeds 90 characters" }
        return out
    }

    fun isValid(input: String): Boolean = try { decode(input); true } catch (_: IllegalArgumentException) { false }
}

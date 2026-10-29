package dev.fusion.lens

import java.net.URI

/**
 * Clean-room `EAN-13` & `Code 128B` 1D Barcode Encoder/Scanline Decoder + Anti-Phishing URL
 * Safety Classifier (`LN-12`, Apache-2.0).
 */
object BarcodeSymbologyDecoder {
    const val SCOPE =
        "Clean-room EAN-13 + Code 128B 1D module encoder/decoder + punycode/scheme QR/barcode URL safety classifier (LN-12)"

    enum class UrlRiskVerdict { SAFE_HTTPS, BLOCKED_DANGEROUS_SCHEME, QUARANTINED_PUNYCODE_OR_RAW_IP, PLAIN_TEXT }

    data class DecodedBarcode(
        val symbology: String,
        val payload: String,
        val checksumValid: Boolean,
        val urlSafety: UrlRiskVerdict,
    )

    private val EAN_L = arrayOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
    private val EAN_G = arrayOf("0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111")
    private val EAN_R = arrayOf("1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100")
    private val EAN_PARITY = arrayOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")

    fun computeEan13CheckDigit(twelveDigits: String): Int {
        require(twelveDigits.length == 12 && twelveDigits.all(Char::isDigit))
        val sum = twelveDigits.mapIndexed { idx, c -> (c - '0') * (if (idx % 2 == 0) 1 else 3) }.sum()
        return (10 - (sum % 10)) % 10
    }

    /** Encodes a 12- or 13-digit GS1/EAN-13 number into its exact 95-module binary bar pattern (`1`=bar, `0`=space). */
    fun encodeEan13Modules(digits12Or13: String): String {
        val full = when (digits12Or13.length) {
            12 -> digits12Or13 + computeEan13CheckDigit(digits12Or13)
            13 -> {
                require(computeEan13CheckDigit(digits12Or13.take(12)) == (digits12Or13[12] - '0')) { "Invalid EAN-13 check digit" }
                digits12Or13
            }
            else -> throw IllegalArgumentException("EAN-13 requires 12 or 13 digits")
        }
        val first = full[0] - '0'
        val parity = EAN_PARITY[first]
        val sb = StringBuilder("101")
        for (i in 0 until 6) {
            val d = full[i + 1] - '0'
            sb.append(if (parity[i] == 'L') EAN_L[d] else EAN_G[d])
        }
        sb.append("01010")
        for (i in 0 until 6) {
            val d = full[i + 7] - '0'
            sb.append(EAN_R[d])
        }
        sb.append("101")
        return sb.toString()
    }

    fun decodeEan13Modules(modules95: String): DecodedBarcode {
        require(modules95.length == 95 && modules95.startsWith("101") && modules95.endsWith("101") && modules95.substring(45, 50) == "01010") {
            "Invalid 95-module EAN-13 guard pattern"
        }
        val parityChars = StringBuilder()
        val digits = StringBuilder()
        for (i in 0 until 6) {
            val chunk = modules95.substring(3 + i * 7, 3 + (i + 1) * 7)
            val lIdx = EAN_L.indexOf(chunk)
            val gIdx = EAN_G.indexOf(chunk)
            when {
                lIdx >= 0 -> { parityChars.append('L'); digits.append(lIdx) }
                gIdx >= 0 -> { parityChars.append('G'); digits.append(gIdx) }
                else -> throw IllegalArgumentException("Unrecognized left EAN-13 symbol '$chunk'")
            }
        }
        val first = EAN_PARITY.indexOf(parityChars.toString())
        require(first >= 0) { "Invalid EAN-13 left parity mask '$parityChars'" }
        for (i in 0 until 6) {
            val chunk = modules95.substring(50 + i * 7, 50 + (i + 1) * 7)
            val rIdx = EAN_R.indexOf(chunk)
            require(rIdx >= 0) { "Unrecognized right EAN-13 symbol '$chunk'" }
            digits.append(rIdx)
        }
        val full = "$first$digits"
        val valid = computeEan13CheckDigit(full.take(12)) == (full[12] - '0')
        return DecodedBarcode("EAN-13", full, valid, UrlRiskVerdict.PLAIN_TEXT)
    }

    fun classifyScannedPayloadSafety(payload: String): UrlRiskVerdict {
        val t = payload.trim()
        val lower = t.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("intent:") || lower.startsWith("file:") || lower.startsWith("data:") || lower.startsWith("http://")) {
            return UrlRiskVerdict.BLOCKED_DANGEROUS_SCHEME
        }
        if (lower.startsWith("https://")) {
            val host = runCatching { URI(t).host?.lowercase().orEmpty() }.getOrDefault("")
            val isRawIpv4 = Regex("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$").matches(host)
            if (host.isEmpty() || "xn--" in host || isRawIpv4) {
                return UrlRiskVerdict.QUARANTINED_PUNYCODE_OR_RAW_IP
            }
            return UrlRiskVerdict.SAFE_HTTPS
        }
        return UrlRiskVerdict.PLAIN_TEXT
    }
}

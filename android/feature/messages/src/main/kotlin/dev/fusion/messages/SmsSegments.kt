// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

/** SMS text encoding: septet-packed GSM-7 or UCS-2 fallback. */
enum class SmsEncoding { GSM7, UCS2 }

/**
 * SMS segmentation math (GSM 03.38 / 03.40): classifies text as GSM-7
 * (basic alphabet plus extension-table escapes) or UCS-2, counts billable
 * units, and returns the multipart segment count (160/153 septets for
 * GSM-7, 70/67 UTF-16 units for UCS-2). Counts only — text is never
 * stored, logged, or transmitted here. Lone surrogates count as one unit
 * each (documented, not repaired): malformed input still gets a
 * deterministic answer.
 */
object SmsSegments {
    const val MAX_CHARS = 1600

    // GSM 03.38 default alphabet (basic set), as literal characters.
    private const val BASIC =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    // Characters sent as ESC + code (2 septets each).
    private const val EXT = "^{}\\[~]|€"

    fun classify(text: String): SmsEncoding {
        require(text.length <= MAX_CHARS)
        for (c in text) {
            if (c in BASIC || c in EXT) continue
            return SmsEncoding.UCS2
        }
        return SmsEncoding.GSM7
    }

    fun units(text: String): Int {
        require(text.length <= MAX_CHARS)
        return if (classify(text) == SmsEncoding.GSM7) {
            var n = 0
            for (c in text) n += if (c in EXT) 2 else 1
            n
        } else {
            text.length // UTF-16 units: BMP 1, surrogate pairs 2, lone surrogates 1
        }
    }

    fun segments(text: String): Int {
        require(text.length <= MAX_CHARS)
        if (text.isEmpty()) return 0
        val n = units(text)
        return if (classify(text) == SmsEncoding.GSM7) {
            if (n <= 160) 1 else (n + 152) / 153
        } else {
            if (n <= 70) 1 else (n + 66) / 67
        }
    }
}

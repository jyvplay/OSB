package dev.fusion.model

/**
 * Hex color literal parsing/formatting in the ANDROID channel order:
 * `#RGB`, `#ARGB`, `#RRGGBB`, `#AARRGGBB` (alpha FIRST, as
 * android.graphics.Color documents — NOT the CSS `#RRGGBBAA` order,
 * which is refused by absence of an 8-digit CSS mode rather than
 * silently misread).
 *
 * Shorthand expands by digit doubling (`#F80` -> `#FF8800`). Formatting
 * emits uppercase long forms and omits the alpha byte when fully
 * opaque unless forced.
 *
 * Declared narrowings: hex literals only — no named colors, no
 * hsl()/rgb() functional forms, no color-space math (contrast and HSL
 * live in the a11y kernels).
 */
object ColorHex {
    const val SCOPE = "Android-order #(A)RGB hex literals only; CSS #RRGGBBAA order deliberately not parsed."

    data class Argb(val alpha: Int, val red: Int, val green: Int, val blue: Int) {
        init {
            require(alpha in 0..255 && red in 0..255 && green in 0..255 && blue in 0..255) { "channel outside 0..255" }
        }
        val opaque: Boolean get() = alpha == 255
    }

    private fun hex(c: Char): Int {
        require(c.isDigit() || c.lowercaseChar() in 'a'..'f') { "'$c' is not a hex digit" }
        return Character.digit(c, 16)
    }

    private fun pair(s: String, i: Int) = hex(s[i]) * 16 + hex(s[i + 1])

    fun parse(text: String): Argb {
        require(text.startsWith("#")) { "hex colors start with '#'" }
        val h = text.substring(1)
        return when (h.length) {
            3 -> Argb(255, hex(h[0]) * 17, hex(h[1]) * 17, hex(h[2]) * 17)
            4 -> Argb(hex(h[0]) * 17, hex(h[1]) * 17, hex(h[2]) * 17, hex(h[3]) * 17)
            6 -> Argb(255, pair(h, 0), pair(h, 2), pair(h, 4))
            8 -> Argb(pair(h, 0), pair(h, 2), pair(h, 4), pair(h, 6))
            else -> throw IllegalArgumentException("hex color needs 3, 4, 6 or 8 digits, got ${h.length}")
        }
    }

    fun format(c: Argb, forceAlpha: Boolean = false): String =
        if (c.opaque && !forceAlpha) "#%02X%02X%02X".format(c.red, c.green, c.blue)
        else "#%02X%02X%02X%02X".format(c.alpha, c.red, c.green, c.blue)

    fun toPackedInt(c: Argb): Int =
        (c.alpha shl 24) or (c.red shl 16) or (c.green shl 8) or c.blue

    fun fromPackedInt(v: Int): Argb =
        Argb((v ushr 24) and 0xFF, (v ushr 16) and 0xFF, (v ushr 8) and 0xFF, v and 0xFF)
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/** SGR color: 0..255 palette index or truecolor triple. */
sealed class SgrColor {
    data class Ansi(val n: Int) : SgrColor() {
        init { require(n in 0..255) { "palette $n outside 0..255" } }
    }
    data class Rgb(val r: Int, val g: Int, val b: Int) : SgrColor() {
        init { require(r in 0..255 && g in 0..255 && b in 0..255) { "rgb out of range" } }
    }
}

/** Text attributes accumulated from SGR parameters. */
data class SgrStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
    val strike: Boolean = false,
    val fg: SgrColor? = null,
    val bg: SgrColor? = null,
)

/**
 * SGR subset: reset, bold/italic/underline/inverse/strike + their resets,
 * 8/16 foreground/background colors + defaults, 256-color and truecolor
 * via `38;5;n`, `48;5;n`, `38;2;r;g;b`, `48;2;r;g;b`. Faint, blink,
 * conceal, fonts, and all other codes are refused (documented subset —
 * unknown styling must not silently pass). Params are bytes (0..255).
 * [strip] removes SGR sequences only; other CSI stays (cursor control is
 * outside this kernel). Caps: 64 params, 1 MiB stripped text.
 */
object AnsiSgr {
    const val MAX_PARAMS = 64
    const val MAX_TEXT = 1024 * 1024
    private const val ESC = '\u001B'

    fun parseSequence(text: String): List<Int>? {
        if (!text.startsWith("$ESC[") || !text.endsWith('m') || text.length < 3) return null
        val inner = text.substring(2, text.length - 1)
        if (inner.isEmpty()) return listOf(0)
        if (!inner.all { it.isDigit() || it == ';' }) return null
        if (inner.startsWith(';') || inner.endsWith(';') || inner.contains(";;")) return null
        val params = inner.split(';').map { it.toIntOrNull() ?: return null }
        if (params.size > MAX_PARAMS || params.any { it !in 0..255 }) return null
        return params
    }

    fun apply(style: SgrStyle, params: List<Int>): SgrStyle {
        require(params.size in 1..MAX_PARAMS && params.all { it in 0..255 }) { "bad params" }
        var s = style
        var i = 0
        while (i < params.size) {
            when (val c = params[i]) {
                0 -> s = SgrStyle()
                1 -> s = s.copy(bold = true)
                3 -> s = s.copy(italic = true)
                4 -> s = s.copy(underline = true)
                7 -> s = s.copy(inverse = true)
                9 -> s = s.copy(strike = true)
                22 -> s = s.copy(bold = false)
                23 -> s = s.copy(italic = false)
                24 -> s = s.copy(underline = false)
                27 -> s = s.copy(inverse = false)
                28 -> s = s.copy(strike = false)
                in 30..37 -> s = s.copy(fg = SgrColor.Ansi(c - 30))
                39 -> s = s.copy(fg = null)
                in 40..47 -> s = s.copy(bg = SgrColor.Ansi(c - 40))
                49 -> s = s.copy(bg = null)
                in 90..97 -> s = s.copy(fg = SgrColor.Ansi(c - 90 + 8))
                in 100..107 -> s = s.copy(bg = SgrColor.Ansi(c - 100 + 8))
                38, 48 -> {
                    require(i + 2 < params.size) { "truncated extended color" }
                    val fg = c == 38
                    when (params[i + 1]) {
                        5 -> {
                            val col = SgrColor.Ansi(params[i + 2])
                            s = if (fg) s.copy(fg = col) else s.copy(bg = col)
                            i += 3
                            continue
                        }
                        2 -> {
                            require(i + 4 < params.size) { "truncated truecolor" }
                            val col = SgrColor.Rgb(params[i + 2], params[i + 3], params[i + 4])
                            s = if (fg) s.copy(fg = col) else s.copy(bg = col)
                            i += 5
                            continue
                        }
                        else -> throw IllegalArgumentException("bad extended mode ${params[i + 1]}")
                    }
                }
                else -> throw IllegalArgumentException("refused SGR code $c")
            }
            i++
        }
        return s
    }

    /** Removes SGR (`ESC [ ... m`) runs; other CSI sequences are left intact. */
    fun strip(text: String): String {
        require(text.length <= MAX_TEXT) { "text over $MAX_TEXT chars" }
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == ESC && i + 1 < text.length && text[i + 1] == '[') {
                var j = i + 2
                while (j < text.length && (text[j].isDigit() || text[j] == ';')) j++
                if (j < text.length && text[j] == 'm') {
                    i = j + 1
                    continue
                }
            }
            out.append(text[i])
            i++
        }
        return out.toString()
    }
}

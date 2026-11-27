// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/**
 * Bounded ANSI escape sequence parser. Parses CSI (Control Sequence
 * Introducer) sequences: ESC [ <params> <letter>, and SGR (Select Graphic
 * Rendition) sub-parameters. Recognizes SGR 0 (reset), 1 (bold), 2 (dim),
 * 3 (italic), 4 (underline), 7 (reverse), 22-29 (various off), 30-37 and
 * 90-97 (foreground colors), 40-47 and 100-107 (background colors).
 * Other escape sequences (OSC, DCS, etc.) are refused, not silently
 * ignored.
 *
 * Declared narrowings: input at most [MAX_INPUT] chars; at most
 * [MAX_PARAMS] parameters per CSI sequence; each parameter at most
 * [MAX_PARAM_LEN] digits; at most [MAX_SEQUENCES] sequences; only CSI and
 * SGR are parsed — other escape types are refused.
 */
class AnsiParser private constructor() {
    companion object {
        const val MAX_INPUT = 1 shl 14  // 16 KiB
        const val MAX_PARAMS = 16
        const val MAX_PARAM_LEN = 8
        const val MAX_SEQUENCES = 256
        private val ESC = '\u001B'
        private val CSI = "["
        private val SGR_RE = Regex("\\d+")

        data class Sgr(
            val reset: Boolean = false,
            val bold: Boolean = false,
            val dim: Boolean = false,
            val italic: Boolean = false,
            val underline: Boolean = false,
            val reverse: Boolean = false,
            val fgColor: Int? = null,
            val bgColor: Int? = null
        )

        data class Csi(
            val params: List<Int>,
            val letter: Char
        )

        data class Parsed(
            val plainText: String,
            val sequences: List<Csi>,
            val sgrList: List<Sgr>
        )

        fun empty(): AnsiParser = AnsiParser()
    }

    fun parse(input: String): Parsed {
        require(input.length <= MAX_INPUT) { "input exceeds $MAX_INPUT chars" }
        val plain = StringBuilder()
        val sequences = mutableListOf<Csi>()
        val sgrList = mutableListOf<Sgr>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == ESC && i + 1 < input.length && input[i + 1] == '[') {
                // CSI sequence
                var j = i + 2
                val paramSb = StringBuilder()
                while (j < input.length &&
                       (input[j] in '0'..'9' || input[j] == ';')) {
                    paramSb.append(input[j])
                    j++
                }
                if (j >= input.length) break  // incomplete
                val letter = input[j]
                val params = paramSb.toString().split(";").filter { it.isNotEmpty() }
                    .map { it.toIntOrNull() }.filterNotNull()
                require(params.size <= MAX_PARAMS) { "too many CSI params" }
                sequences.add(Csi(params, letter))
                if (letter == 'm') {
                    sgrList.add(parseSgr(params))
                }
                i = j + 1
            } else if (c == ESC) {
                // Non-CSI escape — refused
                throw IllegalArgumentException("non-CSI escape sequence at position $i")
            } else {
                plain.append(c)
                i++
            }
        }
        return Parsed(plain.toString(), sequences.toList(), sgrList.toList())
    }

    fun plainText(input: String): String = parse(input).plainText

    private fun parseSgr(params: List<Int>): Sgr {
        var reset = false
        var bold = false; var dim = false; var italic = false
        var underline = false; var reverse = false
        var fgColor: Int? = null
        var bgColor: Int? = null
        for (p in params) {
            when (p) {
                0 -> { reset = true; bold = false; dim = false; italic = false;
                    underline = false; reverse = false; fgColor = null; bgColor = null }
                1 -> bold = true
                2 -> dim = true
                3 -> italic = true
                4 -> underline = true
                7 -> reverse = true
                22 -> { bold = false; dim = false }
                23 -> italic = false
                24 -> underline = false
                27 -> reverse = false
                in 30..37 -> fgColor = p - 30
                in 90..97 -> fgColor = p - 90 + 8
                in 40..47 -> bgColor = p - 40
                in 100..107 -> bgColor = p - 100 + 8
            }
        }
        return Sgr(reset, bold, dim, italic, underline, reverse, fgColor, bgColor)
    }
}

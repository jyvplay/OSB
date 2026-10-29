// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * ECMA-376 Part 1 §22.1 Office Math Markup Language (**OMML** `<m:oMathPara><m:oMath>`) linear
 * TeX-subset compiler supporting fractions (`\frac{a}{b}`), superscripts (`x^{2}`), subscripts
 * (`a_{i}`), radicals (`\sqrt{x}`), and Greek symbols for Fusion Writer scientific authoring.
 */
object DocxEquationOmmlBuilder {
    const val SCOPE =
        "ECMA-376 §22.1 Office MathML (<m:oMathPara>/<m:f>/<m:sSup>/<m:sSub>/<m:rad>) equation compiler from linear TeX notation."

    private val GREEK = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\pi" to "π", "\\sigma" to "σ", "\\theta" to "θ", "\\omega" to "ω",
        "\\Delta" to "Δ", "\\Sigma" to "∑", "\\infty" to "∞", "\\pm" to "±",
    )

    fun compileDisplayEquation(texExpr: String): String {
        val trimmed = texExpr.trim()
        require(trimmed.isNotEmpty()) { "Equation expression must not be blank" }
        val body = renderSegment(trimmed)
        return "<m:oMathPara xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
            "<m:oMath>$body</m:oMath></m:oMathPara>"
    }

    private fun renderSegment(src: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < src.length) {
            when {
                src[i].isWhitespace() -> i++
                src.startsWith("\\frac{", i) -> {
                    val (num, afterNum) = extractBraced(src, i + 5)
                    require(afterNum < src.length && src[afterNum] == '{') { "Missing denominator brace in \\frac" }
                    val (den, afterDen) = extractBraced(src, afterNum)
                    sb.append("<m:f><m:num>").append(renderSegment(num)).append("</m:num><m:den>")
                        .append(renderSegment(den)).append("</m:den></m:f>")
                    i = afterDen
                }
                src.startsWith("\\sqrt{", i) -> {
                    val (rad, afterRad) = extractBraced(src, i + 5)
                    sb.append("<m:rad><m:radPr><m:degHide m:val=\"1\"/></m:radPr><m:deg/><m:e>")
                        .append(renderSegment(rad)).append("</m:e></m:rad>")
                    i = afterRad
                }
                else -> {
                    val (atom, nextIdx) = readAtom(src, i)
                    i = nextIdx
                    if (i < src.length && (src[i] == '^' || src[i] == '_')) {
                        val op = src[i]
                        i++
                        val (arg, afterArg) = if (i < src.length && src[i] == '{') extractBraced(src, i)
                        else readAtom(src, i)
                        i = afterArg
                        if (op == '^') {
                            sb.append("<m:sSup><m:e>").append(runOf(atom)).append("</m:e><m:sup>")
                                .append(renderSegment(arg)).append("</m:sup></m:sSup>")
                        } else {
                            sb.append("<m:sSub><m:e>").append(runOf(atom)).append("</m:e><m:sub>")
                                .append(renderSegment(arg)).append("</m:sub></m:sSub>")
                        }
                    } else {
                        sb.append(runOf(atom))
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun readAtom(s: String, start: Int): Pair<String, Int> {
        if (s[start] == '\\') {
            var j = start + 1
            while (j < s.length && s[j].isLetter()) j++
            val cmd = s.substring(start, j)
            return (GREEK[cmd] ?: cmd.removePrefix("\\")) to j
        }
        return s[start].toString() to (start + 1)
    }

    private fun extractBraced(s: String, openBraceIdx: Int): Pair<String, Int> {
        require(openBraceIdx < s.length && s[openBraceIdx] == '{') { "Expected '{' at $openBraceIdx" }
        var depth = 0
        for (k in openBraceIdx until s.length) {
            if (s[k] == '{') depth++
            else if (s[k] == '}') {
                depth--
                if (depth == 0) return s.substring(openBraceIdx + 1, k) to (k + 1)
            }
        }
        throw IllegalArgumentException("Unclosed '{' in equation '$s'")
    }

    private fun runOf(text: String): String =
        "<m:r><m:t>${text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</m:t></m:r>"
}

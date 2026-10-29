// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

import java.util.Locale
import kotlin.math.max

/**
 * Clean-room inline TeX/Typst-subset mathematical formula AST parser & SVG/MathML renderer (`LatexMathSvg`).
 *
 * Powers offline equation rendering across Writer, Notes, and PDF preview without bundling MathJax/KaTeX JS:
 * - Constructs: `\frac{num}{den}`, `\sqrt{radicand}`, superscripts `base^{sup}`, subscripts `base_{sub}`,
 *   combined `base_{sub}^{sup}`, grouped braces `{...}`, and Greek/operator control words (`\alpha`..`\omega`,
 *   `\Delta`, `\Sigma`, `\Omega`, `\sum`, `\prod`, `\int`, `\pm`, `\mp`, `\times`, `\cdot`, `\leq`, `\geq`,
 *   `\neq`, `\approx`, `\infty`, `\rightarrow`).
 * - Security invariants: bounded recursion depth (`MAX_DEPTH = 24`), bounded input length (`MAX_EXPR_CHARS = 2048`),
 *   refusal of `\input` / `\include` / `\write18` TeX file/shell primitives, and strict XML escaping.
 */
object LatexMathSvg {
    const val SCOPE =
        "Pure-JVM TeX math subset AST parser + deterministic SVG & MathML Core renderer (frac, sqrt, sup/sub, Greek/operators, depth cap 24); full LaTeX macro expansion and align environments out of scope."

    const val MAX_DEPTH = 24
    const val MAX_EXPR_CHARS = 2048

    private val FORBIDDEN_MACROS = setOf("input", "include", "write18", "openin", "openout", "catcode", "def", "csname")

    private val SYMBOLS = mapOf(
        "alpha" to "\u03B1", "beta" to "\u03B2", "gamma" to "\u03B3", "delta" to "\u03B4",
        "epsilon" to "\u03B5", "theta" to "\u03B8", "lambda" to "\u03BB", "mu" to "\u03BC",
        "pi" to "\u03C0", "rho" to "\u03C1", "sigma" to "\u03C3", "tau" to "\u03C4",
        "phi" to "\u03C6", "omega" to "\u03C9", "Delta" to "\u0394", "Sigma" to "\u03A3",
        "Pi" to "\u03A0", "Omega" to "\u03A9", "sum" to "\u2211", "prod" to "\u220F",
        "int" to "\u222B", "pm" to "\u00B1", "mp" to "\u2213", "times" to "\u00D7",
        "cdot" to "\u00B7", "leq" to "\u2264", "geq" to "\u2265", "neq" to "\u2260",
        "approx" to "\u2248", "infty" to "\u221E", "rightarrow" to "\u2192", "to" to "\u2192",
    )

    sealed interface MathNode {
        data class Row(val items: List<MathNode>) : MathNode
        data class Sym(val text: String, val italic: Boolean = false) : MathNode
        data class Frac(val num: MathNode, val den: MathNode) : MathNode
        data class Sqrt(val body: MathNode) : MathNode
        data class SubSup(val base: MathNode, val sub: MathNode?, val sup: MathNode?) : MathNode
    }

    data class BoxLayout(
        val width: Double,
        val ascent: Double,
        val descent: Double,
        val svgBody: String,
    ) {
        val height: Double get() = ascent + descent
    }

    fun parse(expr: String): MathNode {
        val trimmed = expr.trim().removeSurrounding("$")
        require(trimmed.length <= MAX_EXPR_CHARS) { "Math expression exceeds MAX_EXPR_CHARS ($MAX_EXPR_CHARS)" }
        val p = Parser(trimmed)
        val root = p.parseRow(depth = 0, stopAtBrace = false)
        require(p.atEnd()) { "Unexpected trailing token at index ${p.pos} in math expression" }
        return root
    }

    fun renderSvg(expr: String, baseFontPt: Double = 16.0): String {
        require(baseFontPt in 8.0..72.0) { "baseFontPt out of range: $baseFontPt" }
        val ast = parse(expr)
        val box = layout(ast, baseFontPt)
        val pad = 6.0
        val w = max(12.0, box.width + pad * 2)
        val h = max(16.0, box.height + pad * 2)
        val baselineY = pad + box.ascent
        return buildString {
            append("""<svg xmlns="http://www.w3.org/2000/svg" width="${fmt(w)}" height="${fmt(h)}" viewBox="0 0 ${fmt(w)} ${fmt(h)}" role="math" aria-label="${esc(expr.trim())}">""")
            append("""<g transform="translate(${fmt(pad)},${fmt(baselineY)})" fill="#111827" stroke="#111827" font-family="Cambria Math, STIX Two Math, serif">""")
            append(box.svgBody)
            append("</g></svg>")
        }
    }

    fun renderMathMl(expr: String): String {
        val ast = parse(expr)
        return """<math xmlns="http://www.w3.org/1998/Math/MathML">${toMathMl(ast)}</math>"""
    }

    private fun toMathMl(node: MathNode): String = when (node) {
        is MathNode.Row -> "<mrow>${node.items.joinToString("") { toMathMl(it) }}</mrow>"
        is MathNode.Sym -> {
            val tag = when {
                node.text.all { it.isDigit() || it == '.' } -> "mn"
                node.italic -> "mi"
                else -> "mo"
            }
            "<$tag>${esc(node.text)}</$tag>"
        }
        is MathNode.Frac -> "<mfrac>${toMathMl(node.num)}${toMathMl(node.den)}</mfrac>"
        is MathNode.Sqrt -> "<msqrt>${toMathMl(node.body)}</msqrt>"
        is MathNode.SubSup -> when {
            node.sub != null && node.sup != null -> "<msubsup>${toMathMl(node.base)}${toMathMl(node.sub)}${toMathMl(node.sup)}</msubsup>"
            node.sub != null -> "<msub>${toMathMl(node.base)}${toMathMl(node.sub)}</msub>"
            node.sup != null -> "<msup>${toMathMl(node.base)}${toMathMl(node.sup)}</msup>"
            else -> toMathMl(node.base)
        }
    }

    private fun layout(node: MathNode, sz: Double): BoxLayout = when (node) {
        is MathNode.Sym -> {
            val w = max(sz * 0.45, node.text.length * sz * 0.56)
            val style = if (node.italic) """ font-style="italic"""" else ""
            val svg = """<text x="0" y="0" font-size="${fmt(sz)}" stroke="none"$style>${esc(node.text)}</text>"""
            BoxLayout(w, sz * 0.78, sz * 0.24, svg)
        }
        is MathNode.Row -> {
            if (node.items.isEmpty()) BoxLayout(0.0, sz * 0.6, sz * 0.2, "")
            else {
                var x = 0.0
                var asc = 0.0
                var desc = 0.0
                val sb = StringBuilder()
                for (child in node.items) {
                    val b = layout(child, sz)
                    sb.append("""<g transform="translate(${fmt(x)},0)">${b.svgBody}</g>""")
                    x += b.width + sz * 0.08
                    asc = max(asc, b.ascent)
                    desc = max(desc, b.descent)
                }
                BoxLayout(max(0.0, x - sz * 0.08), asc, desc, sb.toString())
            }
        }
        is MathNode.Frac -> {
            val childSz = max(9.0, sz * 0.78)
            val numB = layout(node.num, childSz)
            val denB = layout(node.den, childSz)
            val w = max(numB.width, denB.width) + sz * 0.4
            val barY = -sz * 0.28
            val numY = barY - sz * 0.16 - numB.descent
            val denY = barY + sz * 0.20 + denB.ascent
            val svg = buildString {
                append("""<g transform="translate(${fmt((w - numB.width) / 2)},${fmt(numY)})">${numB.svgBody}</g>""")
                append("""<line x1="0" y1="${fmt(barY)}" x2="${fmt(w)}" y2="${fmt(barY)}" stroke-width="${fmt(max(1.0, sz * 0.06))}"/>""")
                append("""<g transform="translate(${fmt((w - denB.width) / 2)},${fmt(denY)})">${denB.svgBody}</g>""")
            }
            BoxLayout(w, -numY + numB.ascent, denY + denB.descent, svg)
        }
        is MathNode.Sqrt -> {
            val inner = layout(node.body, sz)
            val leadW = sz * 0.55
            val topY = -(inner.ascent + sz * 0.16)
            val botY = inner.descent
            val totalW = leadW + inner.width + sz * 0.15
            val svg = buildString {
                append("""<polyline fill="none" stroke-width="${fmt(max(1.0, sz * 0.065))}" points="0,${fmt(-sz * 0.25)} ${fmt(leadW * 0.42)},${fmt(botY)} ${fmt(leadW)},${fmt(topY)} ${fmt(totalW)},${fmt(topY)}"/>""")
                append("""<g transform="translate(${fmt(leadW)},0)">${inner.svgBody}</g>""")
            }
            BoxLayout(totalW, -topY + sz * 0.08, inner.descent + sz * 0.06, svg)
        }
        is MathNode.SubSup -> {
            val baseB = layout(node.base, sz)
            val subSz = max(8.0, sz * 0.68)
            val subB = node.sub?.let { layout(it, subSz) }
            val supB = node.sup?.let { layout(it, subSz) }
            val scriptW = max(subB?.width ?: 0.0, supB?.width ?: 0.0)
            val supShift = -(baseB.ascent * 0.55)
            val subShift = baseB.descent + subSz * 0.35
            val svg = buildString {
                append(baseB.svgBody)
                if (supB != null) append("""<g transform="translate(${fmt(baseB.width + sz * 0.05)},${fmt(supShift)})">${supB.svgBody}</g>""")
                if (subB != null) append("""<g transform="translate(${fmt(baseB.width + sz * 0.05)},${fmt(subShift)})">${subB.svgBody}</g>""")
            }
            val asc = max(baseB.ascent, if (supB != null) -supShift + supB.ascent else 0.0)
            val desc = max(baseB.descent, if (subB != null) subShift + subB.descent else 0.0)
            BoxLayout(baseB.width + sz * 0.05 + scriptW, asc, desc, svg)
        }
    }

    private class Parser(private val s: String) {
        var pos: Int = 0

        fun atEnd(): Boolean {
            skipWs()
            return pos >= s.length
        }

        fun parseRow(depth: Int, stopAtBrace: Boolean): MathNode {
            require(depth <= MAX_DEPTH) { "Math expression exceeds MAX_DEPTH ($MAX_DEPTH)" }
            val items = mutableListOf<MathNode>()
            while (true) {
                skipWs()
                if (pos >= s.length) {
                    require(!stopAtBrace) { "Unclosed '{' in math expression" }
                    break
                }
                if (s[pos] == '}') {
                    require(stopAtBrace) { "Unmatched '}' at index $pos" }
                    pos++
                    break
                }
                var atom = parseAtom(depth + 1)
                var sub: MathNode? = null
                var sup: MathNode? = null
                while (true) {
                    skipWs()
                    if (pos < s.length && s[pos] == '_') {
                        require(sub == null) { "Duplicate subscript '_' at index $pos" }
                        pos++
                        sub = parseScriptArg(depth + 1)
                    } else if (pos < s.length && s[pos] == '^') {
                        require(sup == null) { "Duplicate superscript '^' at index $pos" }
                        pos++
                        sup = parseScriptArg(depth + 1)
                    } else {
                        break
                    }
                }
                if (sub != null || sup != null) atom = MathNode.SubSup(atom, sub, sup)
                items.add(atom)
            }
            return if (items.size == 1) items[0] else MathNode.Row(items)
        }

        private fun parseScriptArg(depth: Int): MathNode {
            skipWs()
            require(pos < s.length && s[pos] != '}') { "Missing script argument at index $pos" }
            return parseAtom(depth + 1)
        }

        private fun parseAtom(depth: Int): MathNode {
            require(depth <= MAX_DEPTH) { "Math expression exceeds MAX_DEPTH ($MAX_DEPTH)" }
            skipWs()
            require(pos < s.length) { "Unexpected end of math expression" }
            val ch = s[pos]
            return when {
                ch == '{' -> {
                    pos++
                    parseRow(depth + 1, stopAtBrace = true)
                }
                ch == '\\' -> {
                    pos++
                    require(pos < s.length) { "Dangling backslash in math expression" }
                    if (!s[pos].isLetter()) {
                        val escChar = s[pos++].toString()
                        MathNode.Sym(escChar, italic = false)
                    } else {
                        val start = pos
                        while (pos < s.length && (s[pos].isLetter() || (pos > start && s[pos].isDigit()))) pos++
                        val cmd = s.substring(start, pos)
                        require(cmd !in FORBIDDEN_MACROS) { "Refusing unsafe TeX command: \\$cmd" }
                        when (cmd) {
                            "frac" -> {
                                val num = parseMandatoryGroup(depth + 1)
                                val den = parseMandatoryGroup(depth + 1)
                                MathNode.Frac(num, den)
                            }
                            "sqrt" -> MathNode.Sqrt(parseMandatoryGroup(depth + 1))
                            else -> {
                                val sym = SYMBOLS[cmd]
                                    ?: throw IllegalArgumentException("Unsupported math control sequence: \\$cmd")
                                MathNode.Sym(sym, italic = cmd[0].isLowerCase() && sym.length == 1 && sym[0] in '\u03B1'..'\u03C9')
                            }
                        }
                    }
                }
                ch.isDigit() -> {
                    val start = pos
                    while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
                    MathNode.Sym(s.substring(start, pos), italic = false)
                }
                ch.isLetter() -> {
                    pos++
                    MathNode.Sym(ch.toString(), italic = true)
                }
                else -> {
                    require(ch != '_' && ch != '^') { "Script operator '$ch' missing base at index $pos" }
                    pos++
                    val mapped = if (ch == '-') "\u2212" else ch.toString()
                    MathNode.Sym(mapped, italic = false)
                }
            }
        }

        private fun parseMandatoryGroup(depth: Int): MathNode {
            skipWs()
            require(pos < s.length && s[pos] == '{') { "Expected '{' at index $pos" }
            pos++
            return parseRow(depth + 1, stopAtBrace = true)
        }

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)

    private fun esc(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }
}

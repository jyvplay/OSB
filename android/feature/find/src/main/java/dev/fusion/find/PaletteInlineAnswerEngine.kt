// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Clean-room CommandPalette Inline Answer, Unit Conversion, Quick Action, and Opt-In Web-Fallback
 * Planner (`SR-04`, `SR-05`, `SR-06`).
 *
 * Evaluates right inside `Palette.kt` with zero network I/O unless the user explicitly invokes an
 * opt-in web search item ranked strictly BELOW all local matches (`SR-04`).
 */
object PaletteInlineAnswerEngine {
    enum class CardKind {
        INLINE_MATH,
        INLINE_UNIT_CONVERSION,
        SCOPED_FOLDER_FILTER,
        QUICK_COMMAND_HINT,
        OPT_IN_WEB_FALLBACK,
    }

    data class InlineCard(
        val kind: CardKind,
        val headline: String,
        val subtitle: String,
        val rankOrder: Int, // Local cards 0..50; opt-in web fallback ALWAYS 999 (below local results!)
    )

    private val UNIT_TO_BASE: Map<String, Pair<String, Double>> = mapOf(
        "km" to ("len_m" to 1000.0),
        "m" to ("len_m" to 1.0),
        "cm" to ("len_m" to 0.01),
        "mm" to ("len_m" to 0.001),
        "mi" to ("len_m" to 1609.344),
        "ft" to ("len_m" to 0.3048),
        "in" to ("len_m" to 0.0254),
        "kg" to ("mass_g" to 1000.0),
        "g" to ("mass_g" to 1.0),
        "lb" to ("mass_g" to 453.59237),
        "oz" to ("mass_g" to 28.349523125),
        "gb" to ("bytes" to 1024.0 * 1024.0 * 1024.0),
        "mb" to ("bytes" to 1024.0 * 1024.0),
        "kb" to ("bytes" to 1024.0),
    )

    fun evaluateQuery(
        rawQuery: String,
        optInWebSearchEnabled: Boolean = false,
        webTemplate: String = "https://duckduckgo.com/?q=%s",
    ): List<InlineCard> {
        val q = rawQuery.trim()
        if (q.isEmpty()) return emptyList()
        val cards = ArrayList<InlineCard>()

        // 1. Unit conversion: e.g. "12.5 km in mi" or "100 c to f"
        tryUnitConversion(q)?.let { cards.add(it) }

        // 2. Inline arithmetic: "= (125 * 4) + 18.5" or bare arithmetic "42 * 19"
        val mathCandidate = if (q.startsWith("=")) q.substring(1).trim() else q
        if (mathCandidate.any { it in "+-*/" } && mathCandidate.all { it.isDigit() || it in " +-*/().\t" }) {
            runCatching { evalSimpleBigDecimal(mathCandidate) }.getOrNull()?.let { bd ->
                cards.add(
                    InlineCard(
                        CardKind.INLINE_MATH,
                        "= ${bd.stripTrailingZeros().toPlainString()}",
                        "Exact BigDecimal inline calculation (Enter copies/applies)",
                        rankOrder = 1,
                    ),
                )
            }
        }

        // 3. In-folder scoped search prefix (`in:Projects budget`) per SR-05
        Regex("""^in:([^\s]+)\s+(.+)$""", RegexOption.IGNORE_CASE).matchEntire(q)?.let { m ->
            cards.add(
                InlineCard(
                    CardKind.SCOPED_FOLDER_FILTER,
                    "Search '${m.groupValues[2].trim()}' inside folder '${m.groupValues[1]}'",
                    "SR-05 scoped folder query",
                    rankOrder = 2,
                ),
            )
        }

        // 4. Quick commands (`note:`, `focus:`, `cast:`) per SR-06
        when {
            q.startsWith("note:", ignoreCase = true) ->
                cards.add(InlineCard(CardKind.QUICK_COMMAND_HINT, "Quick Note: ${q.substring(5).trim()}", "Stages draft in Notes", 3))
            q.startsWith("focus:", ignoreCase = true) ->
                cards.add(InlineCard(CardKind.QUICK_COMMAND_HINT, "Focus Mode: ${q.substring(6).trim()}", "Toggles workspace focus profile", 3))
            q.startsWith("cast:", ignoreCase = true) ->
                cards.add(InlineCard(CardKind.QUICK_COMMAND_HINT, "Beam Receiver Filter: ${q.substring(5).trim()}", "Opens Beam cast target picker", 3))
        }

        // 5. SR-04: Opt-in web fallback strictly below local hits (rankOrder = 999)
        if (optInWebSearchEnabled) {
            val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8)
            val url = webTemplate.replace("%s", encoded)
            cards.add(
                InlineCard(
                    CardKind.OPT_IN_WEB_FALLBACK,
                    "Search web for \"$q\"",
                    url,
                    rankOrder = 999,
                ),
            )
        }
        return cards.sortedBy { it.rankOrder }
    }

    private fun tryUnitConversion(q: String): InlineCard? {
        val m = Regex("""^([-+]?\d+(?:\.\d+)?)\s*([A-Za-z]+)\s+(?:in|to)\s+([A-Za-z]+)$""", RegexOption.IGNORE_CASE)
            .matchEntire(q) ?: return null
        val amount = m.groupValues[1].toDoubleOrNull() ?: return null
        val from = m.groupValues[2].lowercase(Locale.ROOT)
        val to = m.groupValues[3].lowercase(Locale.ROOT)
        if (from in setOf("c", "f", "k") && to in setOf("c", "f", "k")) {
            val celsius = when (from) {
                "c" -> amount
                "f" -> (amount - 32.0) * 5.0 / 9.0
                else -> amount - 273.15
            }
            val result = when (to) {
                "c" -> celsius
                "f" -> celsius * 9.0 / 5.0 + 32.0
                else -> celsius + 273.15
            }
            return InlineCard(
                CardKind.INLINE_UNIT_CONVERSION,
                String.format(Locale.US, "%.4g %s = %.4f %s", amount, from.uppercase(), result, to.uppercase()),
                "Inline temperature conversion",
                rankOrder = 0,
            )
        }
        val uFrom = UNIT_TO_BASE[from] ?: return null
        val uTo = UNIT_TO_BASE[to] ?: return null
        if (uFrom.first != uTo.first) return null
        val converted = amount * uFrom.second / uTo.second
        return InlineCard(
            CardKind.INLINE_UNIT_CONVERSION,
            String.format(Locale.US, "%s %s = %.6g %s", m.groupValues[1], from, converted, to),
            "Inline dimensional unit conversion",
            rankOrder = 0,
        )
    }

    private fun evalSimpleBigDecimal(src: String): BigDecimal = MathParser(src).parseAll()

    private class MathParser(private val src: String) {
        private var pos = 0
        private val mc = MathContext(34, RoundingMode.HALF_EVEN)
        private fun skip() { while (pos < src.length && src[pos].isWhitespace()) pos++ }
        fun parseAll(): BigDecimal {
            val res = parseExpr()
            skip()
            require(pos == src.length) { "trailing characters" }
            return res
        }
        private fun parseExpr(): BigDecimal {
            var v = parseTerm()
            while (true) {
                skip()
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) {
                    val op = src[pos++]
                    val rhs = parseTerm()
                    v = if (op == '+') v.add(rhs, mc) else v.subtract(rhs, mc)
                } else break
            }
            return v
        }
        private fun parseTerm(): BigDecimal {
            var v = parseFactor()
            while (true) {
                skip()
                if (pos < src.length && (src[pos] == '*' || src[pos] == '/')) {
                    val op = src[pos++]
                    val rhs = parseFactor()
                    v = if (op == '*') v.multiply(rhs, mc) else v.divide(rhs, mc)
                } else break
            }
            return v
        }
        private fun parseFactor(): BigDecimal {
            skip()
            if (pos < src.length && src[pos] == '+') { pos++; return parseFactor() }
            if (pos < src.length && src[pos] == '-') { pos++; return parseFactor().negate() }
            if (pos < src.length && src[pos] == '(') {
                pos++
                val inner = parseExpr()
                skip()
                require(pos < src.length && src[pos] == ')') { "unclosed (" }
                pos++
                return inner
            }
            val start = pos
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
            require(pos > start) { "expected number" }
            return BigDecimal(src.substring(start, pos))
        }
    }
}

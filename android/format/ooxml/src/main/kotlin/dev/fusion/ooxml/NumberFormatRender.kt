// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Deterministic SpreadsheetML number/date format rendering (ECMA-376 Part 1 §18.8.30/§18.8.31).
 *
 * This is the rendering pass that [NumFmtSections] deliberately does not do: it turns a
 * format code plus a value into the string a spreadsheet shows, so `TEXT()` and a future
 * styled-display path share one implementation.
 *
 * Declared narrowings (every refusal is explicit, never a silent approximation):
 * - Conditions in `[...]` sections are refused; color names are accepted and ignored.
 * - Fractions are rendered for three shapes: mixed (`# ?/?`, `0 ??/??`), improper (`?/?`) and
 *   fixed denominator (`# ?/16`, `?/8`). The numerator/denominator pair is the *nearest* fraction
 *   under the placeholder-derived denominator bound, ties going to the smaller denominator; an
 *   exact one carries into the whole part. Padding follows Excel: `?` pads with a space, `0` with
 *   zeros, `#` shows nothing. Trailing padding is not trimmed (Excel keeps it).
 * - Fill (`*c`) is dropped and `?` keeps its documented meaning below.
 * - Text sections support a single `@` placeholder plus quoted/escaped literals.
 * - Elapsed-time brackets `[h] [m] [s]` are supported for whole units only.
 * - Serial dates use the 1900 date system inside `1900-03-01..9999-12-31`, the same
 *   deterministic civil window as [FormulaEval]; earlier serials are refused rather than
 *   inheriting Excel's fictitious 1900-02-29.
 */
object NumberFormatRender {
    const val SCOPE =
        "Number/date/text rendering for one format section set: placeholders 0 # ?, grouping, " +
            "decimal scaling, percent, scientific, quoted/escaped literals, date and time tokens, " +
            "elapsed whole units, and fraction formats (mixed, improper and fixed-denominator). " +
            "Conditions and fill characters are refused."

    const val MAX_FORMAT_CHARS = 128
    const val MAX_OUTPUT_CHARS = 4_096
    private const val MAX_DECIMALS = 15
    private const val MAX_SCALE = 12
    private val EPOCH: LocalDate = LocalDate.of(1899, 12, 30)
    private val MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** Renders a finite number. Throws [IllegalArgumentException] for an unsupported format. */
    fun renderNumber(value: Double, format: String): String {
        require(format.isNotEmpty() && format.length <= MAX_FORMAT_CHARS) { "format length outside 1..$MAX_FORMAT_CHARS" }
        require(value.isFinite()) { "non-finite number" }
        val sections = NumFmtSections.split(format).map { stripAnnotations(it) }
        val chosen = chooseSection(sections.size, value)
        val section = sections[chosen].ifBlank { "General" }
        val out = when {
            section.equals("General", ignoreCase = true) -> general(value)
            isDateFormat(section) -> renderDate(value, section)
            !hasDigitPlaceholder(section) -> renderLiteralOnly(section)
            else -> renderDigits(value, section, autoSign = sections.size == 1)
        }
        require(out.length <= MAX_OUTPUT_CHARS) { "rendered text exceeds $MAX_OUTPUT_CHARS characters" }
        return out
    }

    /** Renders text; a fourth section is the text section, otherwise `@` is substituted. */
    fun renderText(text: String, format: String): String {
        require(format.isNotEmpty() && format.length <= MAX_FORMAT_CHARS) { "format length outside 1..$MAX_FORMAT_CHARS" }
        val sections = NumFmtSections.split(format).map { stripAnnotations(it) }
        if (sections.size == 4) {
            val fourth = sections[3]
            if (fourth.isBlank()) return text
            return substitute(text, fourth, isDate = false)
        }
        val single = sections[0]
        if (single.isBlank() || single.equals("General", ignoreCase = true)) return text
        if (single.equals("@", ignoreCase = false)) return text
        // A numeric section does not alter a text value: Excel returns the text unchanged.
        if ('@' !in single) return text
        require(!isDateFormat(single)) { "date format applied to text" }
        return substitute(text, single, isDate = false)
    }

    /** True when the chosen section for [value] carries date/time tokens. */
    fun usesDateTokens(format: String, value: Double = 1.0): Boolean {
        val sections = NumFmtSections.split(format).map { stripAnnotations(it) }
        val section = sections[chooseSection(sections.size, value)]
        return !section.equals("General", ignoreCase = true) && isDateFormat(section)
    }

    private fun chooseSection(count: Int, value: Double): Int = when {
        count <= 1 -> 0
        count == 2 -> if (value < 0.0) 1 else 0
        else -> when {
            value > 0.0 -> 0
            value < 0.0 -> 1
            else -> 2
        }
    }

    /** Removes `[Color]` annotations and refuses `[Condition]` annotations. */
    private fun stripAnnotations(section: String): String {
        if (!section.startsWith("[")) return section
        val out = StringBuilder()
        var i = 0
        while (i < section.length && section[i] == '[') {
            val end = section.indexOf(']', i)
            require(end > i) { "unterminated bracket in format" }
            val inner = section.substring(i + 1, end)
            require(inner.isNotEmpty()) { "empty bracket in format" }
            if (inner.all { it.lowercaseChar() in "hms" } && inner.lowercase().toSet().size == 1) {
                // An elapsed-time token such as [h]:mm stays in the section for renderDate.
                return section
            }
            require(inner.all { it.isLetter() || it.isDigit() }) { "unsupported condition [$inner] in format" }
            // Colour names carry no text and are ignored; anything else is a condition.
            require(inner.uppercase() in NumFmtSections.NAMED_COLORS || inner.startsWith("Color", ignoreCase = true)) {
                "unsupported conditional format [$inner]"
            }
            i = end + 1
        }
        out.append(section, i, section.length)
        return out.toString()
    }

    private fun general(value: Double): String {
        val clean = if (value == 0.0) 0.0 else value
        val asLong = clean.toLong()
        return if (clean == asLong.toDouble() && kotlin.math.abs(clean) < 1e15) asLong.toString() else clean.toString()
    }

    /** Scans outside quotes and escapes for date/time tokens. */
    private fun isDateFormat(section: String): Boolean {
        var i = 0
        var sawYearOrDay = false
        var sawHour = false
        var sawMinuteOrSecond = false
        while (i < section.length) {
            val c = section[i]
            when {
                c == '"' -> {
                    val end = section.indexOf('"', i + 1)
                    require(end > i) { "unterminated quoted literal" }
                    i = end + 1
                    continue
                }
                c == '\\' -> { i += 2; continue }
                c == '[' -> {
                    val end = section.indexOf(']', i)
                    require(end > i) { "unterminated bracket" }
                    i = end + 1
                    continue
                }
                c == 'y' || c == 'Y' || c == 'd' || c == 'D' -> sawYearOrDay = true
                c == 'h' || c == 'H' -> sawHour = true
                c == 's' || c == 'S' -> sawMinuteOrSecond = true
                c == 'm' || c == 'M' -> {
                    // A lone 'm' is a month unless it sits in a time pattern.
                    if (sawHour) sawMinuteOrSecond = true
                }
            }
            i++
        }
        if (sawYearOrDay) return true
        if (sawHour && sawMinuteOrSecond) return true
        return false
    }

    private fun renderDate(value: Double, section: String): String {
        val dayPart = kotlin.math.floor(value)
        val needsCivilDate = section.any { it.lowercaseChar() == 'y' || it.lowercaseChar() == 'd' }
        require(dayPart >= (if (needsCivilDate) 61.0 else 0.0) && dayPart <= 2_958_465.0) {
            "serial date outside the supported civil window"
        }
        var date = EPOCH.plusDays(dayPart.toLong())
        var seconds = kotlin.math.round((value - dayPart) * 86_400.0).toLong()
        if (seconds >= 86_400L) { date = date.plusDays(1); seconds -= 86_400L }
        val totalSeconds = dayPart.toLong().let { it * 86_400L } + seconds
        val hour24 = (seconds / 3_600L).toInt()
        val minute = ((seconds % 3_600L) / 60L).toInt()
        val second = (seconds % 60L).toInt()
        val twelveHour = section.contains("AM/PM", ignoreCase = true) || section.contains("A/P", ignoreCase = true)
        val out = StringBuilder()
        var i = 0
        var previousTimeToken: Char? = null
        fun nextSignificant(from: Int): Char? {
            var j = from
            while (j < section.length) {
                val ch = section[j]
                if (ch == '"') {
                    val end = section.indexOf('"', j + 1)
                    if (end < 0) return null
                    j = end + 1
                    continue
                }
                if (ch == '\\') { j += 2; continue }
                if (ch == '[') { val end = section.indexOf(']', j); if (end < 0) return null; j = end + 1; continue }
                if (ch.isLetter()) return ch.lowercaseChar()
                j++
            }
            return null
        }
        while (i < section.length) {
            val c = section[i]
            val lower = c.lowercaseChar()
            when {
                c == '"' -> {
                    val end = section.indexOf('"', i + 1)
                    require(end > i) { "unterminated quoted literal" }
                    out.append(section, i + 1, end)
                    i = end + 1
                }
                c == '\\' -> {
                    require(i + 1 < section.length) { "dangling escape" }
                    out.append(section[i + 1]); i += 2
                }
                c == '[' -> {
                    val end = section.indexOf(']', i)
                    require(end > i) { "unterminated bracket" }
                    val inner = section.substring(i + 1, end).lowercase()
                    val repeat = inner.length
                    val token = inner.firstOrNull() ?: throw IllegalArgumentException("empty elapsed bracket")
                    require(inner.all { it == token } && token in "hms") { "unsupported elapsed token [$inner]" }
                    val whole = when (token) {
                        'h' -> totalSeconds / 3_600L
                        'm' -> totalSeconds / 60L
                        else -> totalSeconds
                    }
                    out.append(pad(whole, repeat, '0'))
                    previousTimeToken = token
                    i = end + 1
                }
                lower == 'y' -> {
                    val run = countRun(section, i, 'y')
                    out.append(if (run >= 3) date.year.toString() else (date.year % 100).toString().padStart(2, '0'))
                    i += run
                }
                lower == 'd' -> {
                    val run = countRun(section, i, 'd')
                    out.append(
                        when (run) {
                            1 -> date.dayOfMonth.toString()
                            2 -> date.dayOfMonth.toString().padStart(2, '0')
                            3 -> WEEKDAYS[date.dayOfWeek.value - 1].take(3)
                            else -> WEEKDAYS[date.dayOfWeek.value - 1]
                        },
                    )
                    i += run
                }
                lower == 'm' -> {
                    val run = countRun(section, i, 'm')
                    val asMinute = previousTimeToken == 'h' || nextSignificant(i + run) == 's'
                    if (asMinute) {
                        out.append(renderNumberToken(minute, run))
                        previousTimeToken = 'm'
                    } else {
                        out.append(
                            when (run) {
                                1 -> date.monthValue.toString()
                                2 -> date.monthValue.toString().padStart(2, '0')
                                3 -> MONTHS[date.monthValue - 1].take(3)
                                else -> MONTHS[date.monthValue - 1]
                            },
                        )
                    }
                    i += run
                }
                lower == 'h' -> {
                    val run = countRun(section, i, 'h')
                    val shown = if (twelveHour) ((hour24 + 11) % 12) + 1 else hour24
                    out.append(renderNumberToken(shown, run))
                    previousTimeToken = 'h'
                    i += run
                }
                lower == 's' -> {
                    val run = countRun(section, i, 's')
                    out.append(renderNumberToken(second, run))
                    previousTimeToken = 's'
                    i += run
                    if (i < section.length && section[i] == '.') {
                        var zeros = 0
                        while (i + 1 + zeros < section.length && section[i + 1 + zeros] == '0') zeros++
                        if (zeros > 0) {
                            val fraction = kotlin.math.round((value - dayPart) * 86_400.0 * 1_000.0).toLong() % 1_000L
                            out.append('.').append(fraction.toString().padStart(3, '0').take(zeros).padEnd(zeros, '0'))
                            i += 1 + zeros
                        }
                    }
                }
                (c == 'A' || c == 'a') && section.regionMatches(i, "AM/PM", 0, 5, ignoreCase = true) -> {
                    out.append(if (hour24 < 12) "AM" else "PM"); i += 5
                }
                (c == 'A' || c == 'a') && section.regionMatches(i, "A/P", 0, 3, ignoreCase = true) -> {
                    out.append(if (hour24 < 12) "A" else "P"); i += 3
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    private fun renderNumberToken(value: Int, width: Int): String =
        if (width <= 1) value.toString() else value.toString().padStart(width, '0')

    private fun pad(value: Long, width: Int, ch: Char): String =
        value.toString().padStart(width, ch)

    private fun countRun(text: String, start: Int, token: Char): Int {
        var n = 0
        while (start + n < text.length && text[start + n].lowercaseChar() == token) n++
        return n
    }

    private fun substitute(text: String, section: String, isDate: Boolean): String {
        require(!isDate) { "date format applied to text" }
        val out = StringBuilder()
        var i = 0
        while (i < section.length) {
            val c = section[i]
            when {
                c == '@' -> { out.append(text); i++ }
                c == '"' -> {
                    val end = section.indexOf('"', i + 1)
                    require(end > i) { "unterminated quoted literal" }
                    out.append(section, i + 1, end)
                    i = end + 1
                }
                c == '\\' -> { require(i + 1 < section.length) { "dangling escape" }; out.append(section[i + 1]); i += 2 }
                c == '_' -> { out.append(' '); i += 2 }
                c == '*' -> { i += 2 }
                c == '[' -> {
                    val end = section.indexOf(']', i)
                    require(end > i) { "unterminated bracket" }
                    i = end + 1
                }
                else -> { out.append(c); i++ }
            }
        }
        require(out.length <= MAX_OUTPUT_CHARS) { "rendered text exceeds $MAX_OUTPUT_CHARS characters" }
        return out.toString()
    }

    private fun hasDigitPlaceholder(section: String): Boolean {
        var i = 0
        var inQuote = false
        while (i < section.length) {
            val c = section[i]
            when {
                c == '"' -> inQuote = !inQuote
                c == '\\' -> i++
                !inQuote && (c == '0' || c == '#' || c == '?') -> return true
            }
            i++
        }
        return false
    }

    private fun renderLiteralOnly(section: String): String = substitute("", section, isDate = false)

    private class DigitPattern(
        val prefix: String,
        val suffix: String,
        val integerPlaceholders: String,
        val fraction: String,
        val grouping: Boolean,
        val scale: Int,
        val percents: Int,
        val exponent: Char?,
        val exponentDigits: Int,
    )

    private fun parseDigitPattern(section: String): DigitPattern {
        val prefix = StringBuilder()
        val suffix = StringBuilder()
        val integer = StringBuilder()
        val fraction = StringBuilder()
        var seenPlaceholder = false
        var afterPoint = false
        var grouping = false
        var scale = 0
        var percents = 0
        var exponent: Char? = null
        var exponentDigits = 0
        var i = 0

        fun emitLiteral(text: String) {
            if (seenPlaceholder) suffix.append(text) else prefix.append(text)
        }

        while (i < section.length) {
            val c = section[i]
            when {
                c == '"' -> {
                    val end = section.indexOf('"', i + 1)
                    require(end > i) { "unterminated quoted literal" }
                    emitLiteral(section.substring(i + 1, end))
                    i = end + 1
                }
                c == '\\' -> {
                    require(i + 1 < section.length) { "dangling escape" }
                    emitLiteral(section[i + 1].toString())
                    i += 2
                }
                c == '_' -> { emitLiteral(" "); i += 2 }
                c == '*' -> { i += 2 }
                c == '[' -> {
                    val end = section.indexOf(']', i)
                    require(end > i) { "unterminated bracket" }
                    i = end + 1
                }
                c == '%' -> { percents++; emitLiteral("%"); i++ }
                c == '0' || c == '#' || c == '?' -> {
                    seenPlaceholder = true
                    if (exponent != null) {
                        exponentDigits++
                    } else if (afterPoint) {
                        fraction.append(c)
                    } else {
                        integer.append(c)
                    }
                    i++
                }
                c == '.' -> {
                    require(!afterPoint) { "two decimal points" }
                    require(seenPlaceholder) { "decimal point without digits" }
                    afterPoint = true
                    i++
                }
                c == ',' -> {
                    if (!seenPlaceholder) { emitLiteral(","); i++; continue }
                    if (afterPoint) { emitLiteral(","); i++; continue }
                    var commas = 0
                    var j = i
                    while (j < section.length && section[j] == ',') { commas++; j++ }
                    val nextIsPlaceholder = j < section.length && (section[j] == '0' || section[j] == '#' || section[j] == '?')
                    if (nextIsPlaceholder) {
                        require(commas == 1) { "repeated grouping commas" }
                        grouping = true
                    } else {
                        scale += commas
                        require(scale <= MAX_SCALE) { "too many scaling commas" }
                    }
                    i = j
                }
                c == 'E' || c == 'e' -> {
                    require(seenPlaceholder) { "exponent without digits" }
                    require(exponent == null) { "repeated exponent" }
                    require(i + 1 < section.length && (section[i + 1] == '+' || section[i + 1] == '-')) {
                        "exponent needs +/-"
                    }
                    exponent = section[i + 1]
                    i += 2
                }
                c == '/' -> throw IllegalArgumentException("fraction section reached the digit parser")
                c == '@' -> throw IllegalArgumentException("text placeholder in numeric format")
                else -> { emitLiteral(c.toString()); i++ }
            }
        }
        require(seenPlaceholder) { "format has no digit placeholders" }
        require(integer.isNotEmpty() || exponent != null) { "format has no integer digits" }
        require(fraction.length <= MAX_DECIMALS) { "too many decimal placeholders" }
        if (exponent != null) require(exponentDigits in 1..MAX_DECIMALS) { "exponent needs digits" }
        return DigitPattern(
            prefix.toString(), suffix.toString(), integer.toString(), fraction.toString(),
            grouping, scale, percents, exponent, exponentDigits,
        )
    }

    private fun renderDigits(value: Double, section: String, autoSign: Boolean): String {
        if (hasFractionSlash(section)) return renderFraction(value, section, autoSign)
        val pattern = parseDigitPattern(section)
        var scaled = value
        repeat(pattern.percents) { scaled *= 100.0 }
        repeat(pattern.scale) { scaled /= 1_000.0 }
        require(scaled.isFinite()) { "scaled value overflowed" }
        val negative = scaled < 0.0
        val magnitude = kotlin.math.abs(scaled)
        val decimals = pattern.fraction.length
        val rounded = BigDecimal(magnitude).setScale(decimals, RoundingMode.HALF_UP)
        val builder = StringBuilder()
        if (negative && autoSign) builder.append('-')
        builder.append(pattern.prefix)
        builder.append(
            if (pattern.exponent != null) renderScientific(rounded, pattern) else renderFixed(rounded, pattern),
        )
        builder.append(pattern.suffix)
        return builder.toString()
    }

    /** True when the section carries a top-level `/` outside quotes and brackets. */
    private fun hasFractionSlash(section: String): Boolean {
        var i = 0
        var inQuote = false
        while (i < section.length) {
            when (val c = section[i]) {
                '"' -> inQuote = !inQuote
                '\\' -> i++
                '[' -> { if (!inQuote) { val end = section.indexOf(']', i); if (end > i) { i = end } } }
                '/' -> if (!inQuote) return true
            }
            i++
        }
        return false
    }

    private class FractionPattern(
        val prefix: String,
        val integerPlaceholders: String,
        val separator: String,
        val numerator: String,
        val denominator: String,
        val fixedDenominator: Int?,
        val suffix: String,
        val percents: Int,
        val scale: Int,
    )

    /**
     * Splits a fraction section into its segments. Placeholder runs are kept as strings so the
     * renderer can honour `0`/`#`/`?` padding independently for whole part, numerator and
     * denominator; the run immediately before `/` is the numerator, everything before it is the
     * whole-number part, and the literal between them is the separator.
     */
    private fun parseFractionPattern(section: String): FractionPattern {
        val segments = ArrayList<Pair<Boolean, String>>() // true = placeholder run
        val prefix = StringBuilder()
        val suffix = StringBuilder()
        var percents = 0
        var scale = 0
        var afterSlash = false
        var denominatorDigits = StringBuilder()
        var denominatorPlaceholders: StringBuilder? = null
        var done = false
        var i = 0

        fun appendPlaceholder(c: Char) {
            val last = segments.lastOrNull()
            if (last != null && last.first && !afterSlash) {
                segments[segments.size - 1] = true to last.second + c
            } else {
                segments.add(true to c.toString())
            }
        }
        fun appendLiteral(text: String) {
            if (done || afterSlash) { suffix.append(text); return }
            val last = segments.lastOrNull()
            if (last != null && !last.first) {
                segments[segments.size - 1] = false to last.second + text
            } else {
                segments.add(false to text)
            }
        }

        while (i < section.length) {
            val c = section[i]
            when {
                c == '"' -> {
                    val end = section.indexOf('"', i + 1)
                    require(end > i) { "unterminated quoted literal" }
                    if (afterSlash) suffix.append(section.substring(i + 1, end)) else appendLiteral(section.substring(i + 1, end))
                    i = end + 1
                }
                c == '\\' -> {
                    require(i + 1 < section.length) { "dangling escape" }
                    if (afterSlash) suffix.append(section[i + 1]) else appendLiteral(section[i + 1].toString())
                    i += 2
                }
                c == '_' -> { require(i + 1 < section.length) { "dangling pad" }; if (afterSlash) suffix.append(' ') else appendLiteral(" "); i += 2 }
                c == '*' -> i += 2
                c == '[' -> {
                    val end = section.indexOf(']', i)
                    require(end > i) { "unterminated bracket" }
                    i = end + 1
                }
                c == '%' -> { percents++; if (afterSlash) suffix.append('%') else appendLiteral("%"); i++ }
                c == ',' -> {
                    // Scaling commas only make sense on the whole part; grouping is not supported
                    // together with a fraction and is refused rather than silently dropped.
                    var commas = 0
                    var j = i
                    while (j < section.length && section[j] == ',') { commas++; j++ }
                    val nextIsPlaceholder = j < section.length && section[j] in "0#?"
                    require(!nextIsPlaceholder) { "grouping is not supported in a fraction format" }
                    scale += commas
                    require(scale <= MAX_SCALE) { "too many scaling commas" }
                    i = j
                }
                c == '/' -> {
                    require(!afterSlash) { "repeated fraction slash" }
                    require(segments.isNotEmpty() && segments.last().first) { "fraction needs a numerator" }
                    afterSlash = true
                    i++
                }
                c == '0' || c == '#' || c == '?' -> {
                    if (afterSlash) {
                        val existing = denominatorPlaceholders
                        require(denominatorDigits.isEmpty()) { "denominator mixes digits and placeholders" }
                        denominatorPlaceholders = (existing ?: StringBuilder()).append(c)
                    } else {
                        appendPlaceholder(c)
                    }
                    i++
                }
                c.isDigit() -> {
                    require(afterSlash) { "literal digit outside the denominator in a fraction format" }
                    require(denominatorPlaceholders == null) { "denominator mixes digits and placeholders" }
                    denominatorDigits.append(c)
                    i++
                }
                c == '.' -> throw IllegalArgumentException("fraction formats cannot carry decimal places")
                c == 'E' || c == 'e' -> throw IllegalArgumentException("fraction formats cannot be scientific")
                else -> { if (afterSlash) suffix.append(c) else appendLiteral(c.toString()); i++ }
            }
        }

        require(segments.isNotEmpty()) { "fraction format has no placeholders" }
        require(segments.last().first) { "fraction format must end with a placeholder run" }
        val numerator = segments.removeAt(segments.size - 1).second
        var separator = ""
        if (segments.isNotEmpty() && !segments.last().first) separator = segments.removeAt(segments.size - 1).second
        val integer = if (segments.isNotEmpty() && segments.last().first) segments.removeAt(segments.size - 1).second else ""
        for ((_, text) in segments) prefix.append(text)
        val denomPlaceholders = denominatorPlaceholders?.toString()
        val fixed = if (denominatorDigits.isNotEmpty()) denominatorDigits.toString().toIntOrNull() else null
        require(fixed != null || denomPlaceholders != null) { "fraction needs a denominator" }
        if (fixed != null) require(fixed in 1..9_999) { "fixed denominator outside 1..9999" }
        if (denomPlaceholders != null) require(denomPlaceholders.length in 1..4) { "denominator placeholders outside 1..4" }
        require(numerator.length in 1..4) { "numerator placeholders outside 1..4" }
        return FractionPattern(prefix.toString(), integer, separator, numerator, denomPlaceholders ?: "", fixed,
            suffix.toString(), percents, scale)
    }

    private fun renderFraction(value: Double, section: String, autoSign: Boolean): String {
        val pattern = parseFractionPattern(section)
        var scaled = value
        repeat(pattern.percents) { scaled *= 100.0 }
        repeat(pattern.scale) { scaled /= 1_000.0 }
        require(scaled.isFinite()) { "scaled value overflowed" }
        val negative = scaled < 0.0
        val magnitude = kotlin.math.abs(scaled)
        val hasInteger = pattern.integerPlaceholders.isNotEmpty()
        var whole = if (hasInteger) kotlin.math.floor(magnitude) else 0.0
        val fractional = if (hasInteger) magnitude - whole else magnitude
        val bound = pattern.fixedDenominator ?: maxDenominator(pattern.denominator)
        require(bound in 1..9_999) { "denominator bound outside 1..9999" }

        var bestP = 0L
        var bestQ = 1L
        var bestError = Double.MAX_VALUE
        val candidates = if (pattern.fixedDenominator != null) listOf(pattern.fixedDenominator) else (1..bound).toList()
        for (q in candidates) {
            val p = Math.round(fractional * q)
            val error = kotlin.math.abs(fractional - p.toDouble() / q)
            // Strictly closer wins, so a tie keeps the smaller denominator (the earlier candidate).
            if (error < bestError - 1e-15) { bestError = error; bestP = p.toLong(); bestQ = q.toLong() }
            if (error <= 1e-15) break
        }
        if (hasInteger && bestP == bestQ) { whole += 1.0; bestP = 0 }

        val intText = when {
            !hasInteger -> ""
            whole == 0.0 && '0' !in pattern.integerPlaceholders -> ""
            else -> pad(whole.toLong().toString(), pattern.integerPlaceholders, zeroPad = true)
        }
        val fractionShown = bestP != 0L || !hasInteger
        val body = when {
            !fractionShown -> intText
            intText.isEmpty() -> pad(bestP.toString(), pattern.numerator, zeroPad = true) + "/" +
                pad(bestQ.toString(), pattern.denominator, zeroPad = true)
            else -> intText + pattern.separator + pad(bestP.toString(), pattern.numerator, zeroPad = true) + "/" +
                pad(bestQ.toString(), pattern.denominator, zeroPad = true)
        }
        val core = if (body.isEmpty()) "0" else body
        return buildString {
            if (negative && autoSign) append('-')
            append(pattern.prefix)
            append(core)
            append(pattern.suffix)
        }
    }

    /** `??` allows denominators below 100, `???` below 1000; an empty spec means one placeholder. */
    private fun maxDenominator(placeholders: String): Int {
        val digits = placeholders.length.coerceAtLeast(1)
        var bound = 1
        repeat(digits) { bound *= 10 }
        return bound - 1
    }

    /** Excel padding: `0` fills with zeros, `?` fills with spaces, `#` never pads. */
    private fun pad(text: String, placeholders: String, zeroPad: Boolean): String {
        val width = placeholders.length
        if (text.length >= width) return text
        val filler = when {
            '0' in placeholders -> '0'
            '?' in placeholders && (zeroPad || text == "0") -> ' '
            '?' in placeholders -> ' '
            else -> return text
        }
        return text.padStart(width, filler)
    }

    private fun renderFixed(rounded: BigDecimal, pattern: DigitPattern): String {
        val plain = rounded.toPlainString()
        val dot = plain.indexOf('.')
        val integerDigits = (if (dot < 0) plain else plain.substring(0, dot)).trimStart('0').ifEmpty { "0" }
        val fractionDigits = if (dot < 0) "" else plain.substring(dot + 1)
        val minimumDigits = pattern.integerPlaceholders.count { it == '0' }
        val padded = if (integerDigits.length < minimumDigits) {
            integerDigits.padStart(minimumDigits, '0')
        } else {
            integerDigits
        }
        val suppressZero = integerDigits == "0" && '0' !in pattern.integerPlaceholders && minimumDigits == 0
        var intText = if (suppressZero) "" else if (pattern.grouping) group(padded) else padded
        if (pattern.integerPlaceholders.length > padded.length && '?' in pattern.integerPlaceholders) {
            intText = intText.padStart(pattern.integerPlaceholders.length + (intText.length - padded.length), ' ')
        }
        val frac = StringBuilder()
        var lastSignificant = -1
        for (idx in pattern.fraction.indices) {
            val placeholder = pattern.fraction[idx]
            val digit = if (idx < fractionDigits.length) fractionDigits[idx] else '0'
            when (placeholder) {
                '0' -> { frac.append(digit); lastSignificant = idx }
                '#' -> { frac.append(digit); if (digit != '0') lastSignificant = idx }
                '?' -> { frac.append(digit); if (digit != '0') lastSignificant = idx }
                else -> frac.append(digit)
            }
        }
        val kept = StringBuilder()
        for (idx in frac.indices) {
            val placeholder = pattern.fraction[idx]
            val ch = frac[idx]
            val drop = idx > lastSignificant && (placeholder == '#' || (placeholder == '?' && ch == '0'))
            if (!drop) kept.append(if (placeholder == '?' && ch == '0') ' ' else ch)
        }
        val fracText = kept.toString()
        if (fracText.isEmpty()) return if (intText.isEmpty()) "0" else intText
        return if (intText.isEmpty()) ".$fracText" else "$intText.$fracText"
    }

    private fun renderScientific(rounded: BigDecimal, pattern: DigitPattern): String {
        var mantissa = rounded
        var exponent = 0
        if (rounded > BigDecimal.ZERO) {
            while (mantissa >= BigDecimal.TEN) { mantissa = mantissa.movePointLeft(1); exponent++ }
            while (mantissa < BigDecimal.ONE) { mantissa = mantissa.movePointRight(1); exponent-- }
        }
        val decimals = pattern.fraction.length
        var scaled = mantissa.setScale(decimals, RoundingMode.HALF_UP)
        if (scaled >= BigDecimal.TEN) {
            scaled = scaled.movePointLeft(1).setScale(decimals, RoundingMode.HALF_UP)
            exponent++
        }
        val sign = when (pattern.exponent) {
            '+' -> if (exponent >= 0) "+" else "-"
            '-' -> if (exponent < 0) "-" else ""
            else -> ""
        }
        val expDigits = kotlin.math.abs(exponent).toString().padStart(pattern.exponentDigits, '0')
        return scaled.toPlainString() + "E" + sign + expDigits
    }

    private fun group(digits: String): String {
        if (digits.length <= 3) return digits
        val out = StringBuilder()
        var count = 0
        for (i in digits.indices.reversed()) {
            out.append(digits[i])
            count++
            if (count % 3 == 0 && i != digits.indices.first) out.append(',')
        }
        return out.reverse().toString()
    }
}

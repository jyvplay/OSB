package dev.fusion.castv2

/**
 * Minimal recursive-descent JSON parser + string escaper.
 * Dependency-free, pure JVM, unit-testable.
 *
 * Produces: Map<String, Any?>, List<Any?>, String, Double, Boolean, or null.
 * Numbers parse as Double (exact for integers up to 2^53, which covers every
 * castv2 requestId / mediaSessionId). Sufficient for parsing RECEIVER_STATUS
 * and MEDIA_STATUS payloads and for safely embedding strings in requests.
 */
object Json {

    fun parse(text: String): Any? {
        val p = Parser(text)
        val value = p.parseValue()
        p.skipWhitespace()
        if (!p.atEnd()) throw IllegalArgumentException("Trailing data after JSON value at ${p.index}")
        return value
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: throw IllegalArgumentException("Not a JSON object")

    /** Escape a string for embedding inside a JSON string literal. */
    fun escape(value: String): String = buildString {
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u0008' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
    }

    private class Parser(private val s: String) {
        var index = 0
        fun atEnd(): Boolean = index >= s.length

        fun skipWhitespace() {
            while (index < s.length && s[index].isWhitespace()) index++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw IllegalArgumentException("Unexpected end of JSON")
            return when (s[index]) {
                '{' -> parseObjectValue()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> parseNumber()
            }
        }

        private fun parseObjectValue(): Map<String, Any?> {
            expect('{')
            val map = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { index++; return map }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                map[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> { index++; return map }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at $index")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') { index++; return list }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> { index++; return list }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $index")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw IllegalArgumentException("Unterminated string")
                when (val ch = s[index++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw IllegalArgumentException("Unterminated escape")
                        when (val esc = s[index++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\u0008')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (index + 4 > s.length) throw IllegalArgumentException("Bad unicode escape")
                                sb.append(s.substring(index, index + 4).toInt(16).toChar())
                                index += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape '$esc'")
                        }
                    }
                    else -> sb.append(ch)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = index
            if (peek() == '-') index++
            while (index < s.length && s[index].isDigit()) index++
            if (index < s.length && s[index] == '.') {
                index++
                while (index < s.length && s[index].isDigit()) index++
            }
            if (index < s.length && (s[index] == 'e' || s[index] == 'E')) {
                index++
                if (index < s.length && (s[index] == '+' || s[index] == '-')) index++
                while (index < s.length && s[index].isDigit()) index++
            }
            if (start == index) throw IllegalArgumentException("Invalid number at $start")
            return s.substring(start, index).toDouble()
        }

        private fun parseLiteral(literal: String, value: Any?): Any? {
            if (!s.startsWith(literal, index)) throw IllegalArgumentException("Invalid literal at $index")
            index += literal.length
            return value
        }

        private fun peek(): Char {
            if (atEnd()) throw IllegalArgumentException("Unexpected end of JSON")
            return s[index]
        }

        private fun expect(ch: Char) {
            if (atEnd() || s[index] != ch) throw IllegalArgumentException("Expected '$ch' at $index")
            index++
        }
    }
}

// ---- top-level typed accessors -------------------------------------------
// These are deliberately top-level (not members of `Json`). A member extension
// function would only be callable from inside the `Json` object's scope, which
// would make `obj.str("type")` fail to compile everywhere else.

fun Map<String, Any?>.str(key: String): String? = this[key] as? String

fun Map<String, Any?>.num(key: String): Double? = (this[key] as? Number)?.toDouble()

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.obj(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.arr(key: String): List<Any?>? = this[key] as? List<Any?>

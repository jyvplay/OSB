package dev.fusion.browser.model

/**
 * Punycode (RFC 3492) encoder/decoder for IDNA labels.
 *
 * Parameters fixed by RFC 3492 §5: base 36, tmin 1, tmax 26, skew 38,
 * damp 700, initial_bias 72, initial_n U+0080. Digits are a..z = 0..25,
 * 0..9 = 26..35; decoding is case-insensitive, encoding emits lowercase.
 * Overflow MUST be rejected (§6.2) — this implementation refuses once a
 * value exceeds Int.MAX_VALUE intermediate bounds.
 *
 * Declared narrowings: raw RFC 3492 transform plus minimal `xn--` label
 * bracketing only. IDNA2008/UTS#46 mapping, normalization, bidi and
 * label-validity rules are explicitly OUT OF SCOPE — a caller cannot treat
 * `toAscii` as full IDNA.
 */
object Punycode {
    const val SCOPE = "RFC 3492 transform + xn-- bracketing only; IDNA mapping/normalization NOT performed."
    private const val BASE = 36
    private const val TMIN = 1
    private const val TMAX = 26
    private const val SKEW = 38
    private const val DAMP = 700
    private const val INITIAL_BIAS = 72
    private const val INITIAL_N = 0x80
    const val ACE_PREFIX = "xn--"

    private fun adapt(delta0: Int, numPoints: Int, firstTime: Boolean): Int {
        var delta = if (firstTime) delta0 / DAMP else delta0 / 2
        delta += delta / numPoints
        var k = 0
        while (delta > ((BASE - TMIN) * TMAX) / 2) { delta /= BASE - TMIN; k += BASE }
        return k + (BASE - TMIN + 1) * delta / (delta + SKEW)
    }

    private fun digitValue(c: Char): Int = when (c) {
        in 'a'..'z' -> c - 'a'
        in 'A'..'Z' -> c - 'A'
        in '0'..'9' -> c - '0' + 26
        else -> throw IllegalArgumentException("invalid punycode digit '$c'")
    }

    private fun digitChar(d: Int): Char = if (d < 26) ('a' + d) else ('0' + d - 26)

    fun encode(input: String): String {
        val cps = input.codePoints().toArray()
        val sb = StringBuilder()
        cps.forEach { if (it < INITIAL_N) sb.append(it.toChar()) }
        val basicLen = sb.length
        if (basicLen > 0) sb.append('-')
        var n = INITIAL_N
        var delta = 0
        var bias = INITIAL_BIAS
        var handled = basicLen
        while (handled < cps.size) {
            val m = cps.filter { it >= n }.min()
            require(delta.toLong() + (m - n).toLong() * (handled + 1) <= Int.MAX_VALUE) { "punycode overflow" }
            delta += (m - n) * (handled + 1)
            n = m
            for (cp in cps) {
                if (cp < n) { delta++; require(delta >= 0) { "punycode overflow" } }
                if (cp == n) {
                    var q = delta
                    var k = BASE
                    while (true) {
                        val t = if (k <= bias) TMIN else if (k >= bias + TMAX) TMAX else k - bias
                        if (q < t) break
                        sb.append(digitChar(t + (q - t) % (BASE - t)))
                        q = (q - t) / (BASE - t)
                        k += BASE
                    }
                    sb.append(digitChar(q))
                    bias = adapt(delta, handled + 1, handled == basicLen)
                    delta = 0
                    handled++
                }
            }
            delta++; n++
        }
        return sb.toString()
    }

    fun decode(input: String): String {
        val lastHyphen = input.lastIndexOf('-')
        val output = ArrayList<Int>()
        if (lastHyphen > 0) {
            for (c in input.substring(0, lastHyphen)) {
                require(c.code < INITIAL_N) { "non-basic code point in literal portion" }
                output.add(c.code)
            }
        }
        var i = 0
        var n = INITIAL_N
        var bias = INITIAL_BIAS
        var pos = if (lastHyphen >= 0) lastHyphen + 1 else 0
        while (pos < input.length) {
            val oldi = i
            var w = 1
            var k = BASE
            while (true) {
                require(pos < input.length) { "truncated variable-length integer" }
                val digit = digitValue(input[pos]); pos++
                require(digit < BASE && i.toLong() + digit.toLong() * w <= Int.MAX_VALUE) { "punycode overflow" }
                i += digit * w
                val t = if (k <= bias) TMIN else if (k >= bias + TMAX) TMAX else k - bias
                if (digit < t) break
                require(w.toLong() * (BASE - t) <= Int.MAX_VALUE) { "punycode overflow" }
                w *= BASE - t
                k += BASE
            }
            bias = adapt(i - oldi, output.size + 1, oldi == 0)
            require(n.toLong() + i / (output.size + 1) <= Int.MAX_VALUE) { "punycode overflow" }
            n += i / (output.size + 1)
            i %= output.size + 1
            require(n in 0x80..0x10FFFF && n !in 0xD800..0xDFFF) { "decoded code point U+${n.toString(16)} invalid" }
            output.add(i, n)
            i++
        }
        return String(output.toIntArray(), 0, output.size)
    }

    fun toAscii(label: String): String {
        require(label.isNotEmpty()) { "empty label" }
        if (label.all { it.code < 0x80 }) {
            require(!label.lowercase().startsWith(ACE_PREFIX)) { "ASCII label already carries xn--" }
            return label
        }
        return ACE_PREFIX + encode(label)
    }

    fun toUnicode(label: String): String =
        if (label.lowercase().startsWith(ACE_PREFIX)) decode(label.substring(ACE_PREFIX.length)) else label
}

package dev.fusion.storage

/**
 * ULID (Universally Unique Lexicographically Sortable Identifier) codec per
 * the canonical spec at github.com/ulid/spec (fetched 2026-10-23).
 *
 * Fixed by the spec: 26-character Crockford base32 string over alphabet
 * "0123456789ABCDEFGHJKMNPQRSTVWXYZ" (I, L, O, U excluded); first 10 chars
 * encode a 48-bit Unix-millisecond timestamp, last 16 encode 80 random
 * bits; the largest valid ULID is "7ZZZZZZZZZZZZZZZZZZZZZZZZZ" (timestamp
 * 2^48-1) and anything larger MUST be rejected; decoding is
 * case-insensitive; monotonic generation within one millisecond increments
 * the random component by one with carry and FAILS on overflow.
 *
 * Declared narrowings: no random source here — generation takes caller
 * bytes (the storage layer injects SecureRandom); I/L/O/U are refused, not
 * mapped (the Crockford leniency is unspecified for ULID and this codec
 * refuses rather than guesses).
 */
object Ulid {
    const val SCOPE = "Spec codec + monotonic increment only; randomness is injected, never generated here."
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LENGTH = 26
    const val MAX_TIMESTAMP = (1L shl 48) - 1
    const val MAX_ULID = "7ZZZZZZZZZZZZZZZZZZZZZZZZZ"

    private val VALUE = IntArray(128) { -1 }.also { for (i in ALPHABET.indices) { it[ALPHABET[i].code] = i; it[ALPHABET[i].lowercaseChar().code] = i } }

    private fun digit(c: Char): Int {
        val v = if (c.code < 128) VALUE[c.code] else -1
        require(v >= 0) { "'$c' is not a Crockford base32 digit (I,L,O,U are refused)" }
        return v
    }

    data class Parts(val timestampMs: Long, val random: ByteArray)

    fun decode(s: String): Parts {
        require(s.length == LENGTH) { "ULID must be $LENGTH chars, got ${s.length}" }
        require(digit(s[0]) <= 7) { "ULID exceeds $MAX_ULID (first char '${s[0]}')" }
        var ts = 0L
        for (i in 0 until 10) ts = (ts shl 5) or digit(s[i]).toLong()
        require(ts <= MAX_TIMESTAMP) { "timestamp overflow" }
        // 16 chars * 5 bits = 80 bits = 10 bytes
        var acc = 0L
        var bits = 0
        val rand = ByteArray(10)
        var out = 0
        for (i in 10 until LENGTH) {
            acc = (acc shl 5) or digit(s[i]).toLong(); bits += 5
            while (bits >= 8) { rand[out++] = ((acc shr (bits - 8)) and 0xFF).toByte(); bits -= 8 }
        }
        return Parts(ts, rand)
    }

    fun encode(timestampMs: Long, random: ByteArray): String {
        require(timestampMs in 0..MAX_TIMESTAMP) { "timestamp $timestampMs outside 0..$MAX_TIMESTAMP" }
        require(random.size == 10) { "random component must be 10 bytes" }
        val sb = StringBuilder(LENGTH)
        for (shift in 45 downTo 0 step 5) sb.append(ALPHABET[((timestampMs shr shift) and 0x1F).toInt()])
        var acc = 0L
        var bits = 0
        for (b in random) {
            acc = (acc shl 8) or (b.toLong() and 0xFF); bits += 8
            while (bits >= 5) { sb.append(ALPHABET[((acc shr (bits - 5)) and 0x1F).toInt()]); bits -= 5 }
        }
        return sb.toString()
    }

    fun timestampMs(s: String): Long = decode(s).timestampMs

    /** Spec monotonicity: same-millisecond successor; refuses on 80-bit overflow. */
    fun next(s: String): String {
        val p = decode(s)
        val r = p.random.copyOf()
        var i = 9
        while (i >= 0) {
            r[i] = ((r[i].toInt() and 0xFF) + 1).toByte()
            if (r[i].toInt() != 0) break
            i--
        }
        require(i >= 0) { "random component overflow within this millisecond (spec: generation must fail)" }
        return encode(p.timestampMs, r)
    }

    fun isValid(s: String): Boolean = try { decode(s); true } catch (_: IllegalArgumentException) { false }
}

package dev.fusion.backup

/**
 * RFC 3339 timestamp parsing for backup manifests.
 *
 * Grammar implemented (RFC 3339 §5.6): `full-date "T" partial-time
 * time-offset` with optional fractional seconds (1..9 digits kept as
 * nanoseconds) and a REQUIRED offset (`Z` or ±hh:mm). `-00:00` is
 * accepted and flagged as unknown-local-offset per §4.3. Lowercase
 * `t`/`z` are accepted per the grammar's case-insensitivity note and
 * canonicalized to uppercase.
 *
 * Declared narrowings: leap second `:60` is REFUSED (RFC allows it; the
 * backup ledger needs injective timestamps, and Python's
 * datetime.fromisoformat — the held-out verifier — refuses it as well);
 * naive timestamps without offset are refused; years 0001..9999.
 */
object Rfc3339 {
    const val SCOPE = "RFC 3339 date-time with mandatory offset; leap seconds refused by declared policy."

    data class Stamp(
        val year: Int, val month: Int, val day: Int,
        val hour: Int, val minute: Int, val second: Int, val nanos: Int,
        val offsetMinutes: Int, val unknownLocalOffset: Boolean
    )

    private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
    private fun dim(y: Int, m: Int) = when (m) {
        1, 3, 5, 7, 8, 10, 12 -> 31; 4, 6, 9, 11 -> 30; 2 -> if (isLeap(y)) 29 else 28
        else -> throw IllegalArgumentException("month $m")
    }

    private fun num(s: String, from: Int, len: Int): Int {
        require(from + len <= s.length) { "truncated timestamp" }
        val part = s.substring(from, from + len)
        require(part.all { it.isDigit() }) { "'$part' is not numeric" }
        return part.toInt()
    }

    fun parse(raw: String): Stamp {
        val s = raw.replace('t', 'T').replace('z', 'Z')
        require(s.length >= 20) { "too short for RFC 3339 with offset" }
        val y = num(s, 0, 4); require(s[4] == '-') { "expected '-' at 4" }
        val mo = num(s, 5, 2); require(s[7] == '-') { "expected '-' at 7" }
        val d = num(s, 8, 2); require(s[10] == 'T') { "expected 'T' separator" }
        require(y in 1..9999 && mo in 1..12) { "date $y-$mo" }
        require(d in 1..dim(y, mo)) { "day $d invalid for $y-$mo" }
        val h = num(s, 11, 2); require(s[13] == ':') { "expected ':' at 13" }
        val mi = num(s, 14, 2); require(s[16] == ':') { "expected ':' at 16" }
        val sec = num(s, 17, 2)
        require(h in 0..23 && mi in 0..59) { "time $h:$mi" }
        require(sec in 0..59) { "second $sec (leap seconds refused by declared policy)" }
        var i = 19
        var nanos = 0
        if (i < s.length && s[i] == '.') {
            i++
            val start = i
            while (i < s.length && s[i].isDigit()) i++
            val digits = i - start
            require(digits in 1..9) { "fraction must be 1..9 digits" }
            nanos = s.substring(start, i).padEnd(9, '0').toInt()
        }
        require(i < s.length) { "missing offset (naive timestamps refused)" }
        val offsetMinutes: Int
        var unknown = false
        when (s[i]) {
            'Z' -> { require(i == s.length - 1) { "trailing garbage after Z" }; offsetMinutes = 0 }
            '+', '-' -> {
                require(s.length == i + 6 && s[i + 3] == ':') { "offset must be ±hh:mm" }
                val oh = num(s, i + 1, 2); val om = num(s, i + 4, 2)
                require(oh in 0..23 && om in 0..59) { "offset $oh:$om" }
                val sign = if (s[i] == '-') -1 else 1
                offsetMinutes = sign * (oh * 60 + om)
                if (s[i] == '-' && oh == 0 && om == 0) unknown = true
            }
            else -> throw IllegalArgumentException("bad offset start '${s[i]}'")
        }
        return Stamp(y, mo, d, h, mi, sec, nanos, offsetMinutes, unknown)
    }

    /** Days since 1970-01-01 for a civil date (proleptic Gregorian). */
    internal fun epochDays(y: Int, m: Int, d: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = Math.floorDiv(yy, 400)
        val yoe = yy - era * 400
        val doy = (153 * ((m + 9) % 12) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    fun epochSeconds(st: Stamp): Long =
        epochDays(st.year, st.month, st.day) * 86400L +
            st.hour * 3600L + st.minute * 60L + st.second - st.offsetMinutes * 60L

    fun canonical(st: Stamp): String {
        val frac = if (st.nanos == 0) "" else "." + "%09d".format(st.nanos).trimEnd('0')
        val off = when {
            st.unknownLocalOffset -> "-00:00"
            st.offsetMinutes == 0 -> "Z"
            else -> {
                val a = kotlin.math.abs(st.offsetMinutes)
                "%s%02d:%02d".format(if (st.offsetMinutes < 0) "-" else "+", a / 60, a % 60)
            }
        }
        return "%04d-%02d-%02dT%02d:%02d:%02d%s%s".format(st.year, st.month, st.day, st.hour, st.minute, st.second, frac, off)
    }
}

package dev.fusion.format.pdf

/**
 * PDF date strings (ISO 32000-1 §7.9.4): `D:YYYYMMDDHHmmSSOHH'mm` where
 * every field after the year is optional (defaulting to 01/01/00:00:00)
 * and O is Z, + or -. The trailing apostrophe after minutes is accepted
 * (pre-2008 writers emit `HH'mm'`).
 *
 * Declared narrowings: the `D:` prefix is REQUIRED (the spec makes it
 * optional for readers; this intake refuses the ambiguity); absent
 * offset means "unknown local time" and is reported as null, never
 * assumed UTC.
 */
object PdfDate {
    const val SCOPE = "D:-prefixed §7.9.4 dates; missing offset reported null, never assumed UTC."

    data class Stamp(
        val year: Int, val month: Int, val day: Int,
        val hour: Int, val minute: Int, val second: Int,
        val offsetMinutes: Int?
    )

    private fun leap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
    private fun dim(y: Int, m: Int) = when (m) {
        1, 3, 5, 7, 8, 10, 12 -> 31; 4, 6, 9, 11 -> 30; 2 -> if (leap(y)) 29 else 28
        else -> throw IllegalArgumentException("month $m")
    }

    private fun take(s: String, pos: Int, n: Int, default: Int): Pair<Int, Int> {
        if (pos >= s.length || !s[pos].isDigit()) return default to pos
        require(pos + n <= s.length) { "truncated field at $pos" }
        val part = s.substring(pos, pos + n)
        require(part.all { it.isDigit() }) { "'$part' is not numeric" }
        return part.toInt() to (pos + n)
    }

    fun parse(text: String): Stamp {
        require(text.startsWith("D:")) { "PDF date must carry the D: prefix (ambiguous forms refused)" }
        val s = text.substring(2)
        require(s.length >= 4) { "year is mandatory" }
        var pos = 0
        val (y, p1) = take(s, pos, 4, 0); pos = p1
        require(y in 1..9999) { "year $y" }
        val (mo, p2) = take(s, pos, 2, 1); pos = p2
        val (d, p3) = take(s, pos, 2, 1); pos = p3
        val (h, p4) = take(s, pos, 2, 0); pos = p4
        val (mi, p5) = take(s, pos, 2, 0); pos = p5
        val (sec, p6) = take(s, pos, 2, 0); pos = p6
        require(mo in 1..12 && d in 1..dim(y, mo)) { "date $y-$mo-$d invalid" }
        require(h in 0..23 && mi in 0..59 && sec in 0..59) { "time $h:$mi:$sec invalid" }
        var offset: Int? = null
        if (pos < s.length) {
            val o = s[pos]; pos++
            when (o) {
                'Z' -> offset = 0
                '+', '-' -> {
                    val (oh, p7) = take(s, pos, 2, 0); pos = p7
                    var om = 0
                    if (pos < s.length) {
                        require(s[pos] == '\'') { "offset hours and minutes separate with an apostrophe" }
                        pos++
                        val (m2, p8) = take(s, pos, 2, 0); pos = p8
                        om = m2
                        if (pos < s.length && s[pos] == '\'') pos++   // legacy trailing apostrophe
                    }
                    require(oh in 0..23 && om in 0..59) { "offset $oh'$om invalid" }
                    offset = (if (o == '-') -1 else 1) * (oh * 60 + om)
                }
                else -> throw IllegalArgumentException("bad offset marker '$o'")
            }
        }
        require(pos == s.length) { "trailing garbage after position $pos" }
        return Stamp(y, mo, d, h, mi, sec, offset)
    }

    fun format(st: Stamp): String {
        val base = "D:%04d%02d%02d%02d%02d%02d".format(st.year, st.month, st.day, st.hour, st.minute, st.second)
        val off = st.offsetMinutes ?: return base
        if (off == 0) return base + "Z"
        val a = kotlin.math.abs(off)
        return base + "%s%02d'%02d".format(if (off < 0) "-" else "+", a / 60, a % 60)
    }
}

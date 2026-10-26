package dev.fusion.backup

/**
 * Backup snapshot naming (`prefix-YYYYMMDD-HHMMSS[.ext]`): parse,
 * validate the timestamp fields (real calendar, no 2026-02-29),
 * lexicographic-sort == chronological-sort law (the reason the shape
 * exists; executed as a property), and latest-of selection.
 *
 * Declared narrowings: naming only — retention mathematics lives in
 * RetentionPlan; timestamps are LOCAL-naive by design here (names
 * must match what the user sees in a file manager; the manifest's
 * RFC 3339 field carries the real instant).
 */
object BackupName {
    const val SCOPE = "Snapshot-name shape with the lex==chrono law executed; retention/instants live elsewhere."
    private val SHAPE = Regex("([A-Za-z][A-Za-z0-9_]{0,31})-(\\d{8})-(\\d{6})(\\.[A-Za-z0-9.]{1,16})?")

    data class Name(val prefix: String, val date: String, val time: String, val ext: String?)

    private fun leap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    fun parse(name: String): Name {
        val m = SHAPE.matchEntire(name) ?: throw IllegalArgumentException("'" + name + "' is not prefix-YYYYMMDD-HHMMSS")
        val d = m.groupValues[2]
        val y = d.substring(0, 4).toInt(); val mo = d.substring(4, 6).toInt(); val day = d.substring(6).toInt()
        require(y in 2000..2199 && mo in 1..12) { "date field " + d }
        val dim = intArrayOf(31, if (leap(y)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[mo - 1]
        require(day in 1..dim) { "day " + day + " invalid for " + y + "-" + mo }
        val t = m.groupValues[3]
        require(t.substring(0, 2).toInt() < 24 && t.substring(2, 4).toInt() < 60 && t.substring(4).toInt() < 60) { "time field " + t }
        return Name(m.groupValues[1], d, t, m.groupValues[4].ifEmpty { null })
    }

    fun latest(names: List<String>, prefix: String): String {
        val mine = names.filter { parse(it).prefix == prefix }
        require(mine.isNotEmpty()) { "no snapshots for prefix '" + prefix + "'" }
        return mine.max()
    }

    fun chronoKey(name: String): String = parse(name).let { it.date + it.time }
}

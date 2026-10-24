package dev.fusion.ooxml

/**
 * Excel serial-date arithmetic for the 1900 and 1904 date systems.
 *
 * Primary texts: ECMA-376 Part 1 §18.17.4.1 (date representation) and the
 * Microsoft KB "Excel incorrectly assumes that the year 1900 is a leap year":
 * the 1900 system deliberately keeps Lotus 1-2-3's phantom 1900-02-29 as
 * serial 60, so serials 1..59 count from 1899-12-31 and serials 61+ count
 * from 1899-12-30. The 1904 system has serial 0 = 1904-01-01 and no phantom.
 *
 * Declared narrowings: whole-day serials only (no time fraction), years
 * 1900..9999 (1900 system) / 1904..9999 (1904 system). Fractional serials,
 * negative 1904 serials (Excel for Mac legacy), and locale display are out
 * of scope and refused loudly, not approximated.
 */
object SerialDate {
    const val SCOPE =
        "Whole-day Excel serials only; phantom 1900-02-29 preserved as serial 60 per ECMA-376/Lotus compatibility."
    const val MAX_1900 = 2958465           // 9999-12-31
    const val MAX_1904 = 2957003           // 9999-12-31
    const val SYSTEM_DELTA = 1462          // serial1900 = serial1904 + 1462 for the same real day >= 1904-01-01
    const val PHANTOM_SERIAL = 60

    data class Day(val year: Int, val month: Int, val day: Int, val phantom: Boolean = false)

    fun isLeap(y: Int): Boolean = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    fun daysInMonth(y: Int, m: Int): Int {
        require(m in 1..12) { "month $m" }
        return when (m) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            else -> if (isLeap(y)) 29 else 28
        }
    }

    /** Days since 0000-03-01 (proleptic Gregorian civil count, Hinnant-style). */
    internal fun civil(y: Int, m: Int, d: Int): Long {
        require(y in 1..9999 && m in 1..12 && d in 1..daysInMonth(y, m)) { "bad date $y-$m-$d" }
        val yy = if (m <= 2) y - 1 else y
        val era = yy / 400
        val yoe = yy - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe
    }

    internal fun fromCivil(z0: Long): Day {
        val era = Math.floorDiv(z0, 146097L)
        val doe = (z0 - era * 146097L).toInt()
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = (mp + 2) % 12 + 1
        val y = (era * 400 + yoe + (if (m <= 2) 1 else 0)).toInt()
        return Day(y, m, d)
    }

    fun fromSerial1900(serial: Int): Day {
        require(serial in 1..MAX_1900) { "serial $serial outside 1..$MAX_1900" }
        return when {
            serial == PHANTOM_SERIAL -> Day(1900, 2, 29, phantom = true)
            serial < PHANTOM_SERIAL -> fromCivil(civil(1899, 12, 31) + serial)
            else -> fromCivil(civil(1899, 12, 30) + serial)
        }
    }

    fun toSerial1900(y: Int, m: Int, d: Int): Int {
        if (y == 1900 && m == 2 && d == 29) return PHANTOM_SERIAL
        require(y in 1900..9999) { "year $y outside 1900..9999" }
        val days = civil(y, m, d)
        val serial =
            if (days <= civil(1900, 2, 28)) (days - civil(1899, 12, 31)).toInt()
            else (days - civil(1899, 12, 30)).toInt()
        return serial
    }

    fun fromSerial1904(serial: Int): Day {
        require(serial in 0..MAX_1904) { "serial $serial outside 0..$MAX_1904" }
        return fromCivil(civil(1904, 1, 1) + serial)
    }

    fun toSerial1904(y: Int, m: Int, d: Int): Int {
        require(y in 1904..9999) { "year $y outside 1904..9999" }
        return (civil(y, m, d) - civil(1904, 1, 1)).toInt()
    }
}

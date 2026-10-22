// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/**
 * Decoder for the bounded VEVENT subset [CalendarExport] writes: CRLF lines,
 * space-folding, UID/DTSTAMP/DTSTART/DTEND/SUMMARY/DESCRIPTION with strict
 * UTC stamps. DESCRIPTION may be absent (decodes to ""); every other
 * property is required exactly once. Unknown properties are refused rather
 * than skipped so a "parsed" event never silently drops scheduling data,
 * and backslash escapes are strict (\\ \; \, \n only).
 */
object VEventParse {
    const val MAX_BYTES = 48 * 1024
    const val MAX_LINES = 512
    private val STAMP = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

    fun parse(bytes: ByteArray): SharedCalendarEvent {
        require(bytes.size in 1..MAX_BYTES)
        val text = String(bytes, Charsets.UTF_8)
        val raw = text.split("\r\n")
        require(raw.size in 2..MAX_LINES + 1 && raw.last().isEmpty())
        val lines = ArrayList<String>()
        for (line in raw.dropLast(1)) {
            require(line.isNotEmpty())
            if (line[0] == ' ') {
                require(lines.isNotEmpty())
                lines[lines.size - 1] = lines.last() + line.substring(1)
            } else {
                lines.add(line)
            }
        }
        require(lines.size >= 11)
        require(lines[0] == "BEGIN:VCALENDAR" && lines[1] == "VERSION:2.0")
        require(lines[2] == "PRODID:-//Fusion//Bounded Event Export//EN")
        require(lines[3] == "BEGIN:VEVENT" && lines[lines.size - 2] == "END:VEVENT" && lines.last() == "END:VCALENDAR")
        val props = LinkedHashMap<String, String>()
        for (line in lines.subList(4, lines.size - 2)) {
            val colon = line.indexOf(':')
            require(colon > 0)
            val name = line.substring(0, colon)
            require(name == name.uppercase() && ';' !in name) { "parameters refused" }
            require(!props.containsKey(name)) { "duplicate $name" }
            props[name] = line.substring(colon + 1)
        }
        require(props.keys == setOf("UID", "DTSTAMP", "DTSTART", "DTEND", "SUMMARY") ||
            props.keys == setOf("UID", "DTSTAMP", "DTSTART", "DTEND", "SUMMARY", "DESCRIPTION"))
        val uid = props.getValue("UID")
        require(uid.matches(Regex("[A-Za-z0-9._@-]{1,128}")))
        fun stamp(key: String): Instant {
            val v = props.getValue(key)
            require(v.length == 16 && v.endsWith("Z"))
            val dt = LocalDateTime.parse(v.substring(0, 15), STAMP)
            val t = dt.toInstant(ZoneOffset.UTC)
            require(t.epochSecond in 0..253402300799L)
            return t
        }
        val created = stamp("DTSTAMP"); val start = stamp("DTSTART"); val end = stamp("DTEND")
        require(end > start)
        fun unescape(v: String, limit: Int): String {
            val out = StringBuilder()
            var i = 0
            while (i < v.length) {
                val c = v[i]
                if (c != '\\') { out.append(c); i++ }
                else {
                    require(i + 1 < v.length)
                    when (v[i + 1]) {
                        '\\' -> out.append('\\'); ';' -> out.append(';')
                        ',' -> out.append(','); 'n' -> out.append('\n')
                        else -> throw IllegalArgumentException("bad escape")
                    }
                    i += 2
                }
            }
            require(out.length <= limit)
            require(out.none { it.isISOControl() && it != '\n' })
            return out.toString()
        }
        return SharedCalendarEvent(uid, created, start, end, unescape(props.getValue("SUMMARY"), 1024),
            if (props.containsKey("DESCRIPTION")) unescape(props.getValue("DESCRIPTION"), 8192) else "")
    }
}

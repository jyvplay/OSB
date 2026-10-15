package dev.fusion.continuity

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SharedCalendarEvent(val uid: String, val created: Instant, val start: Instant, val end: Instant, val summary: String, val description: String="")
/** Bounded UTC VEVENT serialization for explicit export; not recurrence, scheduling or CalendarContract. */
object CalendarExport {
    private val format=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'",Locale.ROOT).withZone(ZoneOffset.UTC)
    fun encode(event: SharedCalendarEvent): ByteArray {
        require(event.uid.matches(Regex("[A-Za-z0-9._@-]{1,128}")))
        for(t in listOf(event.created,event.start,event.end)) require(t.nano==0 && t.epochSecond in 0..253402300799L)
        require(event.end>event.start)
        fun escape(text: String, limit: Int): String {
            require(text.length<=limit && text.none { it.isISOControl() && it!='\n' })
            Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text))
            return text.replace("\\","\\\\").replace(";","\\;").replace(",","\\,").replace("\n","\\n")
        }
        val lines=listOf("BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//Fusion//Bounded Event Export//EN","BEGIN:VEVENT",
            "UID:${event.uid}","DTSTAMP:${format.format(event.created)}","DTSTART:${format.format(event.start)}","DTEND:${format.format(event.end)}",
            "SUMMARY:${escape(event.summary,1024)}","DESCRIPTION:${escape(event.description,8192)}","END:VEVENT","END:VCALENDAR")
        val encoded=buildString {
            for(line in lines) {
                var column=0
                val iterator=line.codePoints().iterator()
                while(iterator.hasNext()) {
                    val cp=String(Character.toChars(iterator.nextInt()));val bytes=cp.toByteArray(Charsets.UTF_8).size
                    if(column+bytes>75) { append("\r\n ");column=1 }
                    append(cp);column+=bytes
                };append("\r\n")
            }
        }.toByteArray(Charsets.UTF_8)
        require(encoded.size<=48*1024);return encoded
    }
}

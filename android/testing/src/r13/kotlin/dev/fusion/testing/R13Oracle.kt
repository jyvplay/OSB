package dev.fusion.testing

import dev.fusion.files.ContentDigest
import dev.fusion.terminal.ShellWords
import dev.fusion.continuity.*
import dev.fusion.ooxml.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Base64

/** Test-only pipe consumer. Does not replace an Android or Office runtime. */
object R13Oracle {
    @JvmStatic fun main(args:Array<String>) {
        val dec=Base64.getDecoder();val enc=Base64.getEncoder();val now=Instant.parse("2026-10-15T12:00:00Z")
        generateSequence(::readLine).forEach { line ->
            try {
                val f=line.split('\t');fun text(n:Int)=String(dec.decode(f[n]),Charsets.UTF_8)
                println(when(f[0]) {
                    "D" -> { val r=ContentDigest.scan(dec.decode(f[1]).inputStream());"${r.bytes}:${r.sha256}" }
                    "T" -> ShellWords.parse(text(1)).joinToString(",") { enc.encodeToString(it.toByteArray()) }
                    "C" -> enc.encodeToString(CalendarExport.encode(SharedCalendarEvent("case@fusion",now,now,now.plusSeconds(60),text(1),text(2))))
                    "W" -> enc.encodeToString(LiteralWorkbook.export(text(1),listOf(listOf(LiteralCell.Text(text(2)),LiteralCell.Text("=1+1"),LiteralCell.Number(BigDecimal(f[3])),LiteralCell.Bool(true),LiteralCell.Empty))).bytes)
                    else -> error("Unknown test operation")
                })
            } catch(_:Exception) { println("REFUSED") }
        }
    }
}

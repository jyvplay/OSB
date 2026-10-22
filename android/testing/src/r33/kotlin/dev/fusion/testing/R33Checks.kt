// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.messages.E164Format
import dev.fusion.terminal.AnsiSgr
import dev.fusion.terminal.SgrColor
import dev.fusion.terminal.SgrStyle
import dev.fusion.beam.Chunk
import dev.fusion.beam.ChunkPlan
import dev.fusion.filesui.GlobMatch
import dev.fusion.find.FuzzyScore
import dev.fusion.together.ClockOrder
import dev.fusion.together.VectorClocks
import dev.fusion.documents.DiffHunks
import dev.fusion.documents.LineDiff
import dev.fusion.format.image.BmpInspect
import dev.fusion.format.image.BmpInfo
import dev.fusion.archive.GzipHead
import dev.fusion.archive.GzipInspect
import dev.fusion.vault.TotpCode
import java.io.File

/** R33 host consumer: behavioral contracts over the 10 R33 kernels. Host JVM only. */
object R33Checks {
    var n = 0
    fun ok(c: Boolean, label: String) {
        n++
        if (!c) throw AssertionError("r33 check $n FAILED: $label")
    }
    fun fails(label: String, block: () -> Unit) {
        n++
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("r33 check $n FAILED (no refusal): $label")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args[0])
        e164(); ansi(); chunk(); glob(); fuzzy(); clock(); hunks()
        bmp(File(dir, "tiny.bmp").readBytes())
        gzip(File(dir, "name.gz").readBytes(), File(dir, "hcrc.gz").readBytes())
        totp()
        println("R33 PASS: $n production-kernel assertions across 10 kernels. " +
            "Host JVM only; NOT Android, device, service, anonymity or acceptance evidence.")
    }

    fun e164() {
        ok(E164Format.normalize("+1 800-555-1212") == "+18005551212", "us pretty")
        ok(E164Format.normalize("+44.20.7946.0000") == "+442079460000", "uk dots")
        ok(E164Format.normalize("00442079460000") == "+442079460000", "00 prefix")
        ok(E164Format.normalize("(020) 7946 0000", "+44") == "+442079460000", "trunk strip")
        ok(E164Format.normalize("2079460000", "+44") == "+442079460000", "no trunk")
        ok(E164Format.normalize("+1234567") == "+1234567", "min digits")
        ok(E164Format.normalize("+123456789012345") == "+123456789012345", "max digits")
        fails("letters") { E164Format.normalize("+1-800-FLOWERS") }
        fails("bare local") { E164Format.normalize("02079460000") }
        fails("short") { E164Format.normalize("+123456") }
        fails("long") { E164Format.normalize("+1234567890123456") }
        fails("double plus") { E164Format.normalize("++442079460000") }
        fails("mid plus") { E164Format.normalize("44+2079460000") }
        fails("bad cc") { E164Format.normalize("2079460000", "44") }
        fails("blank") { E164Format.normalize("   ") }
        fails("leading zero intl") { E164Format.normalize("0001234567") }
    }

    fun ansi() {
        ok(AnsiSgr.parseSequence("\u001B[1;31m") == listOf(1, 31), "parse pair")
        ok(AnsiSgr.parseSequence("\u001B[m") == listOf(0), "bare reset")
        ok(AnsiSgr.parseSequence("\u001B[38;5;200m") == listOf(38, 5, 200), "parse 256")
        ok(AnsiSgr.parseSequence("abc") == null, "not a sequence")
        ok(AnsiSgr.parseSequence("\u001B[1;") == null, "no terminator")
        ok(AnsiSgr.parseSequence("\u001B[1;;2m") == null, "empty param")
        ok(AnsiSgr.parseSequence("\u001B[99999999999999999999m") == null, "overflow param")
        ok(AnsiSgr.parseSequence("\u001B[1;Xm") == null, "alpha param")
        val s = AnsiSgr.apply(SgrStyle(), listOf(1, 31))
        ok(s == SgrStyle(bold = true, fg = SgrColor.Ansi(1)), "bold red")
        ok(AnsiSgr.apply(s, listOf(0)) == SgrStyle(), "reset clears")
        ok(AnsiSgr.apply(SgrStyle(), listOf(38, 5, 200)).fg == SgrColor.Ansi(200), "256 fg")
        ok(AnsiSgr.apply(SgrStyle(), listOf(48, 2, 1, 2, 3)).bg == SgrColor.Rgb(1, 2, 3), "truecolor bg")
        ok(!AnsiSgr.apply(s, listOf(22)).bold, "unbold")
        ok(AnsiSgr.apply(SgrStyle(), listOf(90)).fg == SgrColor.Ansi(8), "bright fg")
        ok(AnsiSgr.apply(SgrStyle(fg = SgrColor.Ansi(1)), listOf(39)).fg == null, "default fg")
        fails("faint refused") { AnsiSgr.apply(SgrStyle(), listOf(2)) }
        fails("truncated 256") { AnsiSgr.apply(SgrStyle(), listOf(38, 5)) }
        fails("truncated rgb") { AnsiSgr.apply(SgrStyle(), listOf(48, 2, 1, 2)) }
        fails("bad mode") { AnsiSgr.apply(SgrStyle(), listOf(38, 4, 1)) }
        fails("empty params") { AnsiSgr.apply(SgrStyle(), emptyList()) }
        ok(AnsiSgr.strip("\u001B[1mA\u001B[0m") == "A", "strip sgr")
        ok(AnsiSgr.strip("x\u001B[2Jy") == "x\u001B[2Jy", "csi stays")
        ok(AnsiSgr.strip("a\u001B[") == "a\u001B[", "trailing esc")
    }

    fun chunk() {
        ok(ChunkPlan.plan(10, 3) == listOf(Chunk(0, 0, 3), Chunk(1, 3, 3), Chunk(2, 6, 3), Chunk(3, 9, 1)), "uneven")
        ok(ChunkPlan.plan(9, 3).size == 3, "even")
        ok(ChunkPlan.plan(0, 5).isEmpty(), "empty total")
        ok(ChunkPlan.plan(5, 10) == listOf(Chunk(0, 0, 5)), "single short")
        ok(ChunkPlan.missing(10, 3, setOf(0, 2)) == listOf(Chunk(1, 3, 3), Chunk(3, 9, 1)), "resume")
        ok(ChunkPlan.missing(9, 3, setOf(0, 1, 2)).isEmpty(), "resume done")
        fails("zero chunk") { ChunkPlan.plan(10, 0) }
        fails("negative total") { ChunkPlan.plan(-1, 3) }
        fails("over chunks") { ChunkPlan.plan(1_000_001, 1) }
        fails("done out of range") { ChunkPlan.missing(9, 3, setOf(3)) }
        fails("huge chunk") { ChunkPlan.plan(10, 2L * 1024 * 1024 * 1024) }
    }

    fun glob() {
        ok(GlobMatch.match("*", "anything"), "star all")
        ok(GlobMatch.match("*.kt", "Main.kt"), "star suffix")
        ok(!GlobMatch.match("*.kt", "Main.java"), "star miss")
        ok(GlobMatch.match("a?c", "abc"), "q hit")
        ok(!GlobMatch.match("a?c", "ac"), "q miss")
        ok(GlobMatch.match("[abc]x", "bx"), "class hit")
        ok(!GlobMatch.match("[abc]x", "dx"), "class miss")
        ok(GlobMatch.match("[a-z]x", "qx"), "range hit")
        ok(GlobMatch.match("[!a-z]x", "Qx"), "neg hit")
        ok(!GlobMatch.match("[!a-z]x", "qx"), "neg miss")
        ok(GlobMatch.match("[]]x", "]x"), "literal bracket")
        ok(GlobMatch.match("[-a]x", "-x"), "literal dash")
        ok(GlobMatch.match("\\*x", "*x"), "escaped star")
        ok(GlobMatch.match("", ""), "empty empty")
        ok(!GlobMatch.match("", "a"), "empty miss")
        ok(GlobMatch.match("**", "a/b"), "stars no recursion")
        fails("unterminated") { GlobMatch.match("[ab", "ab") }
        fails("long name") { GlobMatch.match("*", "a".repeat(1025)) }
        fails("reversed") { GlobMatch.match("[z-a]", "m") }
        fails("trailing escape") { GlobMatch.match("ab\\", "ab") }
        fails("long pattern") { GlobMatch.match("*".repeat(257), "a") }
    }

    fun fuzzy() {
        ok(FuzzyScore.score("abc", "abc") == 55, "exact 55")
        ok(FuzzyScore.score("fb", "FooBar") == 41, "camel 41")
        ok(FuzzyScore.score("ac", "abc") == 34, "gap 34")
        ok(FuzzyScore.score("a", "a") == 25, "single 25")
        ok(FuzzyScore.score("FB", "foobar") == 33, "folded")
        ok(FuzzyScore.score("", "anything") == 0, "empty 0")
        ok(FuzzyScore.score("xyz", "abc") == null, "miss")
        ok(FuzzyScore.score("abcd", "abc") == null, "too long")
        fails("over cap") { FuzzyScore.score("a".repeat(257), "a") }
    }

    fun clock() {
        ok(VectorClocks.increment(emptyMap(), "a") == mapOf("a" to 1L), "first tick")
        ok(VectorClocks.increment(mapOf("a" to 1L), "b") == mapOf("a" to 1, "b" to 1L), "new node")
        ok(VectorClocks.merge(mapOf("a" to 2L), mapOf("a" to 1, "b" to 3L)) == mapOf("a" to 2, "b" to 3L), "merge max")
        ok(VectorClocks.merge(mapOf("a" to 0L), emptyMap()).isEmpty(), "merge strips zero")
        ok(VectorClocks.compare(mapOf("a" to 1L), mapOf("a" to 2L)) == ClockOrder.BEFORE, "before")
        ok(VectorClocks.compare(mapOf("a" to 2L), mapOf("a" to 1L)) == ClockOrder.AFTER, "after")
        ok(VectorClocks.compare(mapOf("a" to 1L), mapOf("a" to 1L)) == ClockOrder.EQUAL, "equal")
        ok(VectorClocks.compare(mapOf("a" to 1L), mapOf("b" to 1L)) == ClockOrder.CONCURRENT, "concurrent")
        ok(VectorClocks.compare(emptyMap(), emptyMap()) == ClockOrder.EQUAL, "empty equal")
        fails("overflow") { VectorClocks.increment(mapOf("a" to Long.MAX_VALUE), "a") }
        fails("bad node") { VectorClocks.increment(emptyMap(), "a b") }
        fails("negative") { VectorClocks.compare(mapOf("a" to -1), emptyMap()) }
        fails("too many") { VectorClocks.increment((0..63).associate { "n$it" to 1L }, "zz") }
    }

    fun hunks() {
        val rep = LineDiff.compare("a\nb\nc\n", "a\nB\nc\n")
        ok(rep.lines.map { it.change.toString() } == listOf("SAME", "REMOVED", "ADDED", "SAME"), "linediff order")
        val h = DiffHunks.render(rep.lines, 3)
        ok(h.size == 1, "one hunk")
        ok(DiffHunks.format(h[0]) == "@@ -1,3 +1,3 @@\n a\n-b\n+B\n c\n", "replace ctx3")
        val h0 = DiffHunks.render(rep.lines, 0)
        ok(DiffHunks.format(h0[0]) == "@@ -2,1 +2,1 @@\n-b\n+B\n", "replace ctx0")
        val del = LineDiff.compare("a\nb\n", "a\n")
        ok(DiffHunks.format(DiffHunks.render(del.lines, 3)[0]) == "@@ -1,2 +1,1 @@\n a\n-b\n", "deletion")
        val add = LineDiff.compare("b\n", "a\nb\n")
        ok(DiffHunks.format(DiffHunks.render(add.lines, 3)[0]) == "@@ -1,1 +1,2 @@\n+a\n b\n", "addition top")
        ok(DiffHunks.render(LineDiff.compare("same\n", "same\n").lines).isEmpty(), "identical empty")
        fails("context over") { DiffHunks.render(rep.lines, 33) }
        fails("context negative") { DiffHunks.render(rep.lines, -1) }
    }

    fun bmp(fixture: ByteArray) {
        ok(BmpInspect.parse(fixture) == BmpInfo(2, 2, false, 24, 0, 54, 70), "tiny fixture")
        val topDown = fixture.copyOf()
        topDown[22] = 0xFE.toByte()
        topDown[23] = 0xFF.toByte()
        topDown[24] = 0xFF.toByte()
        topDown[25] = 0xFF.toByte()
        ok(BmpInspect.parse(topDown) == BmpInfo(2, 2, true, 24, 0, 54, 70), "top-down variant")
        fun mutate(label: String, at: Int, value: Byte) {
            val bad = fixture.copyOf()
            bad[at] = value
            fails(label) { BmpInspect.parse(bad) }
        }
        mutate("bad magic", 0, 'Z'.code.toByte())
        mutate("size mismatch", 2, 71)
        mutate("dib v4", 14, 108)
        mutate("planes 2", 26, 2)
        mutate("bpp 16", 28, 16)
        mutate("rle", 30, 1)
        mutate("zero width", 18, 0)
        fails("short") { BmpInspect.parse(ByteArray(53)) }
    }

    fun gzip(named: ByteArray, hcrc: ByteArray) {
        ok(GzipInspect.parse(named) == GzipHead(1700000000, 2, 255, "hello.txt", 20), "fname fixture")
        ok(GzipInspect.parse(hcrc) == GzipHead(1700000000, 2, 3, null, 12), "hcrc fixture")
        fails("short") { GzipInspect.parse(ByteArray(9)) }
        fails("bad magic") { GzipInspect.parse(ByteArray(10) { if (it == 0) 0x1E else 0 }) }
        val cm = named.copyOf()
        cm[2] = 9
        fails("cm 9") { GzipInspect.parse(cm) }
        val res = named.copyOf()
        res[3] = (res[3].toInt() or 0xE0).toByte()
        fails("reserved bits") { GzipInspect.parse(res) }
        fails("truncated fextra") { GzipInspect.parse(byteArrayOf(0x1F, 0x8B.toByte(), 8, 0x04, 0, 0, 0, 0, 0, 0, 5)) }
        fails("unterminated fname") { GzipInspect.parse(byteArrayOf(0x1F, 0x8B.toByte(), 8, 0x08, 0, 0, 0, 0, 0, 0, 0x61, 0x62, 0x63)) }
        val crc = hcrc.copyOf()
        crc[10] = (crc[10] + 1).toByte()
        fails("hcrc mismatch") { GzipInspect.parse(crc) }
        fails("no payload") { GzipInspect.parse(named.copyOf(20)) }
    }

    fun totp() {
        val s = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        ok(TotpCode.code(s, 59, 8) == "94287082", "rfc 59")
        ok(TotpCode.code(s, 1111111109, 8) == "07081804", "rfc 1111111109")
        ok(TotpCode.code(s, 1111111111, 8) == "14050471", "rfc 1111111111")
        ok(TotpCode.code(s, 1234567890, 8) == "89005924", "rfc 1234567890")
        ok(TotpCode.code(s, 2000000000, 8) == "69279037", "rfc 2000000000")
        ok(TotpCode.code(s, 20000000000, 8) == "65353130", "rfc 20000000000")
        ok(TotpCode.code(s, 59) == "287082", "6-digit 59")
        ok(TotpCode.code(s, 1111111109) == "081804", "6-digit 1111111109")
        ok(TotpCode.code(s, 1234567890) == "005924", "6-digit 1234567890")
        ok(TotpCode.code(s, 2000000000, 7) == "9279037", "7-digit")
        ok(TotpCode.verify(s, "94287082", 59 + 30, 8, 1), "window in")
        ok(!TotpCode.verify(s, "94287082", 59 + 90, 8, 1), "window out")
        ok(!TotpCode.verify(s, "94287083", 59, 8, 1), "wrong code")
        fails("lowercase") { TotpCode.code("gezdgnbvgy3tqojqgezdgnbvgy3tqojq", 59) }
        fails("bad length") { TotpCode.code("ABC", 59) }
        fails("short key") { TotpCode.code("JBSWY3DP", 59) }
        fails("negative instant") { TotpCode.code(s, -1) }
        fails("bad digits") { TotpCode.code(s, 59, 5) }
        fails("window over") { TotpCode.verify(s, "287082", 59, 6, 11) }
        fails("code shape") { TotpCode.verify(s, "28708", 59) }
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.lens.EvShift
import dev.fusion.vault.XorSplit
import dev.fusion.documents.WordDiff
import dev.fusion.filesui.DupGroup
import dev.fusion.index.SnippetCut
import dev.fusion.media.Id3Head
import dev.fusion.maps.TileCover
import dev.fusion.maps.TileMath
import dev.fusion.continuity.VCardParse
import dev.fusion.privacy.FieldMask
import dev.fusion.editor.IndentDetect
import java.io.File

/** R35 host consumer: behavioral contracts over the 10 R35 kernels. Host JVM only. */
object R35Checks {
    var n = 0
    fun ok(c: Boolean, label: String) {
        n++
        if (!c) throw AssertionError("r35 check $n FAILED: $label")
    }
    fun fails(label: String, block: () -> Unit) {
        n++
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("r35 check $n FAILED (no refusal): $label")
    }
    fun close(a: Double, b: Double, eps: Double, label: String) {
        ok(kotlin.math.abs(a - b) <= eps, "$label ($a vs $b)")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val fx = File(args[0])
        ev(); xor(); words(); dups(); snips()
        id3(File(fx, "id3.bin").readBytes())
        tiles(); vcard(); fields(); indent()
        println("R35 PASS: $n production-kernel assertions across 10 kernels. " +
            "Host JVM only; not Android/device evidence.")
    }

    fun ev() {
        ok(EvShift.ev(1.0, 1.0) == 0.0, "ev unity")
        ok(EvShift.ev(2.0, 0.5) == 3.0, "ev exact")
        close(EvShift.ev(2.8, 0.01), 9.614709844115207, 1e-12, "ev f2.8")
        close(EvShift.ev(4.0, 1.0 / 60.0), 9.906890595608518, 1e-12, "ev f4")
        ok(EvShift.shutterFor(2.0, 3.0) == 0.5, "shutter exact")
        ok(EvShift.apertureFor(0.5, 3.0) == 2.0, "aperture exact")
        ok(EvShift.shift(2.0, 0.5, 4.0) == 2.0, "shift exact")
        ok(EvShift.ev(4.0, EvShift.shift(2.0, 0.5, 4.0)) == 3.0, "shift roundtrip")
        fails("zero aperture") { EvShift.ev(0.0, 1.0) }
        fails("nan aperture") { EvShift.ev(Double.NaN, 1.0) }
        fails("zero shutter") { EvShift.ev(1.0, 0.0) }
        fails("neg shutter") { EvShift.ev(1.0, -1.0) }
        fails("inf shutter") { EvShift.ev(1.0, Double.POSITIVE_INFINITY) }
        fails("nan ev") { EvShift.shutterFor(2.0, Double.NaN) }
    }

    fun xor() {
        val secret = byteArrayOf(1, 2, 3, 4, 5)
        val mask = byteArrayOf(9, 8, 7, 6, 5)
        val (a, b) = XorSplit.split(secret, mask)
        ok(a.contentEquals(mask), "share is mask")
        ok(b.contentEquals(byteArrayOf(8, 10, 4, 2, 0)), "blinded pins")
        ok(XorSplit.combine(a, b).contentEquals(secret), "roundtrip")
        ok(XorSplit.combine(b, a).contentEquals(secret), "commutative")
        val bad = b.copyOf()
        bad[0] = (bad[0] + 1).toByte()
        ok(!XorSplit.combine(a, bad).contentEquals(secret), "tamper")
        val (s1, s2) = XorSplit.split(byteArrayOf(0xFF.toByte()), byteArrayOf(0x0F))
        ok(s2.contentEquals(byteArrayOf(0xF0.toByte())), "single byte")
        ok(XorSplit.combine(s1, s2).contentEquals(byteArrayOf(0xFF.toByte())), "single roundtrip")
        val big = ByteArray(65536) { 1 }
        ok(XorSplit.combine(XorSplit.split(big, big).first, XorSplit.split(big, big).second).contentEquals(big), "cap ok")
        fails("empty secret") { XorSplit.split(ByteArray(0), ByteArray(0)) }
        fails("over cap") { XorSplit.split(ByteArray(65537), ByteArray(65537)) }
        fails("mask mismatch") { XorSplit.split(secret, byteArrayOf(1)) }
        fails("empty share") { XorSplit.combine(ByteArray(0), ByteArray(0)) }
        fails("share mismatch") { XorSplit.combine(a, byteArrayOf(1)) }
    }

    fun words() {
        val same = WordDiff.compare(listOf("a", "b"), listOf("a", "b"))
        ok(same.ops.all { it.op == WordDiff.Op.SAME } && same.ops.size == 2, "identical")
        ok(same.added == 0 && same.removed == 0, "identical counts")
        val rep = WordDiff.compare(listOf("a", "b", "c"), listOf("a", "B", "c"))
        ok(rep.ops.map { it.op } == listOf(WordDiff.Op.SAME, WordDiff.Op.REMOVED, WordDiff.Op.ADDED, WordDiff.Op.SAME), "replace seq")
        ok(rep.added == 1 && rep.removed == 1, "replace counts")
        ok(rep.ops[1].word == "b" && rep.ops[2].word == "B", "replace words")
        val ins = WordDiff.compare(emptyList(), listOf("x", "y"))
        ok(ins.ops.size == 2 && ins.added == 2 && ins.removed == 0, "pure insert")
        val del = WordDiff.compare(listOf("x", "y"), emptyList())
        ok(del.ops.size == 2 && del.added == 0 && del.removed == 2, "pure delete")
        val empty = WordDiff.compare(emptyList(), emptyList())
        ok(empty.ops.isEmpty(), "both empty")
        val tie = WordDiff.compare(listOf("x"), listOf("y"))
        ok(tie.ops.map { it.op } == listOf(WordDiff.Op.REMOVED, WordDiff.Op.ADDED), "tie prefers remove")
        fails("word budget") { WordDiff.compare(List(2049) { "w" }, emptyList()) }
        fails("matrix budget") { WordDiff.compare(List(1500) { "a" }, List(1500) { "b" }) }
    }

    fun dups() {
        val d1 = "a".repeat(64)
        val d2 = "b".repeat(64)
        val recs = listOf(
            DupGroup.Rec("f1", 10, d1), DupGroup.Rec("f2", 10, d1),
            DupGroup.Rec("g1", 20, d2), DupGroup.Rec("g2", 20, d2), DupGroup.Rec("g3", 20, d2),
            DupGroup.Rec("solo", 10, d1.dropLast(1) + "c"),
            DupGroup.Rec("samesize", 10, "c".repeat(64)),
        )
        val g = DupGroup.group(recs)
        ok(g.size == 2, "two groups")
        ok(g[0].ids == listOf("f1", "f2") && g[0].bytes == 10L, "group a")
        ok(g[1].ids == listOf("g1", "g2", "g3"), "group b triple")
        ok(g.none { it.ids.contains("solo") || it.ids.contains("samesize") }, "uniques excluded")
        ok(g[0].digest < g[1].digest, "digest order")
        ok(DupGroup.group(emptyList()).isEmpty(), "empty in")
        fails("blank id") { DupGroup.group(listOf(DupGroup.Rec(" ", 1, d1))) }
        fails("neg size") { DupGroup.group(listOf(DupGroup.Rec("x", -1, d1))) }
        fails("short digest") { DupGroup.group(listOf(DupGroup.Rec("x", 1, "abc"))) }
        fails("upper digest") { DupGroup.group(listOf(DupGroup.Rec("x", 1, "A".repeat(64)))) }
        fails("dup id") { DupGroup.group(listOf(DupGroup.Rec("x", 1, d1), DupGroup.Rec("x", 1, d1))) }
        fails("over cap") { DupGroup.group(List(100001) { DupGroup.Rec("f$it", 1, d1) }) }
    }

    fun snips() {
        val text = "alpha beta gamma delta epsilon zeta eta theta"
        val at = text.indexOf("gamma")
        val s = SnippetCut.window(text, at, at + 5, 6)
        ok(s.text == "… beta gamma delta…", "middle text")
        ok(s.lo == 7 && s.hi == 12, "middle offsets")
        ok(s.text.substring(s.lo, s.hi) == "gamma", "offset verify")
        val head = SnippetCut.window(text, 0, 5, 40)
        ok(head.text == text && head.lo == 0 && head.hi == 5, "head fit")
        val tail = SnippetCut.window(text, text.length - 5, text.length, 3)
        ok(tail.text == "…ta theta" && tail.lo == 4 && tail.hi == 9, "tail fit")
        val all = SnippetCut.window("tiny hit here", 5, 8, 40)
        ok(all.text == "tiny hit here" && all.lo == 5, "whole")
        val zero = SnippetCut.window(text, at, at + 5, 0)
        ok(zero.text == "…gamma…" && zero.lo == 1 && zero.hi == 6, "radius zero")
        fails("empty text") { SnippetCut.window("", 0, 1, 5) }
        fails("oob start") { SnippetCut.window(text, -1, 3, 5) }
        fails("inverted") { SnippetCut.window(text, 5, 5, 5) }
        fails("end over") { SnippetCut.window(text, 0, text.length + 1, 5) }
        fails("big radius") { SnippetCut.window(text, 0, 1, 1001) }
        fails("neg radius") { SnippetCut.window(text, 0, 1, -1) }
    }

    fun id3(bytes: ByteArray) {
        val h = Id3Head.parse(bytes)
        ok(h.major == 4 && h.minor == 0, "fixture version")
        ok(!h.unsync && !h.extHeader && !h.experimental && !h.footer, "fixture flags")
        ok(h.tagSize == 100, "fixture size")
        ok(h.headerLength == 10, "fixture hdrlen")
        val v23 = Id3Head.parse(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0xC0.toByte(), 0, 0, 0, 0))
        ok(v23.unsync && v23.extHeader && !v23.experimental && !v23.footer, "v23 flags")
        val v22 = Id3Head.parse(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 2, 0, 0, 0, 0, 2, 1))
        ok(v22.tagSize == 257 && !v22.unsync, "v22 syncsafe")
        val foot = Id3Head.parse(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0x10, 0, 0, 0, 1))
        ok(foot.footer && foot.tagSize == 1, "v24 footer")
        fails("short") { Id3Head.parse(ByteArray(9)) }
        fails("magic") { Id3Head.parse(ByteArray(10) { 1 }) }
        fails("major 5") { Id3Head.parse(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 5, 0, 0, 0, 0, 0, 0)) }
        fails("major 1") { Id3Head.parse(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 1, 0, 0, 0, 0, 0, 0)) }
        fails("v22 flags") { Id3Head.parse(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 2, 0, 0x80.toByte(), 0, 0, 0, 0)) }
        fails("v23 reserved") { Id3Head.parse(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0x10, 0, 0, 0, 0)) }
        fails("v24 reserved") { Id3Head.parse(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0x08, 0, 0, 0, 0)) }
        fails("bad syncsafe") { Id3Head.parse(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0x80.toByte(), 0, 0, 0)) }
    }

    fun tiles() {
        val world = TileCover.cover(-180.0, -85.05112878, 180.0, 85.05112878, 0)
        ok(world == listOf(TileMath.Tile(0, 0, 0)), "z0 world")
        val two = TileCover.cover(0.0, 0.0, 0.1, 0.1, 1)
        ok(two.size == 2, "z1 pair size")
        ok(two == listOf(TileMath.Tile(1, 0, 1), TileMath.Tile(1, 1, 1)), "z1 row-major")
        val city = TileCover.cover(0.0, 0.0, 0.01, 0.01, 10)
        ok(city.size == 2, "z10 pair")
        val point = TileCover.cover(10.0, 10.0, 10.0, 10.0, 5)
        ok(point.size == 1, "point box")
        val clamp = TileCover.cover(0.0, 80.0, 1.0, 90.0, 2)
        ok(clamp.isNotEmpty(), "lat clamp")
        fails("lon wrap") { TileCover.cover(10.0, 0.0, -10.0, 1.0, 2) }
        fails("lat invert") { TileCover.cover(0.0, 5.0, 1.0, 4.0, 2) }
        fails("lon oob") { TileCover.cover(-181.0, 0.0, 1.0, 1.0, 2) }
        fails("zoom high") { TileCover.cover(0.0, 0.0, 1.0, 1.0, 20) }
        fails("over cap") { TileCover.cover(-180.0, -85.0, 180.0, 85.0, 10) }
    }

    fun vcard() {
        val full = "BEGIN:VCARD\r\nVERSION:4.0\r\nFN:Jane Doe\r\nN:Doe;Jane;;;\r\n" +
            "TEL;TYPE=work,voice:+1-555-0100\r\nTEL;TYPE=home:+1-555-0101\r\n" +
            "EMAIL;TYPE=work:jane@example.com\r\nEND:VCARD"
        val c = VCardParse.parse(full)
        ok(c.version == "4.0", "version")
        ok(c.fullName == "Jane Doe", "fn")
        ok(c.family == "Doe" && c.given == "Jane", "name parts")
        ok(c.phones.size == 2, "two tels")
        ok(c.phones[0] == VCardParse.Field(listOf("WORK", "VOICE"), "+1-555-0100"), "tel types")
        ok(c.emails == listOf(VCardParse.Field(listOf("WORK"), "jane@example.com")), "email")
        val min = VCardParse.parse("BEGIN:VCARD\nVERSION:3.0\nFN:Bob\nEND:VCARD")
        ok(min.family == null && min.phones.isEmpty() && min.emails.isEmpty(), "minimal")
        val folded = VCardParse.parse("BEGIN:VCARD\nVERSION:4.0\nFN:Long \n Name\nEND:VCARD")
        ok(folded.fullName == "Long Name", "folding")
        val esc = VCardParse.parse("BEGIN:VCARD\nVERSION:4.0\nFN:a\\,b\\nC\nEND:VCARD")
        ok(esc.fullName == "a,b\nC", "escapes")
        fails("no begin") { VCardParse.parse("VERSION:4.0\nFN:x\nEND:VCARD") }
        fails("no end") { VCardParse.parse("BEGIN:VCARD\nVERSION:4.0\nFN:x") }
        fails("missing version") { VCardParse.parse("BEGIN:VCARD\nFN:x\nEND:VCARD") }
        fails("missing fn") { VCardParse.parse("BEGIN:VCARD\nVERSION:4.0\nEND:VCARD") }
        fails("bad version") { VCardParse.parse("BEGIN:VCARD\nVERSION:2.1\nFN:x\nEND:VCARD") }
        fails("unknown prop") { VCardParse.parse("BEGIN:VCARD\nVERSION:4.0\nFN:x\nORG:y\nEND:VCARD") }
        fails("unknown param") { VCardParse.parse("BEGIN:VCARD\nVERSION:4.0\nFN:x\nTEL;PREF=1:1\nEND:VCARD") }
        fails("dup type") { VCardParse.parse("BEGIN:VCARD\nVERSION:4.0\nFN:x\nTEL;TYPE=work,work:1\nEND:VCARD") }
        fails("empty tel") { VCardParse.parse("BEGIN:VCARD\nVERSION:4.0\nFN:x\nTEL:\nEND:VCARD") }
    }

    fun fields() {
        val row = linkedMapOf("a" to "1", "b" to "2", "c" to "3", "d" to "4")
        ok(FieldMask.keep(row, setOf("b", "d")) == mapOf("b" to "2", "d" to "4"), "keep basic")
        ok(FieldMask.keep(row, setOf("b", "d")).keys.toList() == listOf("b", "d"), "keep order")
        ok(FieldMask.keep(row, setOf("zzz")) == emptyMap<String, String>(), "keep missing")
        ok(FieldMask.keep(row, emptySet()) == emptyMap<String, String>(), "keep empty")
        ok(FieldMask.drop(row, setOf("b")) == mapOf("a" to "1", "c" to "3", "d" to "4"), "drop basic")
        ok(FieldMask.drop(row, emptySet()) == row, "drop empty")
        ok(FieldMask.drop(row, setOf("a", "b", "c", "d")) == emptyMap<String, String>(), "drop all")
        fails("blank keep") { FieldMask.keep(row, setOf("")) }
        fails("blank drop") { FieldMask.drop(row, setOf("a", "")) }
        fails("row cap") { FieldMask.keep((1..1025).associate { "k$it" to "v" }, setOf("k1")) }
        fails("key cap") { FieldMask.keep(row, (1..257).map { "k$it" }.toSet()) }
    }

    fun indent() {
        ok(IndentDetect.detect(listOf("x", "\ty", "\t\tz")) == IndentDetect.Style.Tabs, "tabs")
        ok(IndentDetect.detect(listOf("x", "    y", "        z")) == IndentDetect.Style.Spaces(4), "spaces4")
        ok(IndentDetect.detect(listOf("  a", "    b", "      c")) == IndentDetect.Style.Spaces(2), "gcd width")
        ok(IndentDetect.detect(listOf("\ta", "    b")) == null, "mixed")
        ok(IndentDetect.detect(listOf("a", "b")) == null, "no indent")
        ok(IndentDetect.detect(listOf("", "   ", "    x")) == IndentDetect.Style.Spaces(4), "blanks abstain")
        ok(IndentDetect.detect(listOf("    only")) == IndentDetect.Style.Spaces(4), "single run")
        ok(IndentDetect.detect(listOf("x".padStart(41))) == null, "noise width")
        val t = IndentDetect.analyze(listOf("\ta", "  b", "    c", "plain", ""))
        ok(t.tabs == 1 && t.spaces == 2, "tally counts")
        ok(t.width == 2, "tally width")
        ok(IndentDetect.detect(emptyList()) == null, "empty")
    }
}

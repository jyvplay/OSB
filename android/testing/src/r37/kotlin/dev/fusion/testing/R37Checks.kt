// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.airplay.RtspReason
import dev.fusion.archive.XzHead
import dev.fusion.commands.ExitCode
import dev.fusion.continuity.TaskTally
import dev.fusion.continuity.VTodoParse
import dev.fusion.editor.PasteWrap
import dev.fusion.filesui.SizeBucket
import dev.fusion.find.RankFuse
import dev.fusion.lens.FovMath
import dev.fusion.maps.PolyLine
import dev.fusion.media.Mp4Box
import dev.fusion.notes.SlugHead
import dev.fusion.privacy.UtmStrip
import dev.fusion.security.HibpRange
import dev.fusion.terminal.OscLink
import java.io.File

/**
 * R37 host consumer: behavioral contracts over the 14 R37 kernels.
 *
 * Host JVM only. Nothing here is Android, device, service, or
 * acceptance evidence; the kernels are pure logic and this consumer is
 * the only thing that executes them.
 */
object R37Checks {
    var n = 0
    fun ok(c: Boolean, label: String) {
        n++
        if (!c) throw AssertionError("r37 check $n FAILED: $label")
    }
    fun fails(label: String, block: () -> Unit) {
        n++
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("r37 check $n FAILED (no refusal): $label")
    }
    fun close(a: Double, b: Double, eps: Double, label: String) {
        ok(kotlin.math.abs(a - b) <= eps, "$label ($a vs $b)")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val fx = File(args[0])
        slugs(); sizes(); tally(); xz(File(fx, "xz.bin").readBytes()); paste()
        rtsp(); utm(); hibp(); poly(); mp4(File(fx, "mp4.bin").readBytes())
        osc(); fuse(); exit(); fov()
        println("R37 PASS: $n production-kernel assertions across 14 kernels. " +
            "Host JVM only; not Android/device evidence.")
    }

    // ---- SlugHead ----------------------------------------------------

    fun slugs() {
        ok(SlugHead.slug("Hello World") == "hello-world", "basic")
        ok(SlugHead.slug("Hello, World!") == "hello-world", "punctuation dropped")
        ok(SlugHead.slug("A  B") == "a--b", "spaces not collapsed")
        ok(SlugHead.slug("  Trim  Me  ") == "trim--me", "outer trim")
        ok(SlugHead.slug("snake_case-2") == "snake_case-2", "kept chars")
        ok(SlugHead.slug("Tabs\tHere") == "tabs-here", "tab becomes hyphen")
        ok(SlugHead.slug("UPPER") == "upper", "lowercased")
        ok(SlugHead.slug("Café") == "caf", "accent dropped, letters stay")
        ok(SlugHead.unique(emptySet(), "Hi There") == "hi-there", "unique free")
        ok(SlugHead.unique(setOf("hi"), "Hi") == "hi-1", "unique first clash")
        ok(SlugHead.unique(setOf("hi", "hi-1"), "Hi") == "hi-2", "unique second clash")
        ok(SlugHead.unique(setOf("hi", "hi-2"), "Hi") == "hi-1", "unique gap fill")
        ok(SlugHead.unique(setOf("hi-1"), "Hi-1") == "hi-1-1", "suffix is part of base")
        fails("blank heading") { SlugHead.slug("   ") }
        fails("empty heading") { SlugHead.slug("") }
        fails("over-long heading") { SlugHead.slug("a".repeat(SlugHead.MAX_HEADING + 1)) }
        fails("no slug characters") { SlugHead.slug("日本語") }
        fails("emoji only") { SlugHead.slug("🙂🙂") }
    }

    // ---- SizeBucket --------------------------------------------------

    fun sizes() {
        ok(SizeBucket.scale(0).unit == "B", "zero unit")
        ok(SizeBucket.scale(0).value == 0.0, "zero value")
        ok(SizeBucket.scale(1023).unit == "B", "below KiB")
        ok(SizeBucket.scale(1023).value == 1023.0, "below KiB value")
        ok(SizeBucket.scale(1024).unit == "KiB", "KiB boundary")
        ok(SizeBucket.scale(1024).value == 1.0, "KiB boundary value")
        ok(SizeBucket.scale(1536).value == 1.5, "1.5 KiB value")
        ok(SizeBucket.scale(1048575).unit == "KiB", "just below MiB")
        ok(SizeBucket.scale(1048576).unit == "MiB", "MiB boundary")
        ok(SizeBucket.scale(1073741824).unit == "GiB", "GiB boundary")
        ok(SizeBucket.scale(1099511627776).unit == "TiB", "TiB boundary")
        ok(SizeBucket.scale(1125899906842624).unit == "PiB", "PiB boundary")
        ok(SizeBucket.format(0) == "0 B", "format zero")
        ok(SizeBucket.format(512) == "512 B", "format bytes")
        ok(SizeBucket.format(1024) == "1 KiB", "format whole unit")
        ok(SizeBucket.format(1536) == "1.5 KiB", "format half unit")
        ok(SizeBucket.format(1572864) == "1.5 MiB", "format MiB")
        ok(SizeBucket.format(1100) == "1.1 KiB", "format one decimal")
        ok(SizeBucket.format(1048575) == "1024 KiB", "rounds up at unit edge")
        ok(SizeBucket.format(Long.MAX_VALUE) == "8192 PiB", "saturates at PiB")
        fails("negative scale") { SizeBucket.scale(-1) }
        fails("negative format") { SizeBucket.format(-1) }
    }

    // ---- TaskTally ---------------------------------------------------

    private fun todo(status: VTodoParse.Status?) = VTodoParse.Todo(
        uid = "u", summary = "s", status = status, due = null, priority = null, percent = null,
    )

    fun tally() {
        val mixed = TaskTally.tally(
            listOf(
                todo(VTodoParse.Status.NEEDS_ACTION),
                todo(VTodoParse.Status.NEEDS_ACTION),
                todo(VTodoParse.Status.IN_PROGRESS),
                todo(VTodoParse.Status.COMPLETED),
                todo(VTodoParse.Status.CANCELLED),
                todo(null),
            ),
        )
        ok(mixed.needsAction == 2, "needs action")
        ok(mixed.inProgress == 1, "in progress")
        ok(mixed.completed == 1, "completed")
        ok(mixed.cancelled == 1, "cancelled")
        ok(mixed.unspecified == 1, "unspecified")
        ok(mixed.total == 6, "total")
        val empty = TaskTally.tally(emptyList())
        ok(empty.total == 0, "empty total")
        ok(empty.needsAction + empty.inProgress + empty.completed + empty.cancelled +
            empty.unspecified == 0, "empty buckets")
        val allNull = TaskTally.tally(List(3) { todo(null) })
        ok(allNull.unspecified == 3, "null status is not needs-action")
        ok(allNull.needsAction == 0, "null status bucket")
        val one = TaskTally.tally(listOf(todo(VTodoParse.Status.COMPLETED)))
        ok(one.total == 1 && one.completed == 1, "single completed")
        fails("over cap") { TaskTally.tally(List(TaskTally.MAX_TODOS + 1) { todo(null) }) }
        // The wire token is RFC 5545's IN-PROCESS; the repaired parser must accept it.
        val parsed = VTodoParse.parse("BEGIN:VTODO\nUID:a\nSUMMARY:b\nSTATUS:IN-PROCESS\nEND:VTODO")
        ok(parsed.status == VTodoParse.Status.IN_PROGRESS, "RFC 5545 wire token")
        fails("legacy IN-PROGRESS refused") {
            VTodoParse.parse("BEGIN:VTODO\nUID:a\nSUMMARY:b\nSTATUS:IN-PROGRESS\nEND:VTODO")
        }
    }

    // ---- XzHead ------------------------------------------------------

    /** Builds a 12-byte XZ stream header with an explicit little-endian CRC. */
    private fun xzBytes(flag0: Int, flag1: Int, crc: Long): ByteArray {
        val b = ByteArray(12)
        val magic = intArrayOf(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00)
        for (i in magic.indices) b[i] = magic[i].toByte()
        b[6] = flag0.toByte()
        b[7] = flag1.toByte()
        for (i in 0..3) b[8 + i] = ((crc shr (8 * i)) and 0xFF).toByte()
        return b
    }

    fun xz(fixture: ByteArray) {
        ok(XzHead.HEADER_LEN == 12, "header length")
        ok(XzHead.parse(fixture).check == XzHead.Check.CRC32, "fixture check")
        ok(XzHead.parse(xzBytes(0, 0, 0x41D912FFL)).check == XzHead.Check.NONE, "none check")
        ok(XzHead.parse(xzBytes(0, 4, 0x46B4D6E6L)).check == XzHead.Check.CRC64, "crc64 check")
        ok(XzHead.parse(xzBytes(0, 10, 0xA10CFBE1L)).check == XzHead.Check.SHA256, "sha256 id 0x0A")
        ok(XzHead.Check.SHA256.id == 10, "sha256 assigned id")
        ok(XzHead.Check.CRC64.id == 4, "crc64 assigned id")
        ok(XzHead.parse(fixture + byteArrayOf(0, 0, 0, 0)).check == XzHead.Check.CRC32, "trailing bytes")
        fails("short header") { XzHead.parse(fixture.copyOf(11)) }
        fails("bad magic") { XzHead.parse(fixture.copyOf().also { it[0] = 0x00 }) }
        fails("nonzero flags byte 0") { XzHead.parse(xzBytes(1, 1, 0x36DE2269L)) }
        fails("reserved id 2") { XzHead.parse(xzBytes(0, 2, 0x36DE2269L)) }
        fails("reserved id 3") { XzHead.parse(xzBytes(0, 3, 0x36DE2269L)) }
        fails("spec-reserved id 7") { XzHead.parse(xzBytes(0, 7, 0x36DE2269L)) }
        fails("reserved id 0x0B") { XzHead.parse(xzBytes(0, 11, 0x36DE2269L)) }
        fails("reserved high nibble") { XzHead.parse(xzBytes(0, 0x14, 0x36DE2269L)) }
        fails("crc mismatch") { XzHead.parse(fixture.copyOf().also { it[8] = 0x00 }) }
    }

    // ---- PasteWrap ---------------------------------------------------

    fun paste() {
        ok(PasteWrap.reindent(listOf("a"), "  ", 1) == listOf("  a"), "one level")
        ok(PasteWrap.reindent(listOf("  b"), "  ", 1) == listOf("    b"), "stacked")
        ok(PasteWrap.reindent(listOf("a", "\tb"), "\t", 0) == listOf("a", "\tb"), "tabs")
        ok(PasteWrap.reindent(listOf("a"), "\t", 1) == listOf("\ta"), "tab at depth")
        ok(PasteWrap.reindent(listOf("\tx"), "\t", 1) == listOf("\t\tx"), "tab unit at depth")
        ok(PasteWrap.reindent(listOf("a"), "   ", 2) == listOf("      a"), "three-space unit")
        ok(PasteWrap.reindent(listOf("  ", ""), "  ", 2) == listOf("", ""), "blank collapse")
        ok(PasteWrap.reindent(emptyList(), "  ", 0).isEmpty(), "empty list")
        ok(PasteWrap.reindent(listOf("a"), "  ", 0) == listOf("a"), "depth zero identity")
        ok(PasteWrap.reindent(listOf("a"), "  ", PasteWrap.MAX_DEPTH)[0] ==
            "  ".repeat(PasteWrap.MAX_DEPTH) + "a", "max depth")
        fails("empty unit") { PasteWrap.reindent(listOf("a"), "", 0) }
        fails("nine-space unit") { PasteWrap.reindent(listOf("a"), " ".repeat(9), 0) }
        fails("tab+space unit") { PasteWrap.reindent(listOf("a"), " \t", 0) }
        fails("negative depth") { PasteWrap.reindent(listOf("a"), "  ", -1) }
        fails("depth over cap") { PasteWrap.reindent(listOf("a"), "  ", PasteWrap.MAX_DEPTH + 1) }
        fails("ragged indent") { PasteWrap.reindent(listOf(" a"), "  ", 0) }
        fails("mixed indent") { PasteWrap.reindent(listOf("  \ta"), "  ", 0) }
        fails("depth over cap via indent") {
            PasteWrap.reindent(listOf("  a"), "  ", PasteWrap.MAX_DEPTH)
        }
        fails("embedded newline") { PasteWrap.reindent(listOf("a\nb"), "  ", 0) }
        fails("over cap") { PasteWrap.reindent(List(PasteWrap.MAX_LINES + 1) { "a" }, "  ", 0) }
    }

    // ---- RtspReason --------------------------------------------------

    fun rtsp() {
        ok(RtspReason.parse("RTSP/1.0 200 OK") == RtspReason.Status(200, "OK"), "parse ok")
        ok(RtspReason.parse("RTSP/1.0 404 Not Found") ==
            RtspReason.Status(404, "Not Found"), "parse not found")
        ok(RtspReason.parse("RTSP/1.0 454 Session Not Found").reason == "Session Not Found",
            "reason keeps spaces")
        ok(RtspReason.parse("RTSP/1.0 250 Low on Storage Space").code == 250, "parse 250")
        ok(RtspReason.CODES.size == 44, "table size")
        ok(304 in RtspReason.CODES, "304 present")
        ok(306 !in RtspReason.CODES, "306 absent")
        ok(RtspReason.phrase(100) == "Continue", "100")
        ok(RtspReason.phrase(250) == "Low on Storage Space", "250 phrase")
        ok(RtspReason.phrase(304) == "Not Modified", "304 phrase")
        ok(RtspReason.phrase(408) == "Request Time-out", "408 grammar spelling")
        ok(RtspReason.phrase(414) == "Request-URI Too Large", "414 grammar spelling")
        ok(RtspReason.phrase(451) == "Parameter Not Understood", "451 grammar spelling")
        ok(RtspReason.phrase(456) == "Header Field Not Valid for Resource", "456 grammar spelling")
        ok(RtspReason.phrase(459) == "Aggregate operation not allowed", "459 verbatim")
        ok(RtspReason.phrase(462) == "Destination unreachable", "462 verbatim")
        ok(RtspReason.phrase(504) == "Gateway Time-out", "504 grammar spelling")
        ok(RtspReason.phrase(505) == "RTSP Version not supported", "505 grammar spelling")
        ok(RtspReason.phrase(551) == "Option not supported", "551 grammar spelling")
        fails("unknown 199") { RtspReason.phrase(199) }
        fails("unknown 306") { RtspReason.phrase(306) }
        fails("unknown 600") { RtspReason.phrase(600) }
        fails("http version") { RtspReason.parse("HTTP/1.1 200 OK") }
        fails("rtsp 2.0") { RtspReason.parse("RTSP/2.0 200 OK") }
        fails("two-digit code") { RtspReason.parse("RTSP/1.0 20 OK") }
        fails("missing reason") { RtspReason.parse("RTSP/1.0 200") }
        fails("empty reason") { RtspReason.parse("RTSP/1.0 200 ") }
        fails("non-digit code") { RtspReason.parse("RTSP/1.0 abc OK") }
        fails("code out of range") { RtspReason.parse("RTSP/1.0 700 OK") }
        fails("reason newline") { RtspReason.parse("RTSP/1.0 200 OK\nFAIL") }
        fails("line over cap") { RtspReason.parse("RTSP/1.0 200 " + "x".repeat(520)) }
    }

    // ---- UtmStrip ----------------------------------------------------

    fun utm() {
        ok(UtmStrip.clean("https://a.com/p?utm_source=x&id=7#f") == "https://a.com/p?id=7#f",
            "drop and keep")
        ok(UtmStrip.clean("https://a.com/p?utm_source=x") == "https://a.com/p", "empty query")
        ok(UtmStrip.clean("https://a.com/p?id=7") == "https://a.com/p?id=7", "no tracker")
        ok(UtmStrip.clean("https://a.com/p") == "https://a.com/p", "no query")
        ok(UtmStrip.clean("https://a.com/p?UTM_Source=x&a=1") == "https://a.com/p?a=1",
            "case insensitive")
        ok(UtmStrip.clean("https://a.com/p?fbclid=abc&gclid=d&q=1") == "https://a.com/p?q=1",
            "click ids")
        ok(UtmStrip.clean("https://a.com/p?utm_medium=m#frag") == "https://a.com/p#frag",
            "fragment kept")
        ok(UtmStrip.clean("https://a.com/p?b=2&utm_term=t&a=1") == "https://a.com/p?b=2&a=1",
            "order kept")
        ok(UtmStrip.clean("https://a.com/p?&a=1") == "https://a.com/p?a=1", "empty name dropped")
        ok(UtmStrip.clean("https://a.com/p?utm_id") == "https://a.com/p", "valueless tracker")
        ok(UtmStrip.clean("https://a.com/p?q") == "https://a.com/p?q", "valueless kept")
        ok(UtmStrip.clean("https://a.com/p?mc_cid=1&mc_eid=2") == "https://a.com/p",
            "mailchimp pair")
        ok(UtmStrip.clean("https://a.com/p?_openstat=z") == "https://a.com/p", "openstat")
        ok(UtmStrip.clean("https://a.com/p?utm_source=x#a?b=1") == "https://a.com/p#a?b=1",
            "query inside fragment untouched")
        ok(UtmStrip.TRACKERS.size == 18, "tracker set size")
        ok("utmk" !in UtmStrip.TRACKERS, "unknown names not claimed")
        fails("blank url") { UtmStrip.clean("   ") }
        fails("over cap") { UtmStrip.clean("https://a.com/?q=" + "x".repeat(UtmStrip.MAX_URL)) }
        fails("newline url") { UtmStrip.clean("https://a.com/p?q=1\nx") }
    }

    // ---- HibpRange ---------------------------------------------------

    private val SHA = "ABCDE12345ABCDE12345ABCDE12345ABCDE12345"

    fun hibp() {
        val body = "0018A45C4D1DEF81644B54AB7F969B88D65:3\n" +
            "12345ABCDE12345ABCDE12345ABCDE12345:7\n" +
            "FFFFFABCDE12345ABCDE12345ABCDE12345:1\n"
        ok(HibpRange.lookup(SHA, body) == 7L, "suffix hit")
        ok(HibpRange.lookup(SHA, body.replace("\n", "\r\n")) == 7L, "crlf body")
        ok(HibpRange.lookup(SHA, "") == 0L, "empty body")
        ok(HibpRange.lookup("0000000000000000000000000000000000000000", body) == 0L, "absent suffix")
        ok(HibpRange.lookup(SHA.lowercase(), body) == 7L, "lowercase sha accepted")
        ok(HibpRange.lookup(SHA, "\n\n" + body) == 7L, "blank lines skipped")
        ok(HibpRange.lookup(SHA, "0018A45C4D1DEF81644B54AB7F969B88D65:0\n") == 0L, "zero count")
        ok(HibpRange.lookup(SHA, "DEADBEEFDEADBEEFDEADBEEFDEADBEEFDEA:4294967295\n") == 0L,
            "large count parsed, other suffix")
        fails("short sha") { HibpRange.lookup("ABCDE", body) }
        fails("non-hex sha") { HibpRange.lookup("ZZZZZ12345ABCDE12345ABCDE12345ABCDE12345", body) }
        fails("bad suffix length") { HibpRange.lookup(SHA, "12345:7\n") }
        fails("missing colon") { HibpRange.lookup(SHA, "12345ABCDE12345ABCDE12345ABCDE12345\n") }
        fails("non-numeric count") { HibpRange.lookup(SHA, "12345ABCDE12345ABCDE12345ABCDE12345:x\n") }
        fails("negative count") { HibpRange.lookup(SHA, "12345ABCDE12345ABCDE12345ABCDE12345:-1\n") }
        fails("lowercase suffix in body") {
            HibpRange.lookup(SHA, "12345abcde12345abcde12345abcde12345:1\n")
        }
        fails("body over cap") { HibpRange.lookup(SHA, "x".repeat(HibpRange.MAX_BODY + 1)) }
    }

    // ---- PolyLine ----------------------------------------------------

    private val CANON = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    fun poly() {
        val pts = PolyLine.decode(CANON)
        ok(pts.size == 3, "canonical size")
        ok(pts[0] == PolyLine.Pt(38.5, -120.2), "canonical p0")
        ok(pts[1] == PolyLine.Pt(40.7, -120.95), "canonical p1")
        ok(pts[2] == PolyLine.Pt(43.252, -126.453), "canonical p2")
        ok(PolyLine.decode("??") == listOf(PolyLine.Pt(0.0, 0.0)), "origin pair")
        ok(PolyLine.decode("").isEmpty(), "empty decode")
        ok(PolyLine.encode(emptyList()) == "", "empty encode")
        ok(PolyLine.encode(pts) == CANON, "canonical re-encode")
        ok(PolyLine.encode(PolyLine.decode(CANON)) == CANON, "string round trip")
        val antipodes = listOf(PolyLine.Pt(-33.8, 151.2), PolyLine.Pt(51.5, -0.12), PolyLine.Pt(0.0, 0.0))
        ok(PolyLine.decode(PolyLine.encode(antipodes)) == antipodes, "point round trip")
        ok(PolyLine.decode(PolyLine.encode(listOf(PolyLine.Pt(90.0, 180.0)))) ==
            listOf(PolyLine.Pt(90.0, 180.0)), "extreme corners")
        ok(PolyLine.decode(PolyLine.encode(listOf(PolyLine.Pt(-90.0, -180.0)))) ==
            listOf(PolyLine.Pt(-90.0, -180.0)), "negative corners")
        ok(PolyLine.decode(PolyLine.encode(listOf(PolyLine.Pt(0.000001, 0.000001)))) ==
            listOf(PolyLine.Pt(0.0, 0.0)), "quantized to 1e-5")
        fails("bad character") { PolyLine.decode("?? ?") }
        fails("truncated varint") { PolyLine.decode("~") }
        fails("lat out of range") { PolyLine.encode(listOf(PolyLine.Pt(90.00001, 0.0))) }
        fails("lng out of range") { PolyLine.encode(listOf(PolyLine.Pt(0.0, 180.00001))) }
        fails("non-finite") { PolyLine.encode(listOf(PolyLine.Pt(Double.NaN, 0.0))) }
        fails("too many points") { PolyLine.encode(List(PolyLine.MAX_POINTS + 1) { PolyLine.Pt(0.0, 0.0) }) }
        fails("decoded over cap") { PolyLine.decode("??".repeat(PolyLine.MAX_POINTS + 1)) }
    }

    // ---- Mp4Box ------------------------------------------------------

    /** A 32-bit-size box with [payload]. */
    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size)
        val size = out.size
        out[0] = ((size shr 24) and 0xFF).toByte()
        out[1] = ((size shr 16) and 0xFF).toByte()
        out[2] = ((size shr 8) and 0xFF).toByte()
        out[3] = (size and 0xFF).toByte()
        for (i in 0..3) out[4 + i] = type[i].code.toByte()
        payload.copyInto(out, 8)
        return out
    }

    fun mp4(fixture: ByteArray) {
        val boxes = Mp4Box.walk(fixture)
        ok(boxes.size == 2, "fixture box count")
        ok(boxes[0] == Mp4Box.Box("ftyp", 24L, 0L), "fixture ftyp")
        ok(boxes[1] == Mp4Box.Box("free", 8L, 24L), "fixture free")
        ok(Mp4Box.walk(ByteArray(0)).isEmpty(), "empty input")
        ok(Mp4Box.walk(box("moov", ByteArray(0))) == listOf(Mp4Box.Box("moov", 8L, 0L)),
            "empty payload box")
        val mdat = box("mdat", ByteArray(4) { 0x5A })
        ok(Mp4Box.walk(mdat) == listOf(Mp4Box.Box("mdat", 12L, 0L)), "payload box")
        val two = box("ftyp", ByteArray(8)) + box("mdat", ByteArray(2))
        ok(Mp4Box.walk(two).map { it.type } == listOf("ftyp", "mdat"), "two boxes")
        ok(Mp4Box.walk(two)[1].offset == 16L, "second offset")
        // 64-bit largesize: 8-byte header + 8-byte size field + 4-byte payload.
        val large = ByteArray(20)
        large[3] = 1
        "mdat".forEachIndexed { i, c -> large[4 + i] = c.code.toByte() }
        large[15] = 0x14 // 64-bit size field (bytes 8..15) = 20
        ok(Mp4Box.walk(large) == listOf(Mp4Box.Box("mdat", 20L, 0L)), "largesize")
        // size == 0 means "to end of file".
        val toEnd = ByteArray(12)
        "mdat".forEachIndexed { i, c -> toEnd[4 + i] = c.code.toByte() }
        ok(Mp4Box.walk(toEnd) == listOf(Mp4Box.Box("mdat", 12L, 0L)), "size zero to end")
        fails("truncated header") { Mp4Box.walk(ByteArray(5)) }
        fails("size below header") {
            Mp4Box.walk(byteArrayOf(0, 0, 0, 4) + "free".toByteArray())
        }
        fails("size overruns input") {
            Mp4Box.walk(byteArrayOf(0, 0, 0, 100) + "free".toByteArray() + ByteArray(8))
        }
        fails("non-ascii type") {
            Mp4Box.walk(byteArrayOf(0, 0, 0, 8, 1, 2, 3, 4))
        }
        fails("truncated largesize") {
            Mp4Box.walk(byteArrayOf(0, 0, 0, 1) + "mdat".toByteArray() + ByteArray(2))
        }
        fails("box over cap") {
            val many = ByteArray((Mp4Box.MAX_BOXES + 1) * 8)
            for (i in 0..Mp4Box.MAX_BOXES) {
                val o = i * 8
                many[o + 3] = 8
                "free".forEachIndexed { j, c -> many[o + 4 + j] = c.code.toByte() }
            }
            Mp4Box.walk(many)
        }
    }

    // ---- OscLink -----------------------------------------------------

    fun osc() {
        val plain = OscLink.parse("\u001B]8;;https://example.com/a\u0007")
        ok(plain.uri == "https://example.com/a", "plain uri")
        ok(plain.params.isEmpty(), "plain params")
        ok(plain.id == null, "plain id")
        val withId = OscLink.parse("\u001B]8;id=xyz:foo=bar;https://e.com\u0007")
        ok(withId.params["id"] == "xyz", "id param")
        ok(withId.params["foo"] == "bar", "second param")
        ok(withId.id == "xyz", "id exposed")
        ok(OscLink.parse("\u001B]8;;\u0007").uri == "", "close marker")
        ok(OscLink.parse("\u001B]8;;x\u001B\\").uri == "x", "ST terminator")
        ok(OscLink.parse("\u001B]8;id=1;mailto:a@b\u0007").params.size == 1, "single param")
        ok(OscLink.parse("\u001B]8;a-b_c=1;x\u0007").params["a-b_c"] == "1", "key charset")
        fails("bad prefix") { OscLink.parse("]8;;x\u0007") }
        fails("osc 7 not 8") { OscLink.parse("\u001B]7;;x\u0007") }
        fails("bad terminator") { OscLink.parse("\u001B]8;;x") }
        fails("missing separator") { OscLink.parse("\u001B]8;id=1\u0007") }
        fails("empty key") { OscLink.parse("\u001B]8;=v;x\u0007") }
        fails("bad key char") { OscLink.parse("\u001B]8;i d=1;x\u0007") }
        fails("duplicate param") { OscLink.parse("\u001B]8;id=1:id=2;x\u0007") }
        fails("space in uri") { OscLink.parse("\u001B]8;;http://a b\u0007") }
        fails("control in uri") { OscLink.parse("\u001B]8;;http://a\u0001b\u0007") }
        fails("over cap") { OscLink.parse("\u001B]8;;" + "x".repeat(OscLink.MAX_SEQ)) }
    }

    // ---- RankFuse ----------------------------------------------------

    fun fuse() {
        val fused = RankFuse.fuse(listOf(listOf("a", "b"), listOf("b", "c")))
        ok(fused.size == 3, "fused size")
        ok(fused[0].id == "b", "highest first")
        close(fused[0].score, 1.0 / 61 + 1.0 / 62, 1e-12, "b score")
        ok(fused[1].id == "a", "second")
        close(fused[1].score, 1.0 / 61, 1e-12, "a score")
        ok(fused[2].id == "c", "third")
        close(fused[2].score, 1.0 / 62, 1e-12, "c score")
        val tie = RankFuse.fuse(listOf(listOf("a"), listOf("b")))
        ok(tie.map { it.id } == listOf("a", "b"), "stable tie keeps first-seen")
        close(tie[0].score, 1.0 / 61, 1e-12, "default k is 60")
        val k1 = RankFuse.fuse(listOf(listOf("a", "b")), k = 1)
        close(k1[0].score, 0.5, 1e-12, "k=1 rank1")
        close(k1[1].score, 1.0 / 3, 1e-12, "k=1 rank2")
        ok(RankFuse.fuse(listOf(emptyList())).isEmpty(), "empty ranking")
        ok(RankFuse.fuse(listOf(listOf("x"), emptyList()))[0].id == "x", "empty second list")
        fails("k zero") { RankFuse.fuse(listOf(listOf("a")), k = 0) }
        fails("no rankings") { RankFuse.fuse(emptyList()) }
        fails("blank id") { RankFuse.fuse(listOf(listOf(""))) }
        fails("duplicate id") { RankFuse.fuse(listOf(listOf("a", "a"))) }
        fails("too many lists") { RankFuse.fuse(List(RankFuse.MAX_LISTS + 1) { listOf("a") }) }
    }

    // ---- ExitCode ----------------------------------------------------

    fun exit() {
        ok(ExitCode.name(0) == "success", "0")
        ok(ExitCode.name(1) == "general error", "1")
        ok(ExitCode.name(2) == "shell misuse", "2")
        ok(ExitCode.name(126) == "not executable", "126")
        ok(ExitCode.name(127) == "command not found", "127")
        ok(ExitCode.name(64) == "EX_USAGE", "sysexits 64")
        ok(ExitCode.name(65) == "EX_DATAERR", "sysexits 65")
        ok(ExitCode.name(73) == "EX_CANTCREAT", "sysexits 73")
        ok(ExitCode.name(78) == "EX_CONFIG", "sysexits 78")
        ok(ExitCode.name(7) == "exit 7", "unnamed low")
        ok(ExitCode.name(100) == "exit 100", "unnamed mid")
        ok(ExitCode.name(128) == "exit 128", "128 is not a signal")
        ok(ExitCode.name(129) == "killed by SIGHUP", "129")
        ok(ExitCode.name(130) == "killed by SIGINT", "130")
        ok(ExitCode.name(137) == "killed by SIGKILL", "137")
        ok(ExitCode.name(143) == "killed by SIGTERM", "143")
        ok(ExitCode.name(159) == "killed by SIGSYS", "159")
        ok(ExitCode.name(160) == "killed by signal 32", "unnamed signal")
        ok(ExitCode.name(255) == "killed by signal 127", "255")
        ok(ExitCode.isSuccess(0), "isSuccess 0")
        ok(!ExitCode.isSuccess(1), "isSuccess 1")
        ok(!ExitCode.isSuccess(127), "isSuccess 127")
        fails("negative code") { ExitCode.name(-1) }
        fails("code over 255") { ExitCode.name(256) }
        fails("negative isSuccess") { ExitCode.isSuccess(-1) }
    }

    // ---- FovMath -----------------------------------------------------

    fun fov() {
        close(FovMath.fov(50.0, 36.0), 39.597752709049864, 1e-12, "50mm 36mm")
        close(FovMath.fov(35.0, 36.0), 54.43222311461495, 1e-12, "35mm 36mm")
        close(FovMath.fov(50.0, 24.0), 26.991466561591626, 1e-12, "50mm 24mm")
        close(FovMath.fov(85.0, 36.0), 23.913168486298265, 1e-12, "85mm 36mm")
        ok(FovMath.fov(1.0e9, 36.0) in 1.0e-9..1.0e-5, "long lens narrows")
        close(FovMath.fov(1.0e9, 36.0), 2.062648062470963E-6, 1e-12, "long lens pin")
        ok(FovMath.fov(50.0, 36.0) > FovMath.fov(85.0, 36.0), "wider lens wider fov")
        ok(FovMath.fov(50.0, 36.0) < FovMath.fov(50.0, 43.0), "bigger sensor wider fov")
        close(FovMath.equiv(50.0, 1.5), 75.0, 1e-12, "aps-c equivalent")
        close(FovMath.equiv(35.0, 1.0), 35.0, 1e-12, "crop 1 identity")
        close(FovMath.equiv(24.0, 1.0), 24.0, 1e-12, "full frame")
        fails("zero focal") { FovMath.fov(0.0, 36.0) }
        fails("negative focal") { FovMath.fov(-50.0, 36.0) }
        fails("zero sensor") { FovMath.fov(50.0, 0.0) }
        fails("nan focal") { FovMath.fov(Double.NaN, 36.0) }
        fails("infinite sensor") { FovMath.fov(50.0, Double.POSITIVE_INFINITY) }
        fails("zero crop") { FovMath.equiv(50.0, 0.0) }
        fails("negative crop") { FovMath.equiv(50.0, -1.5) }
    }
}

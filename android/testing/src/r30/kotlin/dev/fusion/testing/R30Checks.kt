// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.airplay.RtspMessage
import dev.fusion.archive.TarHeader
import dev.fusion.capture.Timecodes
import dev.fusion.continuity.CalendarExport
import dev.fusion.continuity.VEventParse
import dev.fusion.format.image.GifInspect
import dev.fusion.lens.ZoomLadder
import dev.fusion.messages.SmsEncoding
import dev.fusion.messages.SmsSegments
import dev.fusion.model.DensityProfile
import dev.fusion.model.DensitySelect
import dev.fusion.settingsui.PatchScopes
import dev.fusion.settingsui.SettingsPatch
import dev.fusion.together.LamportClock
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * R30 consumer: executes the 10 production kernels against real fixtures.
 * Host JVM only; NOT Android, device, service, anonymity or acceptance evidence.
 */
object R30Checks {
    private var n = 0
    private fun ok(cond: Boolean, label: String = "") {
        n++
        check(cond) { "R30 check$n: $label" }
    }
    private fun refuse(block: () -> Any?, label: String) {
        n++
        try {
            block()
        } catch (_: Exception) {
            return
        }
        error("R30 accepted invalid input at$n: $label")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val dir = Path.of(args[0])
        rtsp(dir); timecode(); vevent(dir); lamport(); gif(dir); patchscope(); tar(dir); zoom(); sms(); density()
        println("R30 PASS: $n production-kernel assertions across 10 kernels. " +
            "Host JVM only; NOT Android, device, service, anonymity or acceptance evidence.")
    }

    private fun rtsp(dir: Path) {
        val req = RtspMessage.parse(Files.readAllBytes(dir.resolve("rtsp_announce.txt")))
        ok(req.method == "ANNOUNCE" && req.cseq == 1, "fixture method+cseq")
        ok(req.uri == "rtsp://192.168.1.5/3413821438", "fixture uri")
        ok(req.body.size == 135 && String(req.body).startsWith("v=0"), "fixture body")
        ok(req.headers["Apple-Challenge"] == "09KF45soMYmvagi0609xHA", "custom header")
        val bytes = RtspMessage.serialize(req.method, req.uri, req.cseq,
            req.headers - "CSeq" - "Content-Length", req.body)
        ok(RtspMessage.parse(bytes) == req, "serialize round-trip")
        val minimal = RtspMessage.serialize("OPTIONS", "*", 7, emptyMap(), ByteArray(0))
        val back = RtspMessage.parse(minimal)
        ok(back.method == "OPTIONS" && back.cseq == 7 && back.body.isEmpty() &&
            "Content-Length" !in back.headers, "minimal request")
        refuse({ RtspMessage.parse("OPTIONS * RTSP/1.0\nCSeq: 1\n\n".toByteArray()) }, "bare LF")
        refuse({ RtspMessage.parse("OPTIONS * RTSP/1.0\r\nX-A: 1\r\n\r\n".toByteArray()) }, "missing CSeq")
        refuse({ RtspMessage.parse("BREW * RTSP/1.0\r\nCSeq: 1\r\n\r\n".toByteArray()) }, "unknown method")
        refuse({ RtspMessage.parse("OPTIONS * RTSP/1.0\r\nCSeq: 1\r\n folded\r\n\r\n".toByteArray()) }, "folded header")
        refuse({
            RtspMessage.parse("OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nContent-Length: 5\r\n\r\nabc".toByteArray())
        }, "short body")
        refuse({ RtspMessage.parse("OPTIONS * HTTP/1.0\r\nCSeq: 1\r\n\r\n".toByteArray()) }, "bad version")
    }

    private fun timecode() {
        ok(Timecodes.fromMillis(0, 25).toString() == "00:00:00:00", "zero")
        ok(Timecodes.fromMillis(1040, 25).toString() == "00:00:01:01", "frame truncation")
        ok(Timecodes.fromMillis(86399999, 30).toString() == "23:59:59:29", "day edge")
        ok(Timecodes.fromMillis(3661000, 24).toString() == "01:01:01:00", "hour mark")
        ok(Timecodes.parse("01:01:01:00", 24) == Timecodes.fromMillis(3661000, 24), "parse round-trip")
        ok(Timecodes.toMillis(Timecodes.parse("00:00:01:01", 25)) == 1040L, "to millis")
        ok(Timecodes.toMillis(Timecodes.parse("23:59:59:29", 30)) == 86399966L, "to millis edge")
        refuse({ Timecodes.fromMillis(0, 29) }, "29.97 refused")
        refuse({ Timecodes.fromMillis(86400000, 25) }, "24h wrap refused")
        refuse({ Timecodes.parse("1:2:3:4", 25) }, "short fields")
        refuse({ Timecodes.parse("00:00:00:25", 25) }, "frame over fps")
        refuse({ Timecodes.parse("24:00:00:00", 25) }, "hour 24")
    }

    private fun vevent(dir: Path) {
        val e = VEventParse.parse(Files.readAllBytes(dir.resolve("event.ics")))
        ok(e.uid == "evt-1@example", "uid")
        ok(e.created == Instant.parse("2026-10-22T06:30:00Z"), "dtstamp")
        ok(e.start == Instant.parse("2026-10-23T14:00:00Z") && e.end == Instant.parse("2026-10-23T15:00:00Z"), "dtstart/dtend")
        ok(e.summary.startsWith("Planning, review; Q4 goals \\ draft ") && e.summary.length == 125, "unfold+unescape")
        ok(e.description == "Line one\nLine two\\ end", "multiline description")
        val rt = VEventParse.parse(CalendarExport.encode(e))
        ok(rt == e, "encode round-trip")
        val noDesc = String(Files.readAllBytes(dir.resolve("event.ics")))
            .replace("\r\nDESCRIPTION:Line one\\nLine two\\\\ end", "")
        ok(VEventParse.parse(noDesc.toByteArray()).description == "", "description optional")
        refuse({ VEventParse.parse("BEGIN:VCALENDAR\nVERSION:2.0\n".toByteArray()) }, "LF endings")
        refuse({
            VEventParse.parse(CalendarExport.encode(e).toString(Charsets.UTF_8)
                .replace("SUMMARY:", "X-FOO:bar\r\nSUMMARY:").toByteArray())
        }, "unknown property")
        refuse({
            VEventParse.parse(CalendarExport.encode(e).toString(Charsets.UTF_8)
                .replace("SUMMARY:", "SUMMARY:\\t").toByteArray())
        }, "bad escape")
    }

    private fun lamport() {
        val c = LamportClock.fresh()
        ok(c.read() == 0L, "fresh zero")
        ok(c.tick() == 1L && c.send() == 2L, "tick+send")
        ok(c.receive(5) == 6L, "receive jumps")
        ok(c.tick() == 7L && c.receive(3) == 8L, "receive below max")
        ok(c.read() == 8L, "read back")
        val edge = LamportClock.fresh()
        ok(edge.receive(LamportClock.MAX - 1) == LamportClock.MAX, "receive to max")
        refuse({ edge.tick() }, "tick past max")
        refuse({ LamportClock.fresh().receive(-1) }, "negative remote")
        refuse({ LamportClock.fresh().receive(Long.MAX_VALUE) }, "remote over max")
    }

    private fun gif(dir: Path) {
        val g = GifInspect.parse(Files.readAllBytes(dir.resolve("frames2.gif")))
        ok(g.version == "GIF89a" && g.width == 2 && g.height == 1, "fixture header")
        ok(g.frames == 2 && g.hasGlobalTable, "fixture frames+gct")
        val tiny = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x37, 0x61, 0x01, 0x00, 0x01, 0x00,
            0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x02, 0x44, 0x01, 0x00, 0x3B)
        val t = GifInspect.parse(tiny)
        ok(t.version == "GIF87a" && t.frames == 1 && !t.hasGlobalTable, "minimal 87a")
        refuse({ GifInspect.parse("GIF99a".toByteArray() + tiny.copyOfRange(6, tiny.size)) }, "bad magic")
        refuse({ GifInspect.parse(tiny.copyOfRange(0, tiny.size - 1)) }, "missing trailer")
        refuse({ GifInspect.parse(tiny + 0x00) }, "trailing byte")
        val zero = tiny.copyOf(); zero[6] = 0; zero[7] = 0
        refuse({ GifInspect.parse(zero) }, "zero width")
        val noframe = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
            0x00, 0x00, 0x00, 0x3B)
        refuse({ GifInspect.parse(noframe) }, "zero frames")
    }

    private fun patchscope() {
        val scope = PatchScopes.define("appearance", listOf("appearance", "locale"), setOf("theme"))
        ok(PatchScopes.permits(scope, "theme"), "exact key")
        ok(PatchScopes.permits(scope, "appearance") && PatchScopes.permits(scope, "appearance.mode"), "prefix key")
        ok(PatchScopes.permits(scope, "appearance.a.b"), "nested key")
        ok(!PatchScopes.permits(scope, "appearancex"), "prefix boundary")
        ok(!PatchScopes.permits(scope, "other.key"), "outside scope")
        val patch = SettingsPatch.plan(
            mapOf("appearance.mode" to "light", "locale" to "en", "other.key" to "1"),
            mapOf("appearance.mode" to "dark", "appearance.font" to "big", "locale" to "fr", "other.key" to "2"))
        val (allowed, refusedKeys) = PatchScopes.split(patch, scope)
        ok(allowed.map { it.key }.sorted() == listOf("appearance.font", "appearance.mode", "locale"), "split allowed")
        ok(refusedKeys == listOf("other.key"), "split refused")
        refuse({ PatchScopes.define("Bad Name", emptyList(), emptySet()) }, "bad scope name")
        refuse({ PatchScopes.define("s", listOf("a", "a"), emptySet()) }, "duplicate prefixes")
        refuse({ PatchScopes.define("s", (0..32).map { "k$it" }, emptySet()) }, "prefix over cap")
    }

    private fun tar(dir: Path) {
        val bytes = Files.readAllBytes(dir.resolve("archive.tar"))
        val h1 = TarHeader.parse(bytes.copyOfRange(0, 512))
        ok(h1.name == "hello.txt" && h1.size == 6L, "first header")
        ok(h1.mode == 420 && h1.mtime == 1761000000L, "mode+mtime")
        val h2off = (512 + TarHeader.dataBlocks(h1.size) * 512).toInt()
        val h2 = TarHeader.parse(bytes.copyOfRange(h2off, h2off + 512))
        ok(h2.name == "dir/nested.bin" && h2.size == 768L, "second header")
        ok(TarHeader.dataBlocks(0) == 0L && TarHeader.dataBlocks(512) == 1L && TarHeader.dataBlocks(513) == 2L, "block math")
        val corrupt = bytes.copyOfRange(0, 512); corrupt[10] = (corrupt[10] + 1).toByte()
        refuse({ TarHeader.parse(corrupt) }, "checksum mismatch")
        fun resign(h: ByteArray) {
            for (i in 148..155) h[i] = 0x20
            var sum = 0L
            for (b in h) sum += b.toInt() and 0xFF
            "%06o\u0000 ".format(sum).toByteArray().copyInto(h, 148)
        }
        val gnu = bytes.copyOfRange(0, 512)
        "ustar  \u0000".toByteArray().copyInto(gnu, 257)
        resign(gnu)
        refuse({ TarHeader.parse(gnu) }, "gnu magic refused")
        val dirType = bytes.copyOfRange(0, 512); dirType[156] = '5'.code.toByte()
        resign(dirType)
        refuse({ TarHeader.parse(dirType) }, "directory typeflag refused")
        refuse({ TarHeader.parse(bytes.copyOfRange(0, 511)) }, "short record")
    }

    private fun zoom() {
        ok(ZoomLadder.scale(0) == 1.0 && ZoomLadder.scale(2) == 2.0, "exact powers")
        ok(ZoomLadder.scale(-4) == 0.25 && ZoomLadder.scale(8) == 16.0, "ladder ends")
        val half = ZoomLadder.scale(1)
        ok(half > 1.4142 && half < 1.4143, "half stop")
        ok(ZoomLadder.zoomIn(8) == 8 && ZoomLadder.zoomOut(-4) == -4, "clamped ends")
        ok(ZoomLadder.zoomIn(0) == 1 && ZoomLadder.zoomOut(0) == -1, "neighbors")
        ok(ZoomLadder.nearest(1.0) == 0 && ZoomLadder.nearest(2.0) == 2, "nearest exact")
        ok(ZoomLadder.nearest(2.828) == 3, "nearest rounds")
        ok(ZoomLadder.nearest(0.01) == -4 && ZoomLadder.nearest(100.0) == 8, "nearest clamps")
        refuse({ ZoomLadder.scale(9) }, "index over max")
        refuse({ ZoomLadder.nearest(0.0) }, "zero scale")
        refuse({ ZoomLadder.nearest(Double.NaN) }, "nan scale")
    }

    private fun sms() {
        ok(SmsSegments.classify("hello £") == SmsEncoding.GSM7, "gsm basic")
        ok(SmsSegments.classify("a^b") == SmsEncoding.GSM7 && SmsSegments.units("a^b") == 4, "extension costs 2")
        ok(SmsSegments.classify("hi —") == SmsEncoding.UCS2, "em dash falls back")
        ok(SmsSegments.classify("€100") == SmsEncoding.GSM7 && SmsSegments.units("€") == 2, "euro is extension")
        ok(SmsSegments.segments("a".repeat(160)) == 1 && SmsSegments.segments("a".repeat(161)) == 2, "gsm boundary")
        ok(SmsSegments.segments("中".repeat(70)) == 1 && SmsSegments.segments("中".repeat(71)) == 2, "ucs2 boundary")
        ok(SmsSegments.units("\uD83D\uDE00") == 2, "surrogate pair counts 2")
        ok(SmsSegments.segments("") == 0, "empty needs nothing")
        refuse({ SmsSegments.segments("a".repeat(1601)) }, "over char cap")
        refuse({ SmsSegments.classify("b".repeat(2000)) }, "classify over cap")
    }

    private fun density() {
        ok(DensitySelect.select(179, false) == DensityProfile.COMPACT, "compact edge")
        ok(DensitySelect.select(180, false) == DensityProfile.BALANCED, "balanced floor")
        ok(DensitySelect.select(279, false) == DensityProfile.BALANCED, "balanced ceiling")
        ok(DensitySelect.select(280, false) == DensityProfile.TOUCH, "touch floor")
        ok(DensitySelect.select(120, true) == DensityProfile.TOUCH, "touch override")
        ok(DensitySelect.select(1000, false) == DensityProfile.TOUCH, "max dpi")
        refuse({ DensitySelect.select(79, false) }, "dpi under range")
        refuse({ DensitySelect.select(1001, false) }, "dpi over range")
    }
}

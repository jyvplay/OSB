// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.a11y.HslColor
import dev.fusion.automation.IsoWeek
import dev.fusion.beam.Bencode
import dev.fusion.beam.MagnetParse
import dev.fusion.browser.model.HttpRange
import dev.fusion.capture.VttTime
import dev.fusion.format.image.WebpHead
import dev.fusion.format.pdf.PdfName
import dev.fusion.hw.EdidHead
import dev.fusion.index.Soundex
import dev.fusion.media.FlacHead
import dev.fusion.messages.QuotedPrint
import dev.fusion.model.SemVer
import dev.fusion.privacy.LuhnCheck
import dev.fusion.security.KeyCadence
import dev.fusion.security.PemBound
import dev.fusion.storage.UuidInfo
import java.io.File

/**
 * R38 host consumer: behavioral contracts over the 17 R38 kernels.
 *
 * Host JVM only. Nothing here is Android, device, service, or
 * acceptance evidence; the kernels are pure logic and this consumer is
 * the only thing that executes them.
 */
object R38Checks {
    var n = 0
    fun ok(c: Boolean, label: String) {
        n++
        if (!c) throw AssertionError("r38 check $n FAILED: $label")
    }
    fun fails(label: String, block: () -> Unit) {
        n++
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("r38 check $n FAILED (no refusal): $label")
    }
    fun close(a: Double, b: Double, eps: Double, label: String) {
        n++
        if (!(Math.abs(a - b) <= eps)) throw AssertionError("r38 check $n FAILED: $label ($a vs $b)")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val fixtures = File(args[0])
        magnet()
        bencode()
        flac(fixtures)
        webp(fixtures)
        semver()
        uuid()
        range()
        qp()
        pem()
        isoweek()
        hsl()
        edid(fixtures)
        vtt()
        luhn()
        soundex()
        pdfname()
        cadence()
        println("R38 PASS: $n production-kernel assertions across 17 kernels. Host JVM only; not Android/device evidence.")
    }

    fun magnet() {
        val m = MagnetParse.parse(
            "magnet:?xt=urn:btih:c12fe1c06bba254a9dc9f519b335aa7c1367a88a&dn=my+file%20name" +
                "&tr=udp%3A%2F%2Ftracker.example%3A80&tr=http%3A%2F%2Fbackup.example%2Fann&x.pe=10.0.0.1%3A6881"
        )
        ok(m.btih == "c12fe1c06bba254a9dc9f519b335aa7c1367a88a", "btih hex")
        ok(m.displayName == "my file name", "dn decoded with + and %20")
        ok(m.trackers == listOf("udp://tracker.example:80", "http://backup.example/ann"), "tr order preserved")
        ok(m.peers == listOf("10.0.0.1:6881"), "x.pe list")
        ok(m.btmh == null, "no btmh in v1 link")
        val b32 = MagnetParse.parse("magnet:?xt=urn:btih:CT76KHBPIIZZ7AMINCYQAAAAAAAAAAAA")
        ok(b32.btih == "14ffe51c2f42339f818868b10000000000000000", "base32 info-hash decoded to pinned hex")
        val upper = MagnetParse.parse("magnet:?xt=urn:btih:C12FE1C06BBA254A9DC9F519B335AA7C1367A88A")
        ok(upper.btih == "c12fe1c06bba254a9dc9f519b335aa7c1367a88a", "uppercase hex folded")
        val v2 = MagnetParse.parse("magnet:?xt=urn:btmh:1220" + "ab".repeat(32))
        ok(v2.btmh == "1220" + "ab".repeat(32), "btmh multihash accepted")
        val hybrid = MagnetParse.parse(
            "magnet:?xt=urn:btih:" + "0".repeat(40) + "&xt=urn:btmh:1220" + "0".repeat(64)
        )
        ok(hybrid.btih != null && hybrid.btmh != null, "hybrid v1+v2 accepted")
        val extra = MagnetParse.parse("magnet:?xt=urn:btih:" + "a".repeat(40) + "&xl=123&ws=http%3A%2F%2Fx")
        ok(extra.btih == "a".repeat(40) && extra.trackers.isEmpty(), "unrecognized params ignored")
        ok(MagnetParse.base32Decode("CT76KHBPIIZZ7AMINCYQAAAAAAAAAAAA").size == 20, "base32 yields 20 bytes")
        ok(MagnetParse.pctDecode("a+b%2Fc") == "a b/c", "pctDecode plus and %2F")
        fails("xt mandatory") { MagnetParse.parse("magnet:?dn=x") }
        fails("unsupported urn") { MagnetParse.parse("magnet:?xt=urn:sha1:AAAA") }
        fails("39-char hash") { MagnetParse.parse("magnet:?xt=urn:btih:" + "a".repeat(39)) }
        fails("base32 charset: 1 is not in RFC 4648 base32") { MagnetParse.normalizeV1("1".repeat(32)) }
        fails("duplicate btih") { MagnetParse.parse("magnet:?xt=urn:btih:" + "a".repeat(40) + "&xt=urn:btih:" + "b".repeat(40)) }
        fails("duplicate dn") { MagnetParse.parse("magnet:?xt=urn:btih:" + "a".repeat(40) + "&dn=x&dn=y") }
        fails("truncated percent escape") { MagnetParse.parse("magnet:?xt=urn:btih:" + "a".repeat(40) + "&dn=x%2") }
        fails("not a magnet URI") { MagnetParse.parse("https://example.com/?xt=urn:btih:" + "a".repeat(40)) }
        fails("btmh must be sha2-256 multihash") { MagnetParse.normalizeV2("1114" + "a".repeat(64)) }
    }

    private fun bytes(s: String) = s.toByteArray(Charsets.ISO_8859_1)

    fun bencode() {
        ok((Bencode.decode(bytes("i3e")) as Bencode.BValue.BInt).value == 3L, "i3e")
        ok((Bencode.decode(bytes("i-3e")) as Bencode.BValue.BInt).value == -3L, "i-3e")
        ok((Bencode.decode(bytes("i0e")) as Bencode.BValue.BInt).value == 0L, "i0e")
        ok((Bencode.decode(bytes("4:spam")) as Bencode.BValue.BStr).text == "spam", "4:spam")
        ok((Bencode.decode(bytes("0:")) as Bencode.BValue.BStr).bytes.isEmpty(), "0: empty string")
        val list = Bencode.decode(bytes("l4:spam4:eggse")) as Bencode.BValue.BList
        ok(list.items.size == 2 && (list.items[1] as Bencode.BValue.BStr).text == "eggs", "BEP 3 list example")
        val dict = Bencode.decode(bytes("d3:cow3:moo4:spam4:eggse")) as Bencode.BValue.BDict
        ok((dict["cow"] as Bencode.BValue.BStr).text == "moo", "BEP 3 dict example")
        val nested = Bencode.decode(bytes("d4:spaml1:a1:bee")) as Bencode.BValue.BDict
        ok((nested["spam"] as Bencode.BValue.BList).items.size == 2, "BEP 3 nested example")
        ok((Bencode.decode(bytes("le")) as Bencode.BValue.BList).items.isEmpty(), "empty list")
        ok((Bencode.decode(bytes("de")) as Bencode.BValue.BDict).entries.isEmpty(), "empty dict")
        val bep9 = Bencode.decode(bytes("d8:msg_typei0e5:piecei0ee")) as Bencode.BValue.BDict
        ok((bep9["msg_type"] as Bencode.BValue.BInt).value == 0L, "BEP 9 request message decodes")
        ok(Bencode.rawCompare(bytes("a"), bytes("ab")) < 0, "raw prefix sorts first")
        ok(Bencode.rawCompare(bytes("\u00FF"), bytes("a")) > 0, "raw compare is unsigned bytewise")
        fails("i-0e is invalid") { Bencode.decode(bytes("i-0e")) }
        fails("i03e leading zero") { Bencode.decode(bytes("i03e")) }
        fails("leading zero string length") { Bencode.decode(bytes("01:a")) }
        fails("trailing bytes") { Bencode.decode(bytes("i3ei4e")) }
        fails("unsorted keys") { Bencode.decode(bytes("d1:b0:1:a0:e")) }
        fails("duplicate keys") { Bencode.decode(bytes("d1:a0:1:a0:e")) }
        fails("unterminated list") { Bencode.decode(bytes("l4:spam")) }
        fails("dict key must be string") { Bencode.decode(bytes("di1e0:e")) }
        fails("truncated string") { Bencode.decode(bytes("9:abc")) }
        fails("nesting cap") { Bencode.decode(bytes("l".repeat(40) + "e".repeat(40))) }
        fails("integer beyond signed 64-bit scope") { Bencode.decode(bytes("i99999999999999999999e")) }
    }

    fun flac(fixtures: File) {
        val fix = File(fixtures, "flac.bin").readBytes()
        val s = FlacHead.parse(fix)
        ok(s.sampleRateHz == 44100, "sample rate 44100")
        ok(s.channels == 2, "2 channels")
        ok(s.bitsPerSample == 16, "16 bps")
        ok(s.totalSamples == 88200L, "88200 total samples")
        ok(s.durationMs == 2000L, "2000 ms duration")
        ok(s.minBlockSize == 4096 && s.maxBlockSize == 4096, "block sizes 4096")
        ok(s.minFrameSize == 14 && s.maxFrameSize == 14, "frame sizes 14")
        ok(s.lastMetadataBlock, "last-metadata flag set in fixture")
        ok(s.md5Hex == "0".repeat(32), "md5 unknown = 32 zero hex chars")
        val unknown = fix.copyOf()
        // zero the 36-bit total-samples field (low nibble of byte 21 + bytes 22..25 of the body at offset 8)
        unknown[8 + 13] = (unknown[8 + 13].toInt() and 0xF0).toByte()
        for (i in 14..17) unknown[8 + i] = 0
        ok(FlacHead.parse(unknown).durationMs == null, "unknown total samples -> null duration")
        fun mutate(off: Int, v: Int): ByteArray { val b = fix.copyOf(); b[off] = v.toByte(); return b }
        fails("marker") { FlacHead.parse(mutate(0, 0x67)) }
        fails("type 127 forbidden") { FlacHead.parse(mutate(4, 0xFF)) }
        fails("first block must be streaminfo") { FlacHead.parse(mutate(4, 0x81)) }
        fails("length must be 34") { FlacHead.parse(mutate(7, 35)) }
        fails("min block 15") { FlacHead.parse(mutate(9, 0x0F).also { it[8] = 0 }) }
        fails("min > max block") { FlacHead.parse(mutate(10, 0x0F).also { it[11] = 0xFF.toByte() }) }
        run { // sample rate 0
            val b = fix.copyOf()
            b[8 + 10] = 0; b[8 + 11] = 0; b[8 + 12] = (b[8 + 12].toInt() and 0x0F).toByte()
            fails("sample rate 0") { FlacHead.parse(b) }
        }
        run { // bps-1 = 2 -> 3 bits per sample, below FLAC's 4-bit floor
            val b = fix.copyOf()
            b[8 + 12] = ((b[8 + 12].toInt() and 0xF1) or 0x00).toByte()
            b[8 + 13] = ((b[8 + 13].toInt() and 0x0F) or 0x20).toByte()
            fails("bps below 4") { FlacHead.parse(b) }
        }
        fails("short input") { FlacHead.parse(fix.copyOf(20)) }
    }

    fun webp(fixtures: File) {
        val fix = File(fixtures, "webp.bin").readBytes()
        val i = WebpHead.parse(fix)
        ok(i.kind == WebpHead.Kind.EXTENDED, "VP8X extended kind")
        ok(i.canvasWidth == 1024L, "canvas width 1024 (stored 1023)")
        ok(i.canvasHeight == 768L, "canvas height 768 (stored 767)")
        ok(i.hasIcc && i.hasAlpha, "ICC and alpha flags set (0x30)")
        ok(!i.hasExif && !i.hasXmp && !i.isAnimated, "EXIF/XMP/ANIM clear")
        ok(i.riffSize == 22L, "riff size 22")
        val anim = fix.copyOf(); anim[20] = 0x02
        ok(WebpHead.parse(anim).isAnimated, "animation flag 0x02")
        run {
            val lossy = bytes("RIFF") + byteArrayOf(12, 0, 0, 0) + bytes("WEBPVP8 ") + byteArrayOf(0, 0, 0, 0)
            ok(WebpHead.parse(lossy).kind == WebpHead.Kind.LOSSY, "VP8  simple lossy kind")
        }
        run {
            val lossless = bytes("RIFF") + byteArrayOf(12, 0, 0, 0) + bytes("WEBPVP8L") + byteArrayOf(0, 0, 0, 0)
            ok(WebpHead.parse(lossless).kind == WebpHead.Kind.LOSSLESS, "VP8L simple lossless kind")
        }
        fun mutate(off: Int, v: Int): ByteArray { val b = fix.copyOf(); b[off] = v.toByte(); return b }
        fails("RIFF tag") { WebpHead.parse(mutate(0, 0x51)) }
        fails("WEBP form") { WebpHead.parse(mutate(8, 0x57 + 1)) }
        fails("riff size mismatch") { WebpHead.parse(mutate(4, 99)) }
        fails("VP8X payload must be 10") { WebpHead.parse(mutate(16, 11)) }
        fails("unknown first chunk") { WebpHead.parse(mutate(12, 0x41)) }
        fails("short file") { WebpHead.parse(fix.copyOf(19)) }
        run { // canvas 2^24 x 2^24 overflows the 2^32-1 pixel budget
            val b = fix.copyOf()
            for (off in 24..29) b[off] = 0xFF.toByte()
            fails("canvas pixel overflow") { WebpHead.parse(b) }
        }
    }

    fun semver() {
        val chain = listOf(
            "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta",
            "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0",
        ).map { SemVer.parse(it) }
        for (k in 0 until chain.size - 1) {
            ok(chain[k] < chain[k + 1], "semver.org item 11 chain: ${chain[k]} < ${chain[k + 1]}")
        }
        ok(SemVer.parse("1.0.0").compareTo(SemVer.parse("1.0.0")) == 0, "equal versions")
        ok(SemVer.parse("1.0.0+a").compareTo(SemVer.parse("1.0.0+b")) == 0, "build ignored in precedence")
        val v = SemVer.parse("1.2.3-alpha.7+exp.sha.5114f85")
        ok(v.major == 1L && v.minor == 2L && v.patch == 3L, "core fields")
        ok(v.prerelease == listOf("alpha", "7"), "prerelease identifiers")
        ok(v.build == listOf("exp", "sha", "5114f85"), "build identifiers")
        ok(v.toString() == "1.2.3-alpha.7+exp.sha.5114f85", "toString round trip")
        ok(SemVer.parse("2.0.0") > SemVer.parse("1.99.99"), "major wins")
        ok(SemVer.parse("1.0.0-1") < SemVer.parse("1.0.0-a"), "numeric < alphanumeric")
        ok(SemVer.parse("1.0.0-2") < SemVer.parse("1.0.0-11"), "numeric identifiers compare numerically")
        ok(SemVer.parse("1.0.0+001").build == listOf("001"), "build may carry leading zeroes")
        fails("leading zero major") { SemVer.parse("01.0.0") }
        fails("leading zero numeric prerelease") { SemVer.parse("1.0.0-01") }
        fails("empty prerelease identifier") { SemVer.parse("1.0.0-") }
        fails("two-part core") { SemVer.parse("1.2") }
        fails("underscore identifier") { SemVer.parse("1.0.0-al_pha") }
        fails("negative major") { SemVer.parse("-1.0.0") }
        fails("empty dot identifier") { SemVer.parse("1.0.0-a..b") }
    }

    fun uuid() {
        val v7 = UuidInfo.parse("017F22E2-79B0-7CC3-98C4-DC0C0C07398F")
        ok(v7.version == 7, "RFC 9562 v7 vector: version 7")
        ok(v7.variant == UuidInfo.Variant.RFC9562, "v7 vector variant 10xx")
        ok(v7.unixMs == 1645557742000L, "v7 vector timestamp 1645557742000 ms")
        ok(v7.hex.startsWith("017f22e279b0"), "hex folded to lowercase")
        ok(v7.hex.length == 32, "32 hex chars")
        val nil = UuidInfo.parse("00000000-0000-0000-0000-000000000000")
        ok(nil.isNil, "nil uuid")
        ok(nil.variant == UuidInfo.Variant.NCS && nil.version == null, "nil is NCS variant, no version")
        val max = UuidInfo.parse("ffffffff-ffff-ffff-ffff-ffffffffffff")
        ok(max.isMax, "max uuid")
        ok(max.variant == UuidInfo.Variant.RESERVED, "max is reserved variant 111x")
        val v1 = UuidInfo.parse("f81d4fae-7dec-11d0-a765-00a0c91e6bf6")
        ok(v1.version == 1 && v1.variant == UuidInfo.Variant.RFC9562, "RFC 4122 v1 example")
        ok(v1.unixMs == null, "no unix ms for non-v7")
        val ms = UuidInfo.parse("12345678-1234-4123-c123-123456789abc")
        ok(ms.variant == UuidInfo.Variant.MICROSOFT && ms.version == null, "110x Microsoft variant, version suppressed")
        val bVar = UuidInfo.parse("12345678-1234-4123-b123-123456789abc")
        ok(bVar.variant == UuidInfo.Variant.RFC9562 && bVar.version == 4, "b nibble is 10xx variant, v4")
        fails("35 chars") { UuidInfo.parse("017F22E2-79B0-7CC3-98C4-DC0C0C07398") }
        fails("missing hyphen at 8") { UuidInfo.parse("017F22E2x79B0-7CC3-98C4-DC0C0C07398F") }
        fails("hyphen in hex position") { UuidInfo.parse("-17F22E2-79B0-7CC3-98C4-DC0C0C07398F") }
        fails("non-hex char") { UuidInfo.parse("017F22E2-79B0-7CC3-98C4-DC0C0C07398G") }
    }

    fun range() {
        val a = HttpRange.resolve("bytes=0-499", 1000)
        ok(a.size == 1 && a[0].first == 0L && a[0].last == 499L, "int-range 0-499")
        ok(a[0].count == 500L, "count 500")
        val open = HttpRange.resolve("bytes=500-", 1000)
        ok(open[0].first == 500L && open[0].last == 999L, "open-ended 500-")
        val suffix = HttpRange.resolve("bytes=-500", 1000)
        ok(suffix[0].first == 500L && suffix[0].last == 999L, "suffix -500 is final 500 bytes")
        val wholeSuffix = HttpRange.resolve("bytes=-1000000", 1000)
        ok(wholeSuffix[0].first == 0L && wholeSuffix[0].last == 999L, "long suffix selects whole representation")
        val clamp = HttpRange.resolve("bytes=0-9999", 1000)
        ok(clamp[0].last == 999L, "last-pos clamped to L-1")
        val multi = HttpRange.resolve("bytes=0-0,-1", 1000)
        ok(multi.size == 2 && multi[1].first == 999L && multi[1].last == 999L, "RFC 9110 first-and-last example")
        val ows = HttpRange.resolve("bytes=0-0, 500-", 1000)
        ok(ows.size == 2 && ows[1].first == 500L, "optional whitespace after comma")
        val empties = HttpRange.resolve("bytes=0-0,,500-", 1000)
        ok(empties.size == 2, "empty list elements ignored")
        val partial = HttpRange.resolve("bytes=5000-,0-0", 1000)
        ok(partial.size == 1 && partial[0].first == 0L, "unsatisfiable member skipped when another satisfies")
        fails("all members unsatisfiable (416)") { HttpRange.resolve("bytes=5000-", 1000) }
        fails("suffix-length 0 selects no bytes") { HttpRange.resolve("bytes=-0", 1000) }
        fails("last-pos < first-pos") { HttpRange.resolve("bytes=5-2", 1000) }
        fails("unsupported unit") { HttpRange.resolve("items=0-1", 1000) }
        fails("missing =") { HttpRange.resolve("bytes 0-1", 1000) }
        fails("non-digit position") { HttpRange.resolve("bytes=a-b", 1000) }
        fails("empty range-set") { HttpRange.resolve("bytes=", 1000) }
        fails("empty representation has no satisfiable suffix") { HttpRange.resolve("bytes=-5", 0) }
        fails("negative complete length") { HttpRange.resolve("bytes=0-1", -1) }
    }

    fun qp() {
        ok(QuotedPrint.decode("ab=3Dcd") == "ab=cd", "=3D decodes to =")
        ok(QuotedPrint.decode("foo=\r\nbar") == "foobar", "CRLF soft break")
        ok(QuotedPrint.decode("foo=\nbar") == "foobar", "bare LF soft break")
        ok(QuotedPrint.decode("a\r\nb") == "a\r\nb", "hard break preserved")
        ok(QuotedPrint.decode("a\nb") == "a\r\nb", "LF hard break normalized to CRLF")
        ok(QuotedPrint.decode("=41=42") == "AB", "=41=42 -> AB")
        ok(QuotedPrint.decode("a\tb c") == "a\tb c", "tab and space literal")
        ok(QuotedPrint.encode("=") == "=3D", "encode equals sign")
        ok(QuotedPrint.encode("a\nb") == "a\r\nb", "encode hard break as CRLF")
        ok(QuotedPrint.encode("\u0007") == "=07", "encode control char")
        run {
            val long = "a".repeat(100)
            val enc = QuotedPrint.encode(long)
            ok(enc.contains("=\r\n"), "long line soft-wrapped")
            ok(enc.split("\r\n").all { it.length <= 76 }, "all encoded lines within 76")
            ok(QuotedPrint.decode(enc) == long, "long line round trip")
        }
        ok(QuotedPrint.decode(QuotedPrint.encode("café =\t100%")) == "café =\t100%", "mixed round trip")
        fails("lowercase escape digits") { QuotedPrint.decode("=3d") }
        fails("bad escape digit") { QuotedPrint.decode("=G1") }
        fails("soft break at end of input") { QuotedPrint.decode("abc=") }
        fails("truncated escape") { QuotedPrint.decode("ab=4") }
        fails("line longer than 76") { QuotedPrint.decode("a".repeat(77)) }
        fails("bare control char") { QuotedPrint.decode("a\u0007b") }
    }

    fun pem() {
        val block = PemBound.parse("-----BEGIN CERTIFICATE-----\naGVsbG8=\n-----END CERTIFICATE-----\n")
        ok(block.label == "CERTIFICATE", "label read")
        ok(block.bytes.size == 5 && String(block.bytes) == "hello", "body decoded")
        run {
            val data = ByteArray(80) { 0x41 }
            val b64 = java.util.Base64.getEncoder().encodeToString(data)
            ok(b64.length == 108, "80 bytes -> 108 base64 chars")
            val text = "-----BEGIN RAW DATA-----\n" + b64.substring(0, 64) + "\n" + b64.substring(64) +
                "\n-----END RAW DATA-----"
            val two = PemBound.parse(text)
            ok(two.bytes.contentEquals(data), "64-char interior line + short final line")
            ok(two.label == "RAW DATA", "label with interior space")
        }
        fails("label mismatch") { PemBound.parse("-----BEGIN A-----\naGVsbG8=\n-----END B-----") }
        fails("empty label") { PemBound.parse("-----BEGIN -----\naGVsbG8=\n-----END -----") }
        fails("label leading separator") { PemBound.checkLabel(" X") }
        fails("label trailing separator") { PemBound.checkLabel("X-") }
        fails("label double separator") { PemBound.checkLabel("A  B") }
        fails("interior line must be 64") {
            PemBound.parse("-----BEGIN X-----\nabcd\nabcd\n-----END X-----")
        }
        fails("non-alphabet base64") { PemBound.parse("-----BEGIN X-----\naGV!bG8=\n-----END X-----") }
        fails("length not multiple of 4") { PemBound.parse("-----BEGIN X-----\naGVsbG8\n-----END X-----") }
        fails("non-canonical trailing bits") { PemBound.b64decode("QUJDRB==") }
        ok(PemBound.b64decode("QUJDRA==").contentEquals(bytes("ABCD")), "canonical final quantum accepted")
        fails("too much padding") { PemBound.b64decode("QQ======") }
        fails("empty base64") { PemBound.b64decode("") }
        fails("second block") {
            PemBound.parse(
                "-----BEGIN X-----\naGVsbG8=\n-----END X-----\n-----BEGIN X-----\naGVsbG8=\n-----END X-----"
            )
        }
        fails("text before BEGIN") { PemBound.parse("garbage\n-----BEGIN X-----\naGVsbG8=\n-----END X-----") }
        fails("END without BEGIN") { PemBound.parse("aGVsbG8=\n-----END X-----") }
    }

    fun isoweek() {
        fun wd(y: Int, m: Int, d: Int) = IsoWeek.weekDate(y, m, d)
        ok(wd(2026, 1, 1) == IsoWeek.WeekDate(2026, 1, 4), "2026-01-01 = 2026-W01-4")
        ok(wd(2026, 10, 23) == IsoWeek.WeekDate(2026, 43, 5), "2026-10-23 = 2026-W43-5 (today)")
        ok(wd(2021, 1, 1) == IsoWeek.WeekDate(2020, 53, 5), "2021-01-01 belongs to 2020-W53")
        ok(wd(2016, 1, 3) == IsoWeek.WeekDate(2015, 53, 7), "2016-01-03 belongs to 2015-W53")
        ok(wd(2020, 12, 31) == IsoWeek.WeekDate(2020, 53, 4), "2020-12-31 = 2020-W53-4")
        ok(wd(1995, 1, 1) == IsoWeek.WeekDate(1994, 52, 7), "1995-01-01 belongs to 1994-W52")
        ok(wd(2024, 12, 30) == IsoWeek.WeekDate(2025, 1, 1), "2024-12-30 already in 2025-W01")
        ok(IsoWeek.dayOfWeek(1970, 1, 1) == 4, "epoch was a Thursday")
        ok(IsoWeek.dayOfWeek(2000, 2, 29) == 2, "2000-02-29 was a Tuesday")
        ok(IsoWeek.daysFromCivil(1970, 1, 1) == 0L, "epoch day zero")
        ok(IsoWeek.daysFromCivil(2000, 3, 1) == 11017L, "2000-03-01 is day 11017")
        ok(IsoWeek.isLeap(2000), "2000 leap (400 rule)")
        ok(!IsoWeek.isLeap(1900), "1900 not leap (100 rule)")
        ok(IsoWeek.isLeap(2024) && !IsoWeek.isLeap(2026), "2024 leap, 2026 not")
        ok(IsoWeek.daysInMonth(2024, 2) == 29 && IsoWeek.daysInMonth(2026, 2) == 28, "February lengths")
        ok(IsoWeek.longYear(2020), "2020 has 53 weeks (leap, Jan 1 Wednesday)")
        ok(IsoWeek.longYear(2026), "2026 has 53 weeks (Jan 1 Thursday)")
        ok(!IsoWeek.longYear(2025), "2025 has 52 weeks")
        fails("month 13") { IsoWeek.weekDate(2026, 13, 1) }
        fails("Feb 30") { IsoWeek.weekDate(2026, 2, 30) }
        fails("year 0 out of declared range") { IsoWeek.daysFromCivil(0, 1, 1) }
    }

    fun hsl() {
        val red = HslColor.hslToRgb(0.0, 1.0, 0.5)
        close(red.r, 1.0, 1e-12, "red r")
        close(red.g, 0.0, 1e-12, "red g")
        close(red.b, 0.0, 1e-12, "red b")
        val pin = HslColor.hslToRgb(210.0, 0.5, 0.4)
        close(pin.r, 0.2, 1e-12, "pinned 210/50/40 r (colorsys mirror)")
        close(pin.g, 0.4, 1e-12, "pinned 210/50/40 g")
        close(pin.b, 0.6, 1e-12, "pinned 210/50/40 b")
        ok(HslColor.hslToRgb8(120.0, 1.0, 0.5) == Triple(0, 255, 0), "green 8-bit")
        ok(HslColor.hslToRgb8(240.0, 1.0, 0.5) == Triple(0, 0, 255), "blue 8-bit")
        ok(HslColor.hslToRgb8(60.0, 1.0, 0.5) == Triple(255, 255, 0), "yellow 8-bit")
        ok(HslColor.hslToRgb8(0.0, 0.0, 1.0) == Triple(255, 255, 255), "white")
        ok(HslColor.hslToRgb8(0.0, 1.0, 0.0) == Triple(0, 0, 0), "black")
        val gray = HslColor.hslToRgb(123.0, 0.0, 0.5)
        ok(gray.r == gray.g && gray.g == gray.b, "s=0 is achromatic")
        val neg = HslColor.hslToRgb(-150.0, 0.5, 0.4)
        close(neg.r, pin.r, 1e-12, "-150 wraps to 210")
        val big = HslColor.hslToRgb(570.0, 0.5, 0.4)
        close(big.b, pin.b, 1e-12, "570 wraps to 210")
        fails("saturation below 0") { HslColor.hslToRgb(0.0, -0.1, 0.5) }
        fails("saturation above 1") { HslColor.hslToRgb(0.0, 1.1, 0.5) }
        fails("lightness above 1") { HslColor.hslToRgb(0.0, 0.5, 2.0) }
        fails("NaN hue") { HslColor.hslToRgb(Double.NaN, 0.5, 0.5) }
    }

    private fun fixSum(b: ByteArray) {
        b[127] = 0
        var s = 0
        for (i in 0..126) s += b[i].toInt() and 0xFF
        b[127] = ((256 - (s and 0xFF)) and 0xFF).toByte()
    }

    fun edid(fixtures: File) {
        val fix = File(fixtures, "edid.bin").readBytes()
        val i = EdidHead.parse(fix)
        ok(i.manufacturer == "DEL", "0x10AC decodes to DEL")
        ok(i.productCode == 0xA0C5, "product code little-endian")
        ok(i.serial == 0x01020304L, "serial little-endian")
        ok(i.week == 2, "week 2")
        ok(i.year == 2020, "year 1990+30")
        ok(!i.modelYearFlag, "week byte 2 is not the model-year flag")
        ok(i.version == 1 && i.revision == 4, "EDID 1.4")
        ok(i.extensionBlocks == 0, "no extensions")
        run {
            val b = fix.copyOf(); b[8] = 0x24; b[9] = 0x4D; fixSum(b)
            ok(EdidHead.parse(b).manufacturer == "IBM", "wikipedia example 24 4D -> IBM")
        }
        run {
            val b = fix.copyOf(); b[16] = 0; fixSum(b)
            ok(EdidHead.parse(b).week == null, "week 0 means unspecified")
        }
        run {
            val b = fix.copyOf(); b[16] = 0xFF.toByte(); fixSum(b)
            val info = EdidHead.parse(b)
            ok(info.modelYearFlag && info.week == null, "0xFF week is the model-year flag")
        }
        run { val b = fix.copyOf(); b[127] = (b[127] + 1).toByte(); fails("checksum must be 0 mod 256") { EdidHead.parse(b) } }
        run { val b = fix.copyOf(); b[3] = 0; fails("broken header pattern") { EdidHead.parse(b) } }
        fails("127-byte block") { EdidHead.parse(fix.copyOf(127)) }
        run { val b = fix.copyOf(); b[8] = (b[8].toInt() or 0x80).toByte(); fixSum(b); fails("reserved manufacturer bit") { EdidHead.parse(b) } }
        run { val b = fix.copyOf(); b[8] = 0; b[9] = 1; fixSum(b); fails("letter code 0") { EdidHead.parse(b) } }
        run { val b = fix.copyOf(); b[16] = 55; fixSum(b); fails("week 55") { EdidHead.parse(b) } }
        run { val b = fix.copyOf(); b[18] = 2; fixSum(b); fails("EDID version 2") { EdidHead.parse(b) } }
    }

    fun vtt() {
        ok(VttTime.parseTimestamp("00:01.000") == 1000L, "mm:ss.ttt short form")
        ok(VttTime.parseTimestamp("00:00:22.230") == 22230L, "hh:mm:ss.ttt")
        ok(VttTime.parseTimestamp("9999:00:00.000") == 9999L * 3_600_000, "hours may exceed two digits")
        ok(VttTime.parseTimestamp("0100:00:00.000") == 100L * 3_600_000, "4-digit hours")
        ok(VttTime.parseTimestamp("01:02:03.004") == 3_723_004L, "mixed components")
        val cue = VttTime.parseCueTimings("00:01.000 --> 00:04.000")
        ok(cue.startMs == 1000L && cue.endMs == 4000L, "cue start/end")
        ok(cue.settings.isEmpty(), "no settings")
        val set = VttTime.parseCueTimings("00:00.000 --> 00:01.000 align:start position:0%")
        ok(set.settings == listOf("align:start", "position:0%"), "cue settings split")
        val tabbed = VttTime.parseCueTimings("00:01.000\t-->\t00:04.000")
        ok(tabbed.endMs == 4000L, "tab separators accepted")
        fails("end equals start") { VttTime.parseCueTimings("00:01.000 --> 00:01.000") }
        fails("end before start") { VttTime.parseCueTimings("00:04.000 --> 00:01.000") }
        fails("one-digit hours") { VttTime.parseTimestamp("1:00:00.000") }
        fails("minutes 60") { VttTime.parseTimestamp("60:00.000") }
        fails("seconds 60") { VttTime.parseTimestamp("00:60.000") }
        fails("two-digit millis") { VttTime.parseTimestamp("00:01.00") }
        fails("SRT comma millis") { VttTime.parseTimestamp("00:01,000") }
        fails("no space before arrow") { VttTime.parseCueTimings("00:01.000--> 00:02.000") }
        fails("no space after arrow") { VttTime.parseCueTimings("00:01.000 -->00:02.000") }
        fails("missing end timestamp") { VttTime.parseCueTimings("00:01.000 --> ") }
    }

    fun luhn() {
        ok(LuhnCheck.isValid("4111111111111111"), "classic test PAN valid")
        ok(LuhnCheck.isValid("79927398713"), "canonical Luhn example valid")
        ok(!LuhnCheck.isValid("79927398710"), "wrong check digit invalid")
        ok(!LuhnCheck.isValid("4111111111111112"), "single-digit error detected")
        ok(!LuhnCheck.isValid("4111111111111161"), "adjacent transposition 16<->61 detected")
        ok(LuhnCheck.checkDigit("7992739871") == 3, "check digit 3")
        ok(LuhnCheck.checkDigit("411111111111111") == 1, "check digit closes test PAN")
        ok(LuhnCheck.maskPan("4111111111111111") == "411111******1111", "PCI DSS first6+last4")
        ok(LuhnCheck.maskPan("4222222222222") == "422222***2222", "13-digit PAN mask")
        ok(
            LuhnCheck.scrubText("card 4111111111111111 exp 12/27") == "card 411111******1111 exp 12/27",
            "scrub embedded PAN"
        )
        ok(LuhnCheck.scrubText("call 9195551234 now") == "call 9195551234 now", "10-digit phone untouched")
        ok(LuhnCheck.scrubText("ref 4111111111111112") == "ref 4111111111111112", "non-Luhn 16-digit run untouched")
        ok(
            LuhnCheck.scrubText("a 79927398713 b") == "a 79927398713 b",
            "11-digit Luhn-valid run is below PAN length floor"
        )
        fails("mask non-Luhn") { LuhnCheck.maskPan("1234567890123456") }
        fails("mask 12 digits") { LuhnCheck.maskPan("411111111111") }
        fails("empty digits") { LuhnCheck.isValid("") }
        fails("non-digit input") { LuhnCheck.isValid("4111-1111") }
    }

    fun soundex() {
        ok(Soundex.encode("Tymczak") == "T522", "NARA vector Tymczak (vowel separates Z/K)")
        ok(Soundex.encode("Ashcraft") == "A261", "NARA vector Ashcraft (H transparent)")
        ok(Soundex.encode("Ashcroft") == "A261", "Ashcroft same bucket")
        ok(Soundex.encode("Pfister") == "P236", "first-letter side-by-side rule (P/F)")
        ok(Soundex.encode("Robert") == "R163", "Robert R163")
        ok(Soundex.encode("Rupert") == "R163", "Rupert R163")
        ok(Soundex.encode("Honeyman") == "H555", "vowel-separated same codes all kept")
        ok(Soundex.encode("Washington") == "W252", "Washington W252")
        ok(Soundex.encode("Lee") == "L000", "zero padding")
        ok(Soundex.encode("Gutierrez") == "G362", "Gutierrez G362")
        ok(Soundex.encode("Jackson") == "J250", "Jackson J250")
        ok(Soundex.encode("VanDeusen") == "V532", "VanDeusen V532")
        ok(Soundex.encode("Lloyd") == "L300", "double letter collapses")
        ok(Soundex.encode("Burroughs") == "B620", "G/S same code across H coded once")
        ok(Soundex.encode("robert") == "R163", "case-insensitive")
        ok(Soundex.matches("Robert", "Rupert"), "matches same bucket")
        ok(!Soundex.matches("Robert", "Ashcraft"), "different buckets differ")
        fails("empty name") { Soundex.encode("") }
        fails("apostrophe must be stripped by caller") { Soundex.encode("O'Brien") }
    }

    fun pdfname() {
        ok(PdfName.decode("/Name1") == "Name1", "Table 4: Name1")
        ok(PdfName.decode("/paired#28#29parentheses") == "paired()parentheses", "Table 4: escaped parens")
        ok(PdfName.decode("/The_Key_of_F#23_Minor") == "The_Key_of_F#_Minor", "Table 4: #23 is #")
        ok(PdfName.decode("/A#42") == "AB", "Table 4: #42 is B")
        ok(PdfName.decode("/A;Name_With-Various***Characters?") == "A;Name_With-Various***Characters?", "regular chars pass")
        ok(PdfName.decode("/") == "", "zero-length name is legal")
        ok(PdfName.encode("a b") == "/a#20b", "space escapes")
        ok(PdfName.encode("#") == "/#23", "# must self-escape")
        ok(PdfName.encode("paired()parentheses") == "/paired#28#29parentheses", "delimiters escape")
        ok(PdfName.encode("caf\u00E9") == "/caf#E9", "8-bit byte escapes")
        run {
            val weird = "x #/()<>[]{}%\u00FF"
            ok(PdfName.decode(PdfName.encode(weird)) == weird, "round trip")
        }
        fails("missing solidus") { PdfName.decode("Name1") }
        fails("the token #00") { PdfName.decode("/bad#00name") }
        fails("truncated escape") { PdfName.decode("/bad#4") }
        fails("bad hex escape") { PdfName.decode("/bad#4G") }
        fails("unescaped space") { PdfName.decode("/a b") }
        fails("unescaped delimiter") { PdfName.decode("/a(b") }
    }

    fun cadence() {
        val fast = List(10) { 10.0 }
        ok(KeyCadence.classify(fast).verdict == KeyCadence.Verdict.MACHINE_LIKE, "10ms strokes are machine-like")
        val uniform = List(10) { 100.0 }
        ok(KeyCadence.classify(uniform).verdict == KeyCadence.Verdict.MACHINE_LIKE, "zero jitter is machine-like")
        val blazing = List(10) { 4.9 }
        ok(KeyCadence.classify(blazing).ratePerSecond > 200.0, "4.9ms exceeds 200 keys/s")
        val human = listOf(60.0, 140.0, 80.0, 120.0, 70.0, 130.0, 90.0, 110.0)
        val h = KeyCadence.classify(human)
        ok(h.verdict == KeyCadence.Verdict.HUMAN_PLAUSIBLE, "100ms mean with 27ms jitter is human-plausible")
        close(h.meanMs, 100.0, 1e-9, "mean 100ms")
        close(h.sdMs, Math.sqrt(750.0), 1e-9, "population SD sqrt(750)")
        close(h.ratePerSecond, 10.0, 1e-9, "10 keys per second")
        val slow = listOf(250.0, 350.0, 280.0, 320.0, 260.0, 340.0, 290.0, 310.0)
        ok(KeyCadence.classify(slow).verdict == KeyCadence.Verdict.INDETERMINATE, "slow hunt-and-peck is indeterminate")
        val lowJitter = listOf(90.0, 110.0, 90.0, 110.0, 90.0, 110.0, 90.0, 110.0)
        ok(KeyCadence.classify(lowJitter).verdict == KeyCadence.Verdict.INDETERMINATE, "human speed but 10ms SD is indeterminate, not safe")
        ok(KeyCadence.SCOPE.contains("inline hardware"), "scope admits hardware blindness")
        ok(KeyCadence.MACHINE_RATE_PER_S == 200.0 && KeyCadence.MACHINE_SD_MS == 1.5, "published thresholds pinned")
        ok(KeyCadence.HUMAN_MEAN_LOW_MS < KeyCadence.HUMAN_MEAN_HIGH_MS, "bounds ordered")
        close(KeyCadence.HUMAN_MEAN_LOW_MS, 1000.0 / 18.0, 1e-12, "18 keys/s upper human bound")
        fails("seven samples") { KeyCadence.classify(List(7) { 100.0 }) }
        fails("zero interval") { KeyCadence.classify(List(8) { if (it == 3) 0.0 else 100.0 }) }
        fails("NaN interval") { KeyCadence.classify(List(8) { if (it == 3) Double.NaN else 100.0 }) }
        fails("negative interval") { KeyCadence.classify(List(8) { if (it == 3) -5.0 else 100.0 }) }
    }
}

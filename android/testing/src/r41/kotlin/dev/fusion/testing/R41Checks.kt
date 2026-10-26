package dev.fusion.testing

import dev.fusion.ooxml.ColLetters
import dev.fusion.ooxml.SheetRange
import dev.fusion.ooxml.R1c1Ref
import dev.fusion.format.pdf.CropBox
import dev.fusion.format.pdf.PdfRotate
import dev.fusion.format.pdf.PdfPageGeometry
import dev.fusion.commands.ShlexJoin
import dev.fusion.automation.DurationIso
import dev.fusion.maps.DmsFormat
import dev.fusion.model.ColorHex
import dev.fusion.a11y.ReadEase
import dev.fusion.lens.Ean13
import dev.fusion.browser.model.DataUri
import dev.fusion.index.Trigram
import dev.fusion.together.TurnOrder
import dev.fusion.perf.Ewma
import dev.fusion.settings.KeyMigrate
import dev.fusion.castv2.CastNs
import dev.fusion.documents.LineWidth
import dev.fusion.storage.ShardPath
import dev.fusion.find.SpanMerge
import dev.fusion.beam.PiecePlan
import dev.fusion.editor.TabStops
import dev.fusion.launcher.GridPos

/**
 * R41 executed contracts. Host JVM evidence only — NOT Android, device,
 * service or acceptance evidence. Every `ok` is one counted assertion
 * against a production kernel compiled in isolation (the ooxml and pdf
 * groups compile their intra-module dependencies together, nothing else).
 */
object R41Checks {
    private var count = 0

    private fun ok(cond: Boolean, msg: String) {
        if (!cond) throw AssertionError(msg)
        count++
    }

    private fun refuses(msg: String, block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { count++; return }
        throw AssertionError("expected refusal: $msg")
    }

    private fun near(a: Double, b: Double, eps: Double) = kotlin.math.abs(a - b) <= eps

    private fun sheetRange() {
        val r = SheetRange.parse("A1:B2")
        ok(r.top == SheetRange.Cell(1, 1) && r.bottom == SheetRange.Cell(2, 2), "A1:B2 corners")
        ok(SheetRange.parse("B2:A1") == r, "corner order normalizes")
        ok(r.width == 2 && r.height == 2 && r.cells == 4L, "dimensions")
        ok(r.toA1() == "A1:B2", "canonical text")
        val cell = SheetRange.parse("C5")
        ok(cell.top == cell.bottom && cell.toA1() == "C5", "single cell range")
        ok(SheetRange.parseCell("XFD1048576") == SheetRange.Cell(16384, 1048576), "grid corner")
        ok(SheetRange.contains(r, SheetRange.Cell(2, 1)) && !SheetRange.contains(r, SheetRange.Cell(3, 1)), "containment")
        ok(SheetRange.intersect(SheetRange.parse("A1:C3"), SheetRange.parse("B2:D4")) == SheetRange.parse("B2:C3"), "intersection")
        ok(SheetRange.intersect(SheetRange.parse("A1:B2"), SheetRange.parse("C3:D4")) == null, "disjoint -> null")
        ok(SheetRange.union(SheetRange.parse("A1:B2"), SheetRange.parse("C3:D4")) == SheetRange.parse("A1:D4"), "bounding box")
        ok(SheetRange.parse("A1:XFD1048576").cells == 16384L * 1048576, "whole-grid cell count")
        ok(SheetRange.intersect(r, r) == r, "self intersection")
        ok(SheetRange.union(r, r) == r, "self union")
        ok(SheetRange.parse("B2").let { SheetRange.contains(r, it.top) }, "single-cell containment")
        ok(SheetRange.parseCell("a1") == SheetRange.Cell(1, 1), "lowercase column letters canonicalize")
        refuses("absolute marker") { SheetRange.parseCell("\$A\$1") }
        refuses("sheet prefix") { SheetRange.parseCell("Sheet1!A1") }
        refuses("whole column") { SheetRange.parse("A:A") }
        ok(SheetRange.parse("D4:D4") == SheetRange.parse("D4"), "degenerate range equals cell")
        ok(SheetRange.parse("A1:A10").let { it.width == 1 && it.height == 10 }, "column strip")
        refuses("leading zero row") { SheetRange.parseCell("A01") }
        refuses("row zero") { SheetRange.parseCell("A0") }
        refuses("row beyond grid") { SheetRange.parseCell("A1048577") }
        refuses("three corners") { SheetRange.parse("A1:B2:C3") }
        refuses("letters after digits") { SheetRange.parseCell("A1B") }
    }

    private fun r1c1() {
        ok(R1c1Ref.fromA1("C5") == "R5C3", "A1 to absolute R1C1")
        ok(R1c1Ref.toA1("R5C3", 1, 1) == "C5", "absolute ignores anchor")
        ok(R1c1Ref.toA1("R[2]C[-1]", 3, 10) == "B12", "relative offsets")
        ok(R1c1Ref.toA1("RC", 4, 7) == "D7", "bare RC is the anchor itself")
        ok(R1c1Ref.toA1("R[0]C[0]", 4, 7) == "D7", "explicit zero offsets")
        ok(R1c1Ref.toA1("R5C[2]", 3, 1) == "E5", "mixed absolute row relative col")
        ok(R1c1Ref.relativeFromA1("B12", 3, 10) == "R[2]C[-1]", "relative inverse")
        ok(R1c1Ref.relativeFromA1("D7", 4, 7) == "RC", "self is RC")
        var rt = true
        for (a1 in arrayOf("A1", "C5", "XFD1048576", "B12", "AZ52")) {
            if (R1c1Ref.toA1(R1c1Ref.fromA1(a1), 1, 1) != a1) rt = false
        }
        ok(rt, "absolute roundtrip set")
        ok(R1c1Ref.parse("R[-0]C").rowOff == 0, "negative zero offset normalizes")
        ok(R1c1Ref.toA1("R1C16384", 1, 1) == "XFD1", "absolute grid edge")
        ok(R1c1Ref.relativeFromA1("A1", 16384, 1048576) == "R[-1048575]C[-16383]", "extreme relative")
        ok(R1c1Ref.toA1(R1c1Ref.relativeFromA1("E9", 2, 3), 2, 3) == "E9", "relative roundtrip through anchor")
        refuses("offset leaves grid left") { R1c1Ref.toA1("C[-1]R", 1, 1) }
        refuses("offset leaves grid up") { R1c1Ref.toA1("R[-1]C", 1, 1) }
        refuses("row zero absolute") { R1c1Ref.parse("R0C1") }
        ok(R1c1Ref.parse("R3C").rowAbs == 3 && R1c1Ref.parse("R3C").colOff == 0, "absolute row bare col")
        refuses("column beyond grid absolute") { R1c1Ref.parse("R1C16385") }
        refuses("not r1c1 at all") { R1c1Ref.parse("A1") }
        refuses("anchor outside grid") { R1c1Ref.toA1("RC", 0, 1) }
    }

    private fun pageGeometry() {
        val d = PdfPageGeometry.letterUpright()
        ok(d.widthPt == 612.0 && d.heightPt == 792.0 && d.rotation == 0, "letter upright")
        ok(near(d.widthInches, 8.5, 1e-12) && near(d.heightInches, 11.0, 1e-12), "inches at 72dpi")
        ok(d.portrait, "letter is portrait")
        val rotated = PdfPageGeometry.compose(CropBox.LETTER, null, listOf(null, 90))
        ok(rotated.widthPt == 792.0 && rotated.heightPt == 612.0 && !rotated.portrait, "inherited 90 swaps")
        val cropped = PdfPageGeometry.compose(
            CropBox.LETTER, CropBox.normalize(36.0, 36.0, 576.0, 756.0), listOf(270)
        )
        ok(cropped.box == CropBox.Rect(36.0, 36.0, 576.0, 756.0), "crop box kept")
        ok(cropped.widthPt == 720.0 && cropped.heightPt == 540.0, "cropped then rotated size")
        ok(PdfPageGeometry.compose(CropBox.A4, null, listOf(180)).portrait, "A4 at 180 stays portrait")
        ok(PdfPageGeometry.compose(CropBox.LETTER, null, listOf(null, null, 450)).rotation == 90, "deep chain normalizes")
        ok(PdfPageGeometry.compose(CropBox.A4, null, listOf(90)).widthPt == 841.89, "A4 rotated width")
        ok(near(PdfPageGeometry.letterUpright().widthInches * 72.0, 612.0, 1e-9), "inches inverse")
        refuses("UserUnit refused") { PdfPageGeometry.compose(CropBox.LETTER, null, listOf(0), userUnit = 2.0) }
        refuses("disjoint crop propagates refusal") {
            PdfPageGeometry.compose(CropBox.LETTER, CropBox.normalize(700.0, 800.0, 900.0, 900.0), listOf(0))
        }
        refuses("non-right-angle propagates") { PdfPageGeometry.compose(CropBox.LETTER, null, listOf(45)) }
    }

    private fun shlexJoin() {
        ok(ShlexJoin.quote("abc-d_1.txt") == "abc-d_1.txt", "safe token stays bare")
        ok(ShlexJoin.quote("a b") == "'a b'", "space wraps")
        ok(ShlexJoin.quote("it's") == "'it'\"'\"'s'", "embedded quote escape")
        ok(ShlexJoin.quote("") == "''", "empty token")
        ok(ShlexJoin.quote("a;b") == "'a;b'" && ShlexJoin.quote("*") == "'*'", "metacharacters wrap")
        ok(ShlexJoin.join(listOf("ls", "-l", "my file", "o'brien")) == "ls -l 'my file' 'o'\"'\"'brien'", "join matches shlex.join")
        ok(ShlexJoin.quote("@%+=:,./-") == "@%+=:,./-", "whole safe-punct set bare")
        ok(ShlexJoin.quote("\$HOME") == "'\$HOME'", "dollar never expands")
        ok(ShlexJoin.quote("a\nb") == "'a\nb'", "newline wrapped not escaped")
        ok(ShlexJoin.quote("back\\slash") == "'back\\slash'", "backslash wraps, never escapes")
        ok(ShlexJoin.quote("tab\there") == "'tab\there'", "tab wraps")
        ok(ShlexJoin.join(listOf("")) == "''", "single empty arg")
        ok(ShlexJoin.quote("''") == "''\"'\"''\"'\"''", "token of two quotes")
        refuses("NUL") { ShlexJoin.quote("a\u0000b") }
        ok(ShlexJoin.join(listOf("echo", "")) == "echo ''", "empty arg preserved in join")
        ok(ShlexJoin.quote("~tilde") == "'~tilde'", "tilde never expands")
        ok(ShlexJoin.quote("colon:path/ok") == "colon:path/ok", "colon and slash are safe")
        refuses("empty command") { ShlexJoin.join(emptyList()) }
    }

    private fun durationIso() {
        ok(DurationIso.toSeconds(DurationIso.parse("PT1H30M")) == 5400L, "PT1H30M")
        ok(DurationIso.toSeconds(DurationIso.parse("P1W")) == 604800L, "one week")
        ok(DurationIso.toSeconds(DurationIso.parse("P2DT3H4M5S")) == 183845L, "mixed exact duration")
        ok(DurationIso.toSeconds(DurationIso.parse("PT0S")) == 0L, "zero duration")
        val ym = DurationIso.parse("P1Y2M3D")
        ok(ym.years == 1 && ym.months == 2 && ym.days == 3 && ym.calendarDependent, "calendar parts parse")
        refuses("calendar parts refuse conversion") { DurationIso.toSeconds(ym) }
        ok(DurationIso.format(DurationIso.parse("P2DT3H4M5S")) == "P2DT3H4M5S", "format roundtrip")
        ok(DurationIso.format(DurationIso.parse("PT0S")) == "PT0S", "zero formats canonically")
        ok(DurationIso.format(DurationIso.Dur(0, 0, 3, 0, 0, 0, 0)) == "P3W", "weeks format")
        ok(DurationIso.parse("PT90M").minutes == 90 && DurationIso.toSeconds(DurationIso.parse("PT90M")) == 5400L, "unnormalized components allowed")
        ok(DurationIso.parse("P365D").let { DurationIso.toSeconds(it) } == 31536000L, "days convert at fixed 86400")
        ok(DurationIso.format(DurationIso.parse("P1Y2M3D")) == "P1Y2M3D", "calendar duration formats back")
        ok(!DurationIso.parse("P1W").calendarDependent, "weeks are exact")
        refuses("bare P") { DurationIso.parse("P") }
        ok(DurationIso.parse("PT1H").let { it.hours == 1 && it.minutes == 0 }, "single time component")
        ok(DurationIso.format(DurationIso.Dur(2, 0, 0, 0, 0, 0, 0)) == "P2Y", "years-only format")
        refuses("dangling T") { DurationIso.parse("P1DT") }
        refuses("weeks mixed with days") { DurationIso.format(DurationIso.Dur(0, 0, 1, 2, 0, 0, 0)) }
        refuses("negative component") { DurationIso.parse("PT-5S") }
        refuses("fractional second") { DurationIso.parse("PT1.5S") }
        refuses("not a duration") { DurationIso.parse("1H30M") }
    }

    private fun dmsFormat() {
        ok(DmsFormat.formatLat(49.5) == "49\u00B030\u203200\u2033N", "49.5N")
        ok(DmsFormat.formatLon(-123.3686) == "123\u00B022\u203207\u2033W", "Vancouver longitude")
        ok(DmsFormat.formatLat(0.0) == "0\u00B000\u203200\u2033N", "equator is N by sign rule")
        ok(DmsFormat.formatLat(-89.9999999) == "90\u00B000\u203200\u2033S", "carry rolls to 90S cleanly")
        ok(DmsFormat.formatLon(179.99999999) == "180\u00B000\u203200\u2033E", "carry to 180E")
        ok(near(DmsFormat.parse("49\u00B030\u203200\u2033N"), 49.5, 1e-9), "parse back")
        ok(near(DmsFormat.parse("123\u00B022\u203207\u2033W"), -123.36861111, 1e-6), "W negates")
        val v = 37.77493
        ok(near(DmsFormat.parse(DmsFormat.formatLat(v, 2)), v, 0.5 / 360000.0 + 1e-9), "roundtrip within half of last digit")
        ok(DmsFormat.formatLat(90.0) == "90\u00B000\u203200\u2033N", "exact pole")
        ok(DmsFormat.formatLon(-0.001) .endsWith("W"), "tiny negative is W")
        ok(DmsFormat.formatLat(37.77493, 2) == "37\u00B046\u203229.75\u2033N", "two-decimal seconds")
        ok(near(DmsFormat.parse("0\u00B000\u203200\u2033S"), 0.0, 1e-12), "signed zero parses to zero")
        refuses("lat beyond 90") { DmsFormat.formatLat(90.0001) }
        ok(DmsFormat.split(0.5, 90.0).let { it.degrees == 0 && it.minutes == 30 }, "half degree splits")
        ok(DmsFormat.formatLon(0.0).endsWith("E"), "zero longitude is E by sign rule")
        refuses("minutes 60") { DmsFormat.parse("10\u00B060\u203200\u2033N") }
        refuses("seconds 60") { DmsFormat.parse("10\u00B000\u203260\u2033N") }
        refuses("lat 91 on parse") { DmsFormat.parse("91\u00B000\u203200\u2033N") }
        refuses("precision 5") { DmsFormat.formatLat(1.0, 5) }
    }

    private fun colorHex() {
        ok(ColorHex.parse("#FF8800") == ColorHex.Argb(255, 255, 136, 0), "RRGGBB")
        ok(ColorHex.parse("#F80") == ColorHex.parse("#FF8800"), "shorthand doubles")
        ok(ColorHex.parse("#80FF8800") == ColorHex.Argb(128, 255, 136, 0), "alpha FIRST (Android order)")
        ok(ColorHex.parse("#8F80") == ColorHex.Argb(136, 255, 136, 0), "four-digit shorthand")
        ok(ColorHex.format(ColorHex.Argb(255, 1, 2, 3)) == "#010203", "opaque omits alpha")
        ok(ColorHex.format(ColorHex.Argb(128, 1, 2, 3)) == "#80010203", "translucent keeps alpha")
        ok(ColorHex.format(ColorHex.Argb(255, 1, 2, 3), forceAlpha = true) == "#FF010203", "forced alpha")
        ok(ColorHex.toPackedInt(ColorHex.Argb(255, 255, 136, 0)) == 0xFFFF8800.toInt(), "packed int")
        ok(ColorHex.fromPackedInt(0x80FF8800.toInt()) == ColorHex.Argb(128, 255, 136, 0), "unpacked")
        var rt = true
        for (v in intArrayOf(0, 0xFF000000.toInt(), 0x12345678, -1)) {
            if (ColorHex.toPackedInt(ColorHex.fromPackedInt(v)) != v) rt = false
        }
        ok(rt, "packed roundtrip set")
        ok(ColorHex.parse(ColorHex.format(ColorHex.Argb(7, 8, 9, 10))) == ColorHex.Argb(7, 8, 9, 10), "text roundtrip")
        ok(ColorHex.parse("#ff8800") == ColorHex.parse("#FF8800"), "case-insensitive digits")
        ok(ColorHex.parse("#0000").alpha == 0, "fully transparent shorthand")
        ok(ColorHex.fromPackedInt(ColorHex.toPackedInt(ColorHex.Argb(1, 2, 3, 4))) == ColorHex.Argb(1, 2, 3, 4), "pack inverse")
        refuses("no hash") { ColorHex.parse("FF8800") }
        ok(ColorHex.Argb(255, 0, 0, 0).opaque && !ColorHex.Argb(254, 0, 0, 0).opaque, "opaque flag")
        ok(ColorHex.format(ColorHex.parse("#abc")) == "#AABBCC", "shorthand formats long uppercase")
        refuses("five digits") { ColorHex.parse("#12345") }
        refuses("bad digit") { ColorHex.parse("#GG0000") }
    }

    private fun readEase() {
        ok(near(ReadEase.fleschEase(100, 5, 150), 59.635, 1e-9), "pinned formula value")
        ok(near(ReadEase.kincaidGrade(100, 5, 150), 9.91, 1e-9), "pinned grade value")
        ok(ReadEase.band(59.635) == ReadEase.Band.FAIRLY_DIFFICULT, "59.6 bands fairly difficult")
        ok(ReadEase.band(95.0) == ReadEase.Band.VERY_EASY, "very easy band")
        ok(ReadEase.band(60.0) == ReadEase.Band.STANDARD, "60 boundary belongs to standard")
        ok(ReadEase.band(29.99) == ReadEase.Band.VERY_CONFUSING, "below 30")
        ok(ReadEase.band(120.0) == ReadEase.Band.OUT_OF_RANGE_HIGH, "monosyllabic short text can exceed 100 — reported")
        ok(ReadEase.band(-10.0) == ReadEase.Band.OUT_OF_RANGE_LOW, "legalese can go negative — reported")
        ok(ReadEase.fleschEase(10, 10, 10) > 100.0, "one-word one-syllable sentences exceed 100")
        ok(ReadEase.band(90.0) == ReadEase.Band.VERY_EASY && ReadEase.band(89.99) == ReadEase.Band.EASY, "90 boundary")
        ok(ReadEase.band(30.0) == ReadEase.Band.DIFFICULT && ReadEase.band(0.0) == ReadEase.Band.VERY_CONFUSING, "lower boundaries")
        ok(ReadEase.kincaidGrade(100, 5, 150) > ReadEase.kincaidGrade(100, 10, 150), "shorter sentences lower the grade")
        refuses("zero sentences") { ReadEase.fleschEase(10, 0, 12) }
        refuses("syllables below words") { ReadEase.fleschEase(10, 2, 9) }
        refuses("zero words grade") { ReadEase.kincaidGrade(0, 1, 1) }
    }

    private fun ean13() {
        ok(Ean13.checkDigit("400638133393") == 1, "classic EAN body")
        ok(Ean13.isValid("4006381333931"), "classic EAN validates")
        ok(Ean13.checkDigit("629104150021") == 3 && Ean13.isValid("6291041500213"), "second vector")
        ok(!Ean13.isValid("4006381333932"), "wrong check digit")
        ok(!Ean13.isValid("4006381333913"), "transposition caught here")
        ok(Ean13.validate("4006381333931") == "4006381333931", "validate returns canonical")
        ok(Ean13.upcToEan("036000291452") == "0036000291452", "UPC-A prefixes zero")
        ok(Ean13.checkDigit("000000000000") == 0, "all zeros")
        var allValid = true
        for (body in arrayOf("400638133393", "629104150021", "000000000000", "978014300723")) {
            val full = body + Ean13.checkDigit(body)
            if (!Ean13.isValid(full)) allValid = false
        }
        ok(allValid, "generated digits validate (property incl. an ISBN-13 body)")
        ok(Ean13.checkDigit("978014300723") == 4, "ISBN-13 shares the algebra")
        refuses("twelve digits to validate") { Ean13.validate("400638133393") }
        refuses("letters") { Ean13.validate("40063813339AB") }
        refuses("bad UPC check") { Ean13.upcToEan("036000291453") }
        refuses("eleven-digit UPC") { Ean13.upcToEan("03600029145") }
    }

    private fun dataUri() {
        val b = DataUri.parse("data:text/plain;base64,SGVsbG8=")
        ok(b.base64 && b.bytes.contentEquals("Hello".toByteArray()), "base64 payload")
        ok(b.mediatype == "text/plain" && b.parameters.isEmpty(), "explicit mediatype")
        val plain = DataUri.parse("data:,A%20brief%20note")
        ok(String(plain.bytes) == "A brief note", "RFC example percent-decodes")
        ok(plain.mediatype == DataUri.DEFAULT_MEDIATYPE && plain.parameters["charset"] == "US-ASCII", "RFC default type")
        val param = DataUri.parse("data:text/html;charset=utf-8,%3Cp%3Ehi%3C%2Fp%3E")
        ok(param.parameters["charset"] == "utf-8" && String(param.bytes) == "<p>hi</p>", "charset param + html")
        ok(DataUri.isDataUrl("data:,x") && !DataUri.isDataUrl("https://x"), "detector")
        ok(DataUri.parse("data:;base64,").bytes.isEmpty(), "empty base64 body")
        ok(DataUri.parse("data:,").bytes.isEmpty(), "empty plain body")
        ok(DataUri.parse("data:image/png;base64,SGVsbG8=").mediatype == "image/png", "image mediatype")
        ok(DataUri.parse("data:,%41%42").bytes.contentEquals(byteArrayOf(65, 66)), "pure escapes")
        refuses("missing comma") { DataUri.parse("data:text/plain") }
        refuses("bad base64") { DataUri.parse("data:;base64,@@@") }
        refuses("mediatype without subtype") { DataUri.parse("data:text,abc") }
        refuses("truncated escape") { DataUri.parse("data:,abc%2") }
        refuses("raw non-ascii") { DataUri.parse("data:,caf\u00E9") }
        refuses("duplicate parameter") { DataUri.parse("data:text/plain;a=1;a=2,x") }
        refuses("not data url") { DataUri.parse("https://example") }
    }

    private fun trigram() {
        ok(Trigram.ofWord("word") == setOf("  w", " wo", "wor", "ord", "rd "), "pg_trgm padding")
        ok(Trigram.ofWord("a") == setOf("  a", " a "), "single char yields two padded trigrams")
        ok(Trigram.of("two words") == Trigram.ofWord("two") + Trigram.ofWord("words"), "word split unions")
        ok(Trigram.of("WORD") == Trigram.of("word"), "case folds")
        ok(Trigram.similarity("word", "word") == 1.0, "identity similarity")
        ok(Trigram.similarity("word", "word!") == 1.0, "punctuation separates, not contributes")
        val sim = Trigram.similarity("word", "wird")
        ok(sim > 0.0 && sim < 0.5, "one-letter change drops similarity hard")
        ok(Trigram.similarity("abc", "xyz") == 0.0, "disjoint")
        ok(Trigram.matches("hello", "helo", 0.3), "typo passes candidate filter")
        ok(!Trigram.matches("hello", "world", 0.3), "unrelated filtered out")
        ok(Trigram.words("Hello, World9!") == listOf("hello", "world9"), "tokenizer folds case and splits on punctuation")
        ok(Trigram.words("snake_case") == listOf("snake", "case"), "underscore separates words")
        ok(Trigram.ofWord("ab") == setOf("  a", " ab", "ab "), "two-char word")
        ok(Trigram.similarity("hello world", "world hello") == 1.0, "word order irrelevant to the set")
        ok(Trigram.of("a b a") == Trigram.of("a b"), "duplicate words add nothing")
        ok(Trigram.similarity("kitten", "sitting") > Trigram.similarity("kitten", "dog"), "ordering sanity")
        ok(Trigram.matches("search", "search", 1.0), "threshold 1 allows exact")
        refuses("similarity over empty") { Trigram.similarity("", "abc") }
        refuses("threshold above 1") { Trigram.matches("a", "a", 1.5) }
        refuses("ofWord with space") { Trigram.ofWord("two words") }
    }

    private fun turnOrder() {
        var s = TurnOrder.start(listOf("ada", "bo", "cy"))
        ok(s.current == "ada" && s.round == 0, "start state")
        s = TurnOrder.next(s)
        ok(s.current == "bo" && s.round == 0, "advance")
        s = TurnOrder.next(TurnOrder.next(s))
        ok(s.current == "ada" && s.round == 1, "wrap increments round once")
        val skipped = TurnOrder.skip(s, "bo")
        ok(TurnOrder.next(skipped).current == "cy", "skip is honored")
        ok(TurnOrder.next(TurnOrder.unskip(skipped, "bo")).current == "bo", "unskip restores")
        ok(TurnOrder.distanceTo(s, "cy") == 2, "distance counts turns")
        val doubleWrap = TurnOrder.next(TurnOrder.next(TurnOrder.next(TurnOrder.skip(TurnOrder.skip(s, "bo"), "cy"))))
        ok(doubleWrap.current == "ada" && doubleWrap.round == s.round + 3, "solo rotation rounds")
        ok(TurnOrder.skip(TurnOrder.skip(s, "bo"), "bo").skipped.size == 1, "skip is a set")
        ok(TurnOrder.unskip(s, "bo") == s, "unskip of unskipped is identity")
        ok(TurnOrder.distanceTo(TurnOrder.skip(s, "bo"), "cy") == 1, "distance skips the skipped")
        var tenRounds = s
        repeat(30) { tenRounds = TurnOrder.next(tenRounds) }
        ok(tenRounds.round == s.round + 10 && tenRounds.current == s.current, "30 steps of 3 = 10 exact rounds")
        refuses("everyone skipped") { TurnOrder.next(TurnOrder.skip(TurnOrder.skip(TurnOrder.skip(s, "ada"), "bo"), "cy")) }
        ok(TurnOrder.start(listOf("solo")).let { TurnOrder.next(it).round == 1 }, "single participant wraps every turn")
        ok(TurnOrder.distanceTo(s, s.current) == 3, "full cycle back to self")
        refuses("unknown participant") { TurnOrder.skip(s, "zed") }
        refuses("duplicate names") { TurnOrder.start(listOf("a", "a")) }
        refuses("empty session") { TurnOrder.start(emptyList()) }
        refuses("distance to skipped") { TurnOrder.distanceTo(TurnOrder.skip(s, "cy"), "cy") }
    }

    private fun ewma() {
        var s = Ewma.start(0.5)
        refuses("estimate before samples") { s.estimate }
        s = Ewma.add(s, 10.0)
        ok(near(s.estimate, 10.0, 1e-12), "bias correction exact after one sample")
        ok(near(s.raw, 5.0, 1e-12), "uncorrected raw shows the cold-start bias")
        var c = Ewma.start(0.3)
        var allEqual = true
        repeat(20) {
            c = Ewma.add(c, 42.0)
            if (!near(c.estimate, 42.0, 1e-9)) allEqual = false
        }
        ok(allEqual, "constant series equals the constant at EVERY corrected step")
        val gap = 42.0 - c.raw
        ok(near(gap, 42.0 * Math.pow(0.7, 20.0), 1e-9), "uncorrected bias is exactly (1-a)^t of the gap")
        var d = Ewma.addAll(Ewma.start(1.0), doubleArrayOf(1.0, 2.0, 3.0))
        ok(near(d.estimate, 3.0, 1e-12), "alpha 1 tracks the last sample")
        val up = Ewma.addAll(Ewma.start(0.5), doubleArrayOf(0.0, 0.0, 100.0))
        ok(up.estimate > 50.0 && up.estimate < 100.0, "recent sample dominates at alpha .5")
        ok(Ewma.add(Ewma.start(0.5), 7.0).count == 1L, "count tracks")
        ok(near(Ewma.addAll(Ewma.start(0.2), DoubleArray(500) { 13.0 }).raw, 13.0, 1e-9), "raw converges in the limit")
        refuses("alpha zero") { Ewma.start(0.0) }
        refuses("alpha above one") { Ewma.start(1.1) }
        refuses("NaN sample") { Ewma.add(Ewma.start(0.5), Double.NaN) }
    }

    private fun keyMigrate() {
        val renames = KeyMigrate.validate(mapOf("theme" to "ui.theme", "ui.theme" to "appearance.theme"))
        ok(KeyMigrate.resolve(renames, "theme") == "appearance.theme", "transitive resolution")
        ok(KeyMigrate.resolve(renames, "untouched") == "untouched", "unmapped keys pass")
        val applied = KeyMigrate.apply(renames, mapOf("theme" to "dark", "font" to "mono"))
        ok(applied == mapOf("appearance.theme" to "dark", "font" to "mono"), "apply moves and keeps")
        ok(KeyMigrate.apply(renames, applied) == applied, "idempotent")
        val merge = KeyMigrate.apply(mapOf("a" to "c", "b" to "c"), mapOf("a" to "same", "b" to "same"))
        ok(merge == mapOf("c" to "same"), "equal values may converge")
        ok(KeyMigrate.apply(emptyMap(), mapOf("k" to "v")) == mapOf("k" to "v"), "empty migration is identity")
        ok(KeyMigrate.resolve(mapOf("a" to "b", "b" to "c", "c" to "d"), "a") == "d", "three-hop chain")
        ok(KeyMigrate.apply(renames, emptyMap()).isEmpty(), "empty settings stay empty")
        ok(KeyMigrate.validate(mapOf("x" to "y")) == mapOf("x" to "y"), "validate returns the same mapping")
        refuses("colliding different values") {
            KeyMigrate.apply(mapOf("a" to "c", "b" to "c"), mapOf("a" to "1", "b" to "2"))
        }
        refuses("cycle") { KeyMigrate.validate(mapOf("a" to "b", "b" to "a")) }
        refuses("self rename") { KeyMigrate.validate(mapOf("a" to "a")) }
        refuses("empty key") { KeyMigrate.validate(mapOf("" to "x")) }
    }

    private fun castNs() {
        ok(CastNs.validate("urn:x-cast:com.google.cast.media") == "urn:x-cast:com.google.cast.media", "well-known form")
        ok(CastNs.wellKnown("urn:x-cast:com.google.cast.media") == CastNs.WellKnown.MEDIA, "media classified")
        ok(CastNs.wellKnown("urn:x-cast:com.google.cast.tp.heartbeat") == CastNs.WellKnown.HEARTBEAT, "heartbeat classified")
        ok(CastNs.wellKnown("urn:x-cast:dev.fusion.custom") == null, "custom namespace valid but unclassified")
        ok(CastNs.isValid("urn:x-cast:a-b_c.d9"), "charset accepted")
        ok(CastNs.WellKnown.values().all { CastNs.isValid(CastNs.PREFIX + it.suffix) }, "all well-knowns validate")
        ok(CastNs.wellKnown("urn:x-cast:com.google.cast.receiver") == CastNs.WellKnown.RECEIVER, "receiver classified")
        ok(CastNs.isValid("urn:x-cast:" + "a".repeat(117)), "exactly at the length cap")
        refuses("missing prefix") { CastNs.validate("x-cast:com.google.cast.media") }
        ok(!CastNs.isValid("urn:x-cast:" + "a".repeat(118)), "one past the cap")
        ok(CastNs.isValid("urn:x-cast:9numeric.start"), "digits allowed anywhere")
        refuses("empty suffix") { CastNs.validate("urn:x-cast:") }
        refuses("double dot") { CastNs.validate("urn:x-cast:a..b") }
        refuses("trailing dot") { CastNs.validate("urn:x-cast:a.b.") }
        refuses("illegal char") { CastNs.validate("urn:x-cast:a/b") }
        refuses("oversized") { CastNs.validate("urn:x-cast:" + "a.".repeat(70) + "b") }
    }

    private fun lineWidth() {
        ok(LineWidth.of("漢字".codePointAt(0)) == 2, "CJK ideograph wide")
        ok(LineWidth.of("漢字") == 4, "two ideographs = 4 columns")
        ok(LineWidth.of("カタカナ") == 8, "katakana wide")
        ok(LineWidth.of("한글") == 4, "hangul wide")
        ok(LineWidth.of("ｱ".codePointAt(0)) == 1, "HALFwidth katakana narrow")
        ok(LineWidth.of("Ａ".codePointAt(0)) == 2, "FULLwidth latin wide")
        ok(LineWidth.of("\u3000".codePointAt(0)) == 2, "ideographic space wide")
        ok(LineWidth.of("e\u0301") == 1, "combining acute adds zero")
        ok(LineWidth.of("a\u200Bb") == 2, "zero-width space adds zero")
        ok(LineWidth.of("mixed 漢字 text") == 6 + 4 + 5, "mixed-width line")
        ok(LineWidth.of("\uD840\uDC00".codePointAt(0)) == 2, "CJK Ext B astral wide")
        ok(LineWidth.fit("漢字abc", 3) == "漢", "fit never splits a wide char")
        ok(LineWidth.fit("漢字abc", 4) == "漢字", "fit at exact boundary")
        ok(LineWidth.fit("abc", 0) == "", "zero columns")
        ok(LineWidth.of("") == 0, "empty string")
        ok(LineWidth.of("hello") == 5, "plain ASCII")
        ok(LineWidth.fit("e\u0301abc", 1) == "e\u0301", "combining stays attached to its base in fit")
        ok(LineWidth.of("\uFF71".toString()) == 1 && LineWidth.of("\u30A2".toString()) == 2, "same kana, half vs full width")
        ok(LineWidth.fit("abcdef", 100) == "abcdef", "fit beyond length returns all")
        refuses("control char") { LineWidth.of("\u0007".codePointAt(0)) }
        ok(LineWidth.of("\uFF01".codePointAt(0)) == 2 && LineWidth.of("!".codePointAt(0)) == 1, "fullwidth vs ASCII bang")
        ok(LineWidth.of("\u00E9") == 1, "precomposed accent is narrow")
        refuses("tab has no width here") { LineWidth.of("a\tb") }
        refuses("negative columns") { LineWidth.fit("a", -1) }
    }

    private fun shardPath() {
        ok(ShardPath.shard("abcdef0123", 1, 2) == "ab/cdef0123", "git shape")
        ok(ShardPath.shard("abcdef0123", 2, 2) == "ab/cd/ef0123", "two levels")
        ok(ShardPath.unshard("ab/cd/ef0123") == "abcdef0123", "unshard joins")
        ok(ShardPath.isCanonical("ab/cdef0123"), "canonical depth1")
        ok(!ShardPath.isCanonical("abc/def0123"), "wrong width is not canonical")
        var rt = true
        for (d in 1..3) {
            val p = ShardPath.shard("0123456789abcdef", d, 2)
            if (ShardPath.unshard(p) != "0123456789abcdef") rt = false
        }
        ok(rt, "roundtrip over depths")
        ok(ShardPath.shard("abcdef01", 1, 4) == "abcd/ef01", "width four")
        ok(ShardPath.isCanonical(ShardPath.shard("f".repeat(64), 2, 2), 2, 2), "sha256-length digest canonical at depth 2")
        ok(ShardPath.unshard("abcdef") == "abcdef", "slashless path is just the digest")
        refuses("uppercase digest") { ShardPath.shard("ABCDEF0123", 1, 2) }
        refuses("non-hex") { ShardPath.shard("zzzzzz", 1, 2) }
        refuses("too short to leave a leaf") { ShardPath.shard("abcd", 2, 2) }
        refuses("leading slash") { ShardPath.unshard("/ab/cd") }
        refuses("depth five") { ShardPath.shard("a".repeat(40), 5, 2) }
    }

    private fun spanMerge() {
        val merged = SpanMerge.merge(listOf(SpanMerge.Span(5, 7), SpanMerge.Span(1, 3), SpanMerge.Span(3, 5)))
        ok(merged == listOf(SpanMerge.Span(1, 7)), "touching chain merges to one")
        ok(SpanMerge.merge(listOf(SpanMerge.Span(1, 4), SpanMerge.Span(2, 3))) == listOf(SpanMerge.Span(1, 4)), "containment absorbs")
        ok(SpanMerge.merge(listOf(SpanMerge.Span(1, 2), SpanMerge.Span(4, 5))) == listOf(SpanMerge.Span(1, 2), SpanMerge.Span(4, 5)), "disjoint stays")
        ok(SpanMerge.merge(emptyList()) == emptyList<SpanMerge.Span>(), "empty input")
        ok(SpanMerge.merge(merged) == merged, "idempotent")
        ok(SpanMerge.covered(listOf(SpanMerge.Span(0, 3), SpanMerge.Span(2, 5))) == 5, "coverage dedupes overlap")
        val inv = SpanMerge.invert(listOf(SpanMerge.Span(2, 4), SpanMerge.Span(6, 8)), 10)
        ok(inv == listOf(SpanMerge.Span(0, 2), SpanMerge.Span(4, 6), SpanMerge.Span(8, 10)), "inversion gaps")
        ok(SpanMerge.invert(inv, 10) == listOf(SpanMerge.Span(2, 4), SpanMerge.Span(6, 8)), "double inversion returns merged original")
        ok(SpanMerge.covered(inv) + SpanMerge.covered(listOf(SpanMerge.Span(2, 4), SpanMerge.Span(6, 8))) == 10, "cover + gaps = bounds")
        ok(SpanMerge.contains(merged, 6) && !SpanMerge.contains(merged, 7), "half-open membership")
        ok(SpanMerge.invert(emptyList(), 5) == listOf(SpanMerge.Span(0, 5)), "inverting nothing covers all")
        ok(SpanMerge.invert(listOf(SpanMerge.Span(0, 5)), 5) == emptyList<SpanMerge.Span>(), "inverting everything covers nothing")
        ok(SpanMerge.covered(emptyList()) == 0, "empty coverage")
        ok(SpanMerge.merge(List(50) { SpanMerge.Span(it, it + 2) }) == listOf(SpanMerge.Span(0, 51)), "50 overlapping spans collapse")
        refuses("empty span") { SpanMerge.Span(3, 3) }
        refuses("inverted span") { SpanMerge.Span(5, 2) }
        refuses("span outside bound") { SpanMerge.invert(listOf(SpanMerge.Span(8, 12)), 10) }
    }

    private fun piecePlan() {
        val p = PiecePlan.plan(1_000_000, 16384)
        ok(p.pieceCount == 62L && p.lastPieceSize == 576L, "1MB at 16KiB pieces")
        ok(PiecePlan.pieceSize(p, 0) == 16384L && PiecePlan.pieceSize(p, 61) == 576L, "sizes per index")
        ok(PiecePlan.offsetOf(p, 1) == 16384L, "offsets")
        ok(PiecePlan.pieceOf(p, 999_999) == 61L && PiecePlan.pieceOf(p, 0) == 0L, "byte to piece")
        ok(p.piecesFieldBytes == 62L * 20, "pieces field length")
        val exact = PiecePlan.plan(32768, 16384)
        ok(exact.pieceCount == 2L && exact.lastPieceSize == 16384L, "exact multiple keeps full last piece")
        var consistent = true
        for (i in 0 until p.pieceCount) {
            if (PiecePlan.pieceOf(p, PiecePlan.offsetOf(p, i)) != i) consistent = false
        }
        ok(consistent, "offset/piece inverse over all pieces")
        ok(PiecePlan.plan(1, 16384).let { it.pieceCount == 1L && it.lastPieceSize == 1L }, "single byte payload")
        ok(PiecePlan.validatePieceLength(262144) == 262144L, "256KiB standard piece length")
        ok(PiecePlan.plan(1L shl 40, 1 shl 22).pieceCount == (1L shl 18), "1TiB at 4MiB pieces")
        var sizesSum = 0L
        for (i in 0 until p.pieceCount) sizesSum += PiecePlan.pieceSize(p, i)
        ok(sizesSum == p.totalSize, "piece sizes sum to total (property)")
        refuses("piece length 1000") { PiecePlan.plan(5000, 1000) }
        refuses("below 16KiB") { PiecePlan.plan(5000, 8192) }
        refuses("zero size") { PiecePlan.plan(0, 16384) }
        refuses("piece index out of range") { PiecePlan.pieceSize(p, 62) }
        refuses("offset beyond payload") { PiecePlan.pieceOf(p, 1_000_000) }
        refuses("negative total") { PiecePlan.plan(-1, 16384) }
        ok(PiecePlan.plan(16384, 16384).pieceCount == 1L, "exactly one piece")
    }

    private fun tabStops() {
        ok(TabStops.nextStop(0, 4) == 4 && TabStops.nextStop(3, 4) == 4 && TabStops.nextStop(4, 4) == 8, "stop law incl. boundary")
        ok(TabStops.expand("a\tb", 4) == "a   b", "tab fills to stop")
        ok(TabStops.expand("\t", 4) == "    ", "leading tab")
        ok(TabStops.expand("abcd\tx", 4) == "abcd    x", "tab AT a stop advances a full stop")
        ok(TabStops.expand("no tabs", 4) == "no tabs", "no-op")
        ok(TabStops.width("a\tb\tc", 8) == 17, "width with two stops")
        var property = true
        for (line in arrayOf("a\tb", "\t\t", "ab\tcd\te", "")) {
            if (TabStops.expand(line, 4).let { it.contains('\t') || it.length != TabStops.width(line, 4) }) property = false
        }
        ok(property, "expand removes tabs and preserves width (property)")
        ok(TabStops.columnAt("ab\tcd", 3, 4) == 4, "column after the tab")
        ok(TabStops.expand("a\tb", 8) == "a       b", "eight-column stops")
        ok(TabStops.width("\t", 1) == 1, "tab size one degenerates to single spaces")
        ok(TabStops.columnAt("abc", 0, 4) == 0 && TabStops.columnAt("abc", 3, 4) == 3, "plain columns")
        refuses("embedded newline") { TabStops.expand("a\nb", 4) }
        refuses("tab size zero") { TabStops.expand("a", 0) }
        refuses("index out of line") { TabStops.columnAt("ab", 5, 4) }
    }

    private fun gridPos() {
        ok(GridPos.toPos(0, 4, 5, rtl = false) == GridPos.Pos(0, 0, 0), "first tile LTR")
        ok(GridPos.toPos(0, 4, 5, rtl = true) == GridPos.Pos(0, 0, 3), "first tile mirrors in RTL")
        ok(GridPos.toPos(5, 4, 5, rtl = false) == GridPos.Pos(0, 1, 1), "second row")
        ok(GridPos.toPos(20, 4, 5, rtl = false) == GridPos.Pos(1, 0, 0), "second page")
        var bijective = true
        for (rtl in booleanArrayOf(false, true)) {
            for (i in 0 until 3 * 4 * 5) {
                if (GridPos.toIndex(GridPos.toPos(i, 4, 5, rtl), 4, 5, rtl) != i) bijective = false
            }
        }
        ok(bijective, "index<->pos bijection over 3 pages, both directions")
        ok(GridPos.pageCount(0, 4, 5) == 1, "empty grid still shows one page")
        ok(GridPos.pageCount(20, 4, 5) == 1 && GridPos.pageCount(21, 4, 5) == 2, "page boundary")
        ok(GridPos.toPos(3, 4, 5, rtl = true) == GridPos.Pos(0, 0, 0), "RTL end of row is visual col 0")
        ok(GridPos.toPos(23, 4, 5, rtl = false) == GridPos.Pos(1, 0, 3), "page 2 row 0 last col")
        ok(GridPos.pageCount(40, 4, 5) == 2 && GridPos.pageCount(41, 4, 5) == 3, "exact fill vs spill")
        refuses("zero columns") { GridPos.toPos(0, 0, 5, false) }
        refuses("negative index") { GridPos.toPos(-1, 4, 5, false) }
        refuses("visual col outside") { GridPos.toIndex(GridPos.Pos(0, 0, 4), 4, 5, false) }
        refuses("row outside page") { GridPos.toIndex(GridPos.Pos(0, 5, 0), 4, 5, false) }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        sheetRange(); r1c1(); pageGeometry(); shlexJoin(); durationIso()
        dmsFormat(); colorHex(); readEase(); ean13(); dataUri()
        trigram(); turnOrder(); ewma(); keyMigrate(); castNs()
        lineWidth(); shardPath(); spanMerge(); piecePlan(); tabStops(); gridPos()
        println("R41 PASS: $count production-kernel assertions across 21 kernels. Host JVM only; not Android/device evidence.")
    }
}

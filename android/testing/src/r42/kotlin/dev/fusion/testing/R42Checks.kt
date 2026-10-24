package dev.fusion.testing

import dev.fusion.ooxml.ColLetters
import dev.fusion.ooxml.SheetRange
import dev.fusion.ooxml.AbsRef
import dev.fusion.ooxml.MergedCells
import dev.fusion.format.pdf.PdfDate
import dev.fusion.maps.NmeaLat
import dev.fusion.browser.model.HstsParse
import dev.fusion.continuity.PairPin
import dev.fusion.archive.ArHead
import dev.fusion.documents.CaseCycle
import dev.fusion.editor.CommentToggle
import dev.fusion.notes.UnitPrice
import dev.fusion.capture.Rule3rds
import dev.fusion.perf.Histo
import dev.fusion.terminal.CsiCursor
import dev.fusion.privacy.GpcHeader
import dev.fusion.vault.PwPolicy
import dev.fusion.media.GainDb
import dev.fusion.storage.HexBytes
import dev.fusion.alerts.SnoozeSeq
import dev.fusion.calc.NiceStep
import dev.fusion.automation.NextRun
import dev.fusion.messages.Rfc2231
import dev.fusion.format.image.BmpHead

/**
 * R42 executed contracts. Host JVM evidence only — NOT Android, device,
 * service or acceptance evidence. The ooxml group compiles with its
 * declared intra-module dependencies (ColLetters, SheetRange); nothing
 * else shares a jar.
 */
object R42Checks {
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

    private fun absRef() {
        ok(AbsRef.parse("zz10") == AbsRef.Ref(702, 10, false, false), "lowercase letters canonicalize")
        ok(AbsRef.shift(AbsRef.parse("\$A1"), 5, 5).toA1() == "\$A6", "mixed shift formats")
        refuses("letters after row") { AbsRef.parse("A1B") }
        ok(AbsRef.parse("\$XFD\$1048576") == AbsRef.Ref(16384, 1048576, true, true), "grid corner absolute")
        ok(AbsRef.shift(AbsRef.parse("C3"), 0, 0) == AbsRef.parse("C3"), "zero shift is identity")
        ok(AbsRef.parse("\$B2").toA1() == "\$B2" && AbsRef.parse("B\$2").toA1() == "B\$2", "mixed forms format back")
        ok(AbsRef.shift(AbsRef.shift(AbsRef.parse("B2"), 1, 1), -1, -1) == AbsRef.parse("B2"), "shift inverse")
        refuses("digits only") { AbsRef.parse("123") }
        refuses("dollar after row") { AbsRef.parse("A1\$") }
        ok(AbsRef.parse("\$A\$1") == AbsRef.Ref(1, 1, true, true), "fully absolute")
        ok(AbsRef.parse("\$B2") == AbsRef.Ref(2, 2, true, false), "column absolute")
        ok(AbsRef.parse("B\$2") == AbsRef.Ref(2, 2, false, true), "row absolute")
        ok(AbsRef.parse("B2") == AbsRef.Ref(2, 2, false, false), "fully relative")
        ok(AbsRef.parse("\$A\$1").toA1() == "\$A\$1", "format roundtrip absolute")
        val rel = AbsRef.parse("B2")
        ok(AbsRef.shift(rel, 2, 3) == AbsRef.Ref(4, 5, false, false), "relative shifts")
        val mixed = AbsRef.parse("\$B2")
        ok(AbsRef.shift(mixed, 2, 3) == AbsRef.Ref(2, 5, true, false), "absolute column pinned under fill")
        ok(AbsRef.shift(AbsRef.parse("B\$2"), 2, 3) == AbsRef.Ref(4, 2, false, true), "absolute row pinned")
        ok(AbsRef.shift(AbsRef.parse("\$A\$1"), 9, 9) == AbsRef.parse("\$A\$1"), "fully absolute never moves")
        var rt = true
        for (s in arrayOf("\$A\$1", "\$XFD1", "C\$9", "AA11")) if (AbsRef.parse(s).toA1() != s) rt = false
        ok(rt, "parse/format roundtrip set")
        refuses("shift off left edge") { AbsRef.shift(AbsRef.parse("A1"), -1, 0) }
        refuses("shift past XFD") { AbsRef.shift(AbsRef.parse("XFD1"), 1, 0) }
        refuses("row zero") { AbsRef.parse("\$A\$0") }
        refuses("leading zero row") { AbsRef.parse("A01") }
        refuses("empty") { AbsRef.parse("") }
        refuses("lone dollars") { AbsRef.parse("\$\$1") }
    }

    private fun mergedCells() {
        ok(MergedCells.isCovered(listOf(SheetRange.parse("A1:C1")), SheetRange.Cell(3, 1)), "row merge covers its tail")
        ok(MergedCells.hiddenCells(emptyList()) == 0L, "nothing hidden without merges")
        ok(MergedCells.validate(emptyList()).isEmpty(), "no merges is fine")
        ok(MergedCells.hiddenCells(listOf(SheetRange.parse("A1:A5"))) == 4L, "vertical merge hides 4")
        ok(MergedCells.anchorOf(emptyList(), SheetRange.Cell(2, 2)) == SheetRange.Cell(2, 2), "no regions, own anchor")
        ok(MergedCells.validate(listOf(SheetRange.parse("A1:B1"), SheetRange.parse("A2:B2"))).size == 2, "adjacent non-overlapping ok")
        refuses("identical merges overlap") { MergedCells.validate(listOf(SheetRange.parse("A1:B2"), SheetRange.parse("A1:B2"))) }
        val a = SheetRange.parse("A1:B2")
        val c = SheetRange.parse("C3:D4")
        ok(MergedCells.validate(listOf(a, c)) == listOf(a, c), "disjoint merges validate")
        ok(MergedCells.hiddenCells(listOf(a, c)) == 6L, "4+4 cells hide 3+3")
        ok(MergedCells.anchorOf(listOf(a), SheetRange.Cell(2, 2)) == SheetRange.Cell(1, 1), "covered cell maps to anchor")
        ok(MergedCells.anchorOf(listOf(a), SheetRange.Cell(3, 3)) == SheetRange.Cell(3, 3), "uncovered cell is its own anchor")
        ok(MergedCells.isCovered(listOf(a), SheetRange.Cell(2, 1)), "B1 is covered")
        ok(!MergedCells.isCovered(listOf(a), SheetRange.Cell(1, 1)), "anchor is not covered")
        ok(!MergedCells.isCovered(listOf(a), SheetRange.Cell(5, 5)), "outside is not covered")
        refuses("overlapping merges") { MergedCells.validate(listOf(a, SheetRange.parse("B2:C3"))) }
        refuses("single-cell merge") { MergedCells.validate(listOf(SheetRange.parse("E5"))) }
    }

    private fun pdfDate() {
        ok(PdfDate.parse("D:20261024T".dropLast(1)).year == 2026, "clean 8-digit date")
        ok(PdfDate.format(PdfDate.Stamp(2026, 10, 24, 12, 0, 0, 0)) == "D:20261024120000Z", "zero offset formats Z")
        refuses("day 32") { PdfDate.parse("D:20261032") }
        ok(PdfDate.parse("D:202610").month == 10 && PdfDate.parse("D:202610").day == 1, "year+month defaults day")
        ok(PdfDate.parse("D:20261024").hour == 0, "date-only defaults time")
        ok(PdfDate.format(PdfDate.Stamp(1998, 12, 23, 19, 52, 0, -480)).endsWith("-08'00"), "format negative offset")
        ok(PdfDate.parse("D:20261024120000-00'30").offsetMinutes == -30, "negative partial-hour offset")
        refuses("month 13") { PdfDate.parse("D:202613") }
        refuses("year zero") { PdfDate.parse("D:0000") }
        val s = PdfDate.parse("D:199812231952-08'00")
        ok(s.year == 1998 && s.month == 12 && s.day == 23, "spec example date")
        ok(s.hour == 19 && s.minute == 52 && s.second == 0, "spec example time")
        ok(s.offsetMinutes == -480, "spec example offset")
        ok(PdfDate.parse("D:2026").let { it.month == 1 && it.day == 1 && it.offsetMinutes == null }, "year-only defaults")
        ok(PdfDate.parse("D:20261024120000Z").offsetMinutes == 0, "Z offset")
        ok(PdfDate.parse("D:20261024120000+05'30").offsetMinutes == 330, "+05'30")
        ok(PdfDate.parse("D:20261024120000+05'30'").offsetMinutes == 330, "legacy trailing apostrophe")
        ok(PdfDate.format(s) == "D:199812231952" + "00-08'00", "format emits canonical")
        ok(PdfDate.format(PdfDate.Stamp(2026, 1, 1, 0, 0, 0, null)) == "D:20260101000000", "null offset omits marker")
        val rt = PdfDate.parse(PdfDate.format(PdfDate.parse("D:20240229235959Z")))
        ok(rt.day == 29 && rt.offsetMinutes == 0, "leap-day roundtrip")
        refuses("no prefix") { PdfDate.parse("199812231952") }
        refuses("2026-02-29") { PdfDate.parse("D:20260229") }
        refuses("hour 24") { PdfDate.parse("D:2026102424") }
        refuses("bad offset separator") { PdfDate.parse("D:20261024120000+0530") }
        refuses("trailing garbage") { PdfDate.parse("D:20261024120000Zx") }
    }

    private fun nmea() {
        ok(near(NmeaLat.parseLon("00000.000", 'E'), 0.0, 1e-12), "zero lon parses")
        ok(NmeaLat.parseLat("0000.000", 'S') == -0.0 || NmeaLat.parseLat("0000.000", 'S') == 0.0, "signed zero latitude")
        refuses("negative in field") { NmeaLat.parseLat("-807.038", 'N') }
        ok(NmeaLat.formatLat(0.0) == ("0000.0000" to 'N'), "zero lat formats N")
        ok(NmeaLat.formatLon(0.0) == ("00000.0000" to 'E'), "zero lon formats E")
        ok(near(NmeaLat.parseLat("9000.000", 'N'), 90.0, 1e-12), "exact pole accepted")
        ok(NmeaLat.formatLat(90.0) == ("9000.0000" to 'N'), "pole formats")
        refuses("lon beyond 180") { NmeaLat.parseLon("18100.000", 'E') }
        refuses("no decimal point") { NmeaLat.parseLat("4807", 'N') }
        ok(near(NmeaLat.parseLat("4807.038", 'N'), 48.1173, 1e-9), "classic GGA latitude")
        ok(near(NmeaLat.parseLon("01131.000", 'E'), 11.516666666666667, 1e-12), "classic GGA longitude")
        ok(NmeaLat.parseLat("4807.038", 'S') < 0, "S negates")
        ok(NmeaLat.parseLon("01131.000", 'W') < 0, "W negates")
        val (f, h) = NmeaLat.formatLat(48.1173)
        ok(f == "4807.0380" && h == 'N', "format back to ddmm.mmmm")
        ok(NmeaLat.formatLon(-11.516666666666667).let { it.first == "01131.0000" && it.second == 'W' }, "lon format W")
        ok(near(NmeaLat.parseLat(NmeaLat.formatLat(-33.8688).first, 'S'), -33.8688, 1e-4), "roundtrip within 4 decimals of minutes")
        refuses("minutes 60") { NmeaLat.parseLat("4860.000", 'N') }
        refuses("three-digit lat degrees") { NmeaLat.parseLat("14807.038", 'N') }
        refuses("bad hemisphere") { NmeaLat.parseLat("4807.038", 'E') }
        refuses("lat beyond 90") { NmeaLat.parseLat("9107.038", 'N') }
        refuses("lon field too short") { NmeaLat.parseLon("1131.000", 'E') }
    }

    private fun hsts() {
        ok(HstsParse.parse("preload; max-age=1").preload, "order independent")
        ok(HstsParse.parse("max-age=1;preload;x1;x2").unknownDirectives.size == 2, "multiple unknowns recorded")
        refuses("sixteen-digit max-age") { HstsParse.parse("max-age=1234567890123456") }
        ok(HstsParse.parse("max-age=63072000; includeSubDomains; preload").preload, "preload trio")
        ok(HstsParse.parse("max-age=1").let { !it.includeSubDomains && it.unknownDirectives.isEmpty() }, "minimal header")
        ok(HstsParse.isValid("max-age=0") && !HstsParse.isValid(""), "validity pair")
        refuses("empty header") { HstsParse.parse("") }
        refuses("max-age twice via case") { HstsParse.parse("max-age=1; MAX-AGE=1") }
        val h = HstsParse.parse("max-age=31536000; includeSubDomains")
        ok(h.maxAgeSeconds == 31536000L && h.includeSubDomains && !h.preload, "canonical header")
        ok(HstsParse.parse("MAX-AGE=0").maxAgeSeconds == 0L, "delete-policy zero, case-insensitive")
        ok(HstsParse.parse("max-age=\"600\"").maxAgeSeconds == 600L, "quoted value accepted")
        val u = HstsParse.parse("max-age=1; futureDirective=x; preload")
        ok(u.preload && u.unknownDirectives == listOf("futuredirective"), "unknown tolerated per spec, recorded")
        ok(HstsParse.parse("  max-age=5 ;; ").maxAgeSeconds == 5L, "empty tokens skipped")
        refuses("missing max-age") { HstsParse.parse("includeSubDomains") }
        refuses("duplicate directive") { HstsParse.parse("max-age=1; max-age=2") }
        refuses("valued includeSubDomains") { HstsParse.parse("max-age=1; includeSubDomains=yes") }
        refuses("non-numeric max-age") { HstsParse.parse("max-age=soon") }
        ok(!HstsParse.isValid("max-age=-1"), "negative is invalid via the digit gate")
    }

    private fun pairPin() {
        ok(PairPin.interim("5724") == 0, "interim of a valid string is zero")
        ok(PairPin.withCheck("1234567890").length == 11, "long body")
        ok(!PairPin.isValid("5"), "single digit without context fails unless zero-interim")
        ok(PairPin.withCheck("0").length == 2, "single-digit body")
        ok(PairPin.isValid(PairPin.withCheck("00000")), "all zeros round-trips")
        ok((0..9).all { d -> PairPin.isValid(PairPin.withCheck(d.toString())) }, "all single-digit bodies validate")
        ok(PairPin.checkDigit("572") != PairPin.checkDigit("527"), "order matters (anti-symmetry)")
        ok(PairPin.checkDigit("572") == 4, "published Damm example 572 -> 4")
        ok(PairPin.isValid("5724"), "572 with its digit validates")
        ok(!PairPin.isValid("5734"), "single-digit error caught")
        ok(!PairPin.isValid("7524"), "adjacent transposition caught")
        ok(PairPin.withCheck("572") == "5724", "withCheck appends")
        ok(PairPin.checkDigit("0") == PairPin.interim("0"), "interim equals check for bodies")
        var allSingle = true
        val body = "314159"
        val full = PairPin.withCheck(body)
        for (i in full.indices) for (d in '0'..'9') {
            if (d != full[i]) {
                val mutated = full.substring(0, i) + d + full.substring(i + 1)
                if (PairPin.isValid(mutated)) allSingle = false
            }
        }
        ok(allSingle, "ALL single-digit errors detected (property, 60 mutations)")
        var allAdj = true
        for (i in 0 until full.length - 1) {
            if (full[i] != full[i + 1]) {
                val sw = full.substring(0, i) + full[i + 1] + full[i] + full.substring(i + 2)
                if (PairPin.isValid(sw)) allAdj = false
            }
        }
        ok(allAdj, "ALL adjacent transpositions detected (property)")
        refuses("non-digit") { PairPin.checkDigit("12a") }
        refuses("empty") { PairPin.interim("") }
    }

    private fun arHead() {
        ok("!<arch>\n".toByteArray().size == 8, "magic byte length")
        ok(ArHead.MAGIC.length == 8, "8-byte magic")
        fun member(name: String, size: Int): ByteArray {
            val h = StringBuilder()
            h.append(name.padEnd(16)).append("0".padEnd(12)).append("0".padEnd(6)).append("0".padEnd(6))
            h.append("100644".padEnd(8)).append(size.toString().padEnd(10)).append("`\n")
            return h.toString().toByteArray(Charsets.US_ASCII)
        }
        val body1 = "2.0\n".toByteArray()
        val body2 = "abcde".toByteArray()
        val ar = ArHead.MAGIC.toByteArray() + member("debian-binary", body1.size) + body1 +
            member("data.txt", body2.size) + body2 + byteArrayOf(0x0A)
        val members = ArHead.parse(ar)
        ok(members.size == 2, "two members")
        ok(members[0].name == "debian-binary" && members[0].size == 4L, "first member fields")
        ok(members[1].name == "data.txt" && members[1].dataOffset == ar.size - 6, "second member offset")
        ok(ArHead.isDeb(members), "deb layout recognized")
        ok(String(ar, members[0].dataOffset, 4) == "2.0\n", "data offset points at content")
        refuses("bad magic") { ArHead.parse("!<arch>X".toByteArray() + ByteArray(60)) }
        refuses("truncated header") { ArHead.parse(ArHead.MAGIC.toByteArray() + ByteArray(30)) }
        val badTerm = ar.copyOf(); badTerm[8 + 58] = 'X'.code.toByte()
        refuses("bad terminator") { ArHead.parse(badTerm) }
        val overrun = ArHead.MAGIC.toByteArray() + member("big", 999) + body1
        refuses("member overruns file") { ArHead.parse(overrun) }
        refuses("empty archive") { ArHead.parse(ArHead.MAGIC.toByteArray()) }
    }

    private fun caseCycle() {
        ok(CaseCycle.detect("A1B2") == CaseCycle.State.UPPER, "digits ignored in classification")
        ok(CaseCycle.title("x  y") == "X  Y", "double spaces preserved")
        ok(CaseCycle.title("") == "", "empty title is empty")
        ok(CaseCycle.title("a") == "A" && CaseCycle.title("A") == "A", "single letter")
        ok(CaseCycle.detect("A") == CaseCycle.State.UPPER, "single upper letter")
        ok(CaseCycle.title("don't stop") == "Don't Stop", "apostrophe words")
        ok(CaseCycle.next("123 abc") == "123 Abc", "digits pass through the cycle")
        ok(CaseCycle.title("hello world") == "Hello World", "simple title")
        ok(CaseCycle.title("(aside) note") == "(Aside) Note", "leading punctuation skipped to first letter")
        ok(CaseCycle.detect("HELLO") == CaseCycle.State.UPPER, "upper detected")
        ok(CaseCycle.detect("hello") == CaseCycle.State.LOWER, "lower detected")
        ok(CaseCycle.detect("Hello World") == CaseCycle.State.TITLE, "title detected")
        ok(CaseCycle.detect("hELLo") == CaseCycle.State.MIXED, "mixed detected")
        ok(CaseCycle.next("HELLO") == "hello", "upper -> lower")
        ok(CaseCycle.next("hello world") == "Hello World", "lower -> title")
        ok(CaseCycle.next("Hello World") == "HELLO WORLD", "title -> upper")
        ok(CaseCycle.next(CaseCycle.next(CaseCycle.next("SHOUT"))) == "SHOUT", "three steps return home")
        ok(CaseCycle.next("mIxEd") == "MIXED", "mixed enters at upper")
        refuses("no letters") { CaseCycle.detect("123 456") }
    }

    private fun commentToggle() {
        ok(CommentToggle.toggle(listOf("-- sql"), "--") == listOf("sql"), "sql prefix uncomments")
        ok(CommentToggle.toggle(listOf("a"), "//").let { CommentToggle.toggle(it, "//") } == listOf("a"), "toggle twice is identity")
        ok(CommentToggle.toggle(listOf("#x"), "#") == listOf("x"), "hash prefix")
        ok(CommentToggle.toggle(listOf("\t x"), "//")[0].startsWith("//"), "tab indent treated as content, prefix at col 0")
        ok(!CommentToggle.isCommented("x // tail", "//"), "trailing comment is not a commented line")
        ok(CommentToggle.toggle(listOf("a", "b"), "//") == listOf("// a", "// b"), "comment all")
        ok(CommentToggle.toggle(listOf("// a", "// b"), "//") == listOf("a", "b"), "uncomment all")
        ok(CommentToggle.toggle(CommentToggle.toggle(listOf("x", "  y"), "#"), "#") == listOf("x", "  y"), "involution on uniform block")
        ok(CommentToggle.toggle(listOf("  a", "    b"), "//") == listOf("  // a", "  //   b"), "min-indent alignment")
        ok(CommentToggle.toggle(listOf("a", "", "b"), "//") == listOf("// a", "", "// b"), "blank lines pass through")
        ok(CommentToggle.toggle(listOf("// a", "b"), "//") == listOf("// // a", "// b"), "mixed block comments all")
        ok(CommentToggle.toggle(listOf("//nospace"), "//") == listOf("nospace"), "uncomment without space")
        ok(CommentToggle.isCommented("", "//") && CommentToggle.isCommented("  // x", "//"), "isCommented shapes")
        refuses("embedded newline") { CommentToggle.toggle(listOf("a\nb"), "//") }
        refuses("blank prefix") { CommentToggle.toggle(listOf("a"), " ") }
        refuses("all-blank selection") { CommentToggle.toggle(listOf("", "  "), "//") }
    }

    private fun unitPrice() {
        ok(UnitPrice.cheapest(listOf(UnitPrice.offer(5, 1.0, "pc"))).minorUnits == 5L, "singleton cheapest")
        refuses("NaN quantity") { UnitPrice.offer(1, Double.NaN, "g") }
        ok(UnitPrice.UNITS.size == 7, "declared table size")
        ok(UnitPrice.offer(0, 1.0, "g").perBase == 0.0, "free item")
        ok(UnitPrice.compare(UnitPrice.offer(100, 1.0, "kg"), UnitPrice.offer(100, 1000.0, "g")) == 0, "kg and 1000g tie")
        refuses("negative price") { UnitPrice.offer(-1, 1.0, "g") }
        val a = UnitPrice.offer(250, 500.0, "g")
        val b = UnitPrice.offer(450, 1.0, "kg")
        ok(UnitPrice.compare(a, b) > 0, "450/kg beats 250/500g")
        ok(near(a.perBase, 0.5, 1e-12) && near(b.perBase, 0.45, 1e-12), "per-gram prices")
        val dozen = UnitPrice.offer(360, 1.0, "dozen")
        val single = UnitPrice.offer(35, 1.0, "pc")
        ok(UnitPrice.compare(dozen, single) < 0, "30/pc dozen beats 35/pc")
        ok(UnitPrice.cheapest(listOf(a, b)) === b, "cheapest picks per-base winner")
        ok(near(UnitPrice.offer(100, 50.0, "cl").perBase, 0.2, 1e-12), "cl converts to ml")
        refuses("cross dimension") { UnitPrice.compare(a, UnitPrice.offer(100, 1.0, "l")) }
        refuses("unknown unit") { UnitPrice.offer(100, 1.0, "oz") }
        refuses("zero quantity") { UnitPrice.offer(100, 0.0, "g") }
        refuses("mixed-dimension list") { UnitPrice.cheapest(listOf(a, UnitPrice.offer(1, 1.0, "ml"))) }
        refuses("empty offer list") { UnitPrice.cheapest(emptyList()) }
    }

    private fun rule3rds() {
        ok(Rule3rds.powerPoints(1080, 1920)[0] == (360 to 640), "first point order stable")
        ok(Rule3rds.grid(1, 1) == Rule3rds.Grid(0, 0, 0, 0), "degenerate-but-positive frame floors to zero lines")
        ok(Rule3rds.grid(1920, 1080).let { it.vx1 == 640 && it.hy1 == 360 }, "landscape grid")
        ok(Rule3rds.powerPoints(90, 90).toSet().size == 4, "points distinct")
        ok(Rule3rds.nearestPowerPoint(90, 90, 31, 31) == (30 to 30), "near first point")
        refuses("negative height") { Rule3rds.grid(100, -1) }
        val g = Rule3rds.grid(1080, 1920)
        ok(g.vx1 == 360 && g.vx2 == 720 && g.hy1 == 640 && g.hy2 == 1280, "1080x1920 gridlines")
        ok(Rule3rds.powerPoints(1080, 1920).size == 4, "four power points")
        ok(Rule3rds.nearestPowerPoint(1080, 1920, 0, 0) == (360 to 640), "corner snaps to nearest")
        ok(Rule3rds.nearestPowerPoint(1080, 1920, 1079, 1919) == (720 to 1280), "far corner snaps")
        ok(Rule3rds.grid(3, 3) == Rule3rds.Grid(1, 2, 1, 2), "tiny frame floors")
        ok(Rule3rds.nearestPowerPoint(1080, 1920, 540, 960).let { it == (360 to 640) }, "center ties resolve to first point")
        refuses("zero width") { Rule3rds.grid(0, 100) }
        refuses("point outside") { Rule3rds.nearestPowerPoint(100, 100, 100, 50) }
    }

    private fun histo() {
        ok(Histo.bucketOf(Histo.create(longArrayOf(0, 10, 100)), 10) == 1, "boundary opens the next bucket")
        refuses("add below range") { Histo.add(Histo.create(longArrayOf(5, 10)), 4) }
        ok(Histo.create(longArrayOf(0, 1)).counts.size == 1, "minimal histogram")
        ok(Histo.add(Histo.create(longArrayOf(0, 10)), 0).counts[0] == 1L, "lower boundary inclusive")
        ok(Histo.addAll(Histo.create(longArrayOf(0, 10)), LongArray(1000) { (it % 10).toLong() }).let { Histo.conserved(it) && it.total == 1000L }, "1000-sample conservation (property)")
        var h = Histo.create(longArrayOf(0, 10, 100, 1000))
        h = Histo.addAll(h, longArrayOf(5, 9, 10, 99, 100, 999, 5000, 0))
        ok(h.counts.toList() == listOf(3L, 2L, 2L), "bucket counts")
        ok(h.overflow == 1L && h.total == 8L, "overflow and total")
        ok(Histo.conserved(h), "conservation invariant")
        ok(Histo.bucketOf(h, 9) == 0 && Histo.bucketOf(h, 10) == 1, "half-open boundary")
        ok(Histo.bucketOf(h, 99999) == -1, "overflow index")
        var order = Histo.create(longArrayOf(0, 10, 100, 1000))
        order = Histo.addAll(order, longArrayOf(0, 5, 5000, 999, 100, 99, 10, 9))
        ok(order.counts.contentEquals(h.counts) && order.overflow == h.overflow, "order independence")
        refuses("below first boundary") { Histo.add(h, -1) }
        refuses("non-increasing boundaries") { Histo.create(longArrayOf(0, 10, 10)) }
        refuses("single boundary") { Histo.create(longArrayOf(5)) }
    }

    private fun csiCursor() {
        ok(CsiCursor.parse("\u001B[12F") == CsiCursor.Move('F', 12, 1), "CPL with parameter")
        ok(CsiCursor.apply(CsiCursor.Move('B', 5, 1), 22, 10, 24, 80) == (24 to 10), "down clamps at bottom")
        refuses("screen zero") { CsiCursor.apply(CsiCursor.Move('A', 1, 1), 1, 1, 0, 80) }
        ok(CsiCursor.apply(CsiCursor.parse("\u001B[D"), 1, 1, 24, 80) == (1 to 1), "left at edge clamps")
        ok(CsiCursor.parse("\u001B[10G") == CsiCursor.Move('G', 10, 1), "CHA with parameter")
        ok(CsiCursor.apply(CsiCursor.parse("\u001B[H"), 12, 40, 24, 80) == (1 to 1), "bare H homes")
        refuses("not a csi") { CsiCursor.parse("plain") }
        ok(CsiCursor.parse("\u001B[A") == CsiCursor.Move('A', 1, 1), "bare CUU defaults to 1")
        ok(CsiCursor.parse("\u001B[0B").a == 1, "zero parameter becomes 1 (ECMA-48)")
        ok(CsiCursor.parse("\u001B[5;10H") == CsiCursor.Move('H', 5, 10), "CUP row;col")
        ok(CsiCursor.apply(CsiCursor.parse("\u001B[3C"), 5, 5, 24, 80) == (5 to 8), "CUF moves right")
        ok(CsiCursor.apply(CsiCursor.parse("\u001B[99A"), 5, 5, 24, 80) == (1 to 5), "clamps at top")
        ok(CsiCursor.apply(CsiCursor.parse("\u001B[2E"), 5, 40, 24, 80) == (7 to 1), "CNL goes to column 1")
        ok(CsiCursor.apply(CsiCursor.parse("\u001B[G"), 5, 40, 24, 80) == (5 to 1), "CHA default column 1")
        ok(CsiCursor.apply(CsiCursor.parse("\u001B[99;99H"), 1, 1, 24, 80) == (24 to 80), "CUP clamps both axes")
        refuses("SGR final belongs to AnsiSgr") { CsiCursor.parse("\u001B[1m") }
        refuses("two params on CUF") { CsiCursor.parse("\u001B[1;2C") }
        refuses("cursor off screen") { CsiCursor.apply(CsiCursor.Move('A', 1, 1), 25, 1, 24, 80) }
    }

    private fun gpc() {
        ok(GpcHeader.HEADER == "Sec-GPC", "header name pinned")
        refuses("empty value") { GpcHeader.parseHeader("") }
        ok(GpcHeader.parseWellKnown("{ \"gpc\" : true }").gpc, "whitespace tolerated")
        ok(GpcHeader.parseWellKnown("{\"gpc\":true}").lastUpdate == null, "lastUpdate optional")
        ok(!GpcHeader.isSignalPresent(emptyMap()), "empty headers")
        refuses("malformed lastUpdate ignored but gpc still needed") { GpcHeader.parseWellKnown("{\"lastUpdate\":\"x\"}") }
        ok(GpcHeader.parseHeader("1") && GpcHeader.parseHeader(" 1 "), "the one defined value")
        ok(GpcHeader.isSignalPresent(mapOf("sec-gpc" to "1")), "case-insensitive header name")
        ok(!GpcHeader.isSignalPresent(mapOf("Other" to "1")), "absent header is absent")
        val w = GpcHeader.parseWellKnown("{\"gpc\": true, \"lastUpdate\": \"2026-10-01\"}")
        ok(w.gpc && w.lastUpdate == "2026-10-01", "well-known parse")
        ok(!GpcHeader.parseWellKnown("{\"gpc\":false}").gpc, "false is false, not refused")
        refuses("Sec-GPC: 0 refuses, never coerces") { GpcHeader.parseHeader("0") }
        refuses("Sec-GPC: true refuses") { GpcHeader.parseHeader("true") }
        refuses("missing gpc field") { GpcHeader.parseWellKnown("{\"x\":1}") }
        refuses("not json object") { GpcHeader.parseWellKnown("gpc=true") }
    }

    private fun pwPolicy() {
        ok(PwPolicy.check("UPPER1!lower".toCharArray(), PwPolicy.Policy(4, requireUpper = true, requireLower = true, requireDigit = true, requireSymbol = true)).pass, "all classes present")
        ok(!PwPolicy.check("nodigits!A".toCharArray(), PwPolicy.Policy(4, requireDigit = true)).digitOk, "digit gate")
        ok(PwPolicy.check("ab".toCharArray(), PwPolicy.Policy(1, maxLength = 2)).lengthOk, "max boundary inclusive")
        ok(!PwPolicy.check("abc".toCharArray(), PwPolicy.Policy(1, maxLength = 2)).lengthOk, "over max flagged")
        ok(PwPolicy.Policy(8).let { PwPolicy.check("aaaaaaaa".toCharArray(), it).pass }, "no class requirements")
        val policy = PwPolicy.Policy(12, requireUpper = true, requireDigit = true, requireSymbol = true)
        ok(PwPolicy.check("Aaaaaaaaaaa1!".toCharArray(), policy).pass, "weak-but-compliant passes (policy != strength)")
        val r = PwPolicy.check("short1!".toCharArray(), policy)
        ok(!r.pass && !r.lengthOk && r.digitOk && r.symbolOk, "individual flags reported")
        ok(!PwPolicy.check("nouppercase1!aaa".toCharArray(), policy).upperOk, "missing upper flagged")
        ok(PwPolicy.check("ünïcödé-pass".toCharArray(), PwPolicy.Policy(8, requireLower = true)).pass, "non-ASCII satisfies letter class (declared)")
        ok(PwPolicy.check("x".toCharArray(), PwPolicy.Policy(1)).pass, "minimal policy")
        refuses("empty password") { PwPolicy.check(CharArray(0), PwPolicy.Policy(1)) }
        refuses("inverted length window") { PwPolicy.Policy(10, maxLength = 5) }
    }

    private fun gainDb() {
        ok(near(GainDb.dbToAmplitude(0.0), 1.0, 1e-15) && near(GainDb.dbToPower(0.0), 1.0, 1e-15), "0dB is unity in both laws")
        refuses("infinite ratio") { GainDb.amplitudeToDb(Double.POSITIVE_INFINITY) }
        ok(near(GainDb.amplitudeToDb(1.0), 0.0, 1e-15), "unity is 0dB")
        ok(near(GainDb.dbToPower(GainDb.powerToDb(7.5)), 7.5, 1e-12), "power roundtrip")
        ok(GainDb.amplitudeToDb(2.0) > GainDb.powerToDb(2.0), "20log exceeds 10log for same ratio")
        ok(near(GainDb.amplitudeToDb(10.0), 20.0, 1e-12), "x10 amplitude is +20dB")
        ok(near(GainDb.powerToDb(10.0), 10.0, 1e-12), "x10 power is +10dB")
        ok(near(GainDb.dbToAmplitude(-6.020599913279624), 0.5, 1e-12), "-6.02dB is half amplitude")
        ok(near(GainDb.amplitudeToDb(0.5), -6.020599913279624, 1e-12), "inverse direction")
        ok(near(GainDb.dbToAmplitude(GainDb.amplitudeToDb(3.7)), 3.7, 1e-12), "roundtrip")
        ok(near(GainDb.combine(-3.0, -3.0), -6.0, 1e-12), "gains add in dB")
        ok(near(GainDb.powerToDb(2.0), 3.0102999566398116, 1e-12), "double power is ~3dB")
        refuses("zero ratio") { GainDb.amplitudeToDb(0.0) }
        refuses("negative ratio") { GainDb.powerToDb(-1.0) }
        refuses("NaN dB") { GainDb.dbToAmplitude(Double.NaN) }
    }

    private fun hexBytes() {
        ok(HexBytes.isCanonical(HexBytes.encode(byteArrayOf(1, 2, 3))), "encode output is canonical")
        ok(HexBytes.decode("00").single() == 0.toByte(), "single zero byte")
        ok(HexBytes.encode(byteArrayOf(0x7F, -128)) == "7f80", "sign boundary bytes")
        ok(HexBytes.decode(HexBytes.encode(ByteArray(32) { it.toByte() })).size == 32, "32-byte digest roundtrip")
        refuses("uppercase is decodable but 0x is not") { HexBytes.decode("0XAB") }
        ok(HexBytes.encode(byteArrayOf(0, 15, -1)) == "000fff", "encode lowercase")
        ok(HexBytes.decode("000fff").contentEquals(byteArrayOf(0, 15, -1)), "decode")
        ok(HexBytes.decode("ABCDEF").contentEquals(HexBytes.decode("abcdef")), "case-insensitive decode")
        ok(HexBytes.encode(HexBytes.decode("deadbeef")) == "deadbeef", "roundtrip")
        ok(HexBytes.isCanonical("00ff") && !HexBytes.isCanonical("00FF") && !HexBytes.isCanonical("0f0"), "canonical check")
        ok(HexBytes.encode(ByteArray(0)) == "" && HexBytes.decode("").isEmpty(), "empty is fine")
        refuses("odd length") { HexBytes.decode("abc") }
        refuses("0x prefix") { HexBytes.decode("0xab") }
        refuses("whitespace") { HexBytes.decode("ab cd") }
        refuses("non-hex") { HexBytes.decode("zz") }
    }

    private fun snooze() {
        ok(SnoozeSeq.stepsToCap(10, 10) == 0, "already at cap")
        refuses("64-step guard unreachable normally") { SnoozeSeq.duration(1, 60, Int.MIN_VALUE) }
        ok(SnoozeSeq.ladder(1, 8) == listOf(1, 2, 4, 8), "powers of two to cap")
        ok(SnoozeSeq.ladder(7, 20) == listOf(7, 14, 20), "cap clips the last doubling")
        ok(SnoozeSeq.duration(5, 60, 4) == 60, "exactly at cap step")
        ok(SnoozeSeq.ladder(5, 60) == listOf(5, 10, 20, 40, 60), "doubling ladder caps at 60")
        ok(SnoozeSeq.duration(5, 60, 0) == 5 && SnoozeSeq.duration(5, 60, 3) == 40, "indexed durations")
        ok(SnoozeSeq.duration(5, 60, 99) == 60, "beyond cap stays at cap")
        ok(SnoozeSeq.stepsToCap(5, 60) == 4, "steps to cap")
        ok(SnoozeSeq.ladder(10, 10) == listOf(10), "base equals cap")
        var monotone = true
        for (i in 0 until 10) if (SnoozeSeq.duration(3, 45, i + 1) < SnoozeSeq.duration(3, 45, i)) monotone = false
        ok(monotone, "monotone non-decreasing (property)")
        refuses("zero base") { SnoozeSeq.duration(0, 60, 0) }
        refuses("cap below base") { SnoozeSeq.ladder(30, 5) }
        refuses("negative index") { SnoozeSeq.duration(5, 60, -1) }
    }

    private fun niceStep() {
        ok(NiceStep.step(0.0, 50.0, 5) == 10.0, "0..50 five ticks")
        ok(NiceStep.ticks(0.0, 10.0, 5).first() == 0.0, "ticks start at or below min")
        ok(NiceStep.step(0.0, 10.0, 5) == 2.0, "0..10 five ticks")
        ok(NiceStep.ticks(-1.0, 1.0, 4).contains(0.0), "zero line present in symmetric range")
        ok(NiceStep.step(0.0, 7.0, 100) == 0.1, "generous budget picks the smallest nice step of the rough magnitude")
        refuses("NaN bound") { NiceStep.step(Double.NaN, 1.0, 4) }
        ok(NiceStep.step(0.0, 100.0, 6) == 20.0, "0..100 with 6 ticks picks 20")
        ok(NiceStep.step(0.0, 100.0, 11) == 10.0, "11 ticks picks 10")
        ok(NiceStep.step(0.0, 1.0, 5) == 0.2, "unit range picks 0.2")
        ok(NiceStep.step(-50.0, 50.0, 4) == 50.0, "symmetric range")
        ok(NiceStep.ticks(0.0, 100.0, 6) == listOf(0.0, 20.0, 40.0, 60.0, 80.0, 100.0), "tick positions")
        var bounded = true
        for (mt in 2..12) {
            val s = NiceStep.step(3.0, 97.0, mt)
            if (Math.ceil(97.0 / s) - Math.floor(3.0 / s) > mt) bounded = false
        }
        ok(bounded, "tick bound honored across maxTicks sweep (property)")
        ok(NiceStep.step(0.0, 0.003, 4) == 0.001, "small magnitudes")
        refuses("degenerate range") { NiceStep.step(5.0, 5.0, 4) }
        refuses("one tick") { NiceStep.step(0.0, 1.0, 1) }
    }

    private fun nextRun() {
        ok(NextRun.nextAfter(5000, 600, 5000) - 5000 == 600L, "one period after anchor")
        refuses("period 0") { NextRun.isRunTime(0, 0, 0) }
        ok(NextRun.nextAfter(0, 60, 0) == 60L, "minimal period from epoch")
        ok(NextRun.isRunTime(0, 60, 0), "anchor is a run time")
        ok(NextRun.runsBetween(0, 3600, 0, 86400) == 24L, "hourly runs in a day")
        refuses("negative now") { NextRun.nextAfter(0, 60, -5) }
        ok(NextRun.nextAfter(1000, 600, 999) == 1000L, "before anchor returns anchor")
        ok(NextRun.nextAfter(1000, 600, 1000) == 1600L, "at anchor returns next")
        ok(NextRun.nextAfter(1000, 600, 2199) == 2200L, "mid-interval")
        ok(NextRun.nextAfter(1000, 600, 2200) == 2800L, "exactly at a run returns the following")
        ok(NextRun.isRunTime(1000, 600, 2200) && !NextRun.isRunTime(1000, 600, 2201), "run-time predicate")
        ok(NextRun.runsBetween(1000, 600, 999, 2800) == 4L, "runs in window (1000,1600,2200,2800)")
        ok(NextRun.runsBetween(1000, 600, 2800, 2800) == 0L, "empty window")
        var law = true
        for (now in longArrayOf(0, 1000, 1500, 9999)) {
            val n = NextRun.nextAfter(1000, 600, now)
            if (n <= now || !NextRun.isRunTime(1000, 600, n)) law = false
            if (n - 600 > now && n != 1000L) law = false
        }
        ok(law, "smallest-run-strictly-after law (property)")
        refuses("period below floor") { NextRun.nextAfter(0, 59, 100) }
        refuses("inverted window") { NextRun.runsBetween(0, 600, 10, 5) }
    }

    private fun rfc2231() {
        ok(Rfc2231.reassemble("t", mapOf("t*" to "UTF-8'x-klingon'ok")).language == "x-klingon", "language tag surfaces")
        refuses("extended plus continued") { Rfc2231.reassemble("f", mapOf("f*" to "UTF-8''a", "f*0" to "b")) }
        ok(Rfc2231.reassemble("f", mapOf("f*0" to "ab", "f*1" to "cd")).value == "abcd", "plain-only continuation")
        ok(Rfc2231.reassemble("f", mapOf("f*" to "UTF-8''%41")).value == "A", "minimal extended")
        ok(Rfc2231.reassemble("f", mapOf("f*" to "utf-8''x")).value == "x", "charset case-insensitive")
        refuses("missing apostrophes") { Rfc2231.reassemble("f", mapOf("f*" to "noquotes")) }
        val plain = Rfc2231.reassemble("filename", mapOf("filename" to "report.pdf"))
        ok(plain.value == "report.pdf" && plain.language == null, "plain parameter")
        val ext = Rfc2231.reassemble("filename", mapOf("filename*" to "UTF-8''r%C3%A9sum%C3%A9.pdf"))
        ok(ext.value == "r\u00E9sum\u00E9.pdf", "extended value decodes UTF-8")
        val lang = Rfc2231.reassemble("title", mapOf("title*" to "ISO-8859-1'en'%A3%20rates"))
        ok(lang.value == "\u00A3 rates" && lang.language == "en", "latin-1 + language tag kept visible")
        val cont = Rfc2231.reassemble("filename", mapOf(
            "filename*0*" to "UTF-8''very%20long%20",
            "filename*1" to "name",
            "filename*2*" to "%2Epdf"
        ))
        ok(cont.value == "very long name.pdf", "mixed continuation reassembles in order")
        refuses("gap in numbering") { Rfc2231.reassemble("f", mapOf("f*0" to "a", "f*2" to "b")) }
        refuses("must start at zero") { Rfc2231.reassemble("f", mapOf("f*1" to "a")) }
        refuses("plain and continued mixed") { Rfc2231.reassemble("f", mapOf("f" to "a", "f*0" to "b")) }
        refuses("unknown charset") { Rfc2231.reassemble("f", mapOf("f*" to "KOI8-R''x")) }
        refuses("foreign key") { Rfc2231.reassemble("f", mapOf("g*0" to "a")) }
        refuses("raw non-ascii in extended") { Rfc2231.reassemble("f", mapOf("f*" to "UTF-8''caf\u00E9")) }
    }

    private fun bmpHead() {
        ok(BmpHead.stride(1, 32) == 4, "32bpp single pixel")
        refuses("zero width via stride") { BmpHead.stride(0, 24) }
        ok(BmpHead.stride(4, 8) == 4 && BmpHead.stride(5, 8) == 8, "8bpp stride boundary")
        ok(BmpHead.stride(16, 1) == 4, "1bpp packs 16 pixels into one dword")
        fun bmp(width: Int, height: Int, bits: Int): ByteArray {
            val stride = BmpHead.stride(width, bits)
            val dataSize = stride * kotlin.math.abs(height)
            val total = 54 + dataSize
            val b = ByteArray(total)
            b[0] = 'B'.code.toByte(); b[1] = 'M'.code.toByte()
            fun put32(off: Int, v: Int) { for (k in 0..3) b[off + k] = ((v ushr (8 * k)) and 0xFF).toByte() }
            fun put16(off: Int, v: Int) { b[off] = (v and 0xFF).toByte(); b[off + 1] = ((v ushr 8) and 0xFF).toByte() }
            put32(2, total); put32(10, 54); put32(14, 40)
            put32(18, width); put32(22, height); put16(26, 1); put16(28, bits)
            return b
        }
        val h = BmpHead.parse(bmp(3, 2, 24))
        ok(h.width == 3 && h.height == 2 && !h.topDown && h.bitCount == 24, "3x2 24bpp")
        ok(h.dataOffset == 54L && h.fileSize == (54 + 24).toLong(), "offsets and size (stride 12 x 2)")
        ok(BmpHead.stride(3, 24) == 12, "stride pads to 4 bytes")
        ok(BmpHead.parse(bmp(2, -2, 32)).topDown, "negative height is top-down")
        ok(BmpHead.stride(1, 1) == 4, "1px 1bpp still one dword")
        val badMagic = bmp(1, 1, 24); badMagic[0] = 'X'.code.toByte()
        refuses("magic") { BmpHead.parse(badMagic) }
        val v5 = bmp(1, 1, 24); v5[14] = 124.toByte()
        refuses("V5 header refused by biSize") { BmpHead.parse(v5) }
        val badBits = bmp(1, 1, 24); badBits[28] = 7
        refuses("bitCount 7") { BmpHead.parse(badBits) }
        val lie = bmp(1, 1, 24); lie[2] = 1
        refuses("file size mismatch") { BmpHead.parse(lie) }
        refuses("short buffer") { BmpHead.parse(ByteArray(10)) }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        absRef(); mergedCells(); pdfDate(); nmea(); hsts(); pairPin(); arHead()
        caseCycle(); commentToggle(); unitPrice(); rule3rds(); histo(); csiCursor()
        gpc(); pwPolicy(); gainDb(); hexBytes(); snooze(); niceStep(); nextRun()
        rfc2231(); bmpHead()
        println("R42 PASS: $count production-kernel assertions across 22 kernels. Host JVM only; not Android/device evidence.")
    }
}

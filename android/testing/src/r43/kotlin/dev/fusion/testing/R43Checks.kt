package dev.fusion.testing

import dev.fusion.ooxml.ColLetters
import dev.fusion.ooxml.SheetRange
import dev.fusion.ooxml.SheetFreeze
import dev.fusion.documents.BidiStrip
import dev.fusion.editor.DupLines
import dev.fusion.format.pdf.PdfPerm
import dev.fusion.browser.model.RefererPolicy
import dev.fusion.archive.DebControl
import dev.fusion.capabilities.ApiName
import dev.fusion.together.SeatColor
import dev.fusion.media.Bpm
import dev.fusion.beam.TrackerTier
import dev.fusion.lens.QrVersion
import dev.fusion.security.RpIdMatch
import dev.fusion.model.SemVer
import dev.fusion.model.VersionRange
import dev.fusion.find.SpanMerge
import dev.fusion.find.SnippetWindow
import dev.fusion.vault.Bech32
import dev.fusion.vault.AgeId
import dev.fusion.filesui.BreadcrumbTrim
import dev.fusion.perf.Apdex
import dev.fusion.calc.PercentChange
import dev.fusion.airplay.RtspTransport
import dev.fusion.messages.MsgIdParse
import dev.fusion.privacy.TimeCoarse
import dev.fusion.windowing.AspectSnap
import dev.fusion.alerts.AlertKey

/** R43 executed contracts. Host JVM only; NOT Android/device evidence. */
object R43Checks {
    private var count = 0

    private fun ok(cond: Boolean, msg: String) {
        if (!cond) throw AssertionError(msg)
        count++
    }

    private fun refuses(msg: String, block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { count++; return }
        throw AssertionError("expected refusal: " + msg)
    }

    private fun near(a: Double, b: Double, eps: Double) = kotlin.math.abs(a - b) <= eps

    private fun sheetFreeze() {
        ok(SheetFreeze.Freeze(5, 5).let { fr -> SheetFreeze.paneOf(fr, SheetFreeze.scrollHome(fr)) == SheetFreeze.Pane.BOTTOM_RIGHT }, "scroll home is in the scroll pane (property)")
        ok(SheetFreeze.frozenCellCount(SheetFreeze.Freeze(2, 2), 4, 4) == 12L, "16 minus 2x2 scrollable")
        ok(SheetFreeze.scrollHome(SheetFreeze.Freeze(1, 1)) == SheetRange.Cell(2, 2), "B2 classic freeze home")
        ok(SheetFreeze.paneOf(SheetFreeze.Freeze(1, 1), SheetRange.Cell(1, 9)) == SheetFreeze.Pane.BOTTOM_LEFT, "frozen first column scrolls vertically")
        ok(SheetFreeze.frozenCellCount(SheetFreeze.Freeze(0, 1), 5, 5) == 5L, "single frozen row")
        ok(SheetFreeze.frozenCellCount(SheetFreeze.Freeze(2, 3), 10, 10) == SheetFreeze.frozenCellCount(SheetFreeze.Freeze(3, 2), 10, 10), "symmetric freeze areas match on a square viewport")
        refuses("rows eat grid") { SheetFreeze.Freeze(0, SheetRange.MAX_ROW) }
        val f = SheetFreeze.Freeze(2, 3)
        ok(SheetFreeze.paneOf(f, SheetRange.Cell(1, 1)) == SheetFreeze.Pane.TOP_LEFT, "A1 frozen both ways")
        ok(SheetFreeze.paneOf(f, SheetRange.Cell(3, 1)) == SheetFreeze.Pane.TOP_RIGHT, "right of split, above rows")
        ok(SheetFreeze.paneOf(f, SheetRange.Cell(1, 4)) == SheetFreeze.Pane.BOTTOM_LEFT, "below rows, left of split")
        ok(SheetFreeze.paneOf(f, SheetRange.Cell(3, 4)) == SheetFreeze.Pane.BOTTOM_RIGHT, "scroll pane")
        ok(SheetFreeze.paneOf(f, SheetRange.Cell(2, 3)) == SheetFreeze.Pane.TOP_LEFT, "boundary stays frozen")
        ok(SheetFreeze.scrollHome(f) == SheetRange.Cell(3, 4), "scroll home")
        ok(SheetFreeze.frozenCellCount(f, 10, 10) == 100L - 8L * 7L, "frozen band area")
        ok(SheetFreeze.frozenCellCount(SheetFreeze.Freeze(1, 0), 5, 5) == 5L, "single frozen column")
        ok(SheetFreeze.paneOf(SheetFreeze.Freeze(0, 1), SheetRange.Cell(9, 1)) == SheetFreeze.Pane.TOP_RIGHT, "rows-only freeze has an empty left region (consistent classification)")
        refuses("nothing frozen") { SheetFreeze.Freeze(0, 0) }
        refuses("negative freeze") { SheetFreeze.Freeze(-1, 0) }
        refuses("freeze eats grid") { SheetFreeze.Freeze(ColLetters.MAX_COLUMN, 0) }
        refuses("viewport under freeze") { SheetFreeze.frozenCellCount(f, 2, 10) }
    }

    private fun bidiStrip() {
        ok(BidiStrip.strip(BidiStrip.strip("x\u202Ey")) == "xy", "strip idempotent")
        ok(BidiStrip.positions("\u202A\u202B") == listOf(0, 1), "adjacent controls both reported")
        ok(BidiStrip.strip("") == "" && BidiStrip.positions("").isEmpty(), "empty text")
        ok(BidiStrip.isBalanced("\u202A\u202B\u202C\u202C"), "nested pushes balance")
        ok(!BidiStrip.isBalanced("\u202A"), "lone push")
        ok(BidiStrip.strip("a\u202Eb").length == 2, "strip length math")
        ok(BidiStrip.audit("x\u2068y\u2069z").second, "isolate pair audits balanced")
        val trojan = "if (x) {\u202E } \u2066admin\u2069"
        ok(BidiStrip.positions(trojan).size == 3, "three controls found")
        ok(BidiStrip.strip(trojan) == "if (x) { } admin", "strip removes exactly the controls")
        ok(!BidiStrip.isBalanced(trojan), "RLO without PDF unbalanced")
        ok(BidiStrip.isBalanced("\u2066abc\u2069"), "isolate pair balances")
        ok(BidiStrip.isBalanced("plain"), "plain text balanced")
        ok(!BidiStrip.isBalanced("\u202C"), "pop before push unbalanced")
        ok(BidiStrip.positions("a\u200Fb") == listOf(1), "RLM is a control")
        ok(BidiStrip.strip("\u200Eabc\u200F") == "abc", "marks stripped")
        ok(BidiStrip.audit(trojan) == (3 to false), "audit tuple")
        ok(BidiStrip.audit("ok") == (0 to true), "clean audit")
        ok(BidiStrip.strip("legit \u202Bعربي\u202C text").contains("عربي"), "strip keeps RTL letters themselves")
        ok(!BidiStrip.isControl('a') && BidiStrip.isControl('\u202E'), "classifier")
    }

    private fun dupLines() {
        ok(DupLines.dedupeKeepFirst(DupLines.dedupeKeepFirst(listOf("a", "a", "b"))) == listOf("a", "b"), "dedupe idempotent")
        ok(DupLines.duplicateCount(List(10) { "same" }) == 9, "ten identical leave one")
        ok(DupLines.dedupeKeepFirst(listOf("a", "a", "a")) == listOf("a"), "all same collapses to one")
        ok(DupLines.duplicateCount(listOf("x")) == 0, "singleton has no dups")
        ok(DupLines.groups(listOf("b", "a", "b", "a")) == listOf(listOf(1, 3), listOf(2, 4)), "two groups interleaved")
        ok(DupLines.dedupeKeepFirst(listOf("1", "2", "1", "3", "2")) == listOf("1", "2", "3"), "order stable")
        val lines = listOf("a", "b", "a", "c", "b", "a")
        ok(DupLines.groups(lines) == listOf(listOf(1, 3, 6), listOf(2, 5)), "groups in first-seen order")
        ok(DupLines.dedupeKeepFirst(lines) == listOf("a", "b", "c"), "keep first")
        ok(DupLines.duplicateCount(lines) == 3, "three removable lines")
        ok(DupLines.groups(listOf("x", "y")) == emptyList<List<Int>>(), "no dups")
        ok(DupLines.groups(listOf("a ", "a")).isEmpty(), "trailing space matters (exact match)")
        ok(DupLines.dedupeKeepFirst(emptyList()) == emptyList<String>(), "empty input")
        ok(DupLines.groups(listOf("", "", "")) == listOf(listOf(1, 2, 3)), "blank lines group too")
    }

    private fun pdfPerm() {
        ok(PdfPerm.decode(PdfPerm.encode(PdfPerm.decode(12345678))) == PdfPerm.decode(12345678), "decode-encode-decode fixpoint")
        ok(!PdfPerm.decode(0).fillForms && !PdfPerm.decode(0).assemble, "high permission bits clear at zero")
        ok(!PdfPerm.decode(0).print && !PdfPerm.decode(0).copy, "zero grants nothing in the decoded bits")
        ok(PdfPerm.decode(1 shl 2).print && !PdfPerm.decode(1 shl 2).modify, "isolated bit 3")
        ok(PdfPerm.decode(1 shl 11).printHiRes, "isolated bit 12")
        ok(!PdfPerm.allAllowed(-1 and (1 shl 8).inv()), "clearing fillForms breaks allAllowed")
        val roundAll = PdfPerm.Perms(true, true, true, true, true, true, true, true)
        ok(PdfPerm.decode(PdfPerm.encode(roundAll)) == roundAll, "all-true roundtrip")
        val roundNone = PdfPerm.Perms(false, false, false, false, false, false, false, false)
        ok(PdfPerm.decode(PdfPerm.encode(roundNone)) == roundNone, "all-false roundtrip")
        val all = PdfPerm.decode(-1)
        ok(all.print && all.modify && all.copy && all.annotate && all.fillForms && all.extractAccess && all.assemble && all.printHiRes, "-1 grants all")
        ok(PdfPerm.allAllowed(-1), "allAllowed on -1")
        val noPrint = PdfPerm.decode(-1 and (1 shl 2).inv())
        ok(!noPrint.print && noPrint.modify, "bit 3 cleared kills print only")
        val noCopy = PdfPerm.decode(-1 and (1 shl 4).inv())
        ok(!noCopy.copy && noCopy.print, "bit 5 cleared kills copy only")
        val p = PdfPerm.Perms(print = true, modify = false, copy = false, annotate = true, fillForms = true, extractAccess = true, assemble = false, printHiRes = false)
        ok(PdfPerm.decode(PdfPerm.encode(p)) == p, "encode/decode roundtrip")
        ok(PdfPerm.decode(PdfPerm.encode(PdfPerm.decode(-1))).let { it.print && it.printHiRes }, "roundtrip of all-granted")
        ok(PdfPerm.encode(p) < 0, "reserved high bits make P negative (file reality)")
        ok(PdfPerm.encode(p) and 3 == 0, "bits 1-2 reserved zero")
    }

    private fun referer() {
        ok(RefererPolicy.decide(RefererPolicy.Policy.SAME_ORIGIN, RefererPolicy.Origin("https", "x", 443), RefererPolicy.Origin("https", "x", 443)) == RefererPolicy.Send.FULL, "same-origin full")
        ok(RefererPolicy.Policy.values().size == 8, "eight tokens pinned")
        ok(RefererPolicy.decide(RefererPolicy.Policy.ORIGIN, RefererPolicy.Origin("https", "a.b", 443), RefererPolicy.Origin("https", "a.b", 443)) == RefererPolicy.Send.ORIGIN, "origin policy trims even same-origin")
        ok(RefererPolicy.Origin("https", "x", 443) != RefererPolicy.Origin("https", "x", 8443), "port differentiates origins")
        ok(RefererPolicy.decide(RefererPolicy.Policy.NO_REFERRER_WHEN_DOWNGRADE, RefererPolicy.Origin("http", "a", 80), RefererPolicy.Origin("http", "b", 80)) == RefererPolicy.Send.FULL, "http to http is not a downgrade")
        refuses("port 0") { RefererPolicy.Origin("https", "x", 0) }
        refuses("empty host") { RefererPolicy.Origin("https", "", 443) }
        val a = RefererPolicy.Origin("https", "example.org", 443)
        val b = RefererPolicy.Origin("https", "other.net", 443)
        val insecure = RefererPolicy.Origin("http", "other.net", 80)
        ok(RefererPolicy.decide(RefererPolicy.Policy.NO_REFERRER, a, a) == RefererPolicy.Send.NOTHING, "no-referrer always nothing")
        ok(RefererPolicy.decide(RefererPolicy.Policy.UNSAFE_URL, a, insecure) == RefererPolicy.Send.FULL, "unsafe-url leaks everywhere")
        ok(RefererPolicy.decide(RefererPolicy.DEFAULT, a, a) == RefererPolicy.Send.FULL, "default same-origin full")
        ok(RefererPolicy.decide(RefererPolicy.DEFAULT, a, b) == RefererPolicy.Send.ORIGIN, "default cross-origin origin")
        ok(RefererPolicy.decide(RefererPolicy.DEFAULT, a, insecure) == RefererPolicy.Send.NOTHING, "default downgrade nothing")
        ok(RefererPolicy.decide(RefererPolicy.Policy.SAME_ORIGIN, a, b) == RefererPolicy.Send.NOTHING, "same-origin blocks cross")
        ok(RefererPolicy.decide(RefererPolicy.Policy.ORIGIN, a, insecure) == RefererPolicy.Send.ORIGIN, "origin policy ignores downgrade")
        ok(RefererPolicy.decide(RefererPolicy.Policy.STRICT_ORIGIN, a, insecure) == RefererPolicy.Send.NOTHING, "strict-origin respects downgrade")
        ok(RefererPolicy.decide(RefererPolicy.Policy.NO_REFERRER_WHEN_DOWNGRADE, a, b) == RefererPolicy.Send.FULL, "legacy default full cross-origin")
        ok(RefererPolicy.parse("strict-origin-when-cross-origin") == RefererPolicy.DEFAULT, "token parse")
        ok(RefererPolicy.parse(" Origin ") == RefererPolicy.Policy.ORIGIN, "token trims and lowercases")
        ok(RefererPolicy.decide(RefererPolicy.Policy.ORIGIN_WHEN_CROSS_ORIGIN, a, a) == RefererPolicy.Send.FULL, "owco same-origin")
        refuses("unknown token") { RefererPolicy.parse("paranoid") }
        refuses("ftp origin") { RefererPolicy.Origin("ftp", "x", 21) }
        val httpUpgrade = RefererPolicy.decide(RefererPolicy.Policy.STRICT_ORIGIN, insecure, a)
        ok(httpUpgrade == RefererPolicy.Send.ORIGIN, "http->https is not a downgrade")
    }

    private fun debControl() {
        ok(DebControl.parse("Depends: a (>= 1), b")["Depends"] == "a (>= 1), b", "relationship field verbatim")
        ok(DebControl.parse("A: 1\nB: 2").keys.toList() == listOf("A", "B"), "field order preserved")
        ok(DebControl.parse("K: v")["K"] == "v", "minimal field")
        ok(DebControl.parse("K:v")["K"] == "v", "no space after colon is fine")
        ok(DebControl.parse("K: a\n\tb")["K"] == "a\nb", "tab continuation")
        ok(DebControl.get(DebControl.parse("Multi-Arch: same"), "multi-arch") == "same", "hyphenated field name")
        val text = "Package: fusion\nVersion: 1.0-1\nArchitecture: arm64\nMaintainer: dev\nDescription: a tool\n extended line one\n .\n extended after blank\n"
        val f = DebControl.parse(text)
        ok(DebControl.get(f, "package") == "fusion", "case-insensitive lookup")
        ok(f["Description"] == "a tool\nextended line one\n\nextended after blank", "folded description with dot marker")
        ok(DebControl.requireBinaryFields(f) === f, "required fields present")
        ok(DebControl.parse("A: 1\nB: 2\n\nIgnored: x").size == 2, "blank line ends the paragraph")
        refuses("duplicate field") { DebControl.parse("A: 1\na: 2") }
        refuses("continuation first") { DebControl.parse(" cont") }
        refuses("no colon") { DebControl.parse("garbage line") }
        refuses("space in name") { DebControl.parse("Bad Name: x") }
        refuses("missing required") { DebControl.requireBinaryFields(DebControl.parse("Package: x")) }
        refuses("empty input") { DebControl.parse("") }
    }

    private fun apiName() {
        ok(ApiName.of(34).version == "14", "34 is Android 14")
        ok((21..36).count() == 16, "sixteen pinned entries")
        ok(ApiName.of(29).codename == "Q" && ApiName.of(30).codename == "R", "letter era")
        ok(ApiName.supports(99, 36), "future device supports pinned requirement")
        ok(ApiName.MIN == 21 && ApiName.MAX == 36, "bounds pinned")
        refuses("device level zero") { ApiName.supports(0, 21) }
        ok(ApiName.of(33).codename == "Tiramisu", "33 Tiramisu")
        ok(ApiName.of(36).codename == "Baklava" && ApiName.of(36).version == "16", "36 Baklava")
        ok(ApiName.of(21).version == "5.0" && ApiName.of(32).version == "12L", "edges and 12L")
        ok(ApiName.supports(36, 33) && !ApiName.supports(32, 33), "supports comparison")
        ok((ApiName.MIN..ApiName.MAX).all { ApiName.of(it).codename.isNotEmpty() }, "table complete over pinned range")
        refuses("API 37 not pinned yet") { ApiName.of(37) }
        refuses("API 20 below table") { ApiName.of(20) }
        refuses("required outside table") { ApiName.supports(36, 37) }
    }

    private fun seatColor() {
        ok(SeatColor.hues(10).toSet().size == 10, "first ten hues distinct")
        ok(SeatColor.minSeparation(20) > 5.0, "twenty seats still > 5 degrees apart")
        ok(SeatColor.hues(2).let { kotlin.math.abs(it[1] - it[0]) > 100.0 }, "two seats far apart")
        ok(near(SeatColor.hue(360) % 360.0, SeatColor.hue(360), 1e-9), "hue already reduced mod 360")
        ok(SeatColor.minSeparation(2) > SeatColor.minSeparation(50), "more seats pack tighter")
        ok(SeatColor.GOLDEN_ANGLE > 137.5 && SeatColor.GOLDEN_ANGLE < 137.51, "constant sanity")
        ok(SeatColor.hue(0) == 0.0, "seat zero at hue zero")
        ok(near(SeatColor.hue(1), 137.50776405003785, 1e-12), "golden angle step")
        ok(near(SeatColor.hue(2), 275.0155281000757, 1e-9), "second step")
        ok(SeatColor.hues(20).all { it in 0.0..360.0 }, "hues stay on the wheel")
        ok(SeatColor.minSeparation(8) > 20.0, "eight seats stay well separated")
        ok(SeatColor.minSeparation(3) > SeatColor.minSeparation(20), "separation shrinks with count")
        ok(SeatColor.hues(5) == (0 until 5).map { SeatColor.hue(it) }, "deterministic across peers")
        refuses("negative seat") { SeatColor.hue(-1) }
        refuses("count 0") { SeatColor.hues(0) }
        refuses("count 1001") { SeatColor.hues(1001) }
    }

    private fun bpm() {
        ok(near(Bpm.fromMs(Bpm.msPerBeat(97.3)), 97.3, 1e-9), "odd tempo roundtrip")
        refuses("negative tap delta") { Bpm.fromTaps(longArrayOf(100, 50, 200)) }
        ok(near(Bpm.msPerBeat(60.0), 1000.0, 1e-12), "60 bpm is a second")
        ok(near(Bpm.fromTaps(longArrayOf(0, 450, 950, 1400, 1900)), 60000.0 / 475.0, 1e-6), "median of mixed intervals")
        ok(Bpm.median(doubleArrayOf(7.0)) == 7.0, "singleton median")
        refuses("interval implies 500 bpm") { Bpm.fromMs(120.0) }
        ok(near(Bpm.msPerBeat(120.0), 500.0, 1e-12), "120 bpm is 500ms")
        ok(near(Bpm.fromMs(500.0), 120.0, 1e-12), "inverse")
        ok(Bpm.median(doubleArrayOf(480.0, 500.0, 520.0)) == 500.0, "odd median")
        ok(Bpm.median(doubleArrayOf(480.0, 520.0)) == 500.0, "even median averages")
        val taps = longArrayOf(0, 500, 1000, 1500, 3500)
        ok(near(Bpm.fromTaps(taps), 120.0, 1e-9), "median ignores the one missed tap (mean would not)")
        ok(near(Bpm.msPerBeat(Bpm.fromMs(431.0)), 431.0, 1e-9), "roundtrip")
        refuses("bpm 10") { Bpm.msPerBeat(10.0) }
        refuses("bpm 500") { Bpm.msPerBeat(500.0) }
        refuses("two taps") { Bpm.fromTaps(longArrayOf(0, 500)) }
        refuses("non-increasing taps") { Bpm.fromTaps(longArrayOf(0, 500, 500)) }
        refuses("empty median") { Bpm.median(DoubleArray(0)) }
    }

    private fun trackerTier() {
        refuses("whitespace url") { TrackerTier.validate(listOf(listOf("https://a b/ann"))) }
        ok(TrackerTier.flatten(listOf(listOf("udp://x:6969/a", "udp://y:6969/a"), listOf("https://z/a"))).size == 3, "three trackers flattened")
        ok(TrackerTier.flatten(listOf(listOf("udp://t:1/a"))) == listOf("udp://t:1/a"), "single tracker")
        ok(TrackerTier.validate(listOf(listOf("https://a/x"), listOf("https://a/x"))).size == 2, "cross-tier duplicates are legal (dedupe is per-tier)")
        ok(TrackerTier.tierOf(listOf(listOf("https://a/x"), listOf("https://a/x")), "https://a/x") == 0, "lookup finds the first tier")
        val tiers = listOf(listOf("https://a/ann", "udp://b:80/ann", "https://a/ann"), listOf("https://c/ann"))
        val v = TrackerTier.validate(tiers)
        ok(v[0] == listOf("https://a/ann", "udp://b:80/ann"), "in-tier dedupe keeps order")
        ok(TrackerTier.flatten(tiers) == listOf("https://a/ann", "udp://b:80/ann", "https://c/ann"), "tier priority flatten")
        ok(TrackerTier.tierOf(tiers, "https://c/ann") == 1, "tier lookup")
        refuses("empty announce-list") { TrackerTier.validate(emptyList()) }
        refuses("empty tier") { TrackerTier.validate(listOf(emptyList())) }
        refuses("ftp tracker") { TrackerTier.validate(listOf(listOf("ftp://x/ann"))) }
        refuses("unknown url tier") { TrackerTier.tierOf(tiers, "https://zz/ann") }
    }

    private fun qrVersion() {
        ok((21..177 step 4).count() == 40, "forty lattice sizes")
        ok(QrVersion.versionForSize(QrVersion.size(23)) == 23, "mid bijection spot")
        ok(QrVersion.size(10) == 57 && QrVersion.size(25) == 117, "mid versions")
        ok(QrVersion.versionForSize(45) == 7, "45 modules is version 7")
        refuses("size 178") { QrVersion.versionForSize(178) }
        refuses("negative version") { QrVersion.hasVersionInfo(0) }
        ok(QrVersion.size(1) == 21 && QrVersion.size(40) == 177, "lattice endpoints")
        ok(QrVersion.size(7) == 45, "version 7")
        ok(QrVersion.versionForSize(21) == 1 && QrVersion.versionForSize(177) == 40, "inverse endpoints")
        ok((1..40).all { QrVersion.versionForSize(QrVersion.size(it)) == it }, "full bijection 1..40")
        ok(!QrVersion.hasVersionInfo(6) && QrVersion.hasVersionInfo(7), "version info from 7")
        ok(QrVersion.FINDER_COUNT == 3, "three finders always")
        refuses("version 0") { QrVersion.size(0) }
        refuses("version 41") { QrVersion.size(41) }
        refuses("off-lattice 22") { QrVersion.versionForSize(22) }
        refuses("micro 17") { QrVersion.versionForSize(17) }
    }

    private fun rpId() {
        ok(RpIdMatch.matches("example.org", "org").matches, "bare TLD matches by dot rule — exactly WHY the PSL flag exists")
        ok(!RpIdMatch.matches("example.org", "org").pslChecked, "and the flag says PSL was not applied")
        ok(RpIdMatch.matches("deep.a.b.example.org", "example.org").matches, "deep subdomain")
        ok(!RpIdMatch.matches("example.com", "example.org").matches, "different domain")
        ok(RpIdMatch.validateRpId("xn--bcher-kva.example") == "xn--bcher-kva.example", "punycode labels pass shape")
        refuses("trailing dot label") { RpIdMatch.validateRpId("example.org.") }
        refuses("253+ length") { RpIdMatch.validateRpId("a.".repeat(127) + "bc") }
        ok(RpIdMatch.matches("login.example.org", "example.org").matches, "subdomain matches")
        ok(RpIdMatch.matches("example.org", "example.org").matches, "exact matches")
        ok(!RpIdMatch.matches("evilexample.org", "example.org").matches, "dot boundary enforced")
        ok(!RpIdMatch.matches("example.org.evil.net", "example.org").matches, "suffix not prefix")
        ok(!RpIdMatch.matches("x.org", "example.org").pslChecked, "result admits PSL not checked")
        ok(RpIdMatch.matches("LOGIN.Example.ORG", "example.org").matches, "origin host case-folds")
        refuses("scheme in rpId") { RpIdMatch.validateRpId("https://example.org") }
        refuses("port in rpId") { RpIdMatch.matches("x", "example.org:443") }
        refuses("uppercase rpId") { RpIdMatch.validateRpId("Example.org") }
        refuses("IP rpId") { RpIdMatch.validateRpId("192.168.1.1") }
        refuses("hyphen edge label") { RpIdMatch.validateRpId("-bad.org") }
        refuses("empty label") { RpIdMatch.validateRpId("a..b") }
    }

    private fun versionRange() {
        ok(VersionRange.satisfies("1.999.999", "^1.0.0"), "caret spans whole major")
        ok(!VersionRange.satisfies("1.2.3", "~1.2.4"), "tilde lower bound inclusive means 1.2.3 < 1.2.4 fails")
        ok(VersionRange.satisfies("1.2.3", "^1.2.3"), "lower bound inclusive")
        ok(!VersionRange.satisfies("1.2.5-rc.1", "^1.2.3"), "prerelease inside range still refused by declared rule")
        ok(VersionRange.parse("~1.2.3").upperExclusive == SemVer(1, 3, 0, emptyList(), emptyList()), "tilde upper")
        ok(VersionRange.parse("^0.2.3").upperExclusive == SemVer(0, 3, 0, emptyList(), emptyList()), "caret 0.x upper")
        refuses("empty after operator") { VersionRange.parse("^") }
        ok(VersionRange.satisfies("1.5.0", "^1.2.3"), "caret within major")
        ok(!VersionRange.satisfies("2.0.0", "^1.2.3"), "caret excludes next major")
        ok(!VersionRange.satisfies("1.2.2", "^1.2.3"), "below lower bound")
        ok(VersionRange.satisfies("0.2.9", "^0.2.3") && !VersionRange.satisfies("0.3.0", "^0.2.3"), "caret 0.x pins minor")
        ok(VersionRange.satisfies("0.0.3", "^0.0.3") && !VersionRange.satisfies("0.0.4", "^0.0.3"), "caret 0.0.x pins patch")
        ok(VersionRange.satisfies("1.2.9", "~1.2.3") && !VersionRange.satisfies("1.3.0", "~1.2.3"), "tilde pins minor")
        ok(!VersionRange.satisfies("2.0.0-beta.1", "^1.2.3"), "prerelease of upper bound outside")
        ok(VersionRange.parse("^1.2.3").upperExclusive == SemVer(2, 0, 0, emptyList(), emptyList()), "upper bound shape")
        ok(VersionRange.parse("~0.0.3").upperExclusive == SemVer(0, 1, 0, emptyList(), emptyList()), "tilde on 0.0.x still bumps minor")
        refuses("no operator") { VersionRange.parse("1.2.3") }
        refuses("prerelease lower bound") { VersionRange.parse("^1.2.3-rc.1") }
        refuses("wildcard") { VersionRange.parse("^1.x.0") }
    }

    private fun snippetWindow() {
        ok(SnippetWindow.best(listOf(SpanMerge.Span(95, 100)), 10, 100).start == 90, "window clamps to text end")
        ok(SnippetWindow.best(listOf(SpanMerge.Span(0, 3)), 10, 5).start == 0, "short text start zero")
        ok(SnippetWindow.best(listOf(SpanMerge.Span(5, 8)), 3, 10).covered == 3, "exact-fit window")
        ok(SnippetWindow.best(listOf(SpanMerge.Span(0, 100)), 10, 100).covered == 10, "giant span clips to width")
        ok(SnippetWindow.coveredIn(listOf(SpanMerge.Span(0, 5)), 10, 5) == 0, "disjoint window")
        val spans = listOf(SpanMerge.Span(10, 15), SpanMerge.Span(40, 48), SpanMerge.Span(50, 55))
        val w = SnippetWindow.best(spans, 20, 100)
        ok(w.start == 35 && w.covered == 13, "window catches the dense pair; smallest tied start (35, from end-width candidate) wins")
        ok(SnippetWindow.best(spans, 100, 100).covered == 18, "window over everything covers all")
        ok(SnippetWindow.best(emptyList(), 10, 50) == SnippetWindow.Window(0, 0), "no matches")
        ok(SnippetWindow.coveredIn(spans, 0, 12) == 2, "partial overlap counts partially")
        val tie = SnippetWindow.best(listOf(SpanMerge.Span(0, 5), SpanMerge.Span(90, 95)), 10, 100)
        ok(tie.start == 0 && tie.covered == 5, "tie resolves to smallest start")
        var agrees = true
        val small = listOf(SpanMerge.Span(2, 4), SpanMerge.Span(7, 9), SpanMerge.Span(12, 13))
        val bestC = SnippetWindow.best(small, 6, 20)
        for (s in 0..14) if (SnippetWindow.coveredIn(SpanMerge.merge(small), s, 6) > bestC.covered) agrees = false
        ok(agrees, "candidate-edge optimality agrees with brute force (property)")
        refuses("zero width") { SnippetWindow.best(spans, 0, 100) }
        refuses("span outside text") { SnippetWindow.best(listOf(SpanMerge.Span(0, 10)), 5, 8) }
    }

    private fun ageId() {
        ok(AgeId.convert5to8(intArrayOf(31, 28)).let { it.size == 1 && (it[0].toInt() and 0xFF) == 0xFF }, "11111 111 00 packs to 0xFF with clean padding")
        refuses("short payload") { AgeId.recipientPayload(Bech32.encode("age", IntArray(8) { 0 }, Bech32.Variant.BECH32)) }
        ok(AgeId.convert5to8(IntArray(16) { 0 }).size == 10, "16 groups to 10 bytes")
        ok(!AgeId.isRecipient("not-bech32-at-all"), "garbage rejected")
        ok(!AgeId.looksLikeIdentity("age-secret-key-1qqq"), "identity marker is case-exact per spec")
        val payload = IntArray(52) { 1 }
        val rec = Bech32.encode("age", payload.copyOf(51).let { IntArray(52) { i -> if (i < 51) 1 else 0 } }, Bech32.Variant.BECH32)
        // 52 five-bit groups hold 260 bits; 32 bytes need 256 => last 4 bits must be zero padding
        ok(AgeId.convert5to8(IntArray(8) { 0 }).size == 5, "8 groups to 5 bytes")
        ok(AgeId.convert5to8(intArrayOf(1, 0)).let { it.size == 1 && it[0].toInt() == 8 }, "5+5 bits to one byte drops clean padding")
        refuses("nonzero padding") { AgeId.convert5to8(intArrayOf(1, 1)) }
        refuses("value out of range") { AgeId.convert5to8(intArrayOf(32)) }
        ok(AgeId.isRecipient(rec), "well-formed age recipient accepted")
        ok(AgeId.recipientPayload(rec).size == 32, "payload is 32 bytes")
        ok(!AgeId.isRecipient("osb1qpzry9x8gf23krs7p"), "wrong HRP rejected")
        ok(AgeId.looksLikeIdentity("AGE-SECRET-KEY-1QQQ"), "identity shape flagged secret")
        ok(!AgeId.looksLikeIdentity("age1qqq"), "recipient is not identity-shaped")
        refuses("bech32m variant rejected for age") {
            AgeId.recipientPayload(Bech32.encode("age", IntArray(52) { 0 }, Bech32.Variant.BECH32M))
        }
    }

    private fun breadcrumb() {
        ok(BreadcrumbTrim.trim(List(10) { "d" }, 7).components.first() == "d", "deep path keeps root")
        ok(BreadcrumbTrim.trim(List(10) { "d" }, 7).components.size <= 5, "budget caps components")
        ok(BreadcrumbTrim.trim(listOf("a", "b", "c"), 5).components == listOf("a", "b", "c"), "a/b/c fits 5")
        ok(BreadcrumbTrim.trim(listOf("aa", "bb", "cc", "dd"), 9).components.contains(BreadcrumbTrim.ELLIPSIS), "tight budget ellipsizes")
        ok(BreadcrumbTrim.trim(listOf("root", "x"), 100) == BreadcrumbTrim.Trail(listOf("root", "x"), false), "two components fit")
        val parts = listOf("home", "projects", "fusion", "android", "docs")
        ok(BreadcrumbTrim.trim(parts, 100) == BreadcrumbTrim.Trail(parts, false), "fits untouched")
        val t = BreadcrumbTrim.trim(parts, 20)
        ok(t.components.first() == "home" && t.components.last() == "docs", "first and last survive")
        ok(BreadcrumbTrim.ELLIPSIS in t.components, "middle became ellipsis")
        ok(!t.overBudget, "20 chars is achievable")
        val floor = BreadcrumbTrim.trim(listOf("averylongrootname", "mid", "averylongleafname"), 10)
        ok(floor.overBudget && floor.components.size == 3, "floor keeps first+ellipsis+last and reports over budget")
        ok(BreadcrumbTrim.trim(listOf("a"), 1) == BreadcrumbTrim.Trail(listOf("a"), false), "single component")
        ok(BreadcrumbTrim.trim(listOf("longname", "x"), 3).overBudget, "two components cannot trim")
        refuses("empty path") { BreadcrumbTrim.trim(emptyList(), 10) }
        refuses("slash in component") { BreadcrumbTrim.trim(listOf("a/b"), 10) }
        refuses("zero budget") { BreadcrumbTrim.trim(listOf("a"), 0) }
    }

    private fun apdex() {
        ok(Apdex.score(longArrayOf(2001), 500) == 0.0, "just past 4T frustrated")
        ok(Apdex.rating(0.69) == Apdex.Rating.POOR, "just under fair")
        ok(Apdex.score(longArrayOf(0), 100) == 1.0, "zero latency satisfied")
        ok(Apdex.rating(1.0) == Apdex.Rating.EXCELLENT && Apdex.rating(0.0) == Apdex.Rating.UNACCEPTABLE, "extremes")
        ok(near(Apdex.score(longArrayOf(100, 400, 2000, 2000), 500), 0.75, 1e-12), "two satisfied + two tolerating at T=500 gives (2+1)/4 (2000 = 4T inclusive)")
        ok(near(Apdex.score(longArrayOf(600, 600), 500), 0.5, 1e-12), "two tolerating halve")
        val samples = longArrayOf(100, 200, 300, 900, 1500, 5000)
        ok(near(Apdex.score(samples, 500), (3 + 2 / 2.0) / 6, 1e-12), "3 satisfied, 2 tolerating, 1 frustrated at T=500")
        ok(Apdex.score(longArrayOf(1, 2, 3), 10) == 1.0, "all satisfied")
        ok(Apdex.score(longArrayOf(100), 10) == 0.0, "all frustrated")
        ok(Apdex.score(longArrayOf(40), 10) == 0.5, "single tolerating")
        ok(Apdex.rating(0.94) == Apdex.Rating.EXCELLENT && Apdex.rating(0.9399) == Apdex.Rating.GOOD, "0.94 boundary")
        ok(Apdex.rating(0.85) == Apdex.Rating.GOOD && Apdex.rating(0.70) == Apdex.Rating.FAIR, "band edges")
        ok(Apdex.rating(0.50) == Apdex.Rating.POOR && Apdex.rating(0.49) == Apdex.Rating.UNACCEPTABLE, "bottom edge")
        ok(longArrayOf(500, 2000).let { Apdex.score(it, 500) == 0.75 }, "boundary values are satisfied/tolerating inclusively")
        refuses("no samples") { Apdex.score(LongArray(0), 100) }
        refuses("zero threshold") { Apdex.score(longArrayOf(1), 0) }
        refuses("negative latency") { Apdex.score(longArrayOf(-1), 100) }
        refuses("score above 1") { Apdex.rating(1.01) }
    }

    private fun percentChange() {
        ok(near(PercentChange.basisPoints(100.0, 100.25), 25.0, 1e-9), "25bp")
        refuses("infinite to") { PercentChange.percent(1.0, Double.POSITIVE_INFINITY) }
        ok(near(PercentChange.percent(200.0, 200.0), 0.0, 1e-12), "no change")
        ok(near(PercentChange.apply(50.0, -50.0), 25.0, 1e-12), "negative percent applies")
        ok(PercentChange.roundTripError(3.0, 7.0) < 1e-9, "inverse on odd values")
        ok(near(PercentChange.percent(100.0, 150.0), 50.0, 1e-12), "+50%")
        ok(near(PercentChange.percent(100.0, 75.0), -25.0, 1e-12), "-25%")
        ok(near(PercentChange.basisPoints(100.0, 101.0), 100.0, 1e-9), "1% is 100bp")
        ok(near(PercentChange.apply(100.0, 50.0), 150.0, 1e-12), "apply forward")
        ok(PercentChange.roundTripError(80.0, 123.0) < 1e-9, "percent/apply inverse (property)")
        ok(near(PercentChange.percent(0.5, 1.0), 100.0, 1e-12), "doubling is +100%")
        refuses("zero base") { PercentChange.percent(0.0, 5.0) }
        refuses("negative base declared out") { PercentChange.percent(-4.0, -2.0) }
        refuses("NaN") { PercentChange.percent(1.0, Double.NaN) }
        refuses("apply on zero base") { PercentChange.apply(0.0, 10.0) }
    }

    private fun rtsp() {
        ok(RtspTransport.parse("RTP/AVP").let { !it.unicast && !it.multicast }, "bare spec has neither cast mode")
        refuses("empty header") { RtspTransport.parse("") }
        ok(RtspTransport.parse("RTP/AVP/UDP;multicast").multicast, "explicit UDP + multicast")
        ok(RtspTransport.parse("RTP/AVP;unicast;server_port=6000-6001").serverPort == RtspTransport.PortRange(6000, 6001), "server port")
        ok(RtspTransport.parse("RTP/AVP;unicast;;mode=record").extras["mode"] == "record", "empty params skipped, extras kept")
        refuses("lower transport SCTP") { RtspTransport.parse("RTP/AVP/SCTP;unicast") }
        val t = RtspTransport.parse("RTP/AVP;unicast;client_port=3056-3057")
        ok(t.unicast && !t.multicast, "unicast flag")
        ok(t.clientPort == RtspTransport.PortRange(3056, 3057), "client port range")
        ok(t.clientPort!!.isRtpRtcpPair, "adjacent pair flagged")
        ok(t.lowerTransport == "UDP", "default lower transport")
        val tcp = RtspTransport.parse("RTP/AVP/TCP;unicast;interleaved=0-1")
        ok(tcp.lowerTransport == "TCP" && tcp.extras["interleaved"] == "0-1", "TCP + unknown param kept verbatim")
        ok(RtspTransport.parse("RTP/AVP;unicast;client_port=5000").clientPort == RtspTransport.PortRange(5000, 5000), "single port allowed")
        ok(!RtspTransport.parse("RTP/AVP;unicast;client_port=5000-5002").clientPort!!.isRtpRtcpPair, "non-adjacent not a pair")
        refuses("both cast modes") { RtspTransport.parse("RTP/AVP;unicast;multicast") }
        refuses("inverted range") { RtspTransport.parse("RTP/AVP;unicast;client_port=5001-5000") }
        refuses("bad spec") { RtspTransport.parse("RAW/RAW;unicast") }
        refuses("valueless client_port") { RtspTransport.parse("RTP/AVP;client_port") }
        refuses("port 70000") { RtspTransport.parse("RTP/AVP;client_port=70000-70001") }
    }

    private fun msgId() {
        ok(MsgIdParse.MsgId("gen.1", "osb.local").format() == "<gen.1@osb.local>", "generator")
        refuses("brackets only") { MsgIdParse.parse("<>") }
        ok(MsgIdParse.parse("<a@b>").format() == "<a@b>", "minimal id")
        ok(MsgIdParse.isValid("<ts.1729774800.pid.42@host.local>"), "typical generated shape")
        refuses("missing right") { MsgIdParse.parse("<a@>") }
        refuses("control char") { MsgIdParse.parse("<a\u0001b@c>") }
        val m = MsgIdParse.parse("<abc.123@mail.example.org>")
        ok(m.left == "abc.123" && m.right == "mail.example.org", "parts")
        ok(m.format() == "<abc.123@mail.example.org>", "format roundtrip")
        ok(MsgIdParse.isValid("<a@b.c>") && !MsgIdParse.isValid("a@b.c"), "brackets required")
        ok(MsgIdParse.isValid("<x+y=z@host>"), "atext specials allowed")
        refuses("two ats") { MsgIdParse.parse("<a@b@c>") }
        refuses("space inside") { MsgIdParse.parse("<a b@c>") }
        refuses("empty left") { MsgIdParse.parse("<@c>") }
        refuses("empty dot segment") { MsgIdParse.parse("<a..b@c>") }
        refuses("quoted left declared out") { MsgIdParse.parse("<\"q\"@c>") }
        refuses("domain literal declared out") { MsgIdParse.parse("<a@[1.2.3.4]>") }
    }

    private fun timeCoarse() {
        ok(TimeCoarse.sameBucket(0, 86399, TimeCoarse.Grain.DAY), "whole first day one bucket")
        ok(TimeCoarse.floor(TimeCoarse.floor(123456, TimeCoarse.Grain.HOUR), TimeCoarse.Grain.HOUR) == TimeCoarse.floor(123456, TimeCoarse.Grain.HOUR), "floor idempotent")
        ok(TimeCoarse.floor(86399, TimeCoarse.Grain.DAY) == 0L, "last second of day one floors to epoch")
        ok(TimeCoarse.floor(86400, TimeCoarse.Grain.DAY) == 86400L, "midnight starts the next bucket")
        ok(TimeCoarse.bucketIndex(7200, TimeCoarse.Grain.HOUR) == 2L, "third hour has index 2")
        ok(TimeCoarse.bucketsPerDay(TimeCoarse.Grain.DAY) == 1L, "one day bucket")
        ok(TimeCoarse.floor(1792756812, TimeCoarse.Grain.MINUTE) == 1792756800L, "minute floor")
        ok(TimeCoarse.floor(1792756812, TimeCoarse.Grain.HOUR) == 1792756800L, "noon is on the hour")
        ok(TimeCoarse.floor(1792756812, TimeCoarse.Grain.DAY) == 1792713600L, "day floor")
        ok(TimeCoarse.sameBucket(1792756801, 1792756859, TimeCoarse.Grain.MINUTE), "same minute")
        ok(!TimeCoarse.sameBucket(1792756859, 1792756860, TimeCoarse.Grain.MINUTE), "minute boundary splits")
        ok(TimeCoarse.bucketsPerDay(TimeCoarse.Grain.HOUR) == 24L && TimeCoarse.bucketsPerDay(TimeCoarse.Grain.MINUTE) == 1440L, "bucket counts")
        ok(TimeCoarse.floor(0, TimeCoarse.Grain.DAY) == 0L, "epoch floors to itself")
        refuses("pre-1970") { TimeCoarse.floor(-1, TimeCoarse.Grain.HOUR) }
    }

    private fun aspectSnap() {
        ok(AspectSnap.snapHeight(2400, 1001, 0.2) != null, "near-21:9 snaps under loose tolerance")
        ok(AspectSnap.nearest(1500, 1000) == (3 to 2), "3:2 exact")
        ok(AspectSnap.nearest(2560, 1080) == (21 to 9), "ultrawide")
        ok(AspectSnap.snapHeight(1600, 900) == 900, "exact 16:9 snaps to itself")
        ok(AspectSnap.RATIOS.size == 9 && AspectSnap.RATIOS.toSet().size == 9, "pinned table distinct")
        refuses("negative height") { AspectSnap.snapHeight(100, -1) }
        ok(AspectSnap.nearest(1920, 1080) == (16 to 9), "1080p is 16:9")
        ok(AspectSnap.nearest(1080, 1920) == (9 to 16), "portrait symmetric via log distance")
        ok(AspectSnap.nearest(800, 600) == (4 to 3), "4:3")
        ok(AspectSnap.snapHeight(1918, 1080) == 1079, "near-16:9 snaps to corrected height")
        ok(AspectSnap.snapHeight(1500, 1000) != null, "3:2 exact snaps")
        ok(AspectSnap.snapHeight(1300, 1000, 0.01) == null, "13:10 outside tight tolerance")
        ok(AspectSnap.nearest(1000, 1000) == (1 to 1), "square")
        refuses("zero width") { AspectSnap.nearest(0, 100) }
        refuses("zero tolerance") { AspectSnap.snapHeight(100, 100, 0.0) }
    }

    private fun alertKey() {
        ok(AlertKey.key("cat", "src", 59, 60) != AlertKey.key("cat", "src", 60, 60), "bucket edge distinct")
        ok(AlertKey.parts(AlertKey.key("X", "Y", 1000, 100)) == Triple("x", "y", 10L), "parts of generated key")
        ok(AlertKey.key("A", "B", 0, 3600) == "a|b|0", "epoch zero bucket")
        ok(AlertKey.parts(AlertKey.key("Net", "wifi0", 7261, 60)).third == 121L, "bucket math via parts")
        refuses("pipe in source") { AlertKey.key("a", "b|c", 0, 60) }
        ok(AlertKey.key("Battery", "System", 7200, 3600) == "battery|system|2", "canonical key")
        ok(AlertKey.sameAlert(AlertKey.key("A", "s", 100, 60), AlertKey.key("a", "S", 119, 60)), "case and same bucket dedupe")
        ok(!AlertKey.sameAlert(AlertKey.key("a", "s", 119, 60), AlertKey.key("a", "s", 120, 60)), "bucket boundary splits")
        ok(AlertKey.parts("battery|system|2") == Triple("battery", "system", 2L), "parts inverse")
        refuses("pipe in category") { AlertKey.key("a|b", "s", 0, 60) }
        refuses("blank source") { AlertKey.key("a", " ", 0, 60) }
        refuses("zero bucket") { AlertKey.key("a", "s", 0, 0) }
        refuses("not a key") { AlertKey.parts("a|b") }
        refuses("bucket not numeric") { AlertKey.parts("a|b|x") }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        sheetFreeze(); bidiStrip(); dupLines(); pdfPerm(); referer(); debControl(); apiName()
        seatColor(); bpm(); trackerTier(); qrVersion(); rpId(); versionRange(); snippetWindow()
        ageId(); breadcrumb(); apdex(); percentChange(); rtsp(); msgId(); timeCoarse(); aspectSnap(); alertKey()
        println("R43 PASS: " + count + " production-kernel assertions across 23 kernels. Host JVM only; not Android/device evidence.")
    }
}

package dev.fusion.testing

import dev.fusion.ooxml.SheetRange
import dev.fusion.ooxml.SheetHide
import dev.fusion.documents.QuoteNest
import dev.fusion.editor.GoToLine
import dev.fusion.format.pdf.ObjNum
import dev.fusion.security.SriHash
import dev.fusion.archive.DebVersion
import dev.fusion.lens.Issn
import dev.fusion.lens.Ean13
import dev.fusion.lens.UpcE
import dev.fusion.terminal.CwdTilde
import dev.fusion.index.Levenshtein
import dev.fusion.settings.PrefKeyLint
import dev.fusion.commands.HistDedup
import dev.fusion.media.SampleBytes
import dev.fusion.messages.SubjectNorm
import dev.fusion.privacy.GpsRational
import dev.fusion.hw.DisplayInch
import dev.fusion.backup.BackupName
import dev.fusion.launcher.AlphaIndex
import dev.fusion.format.image.ExifOrient
import dev.fusion.browser.model.AcceptLang
import dev.fusion.notes.TagExtract
import dev.fusion.alerts.Severity
import dev.fusion.filesui.HiddenDot
import dev.fusion.together.SessionCode

/** R44 executed contracts. Host JVM only; NOT Android/device evidence. */
object R44Checks {
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

    private fun sheetHide() {
        ok(SheetHide.nextVisible(listOf(SheetHide.Run(1, 100)), 1) == 100, "long run skipped")
        ok(SheetHide.hiddenCount(SheetHide.normalize(listOf(SheetHide.Run(1, 3), SheetHide.Run(2, 5)))) == 4, "merged count")
        ok(SheetHide.normalize(listOf(SheetHide.Run(1, 3), SheetHide.Run(3, 5))) == listOf(SheetHide.Run(1, 5)), "touching runs merge")
        ok(SheetHide.hiddenCount(listOf(SheetHide.Run(10, 11))) == 1, "single hidden row")
        ok(SheetHide.nextVisible(emptyList(), 7) == 7, "no runs, identity")
        ok(!SheetHide.isHidden(emptyList(), 1), "nothing hidden")
        ok(SheetHide.normalize(listOf(SheetHide.Run(5, 9), SheetHide.Run(6, 7))) == listOf(SheetHide.Run(5, 9)), "containment absorbs")
        val runs = listOf(SheetHide.Run(5, 8), SheetHide.Run(2, 4), SheetHide.Run(7, 10))
        ok(SheetHide.normalize(runs) == listOf(SheetHide.Run(2, 4), SheetHide.Run(5, 10)), "overlap merge")
        ok(SheetHide.isHidden(runs, 7) && !SheetHide.isHidden(runs, 4), "membership half-open")
        ok(SheetHide.hiddenCount(runs) == 7, "2,3 + 5..9 hidden")
        ok(SheetHide.nextVisible(runs, 5) == 10, "skip the merged run")
        ok(SheetHide.nextVisible(runs, 4) == 4, "visible stays put")
        ok(SheetHide.nextVisible(runs, 2) == 4, "from inside first run")
        ok(SheetHide.normalize(emptyList()).isEmpty(), "empty runs")
        ok(SheetHide.isHidden(listOf(SheetHide.Run(1, 2)), 1), "row 1 hideable")
        refuses("zero start") { SheetHide.Run(0, 3) }
        refuses("empty run") { SheetHide.Run(5, 5) }
        refuses("beyond grid") { SheetHide.Run(1, SheetRange.MAX_ROW + 2) }
        refuses("zero index query") { SheetHide.isHidden(runs, 0) }
    }

    private fun quoteNest() {
        ok(QuoteNest.depthRuns(listOf("> a")).single() == QuoteNest.RunD(1, listOf("a")), "single run")
        ok(QuoteNest.body(">>>x") == "x", "dense markers")
        ok(QuoteNest.depth(">>> deep") == 3, "triple")
        ok(QuoteNest.depthRuns(emptyList()).isEmpty(), "no lines")
        ok(QuoteNest.body("plain") == "plain", "plain body identity")
        ok(QuoteNest.addLevel(QuoteNest.addLevel("x")) == "> > x", "two adds")
        ok(QuoteNest.depth(QuoteNest.addLevel(QuoteNest.addLevel("x"))) == 2, "depth tracks adds")
        ok(QuoteNest.depth("> hi") == 1 && QuoteNest.depth(">> hi") == 2, "depths")
        ok(QuoteNest.depth("> > nested spaces") == 2, "space-separated markers")
        ok(QuoteNest.depth("plain") == 0, "no marker")
        ok(QuoteNest.body(">> text") == "text", "body strips all levels")
        ok(QuoteNest.stripLevel(">> a") == "> a", "strip exactly one")
        ok(QuoteNest.stripLevel("> a") == "a", "strip to plain")
        ok(QuoteNest.addLevel("x") == "> x", "add level")
        ok(QuoteNest.stripLevel(QuoteNest.addLevel("y")) == "y", "add/strip inverse")
        val runs = QuoteNest.depthRuns(listOf("a", "> b", "> c", "d"))
        ok(runs.size == 3 && runs[1] == QuoteNest.RunD(1, listOf("b", "c")), "depth runs group")
        ok(QuoteNest.depth(">no space") == 1 && QuoteNest.body(">no space") == "no space", "marker without space")
        refuses("strip on plain") { QuoteNest.stripLevel("plain") }
    }

    private fun goToLine() {
        ok(GoToLine.parse("3:1", 1, 5) == GoToLine.Target(3, 3, 1, false), "explicit col 1")
        refuses("plus without number") { GoToLine.parse("+", 1, 5) }
        ok(GoToLine.parse("1", 5, 10).line == 1, "top")
        ok(GoToLine.parse("+0", 5, 10).line == 5, "relative zero stays")
        ok(GoToLine.parse("10", 5, 10).let { !it.clamped }, "exact last line not clamped")
        ok(GoToLine.parse("5:999", 1, 10).column == 999, "column clamping is the buffer layer's")
        refuses("current out of bounds") { GoToLine.parse("1", 11, 10) }
        ok(GoToLine.parse("12", 1, 100) == GoToLine.Target(12, 12, 1, false), "plain line")
        ok(GoToLine.parse("12:34", 1, 100).column == 34, "line:col")
        ok(GoToLine.parse(":7", 5, 100) == GoToLine.Target(5, 5, 7, false), "column-only keeps line")
        ok(GoToLine.parse("+5", 10, 100).line == 15, "relative down")
        ok(GoToLine.parse("-3", 10, 100).line == 7, "relative up")
        val c = GoToLine.parse("999", 1, 120)
        ok(c.clamped && c.line == 120 && c.requestedLine == 999, "clamp REPORTED")
        ok(GoToLine.parse("-99", 50, 100).let { it.line == 1 && it.clamped }, "clamp at top")
        refuses("empty") { GoToLine.parse("  ", 1, 10) }
        refuses("line zero") { GoToLine.parse("0", 1, 10) }
        refuses("column zero") { GoToLine.parse("5:0", 1, 10) }
        refuses("garbage") { GoToLine.parse("abc", 1, 10) }
        refuses("empty doc") { GoToLine.parse("1", 1, 0) }
    }

    private fun objNum() {
        ok(ObjNum.parse("10 0 R").objNumber == 10, "two-digit object")
        refuses("extra token") { ObjNum.parse("1 0 R extra") }
        ok(ObjNum.parse("1 0 R") == ObjNum.Ref(1, 0), "minimal")
        ok(!ObjNum.isReference("12 0R") && !ObjNum.isReference("12 0 R "), "spacing canon strict")
        ok(ObjNum.Ref(7, 3).format() == "7 3 R", "format nonzero gen")
        refuses("negative shape") { ObjNum.parse("-1 0 R") }
        refuses("gen six digits") { ObjNum.parse("1 100000 R") }
        ok(ObjNum.parse("12 0 R") == ObjNum.Ref(12, 0), "classic reference")
        ok(ObjNum.parse("7 3 R").generation == 3, "nonzero generation")
        ok(ObjNum.Ref(12, 0).format() == "12 0 R", "format canon")
        ok(ObjNum.parse(ObjNum.Ref(999, 65534).format()) == ObjNum.Ref(999, 65534), "roundtrip at gen ceiling")
        ok(ObjNum.isReference("1 0 R") && !ObjNum.isReference("1  0 R"), "double space refused (canon)")
        refuses("object zero") { ObjNum.parse("0 0 R") }
        refuses("gen 65535 is the free marker") { ObjNum.Ref(1, 65535) }
        refuses("leading zero obj") { ObjNum.parse("012 0 R") }
        refuses("lowercase r") { ObjNum.parse("1 0 r") }
        refuses("missing R") { ObjNum.parse("1 0") }
    }

    private fun sriHash() {
        ok(SriHash.Alg.SHA256 < SriHash.Alg.SHA512, "enum order encodes strength")
        refuses("empty options still needs digest") { SriHash.parseToken("sha256-?opt") }
        ok(SriHash.parseToken("sha512-" + "G".repeat(86) + "==").alg == SriHash.Alg.SHA512, "sha512 length")
        ok(SriHash.strongest(listOf("sha256-" + "H".repeat(43) + "=")).single().alg == SriHash.Alg.SHA256, "single entry")
        ok(!SriHash.matches(listOf("sha256-" + "H".repeat(43) + "="), SriHash.Alg.SHA256, "X".repeat(43) + "="), "digest mismatch fails")
        refuses("bad base64 char") { SriHash.parseToken("sha256-" + "!".repeat(44)) }
        val t = SriHash.parseToken("sha384-" + "A".repeat(63) + "=")
        ok(t.alg == SriHash.Alg.SHA384 && t.options == null, "sha384 token")
        ok(SriHash.parseToken("sha256-" + "B".repeat(43) + "=?foo").options == "foo", "options split off")
        val meta = listOf("sha256-" + "C".repeat(43) + "=", "sha384-" + "D".repeat(63) + "=")
        ok(SriHash.strongest(meta).single().alg == SriHash.Alg.SHA384, "strongest wins")
        ok(SriHash.matches(meta, SriHash.Alg.SHA384, "D".repeat(63) + "="), "match against strongest")
        ok(!SriHash.matches(meta, SriHash.Alg.SHA256, "C".repeat(43) + "="), "weaker alg cannot pass")
        val multi = listOf("sha512-" + "E".repeat(87) + "=", "sha512-" + "F".repeat(87) + "=")
        ok(SriHash.matches(multi, SriHash.Alg.SHA512, "F".repeat(87) + "="), "any-of within strongest")
        refuses("md5") { SriHash.parseToken("md5-" + "A".repeat(24)) }
        refuses("wrong digest length") { SriHash.parseToken("sha256-short") }
        refuses("empty metadata") { SriHash.strongest(emptyList()) }
        refuses("no dash") { SriHash.parseToken("sha256" + "A".repeat(44)) }
    }

    private fun debVersion() {
        ok(DebVersion.compare("1.0+b1", "1.0") > 0, "plus suffix after release")
        ok(DebVersion.compare("1:0.1", "2:0.0") < 0, "epochs compare numerically")
        ok(DebVersion.compare("1.0", "1.0.1") < 0, "shorter numeric chain")
        ok(DebVersion.compare("1.0~~", "1.0~") < 0, "double tilde before single")
        ok(DebVersion.compare("0:1.0", "1.0") == 0, "explicit zero epoch equal")
        ok(DebVersion.compare("1.2.3-1ubuntu1", "1.2.3-1") > 0, "revision suffix compares")
        ok(DebVersion.cmpPart("abc", "abd") < 0, "letter order inside parts")
        ok(DebVersion.compare("1.0", "1.0") == 0, "equal")
        ok(DebVersion.compare("1.0~rc1", "1.0") < 0, "tilde sorts before release")
        ok(DebVersion.compare("1.0~rc1", "1.0~rc2") < 0, "tilde runs compare inside")
        ok(DebVersion.compare("2:0.9", "1:1.0") > 0, "epoch dominates")
        ok(DebVersion.compare("1.10", "1.9") > 0, "numeric not lexicographic")
        ok(DebVersion.compare("1.0-2", "1.0-10") < 0, "revision numeric")
        ok(DebVersion.compare("1.0a", "1.0+") < 0, "letters before plus")
        ok(DebVersion.parse("1:2.3-4") == DebVersion.V(1, "2.3", "4"), "parse parts")
        ok(DebVersion.parse("2.3").revision == "0", "default revision")
        ok(DebVersion.compare("1.0-1", "1.0") > 0, "revision 1 beats default 0")
        refuses("empty") { DebVersion.parse("") }
        refuses("alpha epoch") { DebVersion.parse("a:1.0") }
        refuses("space inside") { DebVersion.parse("1 .0") }
    }

    private fun issn() {
        ok(Issn.format("03785955") == "0378-5955", "format from bare digits")
        refuses("X in body") { Issn.checkChar("037X595") }
        ok(Issn.checkChar("0000000") == '0', "all zeros")
        ok(!Issn.isValid("1234-5678"), "random digits fail")
        ok(Issn.isValid(Issn.format("0378-5955")), "format output validates")
        refuses("eight digits to checkChar") { Issn.checkChar("03785955") }
        ok(Issn.checkChar("0378595") == '5', "Nature-family style vector computes")
        ok(Issn.isValid("0378-5955"), "canonical ISSN validates")
        ok(Issn.checkChar("2434561") == 'X', "X check digit case (2434-561X)")
        ok(Issn.isValid("2434-561X") && Issn.isValid("2434561x"), "x case-insensitive, hyphen optional")
        ok(!Issn.isValid("0378-5956"), "digit flip caught")
        ok(Issn.format("2434561X") == "2434-561X", "display hyphen")
        refuses("six digits") { Issn.checkChar("037859") }
        refuses("letter in body") { Issn.checkChar("03785A5") }
        refuses("format invalid") { Issn.format("0378-5956") }
    }

    private fun upcE() {
        ok(Ean13.isValid("0" + UpcE.toUpcA("0" + "123453" + Ean13.checkDigit("0" + "01230000045"))), "rule-3 full chain validates")
        refuses("empty") { UpcE.expandBody("") }
        ok(UpcE.expandBody("123450") == "01200000345", "rule 0 interleaves zero as mfr digit 3")
        ok(UpcE.expandBody("123455") == "01234500005", "rule 5 appends")
        ok(UpcE.expandBody("999993") == "09990000099", "rule 3 on nines")
        refuses("letters") { UpcE.expandBody("12345a") }
        ok(UpcE.expandBody("654321") == "06510000432", "last-digit-1 rule interleaves L as mfr digit 3")
        ok(UpcE.expandBody("123453") == "01230000045", "rule 3")
        ok(UpcE.expandBody("123454") == "01234000005", "rule 4")
        ok(UpcE.expandBody("123459") == "01234500009", "rule 5-9")
        val upcA = UpcE.toUpcA("0" + "654321" + Ean13.checkDigit("0" + "06510000432"))
        ok(upcA.length == 12 && upcA.startsWith("06510000432"), "full expansion carries the check")
        ok(Ean13.isValid("0" + upcA), "expanded form validates as EAN-13")
        refuses("seven digits") { UpcE.expandBody("1234567") }
        refuses("number system 1") { UpcE.toUpcA("16543210") }
        refuses("wrong check") { UpcE.toUpcA("06543210") }
    }

    private fun cwdTilde() {
        ok(CwdTilde.collapse("/home/u/.config", "/home/u") == "~/.config", "dotfiles collapse too")
        refuses("relative home") { CwdTilde.expand("~/x", "home") }
        ok(CwdTilde.collapse("/", "/") == "~", "root home edge")
        ok(CwdTilde.collapse("/home/uu/x", "/home/u") == "/home/uu/x", "sibling user not collapsed")
        ok(CwdTilde.expand("plain", "/h") == "plain", "non-tilde display passes")
        ok(CwdTilde.collapse("/home/u/projects", "/home/u") == "~/projects", "collapse")
        ok(CwdTilde.collapse("/home/u", "/home/u") == "~", "home itself")
        ok(CwdTilde.collapse("/homework", "/home") == "/homework", "component boundary enforced")
        ok(CwdTilde.collapse("/other/place", "/home/u") == "/other/place", "unrelated path")
        ok(CwdTilde.expand("~/x", "/home/u") == "/home/u/x", "expand")
        ok(CwdTilde.expand("~", "/home/u") == "/home/u", "bare tilde")
        ok(CwdTilde.expand("/abs", "/home/u") == "/abs", "absolute passes")
        ok(CwdTilde.expand(CwdTilde.collapse("/home/u/a/b", "/home/u"), "/home/u") == "/home/u/a/b", "roundtrip")
        refuses("relative path") { CwdTilde.collapse("rel/x", "/home/u") }
        refuses("~user form") { CwdTilde.expand("~root/x", "/home/u") }
        refuses("home with trailing slash") { CwdTilde.collapse("/a", "/home/") }
    }

    private fun levenshtein() {
        ok(Levenshtein.distance("abc", "acb") == 2, "transposition costs two (no Damerau)")
        ok(Levenshtein.within("abcd", "abcd", 3), "identity within any k")
        ok(Levenshtein.distance("saturday", "sunday") == 3, "third classic")
        ok(Levenshtein.within("", "", 0), "both empty")
        ok(!Levenshtein.within("abc", "abcdef", 2), "length gap exceeds k")
        ok(Levenshtein.within("abc", "abcde", 2), "length gap equals k")
        ok(Levenshtein.distance("kitten", "sitting") == 3, "classic 3")
        ok(Levenshtein.distance("", "abc") == 3 && Levenshtein.distance("abc", "") == 3, "empty sides")
        ok(Levenshtein.distance("same", "same") == 0, "identity")
        ok(Levenshtein.distance("flaw", "lawn") == 2, "second classic")
        ok(Levenshtein.within("kitten", "sitting", 3) && !Levenshtein.within("kitten", "sitting", 2), "bounded agrees at the boundary")
        ok(Levenshtein.within("abc", "abd", 1), "single substitution within 1")
        ok(!Levenshtein.within("abcdefgh", "zyxwvuts", 3), "early exit on hopeless rows")
        ok(Levenshtein.within("a", "a", 0), "k=0 equals")
        var agree = true
        val words = listOf("search", "serach", "sarch", "searching", "xerox")
        for (a in words) for (b in words) {
            val d = Levenshtein.distance(a, b)
            for (k in 0..4) if (Levenshtein.within(a, b, k) != (d <= k)) agree = false
        }
        ok(agree, "banded agrees with classic over a 5x5x5 sweep (property)")
        refuses("negative k") { Levenshtein.within("a", "b", -1) }
        refuses("beyond cap") { Levenshtein.distance("x".repeat(513), "y") }
    }

    private fun prefKeyLint() {
        ok(PrefKeyLint.isValid("net.proxy.mode"), "three deep")
        refuses("uppercase segment") { PrefKeyLint.validate("Net.proxy") }
        ok(PrefKeyLint.isValid("a.b"), "minimal depth 2")
        ok(!PrefKeyLint.isValid("a..b"), "empty segment")
        ok(!PrefKeyLint.isValid("a.b."), "trailing dot")
        refuses("too long") { PrefKeyLint.validate("a." + "b".repeat(80)) }
        ok(PrefKeyLint.validate("appearance.theme") == "appearance.theme", "canonical key")
        ok(PrefKeyLint.isValid("a.b_c.d9"), "underscores and digits")
        ok(!PrefKeyLint.isValid("single"), "depth 1 fails")
        ok(!PrefKeyLint.isValid("A.b"), "uppercase fails")
        ok(!PrefKeyLint.isValid("a.9b"), "digit-led segment fails")
        ok(!PrefKeyLint.isValid("a.tmp.x"), "reserved tmp")
        ok(PrefKeyLint.isValid("legacyKey", setOf("legacyKey")), "explicit exemption passes")
        ok(!PrefKeyLint.isValid("legacyKey"), "same key without exemption fails")
        refuses("debug segment") { PrefKeyLint.validate("net.debug") }
        refuses("six deep") { PrefKeyLint.validate("a.b.c.d.e.f") }
    }

    private fun histDedup() {
        ok(HistDedup.replay(emptyList(), HistDedup.Policy.ERASE_DUPS, true).isEmpty(), "empty replay")
        ok(HistDedup.append(listOf("x"), "x", HistDedup.Policy.ERASE_DUPS, false) == listOf("x"), "erase then append same")
        ok(HistDedup.replay(listOf("a", "b", "a", "b"), HistDedup.Policy.ERASE_DUPS, false) == listOf("a", "b"), "alternating erasedups")
        ok(HistDedup.replay(listOf(" x", "y"), HistDedup.Policy.KEEP_ALL, true) == listOf("y"), "replay honors ignorespace")
        ok(HistDedup.append(emptyList(), "first", HistDedup.Policy.IGNORE_DUPS, false) == listOf("first"), "empty history append")
        ok(HistDedup.append(listOf("a", "b"), "b", HistDedup.Policy.IGNORE_DUPS, false) == listOf("a", "b"), "adjacent dup dropped")
        ok(HistDedup.append(listOf("a", "b"), "a", HistDedup.Policy.IGNORE_DUPS, false) == listOf("a", "b", "a"), "non-adjacent kept")
        ok(HistDedup.append(listOf("a", "b", "a"), "a", HistDedup.Policy.ERASE_DUPS, false) == listOf("b", "a"), "erase keeps newest")
        ok(HistDedup.append(listOf("a"), " secret", HistDedup.Policy.KEEP_ALL, true) == listOf("a"), "ignorespace drops")
        ok(HistDedup.append(listOf("a"), " secret", HistDedup.Policy.KEEP_ALL, false).size == 2, "space kept when off")
        ok(HistDedup.replay(listOf("x", "x", "y", "x"), HistDedup.Policy.IGNORE_DUPS, false) == listOf("x", "y", "x"), "replay ignoredups")
        ok(HistDedup.replay(listOf("x", "x", "y", "x"), HistDedup.Policy.ERASE_DUPS, false) == listOf("y", "x"), "replay erasedups")
        refuses("empty entry") { HistDedup.append(emptyList(), "", HistDedup.Policy.KEEP_ALL, false) }
    }

    private fun sampleBytes() {
        ok(SampleBytes.bytesPerSecond(8000, 1, 8) == 8000L, "telephony byte rate")
        refuses("negative millis") { SampleBytes.bytesFor(48000, 2, 16, -1) }
        ok(SampleBytes.bytesPerSecond(192000, 8, 32) == 6144000L, "max shape")
        ok(SampleBytes.bytesFor(48000, 2, 16, 0) == 0L, "zero duration")
        ok(SampleBytes.millisFor(48000, 2, 16, SampleBytes.bytesFor(48000, 2, 16, 2500)) == 2500L, "roundtrip 2.5s")
        refuses("negative bytes") { SampleBytes.millisFor(48000, 2, 16, -4) }
        ok(SampleBytes.bytesPerSecond(44100, 2, 16) == 176400L, "CD rate")
        ok(SampleBytes.bytesPerSecond(48000, 2, 24) == 288000L, "studio rate")
        ok(SampleBytes.bytesFor(44100, 2, 16, 1000) == 176400L, "one second of CD audio")
        ok(SampleBytes.millisFor(44100, 2, 16, 176400L) == 1000L, "inverse")
        ok(SampleBytes.millisFor(8000, 1, 8, 8000L) == 1000L, "telephony mono")
        refuses("rate 1000") { SampleBytes.bytesPerSecond(1000, 1, 8) }
        refuses("20-bit") { SampleBytes.bytesPerSecond(44100, 2, 20) }
        refuses("nine channels") { SampleBytes.bytesPerSecond(44100, 9, 16) }
        refuses("unaligned size") { SampleBytes.millisFor(44100, 2, 16, 3L) }
    }

    private fun subjectNorm() {
        ok(SubjectNorm.normalize("FW: x").wasForward, "FW counts as forward")
        ok(SubjectNorm.normalize("re:x").subject == "x", "no space after marker")
        ok(SubjectNorm.normalize("plain subject").replyDepth == 0, "no markers")
        ok(SubjectNorm.normalize("Fwd: Re: x").let { it.wasForward && it.replyDepth == 2 }, "mixed markers both counted")
        ok(SubjectNorm.normalize("Reaction: x").subject == "Reaction: x", "Re prefix inside a word untouched")
        ok(SubjectNorm.sameThread("Re[2]: Minutes", "Minutes"), "bracket form threads with the plain subject")
        ok(SubjectNorm.normalize("Re: Hello").subject == "Hello", "single Re")
        ok(SubjectNorm.normalize("Re: RE: re: Hello").replyDepth == 3, "stacked markers counted")
        ok(SubjectNorm.normalize("Re[4]: Hello").replyDepth == 4, "bracket count form")
        ok(SubjectNorm.normalize("Fwd: FW: x").wasForward, "forward flagged")
        ok(SubjectNorm.normalize("[list] Re: topic").subject == "[list] Re: topic", "list tag blocks marker stripping by position (kept)")
        ok(SubjectNorm.normalize("Re: [list] topic").subject == "[list] topic", "list tag after markers is kept")
        ok(SubjectNorm.normalize("  spaced   out  ").subject == "spaced out", "whitespace collapse")
        ok(SubjectNorm.sameThread("Re: Budget", "RE: RE: budget"), "thread identity case-insensitive")
        ok(!SubjectNorm.sameThread("Budget A", "Budget B"), "different subjects differ")
        refuses("implausible count") { SubjectNorm.normalize("Re[1000]: x") }
    }

    private fun gpsRational() {
        ok(GpsRational.isNonZero(GpsRational.Rat(0, 1), GpsRational.Rat(1, 1), GpsRational.Rat(0, 1)), "minutes-only nonzero")
        refuses("negative numerator") { GpsRational.Rat(-1, 2) }
        ok(near(GpsRational.Rat(5405, 100).value, 54.05, 1e-12), "centi-rational")
        ok(near(GpsRational.toDecimal(GpsRational.Rat(0, 1), GpsRational.Rat(30, 1), GpsRational.Rat(0, 1), 'E'), 0.5, 1e-12), "half-degree longitude")
        refuses("seconds 60") { GpsRational.toDecimal(GpsRational.Rat(1, 1), GpsRational.Rat(0, 1), GpsRational.Rat(60, 1), 'N') }
        val d = GpsRational.Rat(49, 1); val m = GpsRational.Rat(30, 1); val s = GpsRational.Rat(0, 1)
        ok(near(GpsRational.toDecimal(d, m, s, 'N'), 49.5, 1e-12), "49 30 0 N")
        ok(near(GpsRational.toDecimal(d, m, s, 'S'), -49.5, 1e-12), "S negates")
        ok(near(GpsRational.toDecimal(GpsRational.Rat(123, 1), GpsRational.Rat(2207, 100), GpsRational.Rat(0, 1), 'W'), -(123 + 22.07 / 60), 1e-9), "fractional minutes form")
        ok(GpsRational.isNonZero(d, m, s), "nonzero presence")
        ok(!GpsRational.isNonZero(GpsRational.Rat(0, 1), GpsRational.Rat(0, 5), GpsRational.Rat(0, 3)), "all-zero coordinates")
        ok(GpsRational.Rat(1, 3).value > 0.333 && GpsRational.Rat(1, 3).value < 0.334, "rational value")
        refuses("zero denominator") { GpsRational.Rat(1, 0) }
        refuses("minutes 60") { GpsRational.toDecimal(d, GpsRational.Rat(60, 1), s, 'N') }
        refuses("lat beyond 90") { GpsRational.toDecimal(GpsRational.Rat(91, 1), s, s, 'N') }
        refuses("bad ref") { GpsRational.toDecimal(d, m, s, 'Q') }
    }

    private fun displayInch() {
        ok(DisplayInch.diagonalInches(300, 400, 100.0) == 5.0, "3-4-5 triangle exact")
        ok(DisplayInch.ppi(300, 400, 5.0) == 100.0, "inverse exact")
        ok(near(DisplayInch.ppi(100, 100, DisplayInch.diagonalInches(100, 100, 150.0)), 150.0, 1e-9), "ppi/diagonal inverse (property)")
        refuses("negative diagonal") { DisplayInch.ppi(10, 10, -1.0) }
        ok(near(DisplayInch.diagonalInches(1080, 1920, 440.0), 5.0066072047324965, 1e-9), "a ~5-inch 1080p panel at 440dpi")
        ok(near(DisplayInch.ppi(1080, 1920, 5.0066072047324965), 440.0, 1e-9), "inverse direction")
        ok(near(DisplayInch.diagonalInches(2000, 0 + 1200, 300.0), Math.sqrt(2000.0 * 2000 + 1200.0 * 1200) / 300.0, 1e-12), "formula shape")
        ok(near(DisplayInch.diagonalInches(100, 100, 100.0, 100.4), DisplayInch.diagonalInches(100, 100, 100.2), 1e-9), "near-isotropic pair averages")
        refuses("zero dpi") { DisplayInch.diagonalInches(100, 100, 0.0) }
        refuses("anisotropic refused") { DisplayInch.diagonalInches(100, 100, 100.0, 160.0) }
        refuses("zero width") { DisplayInch.ppi(0, 100, 5.0) }
    }

    private fun backupName() {
        ok(BackupName.chronoKey("z-20261024-120000") == "20261024120000", "key drops prefix")
        refuses("underscore date sep") { BackupName.parse("x_20261024-120000") }
        ok(BackupName.parse("db-20261024-120000").ext == null, "no extension")
        ok(BackupName.parse("a_b-20261024-120000.tar.zst").ext == ".tar.zst", "dotted extension")
        refuses("minute 60") { BackupName.parse("x-20261024-126000") }
        refuses("year 1999") { BackupName.parse("x-19991231-120000") }
        val n = BackupName.parse("vault-20261024-153000.tar")
        ok(n.prefix == "vault" && n.date == "20261024" && n.ext == ".tar", "parts")
        ok(BackupName.parse("notes-20240229-000000").date == "20240229", "real leap day")
        ok(BackupName.chronoKey("a-20261024-120000") < BackupName.chronoKey("a-20261024-120001"), "chrono order")
        val names = listOf("vault-20261023-090000.tar", "vault-20261024-153000.tar", "notes-20261025-000000.tar")
        ok(BackupName.latest(names, "vault") == "vault-20261024-153000.tar", "latest per prefix")
        var lexLaw = true
        val set = listOf("p-20260101-000000", "p-20251231-235959", "p-20260101-000001")
        if (set.sorted() != set.sortedBy { BackupName.chronoKey(it) }) lexLaw = false
        ok(lexLaw, "lexicographic == chronological (the shape's reason, executed)")
        refuses("2026-02-29") { BackupName.parse("x-20260229-000000") }
        refuses("hour 24") { BackupName.parse("x-20261024-240000") }
        refuses("no prefix") { BackupName.parse("-20261024-120000") }
        refuses("foreign prefix latest") { BackupName.latest(names, "other") }
    }

    private fun alphaIndex() {
        ok(AlphaIndex.bucket("zebra") == 'Z', "last letter bucket")
        ok(AlphaIndex.sections(listOf("b", "B")).getValue('B').size == 2, "case variants share a section")
        ok(AlphaIndex.sections(listOf("a")).keys == setOf('A'), "single section")
        ok(AlphaIndex.sectionOrder(setOf('#')) == listOf('#'), "hash alone")
        ok(AlphaIndex.bucket("\u00e9clair") == '#', "accented letter outside A-Z buckets to hash (declared)")
        ok(AlphaIndex.bucket("Fusion") == 'F', "letter bucket")
        ok(AlphaIndex.bucket("fusion") == 'F', "case folds")
        ok(AlphaIndex.bucket("42 apps") == '#', "digit leads to hash")
        ok(AlphaIndex.bucket("(aside)") == '#' || AlphaIndex.bucket("(aside) app") == 'A', "punctuation skipped to first letter or hash")
        ok(AlphaIndex.bucket("\u6f22app") == '#', "non-Latin letter buckets to hash (declared)")
        val sec = AlphaIndex.sections(listOf("beta", "Alpha", "42", "bravo"))
        ok(sec['A'] == listOf("Alpha") && sec['B'] == listOf("beta", "bravo"), "sections sorted and grouped")
        ok(AlphaIndex.sectionOrder(setOf('#', 'B', 'A')) == listOf('A', 'B', '#'), "hash sorts last")
        refuses("blank label") { AlphaIndex.bucket("  ") }
    }

    private fun exifOrient() {
        ok(ExifOrient.displayedSize(8, 100, 50) == (50 to 100), "8 swaps")
        refuses("negative orientation") { ExifOrient.of(-1) }
        ok(ExifOrient.of(7) == ExifOrient.Op(270, true), "7 mirrored 270")
        ok((5..8).all { ExifOrient.swapsDimensions(it) } && (1..4).none { ExifOrient.swapsDimensions(it) }, "swap partition")
        ok(ExifOrient.inverse(2) == 2 && ExifOrient.inverse(5) == 5, "mirrored self-inverse")
        ok(ExifOrient.of(1) == ExifOrient.Op(0, false), "normal")
        ok(ExifOrient.of(6) == ExifOrient.Op(90, false), "6 is 90cw")
        ok(ExifOrient.of(8) == ExifOrient.Op(270, false), "8 is 270cw")
        ok(ExifOrient.of(2).mirrorHorizontal && ExifOrient.of(5).mirrorHorizontal, "mirrored family")
        ok(ExifOrient.swapsDimensions(6) && ExifOrient.swapsDimensions(5), "transposed values swap")
        ok(!ExifOrient.swapsDimensions(3), "180 keeps dims")
        ok(ExifOrient.displayedSize(6, 4000, 3000) == (3000 to 4000), "portrait photo from landscape sensor")
        ok(ExifOrient.displayedSize(1, 4000, 3000) == (4000 to 3000), "normal keeps")
        ok(ExifOrient.inverse(6) == 8 && ExifOrient.inverse(8) == 6, "rotation inverses")
        ok(ExifOrient.inverse(1) == 1 && ExifOrient.inverse(3) == 3, "self-inverse rotations")
        ok((1..8).all { ExifOrient.inverse(ExifOrient.inverse(it)) == it }, "inverse involution (property)")
        refuses("orientation 0") { ExifOrient.of(0) }
        refuses("orientation 9") { ExifOrient.of(9) }
        refuses("degenerate size") { ExifOrient.displayedSize(1, 0, 10) }
    }

    private fun acceptLang() {
        ok(AcceptLang.parse("en;q=0.000").single().q == 0.0, "three zeros allowed")
        ok(AcceptLang.bestMatch("en-GB", listOf("en")) == null, "prefix rule is one-directional (en-GB does not match bare en)")
        ok(AcceptLang.parse("de-DE, de;q=0.9").size == 2, "two prefs")
        ok(AcceptLang.bestMatch("en;q=0.8,fr;q=0.8", listOf("fr", "en")) == "en", "tie keeps header order")
        ok(AcceptLang.bestMatch("de", listOf("DE-at", "de")) == "DE-at", "available case preserved, prefix first-match")
        refuses("nine-char subtag") { AcceptLang.parse("abcdefghi") }
        val p = AcceptLang.parse("en-US,en;q=0.9,de;q=0.7")
        ok(p[0].tag == "en-us" && p[0].q == 1.0, "implicit q=1 first")
        ok(p[2].tag == "de" && p[2].q == 0.7, "explicit q ordering")
        ok(AcceptLang.bestMatch("en-US,en;q=0.9", listOf("de", "en-GB")) == "en-GB", "prefix rule en matches en-GB")
        ok(AcceptLang.bestMatch("de;q=0.9,fr", listOf("de", "fr")) == "fr", "higher q wins despite order")
        ok(AcceptLang.bestMatch("ja", listOf("de", "fr")) == null, "nothing acceptable")
        ok(AcceptLang.bestMatch("ja,*;q=0.1", listOf("de", "fr")) == "de", "wildcard fallback")
        ok(AcceptLang.bestMatch("de;q=0", listOf("de")) == null, "q=0 means not acceptable")
        ok(AcceptLang.parse("fr").single() == AcceptLang.Pref("fr", 1.0), "single tag")
        refuses("bad q") { AcceptLang.parse("en;q=2") }
        refuses("malformed tag") { AcceptLang.parse("en_US") }
        refuses("empty header") { AcceptLang.parse("  ") }
        refuses("four decimals") { AcceptLang.parse("en;q=0.1234") }
    }

    private fun tagExtract() {
        ok(TagExtract.tags("end #tag") == listOf("tag"), "tag at end")
        ok(TagExtract.strip("no tags") == "no tags", "strip identity")
        ok(TagExtract.tags("#a #b #a") == listOf("a", "b"), "dedupe keeps order")
        ok(TagExtract.strip("#only") == "", "tag-only text strips empty")
        ok(TagExtract.tags("(#x)") == listOf("x"), "parenthesized tag")
        ok(TagExtract.tags("notes about #privacy and #Tor") == listOf("privacy", "Tor"), "two tags, casing kept")
        ok(TagExtract.tags("#dup and #DUP again") == listOf("dup"), "case-insensitive identity, first casing")
        ok(TagExtract.tags("issue #123 is not a tag").isEmpty(), "numeric not a tag")
        ok(TagExtract.tags("mid#word no").isEmpty(), "mid-word hash not a tag")
        ok(TagExtract.tags("##double").isEmpty(), "double hash not a tag")
        ok(TagExtract.tags("#tag-with_parts9 end") == listOf("tag-with_parts9"), "full alphabet")
        ok(TagExtract.hasTag("see #Privacy", "privacy"), "hasTag folds case")
        ok(TagExtract.strip("a #x b") == "a b", "strip removes tags and tidies spaces")
        refuses("digit-led query") { TagExtract.hasTag("x", "9tag") }
    }

    private fun severity() {
        ok(Severity.Level.INFO < Severity.Level.WARN && Severity.Level.ERROR < Severity.Level.CRITICAL, "total order")
        ok(Severity.parse("WARN") == Severity.Level.WARN, "canonical parse")
        ok(Severity.atLeast(Severity.Level.ERROR, Severity.Level.WARN), "error clears a warn floor")
        ok(!Severity.atLeast(Severity.Level.INFO, Severity.Level.WARN), "info below a warn floor")
        ok(Severity.escalate(Severity.Level.WARN, Severity.Level.CRITICAL) == Severity.Level.CRITICAL, "escalation up")
        ok(Severity.escalate(Severity.Level.ERROR, Severity.Level.ERROR) == Severity.Level.ERROR, "same level is legal")
        ok(Severity.highest(listOf(Severity.Level.INFO, Severity.Level.CRITICAL, Severity.Level.WARN)) == Severity.Level.CRITICAL, "aggregate")
        ok(Severity.filter(listOf("a" to Severity.Level.INFO, "b" to Severity.Level.ERROR), Severity.Level.WARN).single().first == "b", "floor filter")
        ok(Severity.Level.values().size == 4, "exactly four levels")
        refuses("de-escalation refused") { Severity.escalate(Severity.Level.CRITICAL, Severity.Level.WARN) }
        refuses("lowercase name") { Severity.parse("warn") }
        refuses("empty aggregate") { Severity.highest(emptyList()) }
    }

    private fun hiddenDot() {
        ok(!HiddenDot.directoryMediaExcluded(emptyList()), "empty dir not excluded")
        ok(HiddenDot.visibleCount(listOf(".a"), false) == 0, "only hidden")
        ok(HiddenDot.visibleCount(emptyList(), false) == 0, "empty dir")
        ok(HiddenDot.isDotHidden("..config"), "double-dot-led name is a name, hidden")
        refuses("slash in name") { HiddenDot.isNoMediaMarker(".nomedia").let { HiddenDot.isDotHidden("a/b") } }
        ok(HiddenDot.isDotHidden(".config") && !HiddenDot.isDotHidden("file.txt"), "dotfile rule")
        ok(HiddenDot.isNoMediaMarker(".nomedia") && !HiddenDot.isNoMediaMarker(".nomedia2"), "exact marker")
        ok(HiddenDot.isDotHidden(".nomedia"), "the marker is itself dot-hidden")
        ok(HiddenDot.visibleCount(listOf(".a", "b", ".c", "d"), false) == 2, "hidden filtered")
        ok(HiddenDot.visibleCount(listOf(".a", "b"), true) == 2, "show-hidden counts all")
        ok(HiddenDot.directoryMediaExcluded(listOf("x.jpg", ".nomedia")), "directory exclusion")
        ok(!HiddenDot.directoryMediaExcluded(listOf("x.jpg")), "no marker")
        refuses("dot token") { HiddenDot.isDotHidden(".") }
        refuses("dotdot token") { HiddenDot.isDotHidden("..") }
        refuses("path not name") { HiddenDot.isDotHidden("a/.b") }
    }

    private fun sessionCode() {
        ok(SessionCode.canonical("hjkm-npqr") == "HJKMNPQR", "mid-alphabet letters")
        ok(SessionCode.matches(SessionCode.display("23456789"), "23456789"), "display form matches bare")
        ok(SessionCode.display(SessionCode.canonical("zz99-zz99")) == "ZZ99-ZZ99", "display of canonical")
        ok(!SessionCode.ALPHABET.contains('L') && !SessionCode.ALPHABET.contains('U'), "L and U excluded too")
        refuses("hyphen only") { SessionCode.canonical("----") }
        ok(SessionCode.canonical("abcd-efgh") == "ABCDEFGH", "case and hyphen fold")
        ok(SessionCode.display("ABCDEFGH") == "ABCD-EFGH", "display grouping")
        ok(SessionCode.matches("abcd-efgh", "ABCDEFGH"), "identity across forms")
        ok(SessionCode.canonical("0123-4567") == "01234567", "digits fine")
        ok(SessionCode.ALPHABET.length == 32 && !SessionCode.ALPHABET.contains('I') && !SessionCode.ALPHABET.contains('O'), "no-ILOU alphabet")
        refuses("letter I") { SessionCode.canonical("IBCDEFGH") }
        refuses("letter U") { SessionCode.canonical("UBCDEFGH") }
        refuses("seven chars") { SessionCode.canonical("ABCDEFG") }
        refuses("nine chars") { SessionCode.canonical("ABCDEFGHJ") }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        sheetHide(); quoteNest(); goToLine(); objNum(); sriHash(); debVersion()
        issn(); upcE(); cwdTilde(); levenshtein(); prefKeyLint(); histDedup()
        sampleBytes(); subjectNorm(); gpsRational(); displayInch(); backupName(); alphaIndex()
        exifOrient(); acceptLang(); tagExtract(); severity(); hiddenDot(); sessionCode()
        println("R44 PASS: " + count + " production-kernel assertions across 24 kernels. Host JVM only; not Android/device evidence.")
    }
}

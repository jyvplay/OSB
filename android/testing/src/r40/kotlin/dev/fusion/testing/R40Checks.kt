package dev.fusion.testing

import dev.fusion.ooxml.NumFmtSections
import dev.fusion.ooxml.CellErr
import dev.fusion.documents.DashEducate
import dev.fusion.documents.ObjReplace
import dev.fusion.editor.FootnoteMark
import dev.fusion.format.pdf.PdfRotate
import dev.fusion.browser.model.HtmlEntity
import dev.fusion.messages.Rfc2047
import dev.fusion.archive.SevenZHead
import dev.fusion.index.Fnv1a
import dev.fusion.lens.Mrz
import dev.fusion.files.SafeName
import dev.fusion.perf.Percentile
import dev.fusion.notes.TaskCheckbox
import dev.fusion.windowing.SnapFraction
import dev.fusion.airplay.PlistHead
import dev.fusion.launcher.Monogram
import dev.fusion.vault.Bech32
import dev.fusion.security.OtpauthUri
import dev.fusion.privacy.IpRedact
import java.io.File

/**
 * R40 executed contracts. Host JVM evidence only — NOT Android, device,
 * service or acceptance evidence. Every `ok` is one counted assertion
 * against a production kernel compiled in isolation.
 */
object R40Checks {
    private var count = 0

    private fun ok(cond: Boolean, msg: String) {
        if (!cond) throw AssertionError(msg)
        count++
    }

    private fun refuses(msg: String, block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { count++; return }
        throw AssertionError("expected refusal: $msg")
    }

    private fun numFmt() {
        ok(NumFmtSections.split("0.00") == listOf("0.00"), "single section")
        ok(NumFmtSections.split("0.00;[Red]-0.00") == listOf("0.00", "[Red]-0.00"), "two sections")
        ok(NumFmtSections.split("#,##0;(#,##0);\"-\";@") .size == 4, "four sections")
        ok(NumFmtSections.split("0\";\"0") == listOf("0\";\"0"), "quoted semicolon does not split")
        ok(NumFmtSections.split("[h];mm") == listOf("[h]", "mm"), "bracket then split")
        ok(NumFmtSections.split("0\\;0") == listOf("0\\;0"), "escaped semicolon does not split")
        val c = NumFmtSections.classify("[Red]-0.00")
        ok(c.color == "RED" && !c.general, "red classified")
        ok(NumFmtSections.classify("[Color42]0").color == "COLOR42", "indexed color")
        ok(NumFmtSections.classify("[Color57]0").color == null, "Color57 beyond 56 not a color")
        ok(NumFmtSections.classify("General").general, "General literal")
        ok(NumFmtSections.classify("GENERAL").general, "case-insensitive General")
        ok(NumFmtSections.classify("[Blue]general").general && NumFmtSections.classify("[Blue]general").color == "BLUE", "colored General")
        ok(!NumFmtSections.classify("[h]:mm").general && NumFmtSections.classify("[h]:mm").color == null, "elapsed-hours bracket is not a color")
        ok(NumFmtSections.parse("0;;;@").size == 4, "empty middle sections allowed")
        ok(NumFmtSections.split("\"a;b\";\"c;d\"") == listOf("\"a;b\"", "\"c;d\""), "two quoted islands")
        ok(NumFmtSections.classify("[RED]0").color == "RED", "color case-insensitive")
        ok(NumFmtSections.classify("[Color1]0").color == "COLOR1" && NumFmtSections.classify("[Color56]0").color == "COLOR56", "index bounds 1 and 56")
        ok(NumFmtSections.classify("[Color0]0").color == null, "Color0 invalid")
        ok(NumFmtSections.classify("0.00").color == null && !NumFmtSections.classify("0.00").general, "plain numeric")
        ok(NumFmtSections.parse("[Green]General;[Red]General").all { it.general }, "both sections General")
        ok(!NumFmtSections.classify("[Red]-General").general, "minus prefix stops the General literal")
        ok(NumFmtSections.split("[\"a\"];b") == listOf("[\"a\"]", "b"), "quote inside bracket still closes before the split")
        refuses("five sections") { NumFmtSections.split("1;2;3;4;5") }
        refuses("unterminated quote") { NumFmtSections.split("0\"abc") }
        refuses("unterminated bracket") { NumFmtSections.split("[Red0") }
        refuses("dangling backslash") { NumFmtSections.split("0\\") }
        refuses("empty format") { NumFmtSections.split("") }
    }

    private fun cellErr() {
        ok(CellErr.parse("#DIV/0!") == CellErr.Err.DIV0, "div0")
        ok(CellErr.parse("#N/A") == CellErr.Err.NA, "NA has no bang")
        ok(CellErr.parse("#NAME?") == CellErr.Err.NAME, "NAME ends in ?")
        ok(CellErr.Err.values().size == 7, "exactly seven classic errors")
        ok(CellErr.Err.values().all { CellErr.parse(CellErr.format(it)) == it }, "format/parse bijection")
        ok(CellErr.isError("#REF!") && !CellErr.isError("#REF"), "exact literal only")
        ok(CellErr.classify("TRUE") == CellErr.Literal.BOOLEAN_TRUE, "TRUE literal")
        ok(CellErr.classify("FALSE") == CellErr.Literal.BOOLEAN_FALSE, "FALSE literal")
        ok(CellErr.classify("true") == CellErr.Literal.OTHER, "case-sensitive booleans")
        ok(CellErr.classify("#NULL!") == CellErr.Literal.ERROR, "error classified")
        ok(CellErr.parse("#NULL!") == CellErr.Err.NULL && CellErr.parse("#VALUE!") == CellErr.Err.VALUE, "null and value")
        ok(CellErr.parse("#NUM!") == CellErr.Err.NUM && CellErr.parse("#REF!") == CellErr.Err.REF, "num and ref")
        ok(!CellErr.isError("#n/a"), "errors are case-sensitive literals")
        ok(CellErr.classify("#NAME?") == CellErr.Literal.ERROR && CellErr.classify("NAME?") == CellErr.Literal.OTHER, "hash required")
        ok(CellErr.format(CellErr.Err.DIV0) == "#DIV/0!", "format emits canonical spelling")
        refuses("SPILL is newer-era") { CellErr.parse("#SPILL!") }
        refuses("CALC is newer-era") { CellErr.parse("#CALC!") }
        refuses("not an error") { CellErr.parse("DIV/0") }
    }

    private fun dashEducate() {
        ok(DashEducate.educate("pages 10--12") == "pages 10\u201312", "en dash")
        ok(DashEducate.educate("wait---now") == "wait\u2014now", "em dash")
        ok(DashEducate.educate("so...") == "so\u2026", "ellipsis")
        ok(DashEducate.educate("well-known") == "well-known", "single hyphen untouched")
        ok(DashEducate.educate("----") == "----", "4-run untouched (rule)")
        ok(DashEducate.educate("....") == "....", "4-dot run untouched")
        ok(DashEducate.educate("a--b---c") == "a\u2013b\u2014c", "mixed runs")
        ok(DashEducate.educate("- list item") == "- list item", "list marker survives")
        ok(DashEducate.educate("") == "", "empty")
        val round = "x--y---z...w-v"
        ok(DashEducate.deEducate(DashEducate.educate(round)) == round, "roundtrip through deEducate")
        ok(DashEducate.educate("..") == "..", "two dots untouched")
        ok(DashEducate.educate("--") == "\u2013", "bare double hyphen")
        ok(DashEducate.educate("-----") == "-----", "5-run untouched")
        ok(DashEducate.educate("a...b...c") == "a\u2026b\u2026c", "two ellipses")
        ok(DashEducate.educate("--...---") == "\u2013\u2026\u2014", "adjacent different runs")
        ok(DashEducate.educate("x-y--z") == "x-y\u2013z", "single next to double")
        ok(DashEducate.deEducate("\u2014\u2013\u2026") == "-----...", "deEducate emits ASCII")
        ok(DashEducate.educate(DashEducate.educate("a--b")) == "a\u2013b", "idempotent on educated text")
    }

    private fun objReplace() {
        val t = ObjReplace.insert(ObjReplace.insert("hello world", 5), 0)
        ok(ObjReplace.positions(t) == listOf(0, 6), "positions after two inserts")
        ok(ObjReplace.strip(t) == "hello world", "strip restores text")
        ok(ObjReplace.strip(t).length + ObjReplace.positions(t).size == t.length, "partition invariant")
        ok(ObjReplace.segments(t) == listOf("", "hello", " world"), "segments around objects")
        ok(ObjReplace.segments("plain") == listOf("plain"), "no-object segments")
        val removed = ObjReplace.remove(t, 0)
        ok(ObjReplace.positions(removed) == listOf(5), "remove shifts bookkeeping")
        ok(ObjReplace.insert("", 0) == "\uFFFC", "insert into empty")
        ok(ObjReplace.positions("plain") == emptyList<Int>(), "no objects in plain text")
        ok(ObjReplace.strip("\uFFFC\uFFFC") == "" && ObjReplace.positions("\uFFFC\uFFFC") == listOf(0, 1), "objects only")
        ok(ObjReplace.segments("\uFFFC") == listOf("", ""), "lone object yields two empty segments")
        ok(ObjReplace.segments(t).size == ObjReplace.positions(t).size + 1, "segments = objects + 1 invariant")
        ok(ObjReplace.remove(ObjReplace.insert("xy", 1), 1) == "xy", "insert/remove inverse")
        refuses("insert out of bounds") { ObjReplace.insert("ab", 3) }
        refuses("remove where no object") { ObjReplace.remove("ab", 0) }
        refuses("remove out of range") { ObjReplace.remove("ab", 5) }
    }

    private fun footnoteMark() {
        ok(FootnoteMark.symbol(1) == "*", "first is asterisk")
        ok(FootnoteMark.symbol(5) == "\u2016", "fifth is double vertical")
        ok(FootnoteMark.symbol(6) == "\u00B6", "sixth is pilcrow")
        ok(FootnoteMark.symbol(7) == "**", "second cycle doubles")
        ok(FootnoteMark.symbol(12) == "\u00B6\u00B6", "twelfth is double pilcrow")
        ok(FootnoteMark.symbol(13) == "***", "third cycle triples")
        ok(FootnoteMark.symbol(18) == "\u00B6\u00B6\u00B6", "cap value")
        ok(FootnoteMark.superscript(123) == "\u00B9\u00B2\u00B3", "1-3 outside the 207x block")
        ok(FootnoteMark.superscript(2046) == "\u00B2\u2070\u2074\u2076", "mixed blocks")
        ok(FootnoteMark.fromSuperscript("\u00B9\u00B2\u00B3") == 123, "inverse")
        ok((1..200).all { FootnoteMark.fromSuperscript(FootnoteMark.superscript(it)) == it }, "superscript bijection 1..200")
        ok(FootnoteMark.symbol(2) == "\u2020" && FootnoteMark.symbol(3) == "\u2021" && FootnoteMark.symbol(4) == "\u00A7", "dagger, double dagger, section")
        ok(FootnoteMark.symbol(8) == "\u2020\u2020", "doubled dagger")
        ok((1..18).map { FootnoteMark.symbol(it) }.toSet().size == 18, "all 18 symbol marks distinct")
        ok(FootnoteMark.superscript(10) == "\u00B9\u2070", "ten mixes blocks")
        ok(FootnoteMark.superscript(4) == "\u2074" && FootnoteMark.superscript(9) == "\u2079", "2070-block digits")
        refuses("zero") { FootnoteMark.symbol(0) }
        refuses("beyond three cycles") { FootnoteMark.symbol(19) }
        refuses("plain digits are not marks") { FootnoteMark.fromSuperscript("12") }
        refuses("leading superscript zero") { FootnoteMark.fromSuperscript("\u2070\u00B9") }
    }

    private fun pdfRotate() {
        ok(PdfRotate.normalize(450) == 90, "450 -> 90")
        ok(PdfRotate.normalize(-90) == 270, "-90 -> 270")
        ok(PdfRotate.normalize(0) == 0 && PdfRotate.normalize(360) == 0, "identity forms")
        ok(PdfRotate.visibleSize(612.0, 792.0, 90) == (792.0 to 612.0), "90 swaps")
        ok(PdfRotate.visibleSize(612.0, 792.0, 180) == (612.0 to 792.0), "180 keeps")
        ok(PdfRotate.resolve(listOf(null, null, 90)) == 90, "ancestor inheritance")
        ok(PdfRotate.resolve(listOf(0, 90)) == 0, "page value wins even when 0")
        ok(PdfRotate.resolve(listOf(null, null)) == 0, "default 0")
        ok(PdfRotate.compose(270, 180) == 90, "composition mod 360")
        ok(PdfRotate.isPortrait(612.0, 792.0, 0) && !PdfRotate.isPortrait(612.0, 792.0, 90), "orientation flips")
        ok(PdfRotate.normalize(-450) == 270, "large negative")
        ok(PdfRotate.visibleSize(595.276, 841.89, 270) == (841.89 to 595.276), "A4 at 270")
        ok(PdfRotate.compose(90, 90) == 180 && PdfRotate.compose(180, 180) == 0, "compose table spots")
        ok((0..3).all { a -> (0..3).all { b -> PdfRotate.compose(a * 90, b * 90) == ((a + b) % 4) * 90 } }, "full compose table")
        ok(PdfRotate.resolve(listOf(null, 270, 90)) == 270, "nearest ancestor wins")
        refuses("45 degrees") { PdfRotate.normalize(45) }
        refuses("degenerate page") { PdfRotate.visibleSize(0.0, 792.0, 0) }
        refuses("bad inherited value") { PdfRotate.resolve(listOf(null, 91)) }
    }

    private fun htmlEntity() {
        ok(HtmlEntity.decode("&amp;&lt;&gt;") == "&<>", "core trio")
        ok(HtmlEntity.decode("&#65;") == "A", "decimal")
        ok(HtmlEntity.decode("&#x1F600;") == String(Character.toChars(0x1F600)), "astral hex")
        ok(HtmlEntity.decode("&hellip;&nbsp;") == "\u2026\u00A0", "named pair")
        ok(HtmlEntity.decode("&dagger;&Dagger;") == "\u2020\u2021", "case-distinct names")
        ok(HtmlEntity.decode("plain text") == "plain text", "no references")
        ok(HtmlEntity.decode("&#X41;") == "A", "capital X hex form")
        ok(HtmlEntity.encode("a<b&c>\"d'") == "a&lt;b&amp;c&gt;&quot;d&apos;", "minimal encode")
        ok(HtmlEntity.decode(HtmlEntity.encode("<&>\"'")) == "<&>\"'", "encode/decode roundtrip")
        ok(HtmlEntity.NAMED.size == 30, "declared subset size")
        ok(HtmlEntity.decode("&quot;&apos;") == "\"'", "quote pair")
        ok(HtmlEntity.decode("&euro;&pound;&yen;&cent;") == "\u20AC\u00A3\u00A5\u00A2", "currency row")
        ok(HtmlEntity.decode("&larr;&rarr;") == "\u2190\u2192", "arrows")
        ok(HtmlEntity.decode("&#x10FFFF;") == String(Character.toChars(0x10FFFF)), "last code point accepted")
        ok(HtmlEntity.decode("&#160;") == "\u00A0", "160 decimal is fine (above the C1 hole)")
        ok(HtmlEntity.encode("no specials") == "no specials", "encode passthrough")
        refuses("semicolonless") { HtmlEntity.decode("&amp x") }
        refuses("unknown name") { HtmlEntity.decode("&bogus;") }
        refuses("NUL") { HtmlEntity.decode("&#0;") }
        refuses("surrogate") { HtmlEntity.decode("&#xD800;") }
        refuses("beyond plane 16") { HtmlEntity.decode("&#x110000;") }
        refuses("C1 quirk range refused") { HtmlEntity.decode("&#128;") }
        refuses("empty reference") { HtmlEntity.decode("&;") }
    }

    private fun rfc2047() {
        ok(Rfc2047.decode("=?ISO-8859-1?Q?Keld_J=F8rn_Simonsen?= <keld@dkuug.dk>") ==
            "Keld J\u00F8rn Simonsen <keld@dkuug.dk>", "RFC sample Q word")
        ok(Rfc2047.decode("=?UTF-8?B?8J+UkA==?= lock") == "\uD83D\uDD10 lock", "B word with astral payload")
        ok(Rfc2047.decode("=?UTF-8?B?YQ==?= =?UTF-8?B?Yg==?=") == "ab", "space between adjacent words deleted")
        ok(Rfc2047.decode("=?UTF-8?B?YQ==?= plain") == "a plain", "space before plain text kept")
        ok(Rfc2047.decode("no words here") == "no words here", "plain header untouched")
        ok(Rfc2047.decode("=?utf-8?q?a_b?=") == "a b", "lowercase charset+encoding, underscore space")
        ok(Rfc2047.containsEncodedWord("=?UTF-8?B?YQ==?=") && !Rfc2047.containsEncodedWord("plain"), "detector")
        ok(Rfc2047.decodeWord("ISO-8859-1", "Q", "J=F8rn") == "J\u00F8rn", "latin-1 byte above ASCII")
        ok(Rfc2047.decodeWord("US-ASCII", "B", "aGk=") == "hi", "ascii B word")
        ok(Rfc2047.decode("tab\t=?UTF-8?B?YQ==?=\t=?UTF-8?B?Yg==?=") == "tab\tab", "tab counts as deletable whitespace between words")
        ok(Rfc2047.decode("=?UTF-8?Q?=E2=82=AC?=") == "\u20AC", "multi-byte UTF-8 Q sequence")
        refuses("unknown charset") { Rfc2047.decode("=?KOI8-R?Q?abc?=") }
        refuses("language tag") { Rfc2047.decode("=?UTF-8*en?Q?abc?=") }
        refuses("lowercase q hex") { Rfc2047.decodeWord("UTF-8", "Q", "=f8") }
        refuses("raw space in Q") { Rfc2047.decodeWord("UTF-8", "Q", "a b") }
        refuses("bad B length") { Rfc2047.decodeWord("UTF-8", "B", "abcde") }
        refuses("truncated escape") { Rfc2047.decodeWord("UTF-8", "Q", "a=4") }
    }

    private fun sevenZ(fixtures: File) {
        val bytes = File(fixtures, "sevenz.bin").readBytes()
        val h = SevenZHead.parse(bytes)
        ok(h.versionMajor == 0 && h.versionMinor == 4, "version 0.4")
        ok(h.nextHeaderOffset == 64L && h.nextHeaderSize == 128L, "geometry fields")
        ok(h.nextHeaderCrc == 0xDEADBEEFL, "next-header CRC passthrough")
        ok(SevenZHead.fits(h, 32 + 64 + 128), "exact fit")
        ok(!SevenZHead.fits(h, 200), "undersized file flagged")
        ok(SevenZHead.MAGIC.size == 6 && (SevenZHead.MAGIC[0].toInt() and 0xFF) == 0x37, "magic starts '7'")
        ok(SevenZHead.fits(h, Long.MAX_VALUE), "huge file fits")
        val crcByte = bytes.copyOf(); crcByte[8] = (crcByte[8].toInt() xor 0x80).toByte()
        refuses("stored CRC tamper") { SevenZHead.parse(crcByte) }
        val flipped = bytes.copyOf(); flipped[20] = (flipped[20].toInt() xor 1).toByte()
        refuses("StartHeaderCRC catches a bit flip") { SevenZHead.parse(flipped) }
        val minorOnly = bytes.copyOf(); minorOnly[7] = 9
        ok(SevenZHead.parse(minorOnly).versionMinor == 9, "minor version is outside the CRC span and tolerated")
        val badMagic = bytes.copyOf(); badMagic[0] = 0x50
        refuses("bad magic") { SevenZHead.parse(badMagic) }
        val badMajor = bytes.copyOf(); badMajor[6] = 1
        refuses("major version 1") { SevenZHead.parse(badMajor) }
        refuses("truncated") { SevenZHead.parse(bytes.copyOf(31)) }
    }

    private fun fnv1a() {
        ok(Fnv1a.hash32("") == 0x811C9DC5.toInt(), "32-bit offset basis for empty input")
        ok(Fnv1a.hash32("a") == 0xE40C292C.toInt(), "published vector a")
        ok(Fnv1a.hash32("foobar") == 0xBF9CF968.toInt(), "published vector foobar")
        ok(Fnv1a.hash64("") == 0xCBF29CE484222325UL.toLong(), "64-bit offset basis")
        ok(Fnv1a.hash64("foobar") == 0x85944171F73967E8UL.toLong(), "published 64 vector")
        ok(Fnv1a.hash32("b") != Fnv1a.hash32("a"), "adjacent inputs differ")
        ok(Fnv1a.bucket("anything", 1) == 0, "single bucket")
        ok((0 until 100).all { Fnv1a.bucket("k$it", 7) in 0..6 }, "buckets stay in range")
        ok(Fnv1a.hash32(byteArrayOf()) == Fnv1a.hash32(""), "byte and string entry points agree on empty")
        ok(Fnv1a.hash32("\u20AC") == Fnv1a.hash32(byteArrayOf(0xE2.toByte(), 0x82.toByte(), 0xAC.toByte())), "UTF-8 bytes under the hood")
        ok(Fnv1a.hash64("a") != Fnv1a.hash64("") && Fnv1a.hash64("ab") != Fnv1a.hash64("ba"), "order sensitivity")
        ok(Fnv1a.bucket("stable-key", 16) == Fnv1a.bucket("stable-key", 16), "deterministic bucket")
        ok((0 until 64).map { Fnv1a.hash32("k$it") }.toSet().size == 64, "no collisions on the small probe set")
        refuses("zero buckets") { Fnv1a.bucket("x", 0) }
    }

    private fun mrz() {
        ok(Mrz.checkDigit("L898902C3") == 6, "ICAO sample document number")
        ok(Mrz.checkDigit("740812") == 2, "ICAO sample birth date")
        ok(Mrz.checkDigit("120415") == 9, "ICAO sample expiry")
        ok(Mrz.checkDigit("ZE184226B<<<<<") == 1, "ICAO sample personal number")
        ok(Mrz.value('<') == 0 && Mrz.value('A') == 10 && Mrz.value('Z') == 35, "character values")
        val line2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
        val td3 = Mrz.validateTd3Line2(line2)
        ok(td3.documentNumber == "L898902C3" && td3.nationality == "UTO", "ICAO TD3 line accepted")
        ok(td3.birth == "740812" && td3.expiry == "120415" && td3.sex == 'F', "fields extracted")
        ok(td3.personalNumber == "ZE184226B", "filler trimmed")
        val flipped = "L898902C36UTO7408122F1204159ZE184226B<<<<<11"
        ok(Mrz.checkDigit("<<<<<<") == 0, "all filler is zero")
        ok(Mrz.checkDigit("0") == 0 && Mrz.checkDigit("9") == 3, "single digits (9*7 mod 10)")
        ok(Mrz.verify("L898902C3", '6') && !Mrz.verify("L898902C3", '5'), "verify both ways")
        ok(Mrz.checkDigit("AB") == (10 * 7 + 11 * 3) % 10, "weight cycle spot check")
        refuses("composite digit catches tampering") { Mrz.validateTd3Line2(flipped) }
        val badDoc = "L898902C37" + line2.substring(10)
        refuses("document check digit") { Mrz.validateTd3Line2(badDoc) }
        refuses("wrong length") { Mrz.validateTd3Line2(line2.dropLast(1)) }
        refuses("lowercase refused") { Mrz.checkDigit("l898902c3") }
        refuses("empty field") { Mrz.checkDigit("") }
    }

    private fun safeName() {
        ok(SafeName.isSafe("report.pdf"), "ordinary name")
        ok(!SafeName.isSafe("CON"), "device name")
        ok(!SafeName.isSafe("con.txt"), "device name with extension, case-insensitive")
        ok(!SafeName.isSafe("COM7"), "numbered device")
        ok(SafeName.isSafe("COM10"), "COM10 is NOT reserved")
        ok(!SafeName.isSafe("trailing.") && !SafeName.isSafe("trailing "), "trailing dot/space")
        ok(!SafeName.isSafe("a<b"), "forbidden char")
        ok(!SafeName.isSafe(".") && !SafeName.isSafe(".."), "path tokens")
        ok(SafeName.sanitize("a<b>c:d.txt") == "a_b_c_d.txt", "replacement")
        ok(SafeName.sanitize("report.pdf.") == "report.pdf", "trailing dot trimmed")
        ok(SafeName.sanitize("CON.txt") == "_CON.txt", "reserved stem prefixed")
        ok(SafeName.sanitize(SafeName.sanitize("a<b|c")) == SafeName.sanitize("a<b|c"), "idempotent")
        ok(SafeName.isSafe(SafeName.sanitize("NUL")), "sanitize output is safe")
        ok(SafeName.sanitize("x".repeat(300)).length == 255, "length cap")
        ok(!SafeName.isSafe("LPT3"), "LPT family")
        ok(SafeName.isSafe("CONSOLE") && SafeName.isSafe("null.txt2"), "near-miss names are fine")
        ok(!SafeName.isSafe("ctrl\u0001char"), "control character")
        ok(!SafeName.isSafe("x".repeat(256)) && SafeName.isSafe("x".repeat(255)), "length boundary")
        ok(SafeName.sanitize("tab\there") == "tab_here", "tab replaced")
        ok(SafeName.isSafe(SafeName.sanitize("  CON  ")), "sanitize of padded CON is safe")
        refuses("nothing survives") { SafeName.sanitize("???") }
        refuses("empty input") { SafeName.sanitize("") }
        refuses("dots only") { SafeName.sanitize("..") }
    }

    private fun percentile() {
        val wiki = listOf(15L, 20L, 35L, 40L, 50L)
        ok(Percentile.nearestRank(wiki, 30.0) == 20L, "classic worked example p30")
        ok(Percentile.nearestRank(wiki, 40.0) == 20L, "p40")
        ok(Percentile.nearestRank(wiki, 50.0) == 35L, "p50")
        ok(Percentile.nearestRank(wiki, 100.0) == 50L, "p100 is max")
        val hundred = (1L..100L).toList()
        ok(Percentile.nearestRank(hundred, 95.0) == 95L, "p95 of 1..100")
        ok(Percentile.nearestRank(hundred, 0.5) == 1L, "tiny percentile returns first")
        ok(hundred.all { v -> Percentile.nearestRank(hundred, v.toDouble()) == v }, "self-map on 1..100")
        ok(Percentile.nearestRank(hundred, 55.0) == 55L, "p55 regression attack on the FP-order repair")
        ok(Percentile.nearestRank(hundred, 29.0) == 29L, "p29 the other drift direction")
        val s = Percentile.summarize(wiki)
        ok(s.p50 == 35L && s.min == 15L && s.max == 50L && s.n == 5, "summary")
        ok(Percentile.nearestRank(listOf(7L), 99.0) == 7L, "singleton")
        refuses("unsorted refused loudly") { Percentile.nearestRank(listOf(3L, 1L), 50.0) }
        refuses("empty") { Percentile.nearestRank(emptyList(), 50.0) }
        refuses("p zero") { Percentile.nearestRank(wiki, 0.0) }
        refuses("p above 100") { Percentile.nearestRank(wiki, 100.1) }
    }

    private fun taskCheckbox() {
        ok(TaskCheckbox.parse("- [x] done")!!.done, "dash done")
        ok(!TaskCheckbox.parse("* [ ] todo")!!.done, "star open")
        ok(TaskCheckbox.parse("1. [X] numbered")!!.done, "ordered uppercase X")
        ok(TaskCheckbox.parse("  - [ ] indented")!!.indent == "  ", "indent preserved")
        ok(TaskCheckbox.parse("-[ ] nope") == null, "marker space required (GFM)")
        ok(TaskCheckbox.parse("- [y] nope") == null, "only space/x/X in the box")
        ok(TaskCheckbox.parse("plain line") == null, "non-list line")
        ok(TaskCheckbox.toggle("- [ ] write tests") == "- [x] write tests", "toggle on")
        ok(TaskCheckbox.toggle("- [x] write tests") == "- [ ] write tests", "toggle off")
        ok(TaskCheckbox.toggle(TaskCheckbox.toggle("  2) [X] keep me")) == "  2) [x] keep me", "double toggle normalizes x case only")
        val st = TaskCheckbox.stats(listOf("- [x] a", "- [ ] b", "- [ ] c", "plain"))
        ok(st.open == 2 && st.done == 1 && st.total == 3, "stats")
        ok(TaskCheckbox.parse("+ [ ] plus marker") != null, "plus marker")
        ok(TaskCheckbox.parse("10) [ ] paren ordered") != null, "paren ordered marker")
        ok(TaskCheckbox.parse("- [x]")!!.text == "", "empty text task")
        ok(TaskCheckbox.parse("- [x]\tafter tab")!!.done, "tab after box")
        ok(TaskCheckbox.parse("1234567890. [ ] too long") == null, "ten-digit ordinal exceeds GFM cap")
        ok(TaskCheckbox.stats(emptyList()).total == 0, "empty stats")
        refuses("toggle of non-task") { TaskCheckbox.toggle("plain") }
    }

    private fun snapFraction() {
        ok(SnapFraction.halves(1081) == (540 to 541), "odd width halves sum exactly")
        ok(SnapFraction.spans(1000, 3) == listOf(333, 333, 334), "thirds of 1000 (floor rule puts the remainder last)")
        var tiles = true
        for (total in intArrayOf(1, 2, 3, 7, 100, 1080, 1081, 2560, 3839)) {
            for (n in 1..6) if (SnapFraction.spans(total, n).sum() != total) tiles = false
        }
        ok(tiles, "exact tiling sweep (9 widths x 6 column counts)")
        ok(SnapFraction.spans(2, 3) == listOf(0, 1, 1), "more columns than pixels still tiles")
        ok(SnapFraction.boundary(1000, 0, 3) == 0 && SnapFraction.boundary(1000, 3, 3) == 1000, "end boundaries")
        ok(SnapFraction.columnAt(1000, 0, 3) == 0 && SnapFraction.columnAt(1000, 999, 3) == 2, "column lookup ends")
        ok(SnapFraction.columnAt(1000, 333, 3) == 1, "boundary pixel belongs to the right span")
        ok(SnapFraction.spans(100, 1) == listOf(100), "single column")
        ok(SnapFraction.spans(0, 3) == listOf(0, 0, 0), "zero total tiles to zeros")
        ok((0 until 999).all { x -> SnapFraction.columnAt(999, x, 4).let { it == 0 || it == 1 || it == 2 || it == 3 } }, "column lookup total sweep")
        ok((0 until 4).all { k -> SnapFraction.columnAt(1000, SnapFraction.boundary(1000, k, 4), 4) == k }, "each boundary starts its own column")
        refuses("zero columns") { SnapFraction.spans(100, 0) }
        refuses("x out of range") { SnapFraction.columnAt(100, 100, 2) }
        refuses("span index") { SnapFraction.span(100, 2, 2) }
    }

    private fun plistHead(fixtures: File) {
        val bytes = File(fixtures, "bplist.bin").readBytes()
        val t = PlistHead.parse(bytes)
        ok(t.numObjects == 7L && t.topObject == 0L, "plistlib-written object count")
        ok(t.offsetIntSize == 1 && t.objectRefSize == 1, "small-table sizes")
        ok(t.offsetTableOffset == 51L, "offset table position")
        ok(PlistHead.isBinaryPlist(bytes), "detector positive")
        ok(!PlistHead.isBinaryPlist("<?xml version".toByteArray()), "detector negative on text plist")
        ok(t.sortVersion == 0, "plistlib writes sortVersion 0")
        ok(t.offsetTableOffset + t.numObjects * t.offsetIntSize <= (bytes.size - 32).toLong(), "table fits before trailer")
        ok(PlistHead.isBinaryPlist(byteArrayOf(0x62, 0x70, 0x6C, 0x69, 0x73, 0x74, 0x30, 0x30)), "detector on bare header")
        val badVer = bytes.copyOf(); badVer[7] = '1'.code.toByte()
        refuses("version 01 outside scope") { PlistHead.parse(badVer) }
        val badTable = bytes.copyOf(); badTable[bytes.size - 25] = 0x7F
        refuses("offset table past trailer") { PlistHead.parse(badTable) }
        val badMagic = bytes.copyOf(); badMagic[0] = 'x'.code.toByte()
        refuses("bad magic") { PlistHead.parse(badMagic) }
        val badTop = bytes.copyOf(); badTop[bytes.size - 9] = 9
        refuses("topObject beyond count") { PlistHead.parse(badTop) }
        val badSize = bytes.copyOf(); badSize[bytes.size - 26] = 0
        refuses("offsetIntSize 0") { PlistHead.parse(badSize) }
        refuses("truncated") { PlistHead.parse(bytes.copyOf(30)) }
    }

    private fun monogram() {
        ok(Monogram.of("Fusion Shell") == "FS", "two words")
        ok(Monogram.of("beam") == "B", "single word")
        ok(Monogram.of("  \u00E9mile zola ") == "\u00C9Z", "accented uppercase + trim")
        ok(Monogram.of("123 app") == "1A", "digit initial")
        ok(Monogram.of("the quick brown fox") == "TQ", "only first two tokens")
        ok(Monogram.of("(secret) notes") == "SN", "punctuation skipped inside token")
        ok(Monogram.of("\uD83D\uDE00 party") == "P", "emoji token contributes nothing, next token does")
        ok(Monogram.orNull("!!!") == null, "orNull fallback")
        ok(Monogram.of("a") == "A", "lowercase single letter")
        ok(Monogram.of("word !!!") == "W", "second token without letters contributes nothing")
        ok(Monogram.of("x\ty line") == "XY", "tab separates tokens")
        ok(Monogram.orNull("ok name") == "ON", "orNull mirrors of")
        refuses("no letters or digits") { Monogram.of("!!! ???") }
        refuses("whitespace only") { Monogram.of("   ") }
    }

    private fun bech32() {
        ok(Bech32.isValid("A12UEL5L"), "BIP-173 valid vector")
        ok(Bech32.isValid("a12uel5l"), "lowercase form")
        ok(!Bech32.isValid("A12UEL5M"), "last-char tamper fails")
        ok(!Bech32.isValid("A12uEL5L"), "mixed case refused")
        val m = Bech32.decode("osb1qpzry9x8gf2y2numr")
        ok(m.variant == Bech32.Variant.BECH32M && m.hrp == "osb", "bech32m constant distinguishes variant")
        val b = Bech32.decode("osb1qpzry9x8gf23krs7p")
        ok(b.variant == Bech32.Variant.BECH32, "bech32 constant")
        ok(m.data.contentEquals(IntArray(11) { it }) && b.data.contentEquals(m.data), "payload recovered either way")
        ok(Bech32.encode("osb", IntArray(11) { it }, Bech32.Variant.BECH32M) == "osb1qpzry9x8gf2y2numr", "encode reproduces")
        ok(Bech32.decode(Bech32.encode("test", intArrayOf(31, 0, 15), Bech32.Variant.BECH32)).data.contentEquals(intArrayOf(31, 0, 15)), "roundtrip")
        ok(Bech32.isValid("A12UEL5L".lowercase()) && Bech32.isValid("A12UEL5L".uppercase()), "either single case")
        ok(Bech32.decode("a12uel5l").hrp == "a" && Bech32.decode("a12uel5l").data.isEmpty(), "empty payload vector")
        ok(Bech32.CHARSET.length == 32 && Bech32.CHARSET.toSet().size == 32, "32 distinct data characters")
        ok(!Bech32.CHARSET.contains('b') && !Bech32.CHARSET.contains('i') && !Bech32.CHARSET.contains('o'), "confusable letters excluded")
        ok(Bech32.encode("osb", IntArray(11) { it }, Bech32.Variant.BECH32) == "osb1qpzry9x8gf23krs7p", "bech32 encode reproduces")
        refuses("no separator") { Bech32.decode("qpzry9x8") }
        refuses("short checksum") { Bech32.decode("a1qqq") }
        refuses("charset violation b") { Bech32.decode("osb1qpzrb9x8gf2y2numr") }
        refuses("data value 32") { Bech32.encode("osb", intArrayOf(32), Bech32.Variant.BECH32) }
        refuses("over 90 chars") { Bech32.encode("a".repeat(84), IntArray(1), Bech32.Variant.BECH32) }
    }

    private fun otpauth() {
        val t = OtpauthUri.parse("otpauth://totp/Fusion:alice%40example.org?secret=JBSWY3DPEHPK3PXP&issuer=Fusion&digits=6&period=30")
        ok(!t.hotp && t.issuer == "Fusion" && t.account == "alice@example.org", "label + params")
        ok(t.secretBase32 == "JBSWY3DPEHPK3PXP" && t.digits == 6 && t.period == 30, "core fields")
        ok(t.algorithm == "SHA1", "default algorithm")
        val h = OtpauthUri.parse("otpauth://hotp/acct?secret=JBSWY3DP&counter=0")
        ok(h.hotp && h.counter == 0L && h.period == null, "hotp with counter")
        val bare = OtpauthUri.parse("otpauth://totp/just-account?secret=ABCDEFGH")
        ok(bare.issuer == null && bare.account == "just-account", "no issuer anywhere")
        ok(OtpauthUri.parse("otpauth://totp/a?secret=ABCD====").secretBase32 == "ABCD====", "padded secret kept verbatim")
        ok(OtpauthUri.parse("otpauth://totp/a?secret=ABCD&algorithm=SHA512").algorithm == "SHA512", "sha512 accepted")
        val esc = OtpauthUri.parse("otpauth://totp/I%3Aname:acct?secret=ABCD")
        ok(esc.account == "acct" && esc.issuer == "I:name", "escaped colon stays inside the issuer half")
        ok(OtpauthUri.parse("otpauth://totp/A:b%3Ac?secret=ABCD").account == "b:c", "escaped colon inside the account half (attack on the split-then-decode patch)")
        ok(OtpauthUri.parse("otpauth://totp/Big%20Corp:bob?secret=ABCD").issuer == "Big Corp", "percent-decoded issuer from label")
        ok(OtpauthUri.parse("otpauth://totp/a?secret=ABCD").period == 30 && OtpauthUri.parse("otpauth://totp/a?secret=ABCD").digits == 6, "defaults")
        refuses("missing secret") { OtpauthUri.parse("otpauth://totp/a?digits=6") }
        refuses("bad base32 (1 is not in the alphabet)") { OtpauthUri.parse("otpauth://totp/a?secret=ABC1") }
        refuses("issuer mismatch") { OtpauthUri.parse("otpauth://totp/X:a?secret=ABCD&issuer=Y") }
        refuses("digits 9") { OtpauthUri.parse("otpauth://totp/a?secret=ABCD&digits=9") }
        refuses("period 5") { OtpauthUri.parse("otpauth://totp/a?secret=ABCD&period=5") }
        refuses("hotp without counter") { OtpauthUri.parse("otpauth://hotp/a?secret=ABCD") }
        refuses("unknown parameter") { OtpauthUri.parse("otpauth://totp/a?secret=ABCD&image=x") }
        refuses("duplicate parameter") { OtpauthUri.parse("otpauth://totp/a?secret=ABCD&secret=EFGH") }
        refuses("wrong scheme") { OtpauthUri.parse("https://totp/a?secret=ABCD") }
        refuses("sms type") { OtpauthUri.parse("otpauth://sms/a?secret=ABCD") }
    }

    private fun ipRedact() {
        ok(IpRedact.redactV4("203.0.113.77") == "203.0.113.0", "v4 keeps /24")
        ok(IpRedact.redact("203.0.113.77") == "203.0.113.0", "dispatch v4")
        ok(IpRedact.redactV6("2001:db8:85a3::8a2e:370:7334") == "2001:db8:85a3::", "v6 keeps /48")
        ok(IpRedact.redact("2001:db8::1") == "2001:db8:0::", "dispatch v6 with compressed middle")
        ok(IpRedact.parseV6("::1").toList() == listOf(0, 0, 0, 0, 0, 0, 0, 1), "loopback expansion")
        ok(IpRedact.parseV6("fe80::") .toList().take(2) == listOf(0xfe80, 0), "trailing compression")
        ok(IpRedact.isPrivateV4("10.1.2.3") && IpRedact.isPrivateV4("172.16.0.1") && IpRedact.isPrivateV4("192.168.9.9"), "RFC 1918 trio")
        ok(!IpRedact.isPrivateV4("172.32.0.1") && !IpRedact.isPrivateV4("8.8.8.8"), "non-private")
        ok(IpRedact.parseV4("0.0.0.0").toList() == listOf(0, 0, 0, 0), "all zeros valid")
        ok(IpRedact.parseV4("255.255.255.255").toList() == listOf(255, 255, 255, 255), "broadcast valid")
        ok(IpRedact.redactV6("::1") == "0:0:0::", "loopback redacts to the zero /48")
        ok(IpRedact.parseV6("2001:DB8::1")[1] == 0xDB8, "uppercase hex groups")
        ok(IpRedact.redact(IpRedact.redactV4("203.0.113.77")) == "203.0.113.0", "redaction idempotent")
        refuses("leading zero octal trap") { IpRedact.parseV4("192.168.01.1") }
        refuses("octet 256") { IpRedact.parseV4("1.2.3.256") }
        refuses("three octets") { IpRedact.parseV4("1.2.3") }
        refuses("double double-colon") { IpRedact.parseV6("1::2::3") }
        refuses("nine groups") { IpRedact.parseV6("1:2:3:4:5:6:7:8:9") }
        refuses("seven groups uncompressed") { IpRedact.parseV6("1:2:3:4:5:6:7") }
        refuses("embedded v4") { IpRedact.parseV6("::ffff:1.2.3.4") }
        refuses("zone index") { IpRedact.parseV6("fe80::1%eth0") }
        refuses("pointless compression") { IpRedact.parseV6("1:2:3:4:5:6:7::8") }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isNotEmpty()) { "usage: R40Checks <fixtures-dir>" }
        val fixtures = File(args[0])
        numFmt(); cellErr(); dashEducate(); objReplace(); footnoteMark()
        pdfRotate(); htmlEntity(); rfc2047(); sevenZ(fixtures); fnv1a()
        mrz(); safeName(); percentile(); taskCheckbox(); snapFraction()
        plistHead(fixtures); monogram(); bech32(); otpauth(); ipRedact()
        println("R40 PASS: $count production-kernel assertions across 20 kernels. Host JVM only; not Android/device evidence.")
    }
}

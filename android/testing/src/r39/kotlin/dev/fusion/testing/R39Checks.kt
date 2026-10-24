package dev.fusion.testing

import dev.fusion.ooxml.SerialDate
import dev.fusion.ooxml.ColLetters
import dev.fusion.ooxml.ColWidth
import dev.fusion.documents.SmartQuote
import dev.fusion.documents.HyphenSoft
import dev.fusion.editor.RomanNum
import dev.fusion.format.pdf.CropBox
import dev.fusion.format.image.IcoDir
import dev.fusion.browser.model.Punycode
import dev.fusion.hw.UsbDesc
import dev.fusion.media.MidiNote
import dev.fusion.storage.Ulid
import dev.fusion.security.JwtSplit
import dev.fusion.vault.IbanCheck
import dev.fusion.find.GlobMatch
import dev.fusion.privacy.MacAddr
import dev.fusion.backup.Rfc3339
import dev.fusion.alerts.QuietHours
import dev.fusion.capture.ExposureEv
import java.io.File

/**
 * R39 executed contracts. Host JVM evidence only — NOT Android, device,
 * service or acceptance evidence. Every `ok` is one counted assertion
 * against a production kernel compiled in isolation.
 */
object R39Checks {
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

    private fun serialDate() {
        ok(SerialDate.fromSerial1900(1) == SerialDate.Day(1900, 1, 1), "serial 1")
        ok(SerialDate.fromSerial1900(59) == SerialDate.Day(1900, 2, 28), "serial 59")
        val phantom = SerialDate.fromSerial1900(60)
        ok(phantom.phantom && phantom.month == 2 && phantom.day == 29, "serial 60 phantom Lotus day")
        ok(SerialDate.fromSerial1900(61) == SerialDate.Day(1900, 3, 1), "serial 61")
        ok(SerialDate.fromSerial1900(25569) == SerialDate.Day(1970, 1, 1), "unix epoch serial")
        ok(SerialDate.fromSerial1900(46318) == SerialDate.Day(2026, 10, 23), "today's serial")
        ok(SerialDate.fromSerial1900(SerialDate.MAX_1900) == SerialDate.Day(9999, 12, 31), "max serial")
        ok(SerialDate.toSerial1900(1900, 1, 1) == 1, "to serial 1")
        ok(SerialDate.toSerial1900(1900, 2, 29) == 60, "phantom maps to 60")
        ok(SerialDate.toSerial1900(2026, 10, 23) == 46318, "to today")
        ok(SerialDate.toSerial1900(1970, 1, 1) == 25569, "to unix epoch")
        ok(SerialDate.fromSerial1904(0) == SerialDate.Day(1904, 1, 1), "1904 serial 0")
        ok(SerialDate.toSerial1904(2026, 10, 23) == 44856, "1904 today")
        ok(SerialDate.toSerial1900(2026, 10, 23) - SerialDate.toSerial1904(2026, 10, 23) == SerialDate.SYSTEM_DELTA, "1462 bridge")
        ok(!SerialDate.isLeap(1900) && SerialDate.isLeap(2000) && SerialDate.isLeap(2024), "leap rules")
        var roundtrip = true
        for (s in intArrayOf(1, 59, 61, 100, 25569, 46318, 400000, SerialDate.MAX_1900)) {
            val d = SerialDate.fromSerial1900(s)
            if (SerialDate.toSerial1900(d.year, d.month, d.day) != s) roundtrip = false
        }
        ok(roundtrip, "serial roundtrip set")
        ok(SerialDate.fromSerial1900(36585) == SerialDate.Day(2000, 2, 29), "real leap day 2000")
        ok(SerialDate.toSerial1900(2000, 2, 29) == 36585, "to real leap day")
        var rt1904 = true
        for (s in intArrayOf(0, 100, 44856, SerialDate.MAX_1904)) {
            val d = SerialDate.fromSerial1904(s)
            if (SerialDate.toSerial1904(d.year, d.month, d.day) != s) rt1904 = false
        }
        ok(rt1904, "1904 roundtrip set")
        ok(SerialDate.daysInMonth(1900, 2) == 28 && SerialDate.daysInMonth(2000, 2) == 29, "February honesty")
        refuses("negative 1904 serial") { SerialDate.fromSerial1904(-1) }
        refuses("1903 in 1904 system") { SerialDate.toSerial1904(1903, 12, 31) }
        refuses("serial 0") { SerialDate.fromSerial1900(0) }
        refuses("serial beyond 9999") { SerialDate.fromSerial1900(SerialDate.MAX_1900 + 1) }
        refuses("pre-epoch year") { SerialDate.toSerial1900(1899, 12, 31) }
        refuses("1900-02-30") { SerialDate.toSerial1900(1900, 2, 30) }
    }

    private fun colLetters() {
        ok(ColLetters.toLetters(1) == "A", "1=A")
        ok(ColLetters.toLetters(26) == "Z", "26=Z")
        ok(ColLetters.toLetters(27) == "AA", "27=AA")
        ok(ColLetters.toLetters(52) == "AZ", "52=AZ")
        ok(ColLetters.toLetters(53) == "BA", "53=BA")
        ok(ColLetters.toLetters(702) == "ZZ", "702=ZZ")
        ok(ColLetters.toLetters(703) == "AAA", "703=AAA")
        ok(ColLetters.toLetters(16384) == "XFD", "16384=XFD")
        ok(ColLetters.fromLetters("XFD") == 16384, "XFD=16384")
        ok(ColLetters.fromLetters("xfd") == 16384, "lowercase canonicalised")
        var all = true
        for (i in 1..ColLetters.MAX_COLUMN) if (ColLetters.fromLetters(ColLetters.toLetters(i)) != i) all = false
        ok(all, "full 1..16384 bijection")
        ok(ColLetters.fromLetters("AZ") == 52 && ColLetters.fromLetters("BA") == 53, "borrow boundary parse")
        ok(ColLetters.toLetters(728) == "AAZ", "second-tier borrow")
        refuses("ZZZ beyond grid") { ColLetters.fromLetters("ZZZ") }
        refuses("column 0") { ColLetters.toLetters(0) }
        refuses("column 16385") { ColLetters.toLetters(16385) }
        refuses("XFE beyond grid") { ColLetters.fromLetters("XFE") }
        refuses("empty letters") { ColLetters.fromLetters("") }
        refuses("four letters") { ColLetters.fromLetters("ABCD") }
        refuses("digit in letters") { ColLetters.fromLetters("A1") }
    }

    private fun colWidth() {
        ok(ColWidth.contentPx(8.43) == 59, "default content px")
        ok(ColWidth.totalPx(8.43) == 64, "default total px (Excel's famous 64)")
        ok(ColWidth.fileWidth(8.43) == 9.140625, "file width attribute")
        ok(ColWidth.charsFromTotalPx(64) == 8.43, "px back to chars")
        ok(ColWidth.totalPx(10.0) == 75, "10 chars at MDW 7")
        ok(ColWidth.contentPx(1.0) == 7, "one char = one MDW")
        ok(ColWidth.charsFromTotalPx(ColWidth.totalPx(15.71)) == 15.71, "roundtrip 15.71")
        ok(ColWidth.fileWidth(1.0) == kotlin.math.floor(((1.0 * 7 + 5) / 7) * 256.0) / 256.0, "file width formula shape")
        ok(ColWidth.contentPx(8.43, 8) == 67, "MDW 8 changes content px")
        ok(ColWidth.totalPx(255.0) == 1790, "widest legal column")
        ok(ColWidth.charsFromTotalPx(12) == 1.0, "narrow column back to one char")
        var monotone = true
        var lastPx = 0
        for (w in intArrayOf(1, 2, 5, 8, 20, 50, 100, 255)) {
            val px = ColWidth.totalPx(w.toDouble())
            if (px <= lastPx) monotone = false
            lastPx = px
        }
        ok(monotone, "pixel width strictly grows with chars")
        refuses("fileWidth below one char") { ColWidth.fileWidth(0.9) }
        refuses("below one char") { ColWidth.contentPx(0.5) }
        refuses("beyond 255 chars") { ColWidth.contentPx(255.5) }
        refuses("zero MDW") { ColWidth.contentPx(8.43, 0) }
        refuses("px below padding") { ColWidth.charsFromTotalPx(5) }
    }

    private fun smartQuote() {
        ok(SmartQuote.educate("\"Hello,\" she said.") == "\u201CHello,\u201D she said.", "double pair")
        ok(SmartQuote.educate("can't") == "can\u2019t", "inner apostrophe")
        ok(SmartQuote.educate("the '90s") == "the \u201990s", "decade elision")
        ok(SmartQuote.educate("'quoted'") == "\u2018quoted\u2019", "single pair")
        ok(SmartQuote.educate("\"He said 'go' now\"") == "\u201CHe said \u2018go\u2019 now\u201D", "nested quotes")
        ok(SmartQuote.educate("('aside')") == "(\u2018aside\u2019)", "opens after bracket")
        ok(SmartQuote.educate("rock \u2019n\u2019 roll") == "rock \u2019n\u2019 roll", "curly passes through")
        ok(SmartQuote.educate("a\"b") == "a\u201Db", "closes mid-word")
        ok(SmartQuote.educate("'Tis") == "\u2018Tis", "declared elision narrowing (opens)")
        ok(SmartQuote.educate("") == "", "empty")
        ok(SmartQuote.unbalancedDoubles("\"a\" \"b") == 1, "unbalance count")
        ok(SmartQuote.unbalancedDoubles("\"a\" \"b\"") == 0, "balanced")
        ok(SmartQuote.educate("\u2014'em") == "\u2014\u2018em", "opens after em dash")
        ok(SmartQuote.educate("it's a test, isn't it") == "it\u2019s a test, isn\u2019t it", "two apostrophes")
        ok(SmartQuote.educate("\"\"") == "\u201C\u201D", "empty double pair")
        ok(SmartQuote.unbalancedDoubles("\u201Ccurly\u201D already") == 0, "curly input stays balanced")
    }

    private fun hyphenSoft() {
        val word = "hy\u00ADphen\u00ADa\u00ADtion"
        ok(HyphenSoft.strip(word) == "hyphenation", "strip")
        ok(HyphenSoft.candidates(word) == listOf(2, 6, 7), "candidates in stripped coordinates")
        ok(HyphenSoft.segments(word) == listOf("hy", "phen", "a", "tion"), "segments")
        ok(HyphenSoft.display(word, 6) == ("hyphen-" to "ation"), "display split")
        ok(HyphenSoft.candidates("\u00ADword") == emptyList<Int>(), "leading SHY dropped")
        ok(HyphenSoft.candidates("word\u00AD") == emptyList<Int>(), "trailing SHY dropped")
        ok(HyphenSoft.candidates("a\u00AD\u00ADb") == listOf(1), "SHY run collapses")
        ok(HyphenSoft.strip("plain") == "plain", "no-op strip")
        ok(HyphenSoft.segments("plain") == listOf("plain"), "no-op segments")
        ok(HyphenSoft.candidates("") == emptyList<Int>(), "empty text")
        ok(HyphenSoft.display(word, 2) == ("hy-" to "phenation"), "first candidate split")
        ok(HyphenSoft.segments(word).joinToString("") == HyphenSoft.strip(word), "segments rejoin to stripped text")
        refuses("non-candidate break") { HyphenSoft.display(word, 3) }
        refuses("break in plain text") { HyphenSoft.display("plain", 2) }
    }

    private fun romanNum() {
        ok(RomanNum.to(2026) == "MMXXVI", "2026")
        ok(RomanNum.to(1994) == "MCMXCIV", "1994 subtractive")
        ok(RomanNum.to(3999) == "MMMCMXCIX", "3999")
        ok(RomanNum.to(1) == "I" && RomanNum.to(4) == "IV" && RomanNum.to(9) == "IX", "small values")
        ok(RomanNum.to(40) == "XL" && RomanNum.to(90) == "XC" && RomanNum.to(400) == "CD" && RomanNum.to(900) == "CM", "all subtractive pairs")
        ok(RomanNum.from("MMXXVI") == 2026, "parse 2026")
        ok(RomanNum.from("MCMXCIV") == 1994, "parse 1994")
        ok(RomanNum.toLower(14) == "xiv", "lowercase presentation")
        var all = true
        for (n in 1..3999) if (RomanNum.from(RomanNum.to(n)) != n) all = false
        ok(all, "full 1..3999 bijection")
        ok(RomanNum.to(1666) == "MDCLXVI", "all simple glyphs descending")
        ok(RomanNum.from("MDCLXVI") == 1666, "parse 1666")
        ok(RomanNum.to(3888) == "MMMDCCCLXXXVIII", "longest canonical numeral (15 glyphs)")
        refuses("doubled subtractive IXIX") { RomanNum.from("IXIX") }
        refuses("XD illegal subtraction") { RomanNum.from("XD") }
        refuses("additive IIII") { RomanNum.from("IIII") }
        refuses("illegal VX") { RomanNum.from("VX") }
        refuses("illegal IM") { RomanNum.from("IM") }
        refuses("doubled V") { RomanNum.from("VV") }
        refuses("doubled CM") { RomanNum.from("CMCM") }
        refuses("lowercase input") { RomanNum.from("xiv") }
        refuses("empty") { RomanNum.from("") }
        refuses("zero") { RomanNum.to(0) }
        refuses("4000") { RomanNum.to(4000) }
    }

    private fun cropBox() {
        val m = CropBox.LETTER
        ok(CropBox.effective(m, null) == m, "absent crop defaults to media")
        val crop = CropBox.normalize(612.0, 792.0, 36.0, 36.0)
        ok(crop == CropBox.Rect(36.0, 36.0, 612.0, 792.0), "corner order normalized")
        ok(CropBox.effective(m, crop) == crop, "inside crop kept")
        val over = CropBox.normalize(-50.0, -50.0, 700.0, 900.0)
        ok(CropBox.effective(m, over) == m, "oversized crop clipped to media")
        val partial = CropBox.effective(m, CropBox.normalize(300.0, 400.0, 900.0, 1000.0))
        ok(partial == CropBox.Rect(300.0, 400.0, 612.0, 792.0), "partial overlap intersects")
        ok(near(CropBox.A4.width, 595.276, 1e-9) && near(CropBox.A4.height, 841.89, 1e-9), "A4 points")
        ok(near(m.area, 612.0 * 792.0, 1e-9), "letter area")
        ok(CropBox.effective(m, m) == m, "crop equal to media is identity")
        ok(CropBox.intersect(crop, over) == CropBox.intersect(over, crop), "intersection commutes")
        ok(CropBox.normalize(36.0, 36.0, 612.0, 792.0) == crop, "already-ordered corners unchanged")
        refuses("degenerate rect") { CropBox.normalize(10.0, 10.0, 10.0, 50.0) }
        refuses("disjoint crop") { CropBox.effective(m, CropBox.normalize(700.0, 800.0, 900.0, 1000.0)) }
    }

    private fun icoDir(fixtures: File) {
        val bytes = File(fixtures, "ico.bin").readBytes()
        val dir = IcoDir.parse(bytes)
        ok(!dir.cursor && dir.entries.size == 2, "icon with two entries")
        val bmp = dir.entries[0]
        ok(bmp.payload == IcoDir.Payload.BMP && bmp.width == 16 && bmp.height == 16, "BMP entry")
        ok(bmp.bitCount == 32 && bmp.planes == 1, "BMP entry fields")
        val png = dir.entries[1]
        ok(png.payload == IcoDir.Payload.PNG && png.width == 256 && png.height == 256, "PNG entry with 0=>256")
        ok(IcoDir.best(dir, 32) === png, "best for 32 is the 256 PNG")
        ok(IcoDir.best(dir, 16) === bmp, "best for 16 is the exact BMP")
        ok(IcoDir.best(dir, 300) === png, "oversize request falls back to largest")
        ok(bmp.bytes == 40 && bmp.offset == 38, "BMP payload accounting")
        ok(png.bytes == 33 && png.offset == 38 + 40, "PNG payload accounting")
        ok(IcoDir.best(dir, 256) === png, "exact 256 request")
        refuses("reserved field") { IcoDir.parse(byteArrayOf(1, 0, 1, 0, 1, 0)) }
        refuses("type 3") { IcoDir.parse(byteArrayOf(0, 0, 3, 0, 1, 0)) }
        refuses("zero count") { IcoDir.parse(byteArrayOf(0, 0, 1, 0, 0, 0)) }
        refuses("truncated entries") { IcoDir.parse(byteArrayOf(0, 0, 1, 0, 2, 0, 0)) }
        val lying = bytes.copyOf(); lying[6] = 32  // directory now claims 32px for the 16px BMP; harmless (BMP dims unchecked)
        ok(IcoDir.parse(lying).entries[0].width == 32, "BMP dims are directory-trusted (declared)")
        val lyingPng = bytes.copyOf(); lyingPng[6 + 16] = 64
        refuses("PNG dimension disagreement") { IcoDir.parse(lyingPng) }
        refuses("offset past EOF") {
            val cut = bytes.copyOf(bytes.size - 4); IcoDir.parse(cut)
        }
    }

    private fun punycode() {
        ok(Punycode.encode("b\u00FCcher") == "bcher-kva", "bücher")
        ok(Punycode.decode("bcher-kva") == "b\u00FCcher", "decode bücher")
        ok(Punycode.encode("m\u00FCnchen") == "mnchen-3ya", "münchen")
        ok(Punycode.encode("\u00FC") == "tda", "single non-ASCII, no hyphen")
        ok(Punycode.decode("tda") == "\u00FC", "decode tda")
        ok(Punycode.encode("Maji\u3067Koi\u3059\u308B5\u79D2\u524D") == "MajiKoi5-783gue6qz075azm5e", "RFC mixed-case sample L")
        ok(Punycode.decode("MajiKoi5-783gue6qz075azm5e") == "Maji\u3067Koi\u3059\u308B5\u79D2\u524D", "decode sample L")
        ok(Punycode.decode("bcher-KVA") == "b\u00FCcher", "case-insensitive extended digits (literal portion keeps case per RFC)")
        ok(Punycode.toAscii("b\u00FCcher") == "xn--bcher-kva", "ACE bracketing")
        ok(Punycode.toAscii("plain") == "plain", "ASCII label untouched")
        ok(Punycode.toUnicode("xn--bcher-kva") == "b\u00FCcher", "ACE unwrap")
        ok(Punycode.toUnicode("plain") == "plain", "non-ACE unwrap")
        val round = "r\u00E9sum\u00E9-caf\u00E9"
        ok(Punycode.decode(Punycode.encode(round)) == round, "roundtrip")
        ok(Punycode.encode("London") == "London-", "pure-basic encode keeps RFC delimiter")
        ok(Punycode.decode("London-") == "London", "pure-basic decode strips delimiter")
        refuses("bad digit") { Punycode.decode("abc\u00FF") }
        refuses("non-basic literal portion") { Punycode.decode("b\u00FCcher-kva") }
        refuses("empty label") { Punycode.toAscii("") }
        refuses("spoofed xn-- on ASCII") { Punycode.toAscii("xn--fake") }
        refuses("surrogate decode") { Punycode.decode("999999a") }
    }

    private fun usbDesc(fixtures: File) {
        val d = UsbDesc.parse(File(fixtures, "usbdev.bin").readBytes())
        ok(d.usbVersion == "2.0", "bcdUSB 0x0200")
        ok(d.idVendor == 0x1D6B && d.idProduct == 0x0104, "vid/pid")
        ok(UsbDesc.vidPid(d) == "1d6b:0104", "lsusb-style id")
        ok(d.deviceClass == 0 && d.classAtInterfaceLevel, "class deferred to interfaces")
        ok(d.maxPacketSize0 == 64, "EP0 packet size")
        ok(d.deviceVersion == "1.21", "bcdDevice 0x0121")
        ok(d.iManufacturer == 1 && d.iProduct == 2 && d.iSerialNumber == 3, "string indexes")
        ok(d.numConfigurations == 1, "one configuration")
        val raw = File(fixtures, "usbdev.bin").readBytes()
        refuses("short buffer") { UsbDesc.parse(raw.copyOf(17)) }
        val badLen = raw.copyOf(); badLen[0] = 17
        refuses("bLength") { UsbDesc.parse(badLen) }
        val badType = raw.copyOf(); badType[1] = 2
        refuses("bDescriptorType") { UsbDesc.parse(badType) }
        val badMps = raw.copyOf(); badMps[7] = 63
        refuses("invalid EP0 size") { UsbDesc.parse(badMps) }
        val v21 = raw.copyOf(); v21[2] = 0x10; v21[3] = 0x02
        ok(UsbDesc.parse(v21).usbVersion == "2.1", "bcdUSB 0x0210")
        val v11 = raw.copyOf(); v11[2] = 0x10; v11[3] = 0x01
        ok(UsbDesc.parse(v11).usbVersion == "1.1", "bcdUSB 0x0110")
        val usb3 = raw.copyOf(); usb3[3] = 3; usb3[7] = 9
        ok(UsbDesc.parse(usb3).usbVersion == "3.0", "USB3 EP0 exponent accepted")
        val usb3bad = raw.copyOf(); usb3bad[3] = 3
        refuses("USB3 needs EP0=9") { UsbDesc.parse(usb3bad) }
        val zeroCfg = raw.copyOf(); zeroCfg[17] = 0
        refuses("zero configurations") { UsbDesc.parse(zeroCfg) }
    }

    private fun midiNote() {
        ok(MidiNote.name(69) == "A4", "A4=69")
        ok(MidiNote.name(60) == "C4", "middle C convention")
        ok(MidiNote.name(0) == "C-1" && MidiNote.name(127) == "G9", "range endpoints")
        ok(MidiNote.parse("A4") == 69 && MidiNote.parse("C4") == 60, "parse names")
        ok(MidiNote.parse("C#4") == 61 && MidiNote.parse("Db4") == 61, "enharmonic parse")
        ok(MidiNote.parse("Bb3") == 58 && MidiNote.parse("B3") == 59, "flat vs natural B")
        ok(MidiNote.parse("c-1") == 0, "lowercase + negative octave")
        ok(MidiNote.frequency(69) == 440.0, "exact A440")
        ok(near(MidiNote.frequency(60), 261.6255653005986, 1e-9), "middle C frequency")
        ok(near(MidiNote.frequency(81), 880.0, 1e-9), "octave doubles")
        val (n, cents) = MidiNote.fromFrequency(442.0)
        ok(n == 69 && cents > 0 && cents < 10, "442 Hz is sharp A4 by ~8 cents")
        ok(MidiNote.fromFrequency(440.0).first == 69, "440 back to 69")
        ok(MidiNote.name(21) == "A0", "piano low A")
        ok(MidiNote.parse("G9") == 127, "top of MIDI range")
        ok(near(MidiNote.frequency(127), 12543.853951415975, 1e-6), "G9 frequency")
        refuses("note 128") { MidiNote.name(128) }
        refuses("negative note") { MidiNote.frequency(-1) }
        refuses("H pitch") { MidiNote.parse("H4") }
        refuses("octave out of range") { MidiNote.parse("C10") }
        refuses("zero frequency") { MidiNote.fromFrequency(0.0) }
    }

    private fun ulid() {
        val sample = "01ARZ3NDEKTSV4RRFFQ69G5FAV"
        ok(Ulid.timestampMs(sample) == 1469922850259L, "spec README sample timestamp")
        ok(Ulid.timestampMs(sample.lowercase()) == 1469922850259L, "case-insensitive decode")
        ok(Ulid.timestampMs(Ulid.MAX_ULID) == Ulid.MAX_TIMESTAMP, "max ULID timestamp 2^48-1")
        val p = Ulid.decode(sample)
        ok(Ulid.encode(p.timestampMs, p.random) == sample, "decode/encode roundtrip")
        ok(Ulid.next("01BX5ZZKBKACTAV9WEVGEMMVRZ") == "01BX5ZZKBKACTAV9WEVGEMMVS0", "spec monotonic successor")
        val ceiling = Ulid.encode(123L, ByteArray(10) { 0xFF.toByte() })
        refuses("monotonic overflow must fail per spec") { Ulid.next(ceiling) }
        ok(Ulid.isValid(sample) && !Ulid.isValid("not-a-ulid"), "isValid")
        ok(Ulid.encode(0L, ByteArray(10)) == "0".repeat(26), "zero ULID")
        var sorted = true
        var prev = Ulid.encode(1000L, ByteArray(10))
        for (ts in longArrayOf(2000, 30000, 400000, 1469922850259L)) {
            val cur = Ulid.encode(ts, ByteArray(10))
            if (cur <= prev) sorted = false
            prev = cur
        }
        ok(sorted, "lexicographic order follows time")
        val succ = Ulid.next("01BX5ZZKBKACTAV9WEVGEMMVRZ")
        ok(succ.substring(0, 10) == "01BX5ZZKBK", "monotonic successor keeps timestamp chars")
        var tsIdentity = true
        for (ts in longArrayOf(0, 1, 1000, 1469922850259L, Ulid.MAX_TIMESTAMP)) {
            if (Ulid.timestampMs(Ulid.encode(ts, ByteArray(10))) != ts) tsIdentity = false
        }
        ok(tsIdentity, "timestamp identity across range")
        refuses("length 25") { Ulid.decode(sample.dropLast(1)) }
        refuses("8 prefix overflow") { Ulid.decode("8" + "Z".repeat(25)) }
        refuses("letter I") { Ulid.decode("I" + sample.drop(1)) }
        refuses("letter O") { Ulid.decode("O" + sample.drop(1)) }
        refuses("letter U refused") { Ulid.decode(sample.dropLast(1) + "U") }
        refuses("timestamp too large") { Ulid.encode(1L shl 48, ByteArray(10)) }
        refuses("short random") { Ulid.encode(0L, ByteArray(9)) }
    }

    private fun jwtSplit() {
        val tok = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJvc2IiLCJuIjo3fQ.AQIDBA"
        val j = JwtSplit.split(tok)
        ok(j.alg == "HS256" && j.typ == "JWT", "header fields")
        ok(!j.signatureChecked, "signatureChecked is constitutionally false")
        ok(!j.algNone, "HS256 is not none")
        ok(String(j.payload, Charsets.UTF_8) == "{\"sub\":\"osb\",\"n\":7}", "payload bytes")
        ok(j.signature.contentEquals(byteArrayOf(1, 2, 3, 4)), "signature bytes pass through unverified")
        val none = JwtSplit.split("eyJhbGciOiJub25lIn0.eyJzdWIiOiJvc2IiLCJuIjo3fQ.")
        ok(none.algNone && none.signature.isEmpty(), "alg none surfaced for refusal")
        ok(none.typ == null, "absent typ is null")
        refuses("empty payload segment") { JwtSplit.split("eyJhbGciOiJub25lIn0..") }
        refuses("two segments") { JwtSplit.split("a.b") }
        refuses("four segments (JWE out of scope)") { JwtSplit.split("a.b.c.d") }
        refuses("padding char") { JwtSplit.split("eyJhbGciOiJub25lIn0=.e30.") }
        refuses("plus is base64, not base64url") { JwtSplit.b64urlDecode("a+b") }
        refuses("empty sig with signed alg") { JwtSplit.split(tok.substringBeforeLast('.') + ".") }
        refuses("header without alg") {
            JwtSplit.split("eyJ0eXAiOiJKV1QifQ.e30.AQIDBA")
        }
        refuses("escaped header refused") {
            // header is {"alg":"HS\u0041256","typ":"JWT"} — escapes must refuse, not mis-parse
            JwtSplit.split("eyJhbGciOiJIU1x1MDA0MTI1NiIsInR5cCI6IkpXVCJ9.e30.AQIDBA")
        }
        ok(JwtSplit.b64urlDecode("AQIDBA").contentEquals(byteArrayOf(1, 2, 3, 4)), "b64url decode")
        refuses("impossible length") { JwtSplit.b64urlDecode("abcde") }
        refuses("nonzero trailing bits") { JwtSplit.b64urlDecode("ab") }
    }

    private fun ibanCheck() {
        ok(IbanCheck.validate("GB82 WEST 1234 5698 7654 32") == "GB82WEST12345698765432", "GB registry example")
        ok(IbanCheck.isValid("DE89370400440532013000"), "DE example")
        ok(IbanCheck.isValid("NL91ABNA0417164300"), "NL example")
        ok(IbanCheck.isValid("NO9386011117947"), "NO shortest in subset")
        ok(!IbanCheck.isValid("GB82WEST12345698765433"), "single digit flip caught")
        ok(!IbanCheck.isValid("GB82WEST1234569876543"), "length off by one")
        ok(IbanCheck.checkDigits("GB", "WEST12345698765432") == "82", "check digit generation")
        ok(IbanCheck.checkDigits("DE", "370400440532013000") == "89", "DE check digits")
        ok(IbanCheck.paper("GB82WEST12345698765432") == "GB82 WEST 1234 5698 7654 32", "paper grouping")
        ok(IbanCheck.electronic("gb82 west 1234 5698 7654 32") == "GB82WEST12345698765432", "electronic normalization")
        ok(IbanCheck.mod97("GB82WEST12345698765432") == 1, "mod97 == 1")
        ok(IbanCheck.isValid("IT60X0542811101000000123456"), "IT longest in subset")
        ok(IbanCheck.validate(IbanCheck.paper("NL91ABNA0417164300")) == "NL91ABNA0417164300", "paper form revalidates")
        refuses("unknown country refused not guessed") { IbanCheck.validate("XX82WEST12345698765432") }
        refuses("check digits 00") { IbanCheck.validate("GB00WEST12345698765432") }
        refuses("check digits 99") { IbanCheck.validate("GB99WEST12345698765432") }
        refuses("non-alphanumeric") { IbanCheck.validate("GB82WEST1234569876543!") }
        refuses("too short") { IbanCheck.validate("GB8") }
    }

    private fun globMatch() {
        ok(GlobMatch.matches("*.txt", "notes.txt"), "suffix star")
        ok(!GlobMatch.matches("*.txt", "notes.txt.bak"), "anchored end")
        ok(GlobMatch.matches("a*b*c", "aXbYbZc"), "double star backtrack")
        ok(GlobMatch.matches("?at", "cat") && !GlobMatch.matches("?at", "at"), "question mark")
        ok(GlobMatch.matches("[abc]x", "bx") && !GlobMatch.matches("[abc]x", "dx"), "set")
        ok(GlobMatch.matches("[!abc]x", "dx") && !GlobMatch.matches("[!abc]x", "ax"), "negated set")
        ok(GlobMatch.matches("[a-c]x", "bx") && !GlobMatch.matches("[a-c]x", "dx"), "range")
        ok(GlobMatch.matches("[]]", "]"), "leading ] is literal in set")
        ok(GlobMatch.matches("a[", "a["), "unterminated bracket is literal")
        ok(GlobMatch.matches("[*]star", "*star"), "set escapes star")
        ok(GlobMatch.matches("*", "") && GlobMatch.matches("*", "anything"), "lone star")
        ok(!GlobMatch.matches("", "x") && GlobMatch.matches("", ""), "empty pattern")
        ok(GlobMatch.matches("[a-]x", "-x") && GlobMatch.matches("[a-]x", "ax"), "trailing dash literal")
        ok(!GlobMatch.matches("A*", "a"), "case-sensitive")
        ok(GlobMatch.matches("r39-*.??r", "r39-beam.jar"), "composite pattern")
        ok(!GlobMatch.matches("?", ""), "? needs one char")
        ok(GlobMatch.matches("[!a-c]x", "dx") && !GlobMatch.matches("[!a-c]x", "bx"), "negated range")
        ok(GlobMatch.matches("a*a*a*b", "aaaaaaab"), "backtracking worst case")
        ok(!GlobMatch.matches("a*a*a*b", "aaaaaaac"), "backtracking rejects")
        refuses("reversed range") { GlobMatch.matches("[z-a]", "m") }
    }

    private fun macAddr() {
        val rand = MacAddr.parse("DA:A1:19:12:34:56")
        ok(rand.locallyAdministered && !rand.multicast && rand.localUnicast, "Android-randomized shape")
        ok(MacAddr.oui(rand) == null, "no OUI claim for local addresses")
        val dock = MacAddr.parse("00:25:96:12:34:56")
        ok(!dock.locallyAdministered && MacAddr.oui(dock) == "00:25:96", "universal OUI")
        ok(MacAddr.canonical(dock) == "00:25:96:12:34:56", "canonical lower colon")
        ok(MacAddr.canonical(MacAddr.parse("00-25-96-AB-CD-EF")) == "00:25:96:ab:cd:ef", "hyphen form")
        ok(MacAddr.canonical(MacAddr.parse("0025.96AB.CDEF")) == "00:25:96:ab:cd:ef", "Cisco form")
        val bcast = MacAddr.parse("FF:FF:FF:FF:FF:FF")
        ok(bcast.broadcast && bcast.multicast, "broadcast is group")
        val mcast = MacAddr.parse("01:00:5E:00:00:FB")
        ok(mcast.multicast && !mcast.locallyAdministered && !mcast.localUnicast, "IPv4 multicast block")
        ok(MacAddr.redact(dock) == "00:25:96:xx:xx:xx", "redaction keeps OUI half")
        ok(MacAddr.canonical(rand) == "da:a1:19:12:34:56", "canonical of randomized shape")
        ok(MacAddr.redact(rand) == "da:a1:19:xx:xx:xx", "redaction works for local too")
        refuses("five octets") { MacAddr.parse("00:25:96:12:34") }
        refuses("bad hex") { MacAddr.parse("00:25:96:12:34:GG") }
        refuses("weird shape") { MacAddr.parse("0025961234 56") }
        refuses("cisco short group") { MacAddr.parse("025.96AB.CDEF") }
    }

    private fun rfc3339() {
        val a = Rfc3339.parse("1985-04-12T23:20:50.52Z")
        ok(a.year == 1985 && a.nanos == 520_000_000 && a.offsetMinutes == 0, "RFC example 1")
        ok(Rfc3339.epochSeconds(a) == 482196050L, "epoch of example 1")
        val b = Rfc3339.parse("1996-12-19T16:39:57-08:00")
        ok(b.offsetMinutes == -480 && !b.unknownLocalOffset, "RFC example 2 offset")
        ok(Rfc3339.epochSeconds(b) == 851042397L, "epoch of example 2")
        ok(Rfc3339.epochSeconds(Rfc3339.parse("2026-10-23T12:00:00+00:00")) == 1792756800L, "today noon UTC")
        val unk = Rfc3339.parse("2026-10-23T12:00:00-00:00")
        ok(unk.unknownLocalOffset && unk.offsetMinutes == 0, "-00:00 unknown-offset flag")
        ok(Rfc3339.canonical(unk) == "2026-10-23T12:00:00-00:00", "unknown offset survives canonicalization")
        ok(Rfc3339.canonical(a) == "1985-04-12T23:20:50.52Z", "canonical trims fraction zeros")
        ok(Rfc3339.parse("2026-10-23t12:00:00z").hour == 12, "lowercase t/z accepted")
        ok(Rfc3339.parse("2024-02-29T00:00:00Z").day == 29, "real leap day accepted")
        ok(Rfc3339.epochSeconds(Rfc3339.parse("1970-01-01T00:00:00Z")) == 0L, "epoch zero")
        val india = Rfc3339.parse("2026-10-23T17:30:00+05:30")
        ok(india.offsetMinutes == 330, "+05:30 offset minutes")
        ok(Rfc3339.epochSeconds(india) == 1792756800L, "IST noon-UTC equivalence")
        ok(Rfc3339.canonical(india) == "2026-10-23T17:30:00+05:30", "canonical keeps +05:30")
        ok(Rfc3339.parse("2026-10-23T12:00:00.123456789Z").nanos == 123456789, "nine fraction digits")
        refuses("2026 is no leap year") { Rfc3339.parse("2026-02-29T00:00:00Z") }
        refuses("hour 24") { Rfc3339.parse("2026-10-23T24:00:00Z") }
        refuses("leap second policy") { Rfc3339.parse("2016-12-31T23:59:60Z") }
        refuses("naive timestamp") { Rfc3339.parse("2026-10-23T12:00:00") }
        refuses("bad offset shape") { Rfc3339.parse("2026-10-23T12:00:00+0000") }
        refuses("trailing garbage") { Rfc3339.parse("2026-10-23T12:00:00Zx") }
        refuses("ten fraction digits") { Rfc3339.parse("2026-10-23T12:00:00.0123456789Z") }
    }

    private fun quietHours() {
        val night = QuietHours.Window(22 * 60, 7 * 60)
        ok(night.wraps && night.minutes == 9 * 60, "22:00->07:00 wraps, 9h")
        ok(QuietHours.contains(night, 23 * 60) && QuietHours.contains(night, 3 * 60), "inside both halves")
        ok(!QuietHours.contains(night, 12 * 60), "noon outside")
        ok(QuietHours.contains(night, 22 * 60) && !QuietHours.contains(night, 7 * 60), "half-open ends")
        val lunch = QuietHours.Window(12 * 60, 13 * 60)
        ok(!lunch.wraps && lunch.minutes == 60, "plain window")
        ok(QuietHours.untilBoundary(night, 21 * 60) == 60, "one hour to start")
        ok(QuietHours.untilBoundary(night, 23 * 60) == 8 * 60, "eight hours to end")
        ok(QuietHours.untilBoundary(lunch, 13 * 60) == 23 * 60, "just closed: full cycle minus window")
        ok(QuietHours.overlapMinutes(night, QuietHours.Window(6 * 60, 8 * 60)) == 60, "morning tail overlap")
        ok(QuietHours.overlapMinutes(night, lunch) == 0, "disjoint")
        ok(QuietHours.overlapMinutes(night, night) == night.minutes, "self overlap = duration")
        val both = QuietHours.overlapMinutes(night, QuietHours.Window(23 * 60, 1 * 60))
        ok(both == 120, "wrapping-vs-wrapping overlap")
        ok(QuietHours.Window(0, 1439).minutes == 1439, "near-full-day window")
        refuses("degenerate window") { QuietHours.Window(300, 300) }
        refuses("minute 1440") { QuietHours.Window(0, 1440) }
        refuses("query out of range") { QuietHours.contains(night, 1440) }
    }

    private fun exposureEv() {
        ok(near(ExposureEv.ev(16.0, 1.0 / 125.0), 15.0, 0.05), "sunny 16 is EV15")
        ok(near(ExposureEv.ev(1.0, 1.0), 0.0, 1e-12), "f/1 at 1s is EV0")
        ok(near(ExposureEv.ev(1.4142135623730951, 1.0), 1.0, 1e-9), "sqrt2 aperture adds one stop")
        ok(ExposureEv.equivalent(2.0, 1.0 / 100.0, 2.8284271247461903, 1.0 / 50.0), "exact reciprocity pair")
        ok(!ExposureEv.equivalent(2.0, 1.0 / 100.0, 2.0, 1.0 / 50.0), "one stop apart is not equivalent")
        ok(near(ExposureEv.ev100(16.0, 1.0 / 125.0, 400.0), 13.0, 0.05), "ISO 400 reading maps to EV100-2")
        ok(near(ExposureEv.shutterFor(15.0, 16.0), 1.0 / 128.0, 1e-9), "shutter solver")
        ok(near(ExposureEv.apertureFor(0.0, 1.0), 1.0, 1e-12), "aperture solver identity")
        val n = ExposureEv.apertureFor(15.0, 1.0 / 125.0)
        ok(near(ExposureEv.ev(n, 1.0 / 125.0), 15.0, 1e-9), "solver closes the loop")
        ok(near(ExposureEv.ev100(16.0, 1.0 / 125.0, 50.0), 15.965784284662087, 1e-9), "ISO 50 reading gains a stop on the 100 scale")
        ok(near(ExposureEv.shutterFor(0.0, 1.0), 1.0, 1e-12), "EV0 f/1 is one second")
        refuses("zero aperture") { ExposureEv.ev(0.0, 1.0) }
        refuses("zero shutter") { ExposureEv.ev(2.0, 0.0) }
        refuses("zero iso") { ExposureEv.ev100(2.0, 0.01, 0.0) }
        refuses("negative tolerance") { ExposureEv.equivalent(2.0, 0.01, 2.0, 0.01, -0.1) }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isNotEmpty()) { "usage: R39Checks <fixtures-dir>" }
        val fixtures = File(args[0])
        serialDate(); colLetters(); colWidth()
        smartQuote(); hyphenSoft(); romanNum()
        cropBox(); icoDir(fixtures); punycode()
        usbDesc(fixtures); midiNote(); ulid()
        jwtSplit(); ibanCheck(); globMatch()
        macAddr(); rfc3339(); quietHours(); exposureEv()
        println("R39 PASS: $count production-kernel assertions across 19 kernels. Host JVM only; not Android/device evidence.")
    }
}

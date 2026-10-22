// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.calc.StatSummary
import dev.fusion.files.MimeGuess
import dev.fusion.notes.OutlineParse
import dev.fusion.editor.BracketMatch
import dev.fusion.cast.dlna.SoapFault
import dev.fusion.commands.PipePlan
import dev.fusion.format.image.JpegSeg
import dev.fusion.archive.ZlibHead
import dev.fusion.continuity.VTodoParse
import dev.fusion.privacy.Pseudonym
import java.io.File

/** R34 host consumer: behavioral contracts over the 10 R34 kernels. Host JVM only. */
object R34Checks {
    var n = 0
    fun ok(c: Boolean, label: String) {
        n++
        if (!c) throw AssertionError("r34 check $n FAILED: $label")
    }
    fun fails(label: String, block: () -> Unit) {
        n++
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("r34 check $n FAILED (no refusal): $label")
    }
    fun close(a: Double, b: Double, eps: Double, label: String) {
        ok(kotlin.math.abs(a - b) <= eps, "$label ($a vs $b)")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val fx = File(args[0])
        stats(); mime(); outline(); brackets(); soap(); pipes()
        jpeg(File(fx, "tiny.jpg").readBytes())
        zlib(File(fx, "zlib.bin").readBytes())
        vtodo(); pseudonym()
        println("R34 PASS: $n production-kernel assertions across 10 kernels. " +
            "Host JVM only; not Android/device evidence.")
    }

    fun stats() {
        val s = StatSummary.summarize(listOf(1.0, 2.0, 3.0, 4.0))
        ok(s.count == 4, "count")
        ok(s.sum == 10.0, "sum")
        ok(s.mean == 2.5, "mean")
        ok(s.variance == 1.25, "variance")
        ok(s.min == 1.0 && s.max == 4.0, "minmax")
        ok(s.median == 2.5, "even median")
        val one = StatSummary.summarize(listOf(7.0))
        ok(one.count == 1 && one.mean == 7.0, "single mean")
        ok(one.variance == 0.0, "single variance")
        ok(one.median == 7.0, "single median")
        ok(one.min == 7.0 && one.max == 7.0, "single minmax")
        ok(StatSummary.summarize(listOf(5.0, 1.0, 3.0)).median == 3.0, "odd median")
        ok(StatSummary.summarize(listOf(9.0, -3.0)).median == 3.0, "mixed median")
        val big = StatSummary.summarize(listOf(1e9 + 1, 1e9 + 2, 1e9 + 3))
        ok(big.mean == 1000000002.0, "welford mean")
        close(big.variance, 2.0 / 3.0, 1e-9, "welford variance")
        ok(big.min == 1000000001.0, "welford min")
        ok(big.max == 1000000003.0, "welford max")
        ok(big.count == 3, "welford count")
        fails("empty") { StatSummary.summarize(emptyList()) }
        fails("nan") { StatSummary.summarize(listOf(1.0, Double.NaN)) }
        fails("+inf") { StatSummary.summarize(listOf(Double.POSITIVE_INFINITY)) }
        fails("-inf") { StatSummary.summarize(listOf(1.0, Double.NEGATIVE_INFINITY)) }
    }

    fun mime() {
        ok(MimeGuess.guess("a.pdf") == "application/pdf", "pdf")
        ok(MimeGuess.guess("a.txt") == "text/plain", "txt")
        ok(MimeGuess.guess("a.md") == "text/markdown", "md")
        ok(MimeGuess.guess("a.csv") == "text/csv", "csv")
        ok(MimeGuess.guess("a.json") == "application/json", "json")
        ok(MimeGuess.guess("a.html") == "text/html", "html")
        ok(MimeGuess.guess("a.docx") == "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx")
        ok(MimeGuess.guess("a.xlsx") == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
        ok(MimeGuess.guess("a.odt") == "application/vnd.oasis.opendocument.text", "odt")
        ok(MimeGuess.guess("a.epub") == "application/epub+zip", "epub")
        ok(MimeGuess.guess("a.png") == "image/png", "png")
        ok(MimeGuess.guess("a.jpg") == "image/jpeg", "jpg")
        ok(MimeGuess.guess("a.gif") == "image/gif", "gif")
        ok(MimeGuess.guess("a.webp") == "image/webp", "webp")
        ok(MimeGuess.guess("a.svg") == "image/svg+xml", "svg")
        ok(MimeGuess.guess("a.avif") == "image/avif", "avif")
        ok(MimeGuess.guess("a.mp3") == "audio/mpeg", "mp3")
        ok(MimeGuess.guess("a.opus") == "audio/ogg", "opus")
        ok(MimeGuess.guess("a.flac") == "audio/flac", "flac")
        ok(MimeGuess.guess("a.mp4") == "video/mp4", "mp4")
        ok(MimeGuess.guess("a.mkv") == "video/x-matroska", "mkv")
        ok(MimeGuess.guess("a.zip") == "application/zip", "zip")
        ok(MimeGuess.guess("a.gz") == "application/gzip", "gz")
        ok(MimeGuess.guess("a.tar") == "application/x-tar", "tar")
        ok(MimeGuess.guess("a.apk") == "application/vnd.android.package-archive", "apk")
        ok(MimeGuess.guess("a.ics") == "text/calendar", "ics")
        ok(MimeGuess.guess("PHOTO.JPG") == "image/jpeg", "case fold")
        ok(MimeGuess.guess("archive.tar.gz") == "application/gzip", "last ext")
        ok(MimeGuess.guess("file.xyz") == null, "unknown null")
        ok(MimeGuess.guess("data.bin") == null, "bin null")
        fails("blank") { MimeGuess.guess("  ") }
        fails("no ext") { MimeGuess.guess("noext") }
        fails("trailing dot") { MimeGuess.guess("a.") }
        fails("bad chars") { MimeGuess.guess("a.b_c") }
    }

    fun outline() {
        val md = "# Title\n\ntext\n\n## Section A\n\n```\n# not a heading\n```\n\n### Deep\n\n#nope\n\n####### seven\n\n# \n\n~~~\n## tilde fake\n~~~\n\n## After ##\n"
        val o = OutlineParse.outline(md)
        ok(o.size == 4, "entry count")
        ok(o[0] == OutlineParse.Entry(1, "Title", 1), "h1")
        ok(o[1] == OutlineParse.Entry(2, "Section A", 5), "h2")
        ok(o[2] == OutlineParse.Entry(3, "Deep", 11), "h3 in fence gap")
        ok(o[3] == OutlineParse.Entry(2, "After", 23), "closing hashes")
        ok(OutlineParse.outline("no headings here\njust text\n").isEmpty(), "empty outline")
        ok(OutlineParse.outline("").isEmpty(), "blank doc")
        ok(OutlineParse.outline("```\n# unclosed fence\n# still code\n").isEmpty(), "unclosed fence")
        ok(OutlineParse.outline("   ### Indented\n")[0].level == 3, "indented")
        ok(OutlineParse.outline("#\tafter\n")[0].title == "after", "tab space")
    }

    fun brackets() {
        ok(BracketMatch.mate("(a)", 0) == 2, "paren fwd")
        ok(BracketMatch.mate("(a)", 2) == 0, "paren bwd")
        ok(BracketMatch.mate("[{()}]", 0) == 5, "nest outer")
        ok(BracketMatch.mate("[{()}]", 1) == 4, "nest mid")
        ok(BracketMatch.mate("[{()}]", 2) == 3, "nest inner")
        ok(BracketMatch.mate("([)]", 0) == 2, "lexical interleave fwd")
        ok(BracketMatch.mate("([)]", 1) == 3, "lexical interleave mid")
        ok(BracketMatch.mate("()", 1) == 0, "adjacent")
        ok(BracketMatch.mate("(a", 0) == null, "unbalanced open")
        ok(BracketMatch.mate(")a(", 0) == null, "unbalanced close")
        ok(BracketMatch.mate("(]", 0) == null, "mismatch")
        fails("oob neg") { BracketMatch.mate("(a)", -1) }
        fails("oob len") { BracketMatch.mate("(a)", 3) }
        fails("not bracket") { BracketMatch.mate("(a)", 1) }
    }

    fun soap() {
        val xml = "<?xml version=\"1.0\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
            "<s:Body><s:Fault><faultcode>s:Client</faultcode>" +
            "<faultstring>UPnPError</faultstring>" +
            "<detail><UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\">" +
            "<errorCode>501</errorCode><errorDescription>Action Failed</errorDescription>" +
            "</UPnPError></detail></s:Fault></s:Body></s:Envelope>"
        val f = SoapFault.parse(xml)
        ok(f.code == "s:Client", "code")
        ok(f.message == "UPnPError", "message")
        ok(f.upnpCode == 501, "upnp code")
        ok(f.upnpDescription == "Action Failed", "upnp desc")
        val plain = "<Fault><faultcode>x</faultcode><faultstring>y</faultstring>" +
            "<detail><UPnPError><errorCode>402</errorCode>" +
            "<errorDescription>Invalid Args</errorDescription></UPnPError></Detail></Fault>"
        val g = SoapFault.parse(plain)
        ok(g.code == "x" && g.upnpCode == 402, "unprefixed")
        fails("no fault") { SoapFault.parse("<Envelope/>") }
        fails("no code") { SoapFault.parse("<Fault><faultstring>y</faultstring></Fault>") }
        fails("no string") { SoapFault.parse("<Fault><faultcode>x</faultcode></Fault>") }
        fails("no detail") { SoapFault.parse("<Fault><faultcode>x</faultcode><faultstring>y</faultstring></Fault>") }
        fails("no upnp") { SoapFault.parse("<Fault><faultcode>x</faultcode><faultstring>y</faultstring><detail></detail></Fault>") }
        fails("no errcode") { SoapFault.parse("<Fault><faultcode>x</faultcode><faultstring>y</faultstring><detail><UPnPError><errorDescription>d</errorDescription></UPnPError></detail></Fault>") }
        fails("bad errcode") { SoapFault.parse("<Fault><faultcode>x</faultcode><faultstring>y</faultstring><detail><UPnPError><errorCode>abc</errorCode><errorDescription>d</errorDescription></UPnPError></detail></Fault>") }
        fails("no errdesc") { SoapFault.parse("<Fault><faultcode>x</faultcode><faultstring>y</faultstring><detail><UPnPError><errorCode>1</errorCode></UPnPError></Detail></Fault>") }
    }

    fun pipes() {
        ok(PipePlan.split("ls") == listOf("ls"), "single")
        val three = PipePlan.split("cat f | grep x | wc -l")
        ok(three.size == 3, "three count")
        ok(three[0] == "cat f" && three[2] == "wc -l", "three trim")
        val sq = PipePlan.split("echo 'a|b' | wc")
        ok(sq.size == 2, "squote count")
        ok(sq[0] == "echo 'a|b'" && sq[1] == "wc", "squote literal")
        val dq = PipePlan.split("echo \"x|y\"|cat")
        ok(dq.size == 2, "dquote count")
        ok(dq[0] == "echo \"x|y\"", "dquote literal")
        val esc = PipePlan.split("echo a\\|b")
        ok(esc.size == 1, "escaped single")
        ok(esc[0] == "echo a\\|b", "escaped kept")
        fails("leading") { PipePlan.split("| a") }
        fails("trailing") { PipePlan.split("a |") }
        fails("double") { PipePlan.split("a || b") }
        fails("blank") { PipePlan.split("   ") }
        fails("unterminated single") { PipePlan.split("echo 'abc") }
        fails("unterminated double") { PipePlan.split("echo \"abc") }
        fails("trailing backslash") { PipePlan.split("abc\\") }
    }

    fun jpeg(bytes: ByteArray) {
        val info = JpegSeg.walk(bytes)
        ok(info.segments.size == 5, "seg count")
        ok(info.segments.map { it.marker } == listOf(0xE0, 0xDB, 0xC0, 0xC4, 0xDA), "markers")
        ok(info.segments[0].offset == 2, "app0 off")
        ok(info.segments[2].offset == 89, "sof0 off")
        ok(info.segments[4].offset == 124, "sos off")
        ok(info.segments.map { it.length } == listOf(16, 67, 11, 20, 8), "lengths")
        ok(info.width == 1 && info.height == 1, "dims")
        ok(info.components == 1, "comps")
        ok(info.sofType == 0xC0, "sof type")
        fails("no soi") { JpegSeg.walk(byteArrayOf(0x42, 0x4D, 0x00)) }
        fails("truncated") { JpegSeg.walk(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) }
        fails("bad length") { JpegSeg.walk(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x01)) }
        fails("overrun") { JpegSeg.walk(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x01, 0x00)) }
        fails("bad frame") {
            JpegSeg.walk(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xC0.toByte(),
                0x00, 0x0B, 0x08, 0x00, 0x00, 0x00, 0x01, 0x01, 0x01, 0x11, 0x00))
        }
    }

    fun zlib(bytes: ByteArray) {
        val h = ZlibHead.parse(bytes)
        ok(h.cinfo == 7, "cinfo")
        ok(h.flevel == 2, "flevel")
        ok(h.headerLength == 2, "hdrlen")
        fails("short") { ZlibHead.parse(byteArrayOf(0x78)) }
        fails("fcheck") { ZlibHead.parse(byteArrayOf(0x78, 0x9D.toByte())) }
        fails("not deflate") { ZlibHead.parse(byteArrayOf(0x70, 0x03)) }
        fails("window") { ZlibHead.parse(byteArrayOf(0x88.toByte(), 0x1C)) }
        fails("fdict") { ZlibHead.parse(byteArrayOf(0x78, 0x20)) }
        fails("zero") { ZlibHead.parse(byteArrayOf(0x00, 0x00)) }
    }

    fun vtodo() {
        val full = "BEGIN:VTODO\r\nUID:task-1\r\nSUMMARY:Write report\r\nSTATUS:IN-PROGRESS\r\n" +
            "DUE:20261022T120000Z\r\nPRIORITY:1\r\nPERCENT-COMPLETE:50\r\nEND:VTODO"
        val t = VTodoParse.parse(full)
        ok(t.uid == "task-1", "uid")
        ok(t.summary == "Write report", "summary")
        ok(t.status == VTodoParse.Status.IN_PROGRESS, "status")
        ok(t.due == "20261022T120000Z", "due")
        ok(t.priority == 1, "priority")
        ok(t.percent == 50, "percent")
        val min = VTodoParse.parse("BEGIN:VTODO\nUID:a\nSUMMARY:b\nEND:VTODO")
        ok(min.uid == "a", "min uid")
        ok(min.status == null && min.due == null, "min nulls")
        ok(min.priority == null && min.percent == null, "min nulls 2")
        val folded = VTodoParse.parse("BEGIN:VTODO\r\nUID:a\r\nSUMMARY:long \r\n continued\r\nEND:VTODO")
        ok(folded.summary == "long continued", "folding")
        val esc = VTodoParse.parse("BEGIN:VTODO\nUID:a\nSUMMARY:a\\,b\\nC\nEND:VTODO")
        ok(esc.summary == "a,b\nC", "escapes")
        val dated = VTodoParse.parse("BEGIN:VTODO\nUID:a\nSUMMARY:b\nDUE:20261022\nEND:VTODO")
        ok(dated.due == "20261022", "date due")
        fails("no begin") { VTodoParse.parse("UID:a\nSUMMARY:b\nEND:VTODO") }
        fails("no end") { VTodoParse.parse("BEGIN:VTODO\nUID:a\nSUMMARY:b") }
        fails("missing uid") { VTodoParse.parse("BEGIN:VTODO\nSUMMARY:b\nEND:VTODO") }
        fails("missing summary") { VTodoParse.parse("BEGIN:VTODO\nUID:a\nEND:VTODO") }
        fails("bad status") { VTodoParse.parse("BEGIN:VTODO\nUID:a\nSUMMARY:b\nSTATUS:DONE\nEND:VTODO") }
        fails("bad due") { VTodoParse.parse("BEGIN:VTODO\nUID:a\nSUMMARY:b\nDUE:tomorrow\nEND:VTODO") }
        fails("priority range") { VTodoParse.parse("BEGIN:VTODO\nUID:a\nSUMMARY:b\nPRIORITY:10\nEND:VTODO") }
        fails("percent range") { VTodoParse.parse("BEGIN:VTODO\nUID:a\nSUMMARY:b\nPERCENT-COMPLETE:101\nEND:VTODO") }
        fails("unknown prop") { VTodoParse.parse("BEGIN:VTODO\nUID:a\nSUMMARY:b\nLOCATION:x\nEND:VTODO") }
    }

    fun pseudonym() {
        val key = ByteArray(32) { (it * 7 + 1).toByte() }
        val a = Pseudonym.derive(key, "crash", "user-1")
        ok(Pseudonym.derive(key, "crash", "user-1") == a, "deterministic")
        ok(a.length == 32, "length")
        ok(a.all { it in '0'..'9' || it in 'a'..'f' }, "hex shape")
        ok(Pseudonym.derive(key, "abuse", "user-1") != a, "context separates")
        ok(Pseudonym.derive(key, "crash", "user-2") != a, "value separates")
        val key2 = ByteArray(32) { (it * 7 + 2).toByte() }
        ok(Pseudonym.derive(key2, "crash", "user-1") != a, "key separates")
        ok(Pseudonym.matches(key, "crash", "user-1", a), "matches true")
        ok(!Pseudonym.matches(key, "crash", "user-1", "0".repeat(32)), "matches wrong")
        ok(!Pseudonym.matches(key, "crash", "user-1", "abc"), "matches badlen")
        fails("short key") { Pseudonym.derive(ByteArray(15), "c", "v") }
        fails("long key") { Pseudonym.derive(ByteArray(65), "c", "v") }
        fails("blank context") { Pseudonym.derive(key, " ", "v") }
        fails("empty value") { Pseudonym.derive(key, "c", "") }
        fails("oversized") { Pseudonym.derive(key, "c", "x".repeat(1025)) }
    }
}

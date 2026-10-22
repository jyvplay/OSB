// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.privacy.LogScrubber
import dev.fusion.capabilities.CapabilityGate
import dev.fusion.capabilities.DeviceCaps
import dev.fusion.index.QueryNode
import dev.fusion.index.SearchQuery
import dev.fusion.editor.TextOffsets
import dev.fusion.vault.VaultItem
import dev.fusion.vault.VaultManifest
import dev.fusion.format.pdf.PdfTrailer
import dev.fusion.format.pdf.XrefEntry
import dev.fusion.psd.ImageResource
import dev.fusion.psd.PsdResources
import dev.fusion.hw.CodecPreference
import dev.fusion.model.CodecInfo
import dev.fusion.perf.PhaseLedger
import dev.fusion.browser.model.RobotsRules
import java.io.File

/** R31 host consumer: behavioral contracts over the 10 R31 kernels. Host JVM only. */
object R31Checks {
    var n = 0
    fun ok(c: Boolean, label: String) {
        n++
        if (!c) throw AssertionError("r31 check $n FAILED: $label")
    }
    fun fails(label: String, block: () -> Unit) {
        n++
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("r31 check $n FAILED (no refusal): $label")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args[0])
        scrub(File(dir, "scrub_sample.txt").readText())
        gate(); query(); offsets(); vault()
        pdf(File(dir, "min.pdf").readBytes())
        psd(File(dir, "psd_res.bin").readBytes())
        codec(); phase()
        robots(File(dir, "robots.txt").readText())
        println("R31 PASS: $n production-kernel assertions across 10 kernels. " +
            "Host JVM only; NOT Android, device, service, anonymity or acceptance evidence.")
    }

    fun scrub(sample: String) {
        val expected = "contact [email] or [phone]\n" +
            "token=[secret] auth header: Bearer [secret]\n" +
            "open [path] please\nplain line stays intact\n"
        ok(LogScrubber.scrub(sample) == expected, "fixture scrub exact")
        ok(LogScrubber.containsSensitive(sample), "fixture sensitive")
        ok(!LogScrubber.containsSensitive("plain line stays intact\n"), "plain insensitive")
        ok(LogScrubber.scrub(expected) == expected, "idempotent")
        ok(LogScrubber.scrub("user@host") == "user@host", "no-TLD untouched (honest limit)")
        ok(LogScrubber.scrub("call 555-1234") == "call 555-1234", "7-digit under threshold")
        ok(LogScrubber.scrub("u12345678@x.com") == "[email]", "email eats digit run")
        ok(LogScrubber.scrub("token=ab&cd") == "token=[secret]&cd", "token stops at &")
        ok(LogScrubber.scrub("") == "", "empty")
        fails("over cap") { LogScrubber.scrub("x".repeat(65537)) }
        fails("contains over cap") { LogScrubber.containsSensitive("x".repeat(65537)) }
    }

    fun gate() {
        val full = DeviceCaps(34, 8.0, false, listOf("video/avc", "video/hevc"), true)
        val allow = CapabilityGate.decide(full, 26, "video/hevc", 4.0, true)
        ok(allow == dev.fusion.capabilities.GateDecision(true, "all requirements met"), "full allow")
        ok(CapabilityGate.decide(full.copy(sdk = 25), 26).reason == "sdk 25 < 26", "sdk deny")
        ok(!CapabilityGate.decide(full.copy(sdk = 25), 26).enabled, "sdk deny disabled")
        ok(CapabilityGate.decide(full.copy(encoders = null), 26, "video/hevc").reason == "encoder list unknown", "encoder unknown")
        ok(CapabilityGate.decide(full.copy(encoders = listOf("video/avc")), 26, "video/hevc").reason == "no video/hevc encoder", "encoder missing")
        ok(CapabilityGate.decide(full.copy(encoders = listOf("Video/HEVC")), 26, "video/hevc").enabled, "encoder case-insensitive")
        ok(CapabilityGate.decide(full.copy(ramGb = null), 26, needsRamGb = 2.0).reason == "ram unknown", "ram unknown")
        ok(CapabilityGate.decide(full.copy(ramGb = 1.5), 26, needsRamGb = 2.0).reason == "ram 1.5 < 2.0", "ram short")
        val fu = CapabilityGate.decide(full.copy(freeform = null), 26, needsFreeform = true)
        ok(!fu.enabled && fu.reason == "freeform unknown", "freeform unknown")
        ok(CapabilityGate.decide(full.copy(freeform = false), 26, needsFreeform = true).reason == "freeform absent", "freeform absent")
        fails("bad minSdk") { CapabilityGate.decide(full, 0) }
        fails("bad ram need") { CapabilityGate.decide(full, 26, needsRamGb = 0.0) }
    }

    fun query() {
        ok(SearchQuery.parse("Hello") == QueryNode.Term("hello"), "term lowercased")
        ok(SearchQuery.matches(SearchQuery.parse("alpha beta"), "Alpha + beta"), "implicit AND hit")
        ok(!SearchQuery.matches(SearchQuery.parse("alpha beta"), "alpha only"), "implicit AND miss")
        ok(SearchQuery.matches(SearchQuery.parse("alpha OR beta"), "nothing beta here"), "OR hit")
        ok(!SearchQuery.matches(SearchQuery.parse("alpha OR beta"), "nothing here"), "OR miss")
        ok(!SearchQuery.matches(SearchQuery.parse("alpha -beta"), "alpha beta"), "NOT excludes")
        ok(SearchQuery.matches(SearchQuery.parse("alpha -beta"), "alpha gamma"), "NOT keeps")
        ok(SearchQuery.matches(SearchQuery.parse("\"quick brown\""), "the quick brown fox"), "phrase hit")
        ok(!SearchQuery.matches(SearchQuery.parse("\"quick brown\""), "quick red brown"), "phrase miss")
        ok(SearchQuery.matches(SearchQuery.parse("(alpha OR beta) gamma"), "beta gamma"), "parens hit")
        ok(!SearchQuery.matches(SearchQuery.parse("(alpha OR beta) gamma"), "beta alone"), "parens miss")
        val prec = SearchQuery.parse("a OR b c")
        ok(prec == QueryNode.Or(listOf(QueryNode.Term("a"),
            QueryNode.And(listOf(QueryNode.Term("b"), QueryNode.Term("c"))))), "AND binds tighter")
        val lower = SearchQuery.parse("a or b")
        ok(lower is QueryNode.And && lower.nodes.size == 3, "lowercase or is a term")
        ok(SearchQuery.parse("e-mail") == QueryNode.And(listOf(QueryNode.Term("e"),
            QueryNode.Not(QueryNode.Term("mail")))), "hyphen negates (documented)")
        ok(SearchQuery.matches(SearchQuery.parse("caf\u00e9"), "hot CAF\u00c9"), "unicode term")
        fails("empty") { SearchQuery.parse("") }
        fails("blank") { SearchQuery.parse("   ") }
        fails("empty group") { SearchQuery.parse("()") }
        fails("unbalanced") { SearchQuery.parse("(a") }
        fails("unterminated phrase") { SearchQuery.parse("\"abc") }
        fails("empty phrase") { SearchQuery.parse("\"\"") }
        fails("dangling NOT") { SearchQuery.parse("-") }
        fails("dangling NOT paren") { SearchQuery.parse("(-)") }
        fails("trailing") { SearchQuery.parse("a)") }
        fails("over chars") { SearchQuery.parse("a".repeat(257)) }
        fails("over terms") { SearchQuery.parse((1..17).joinToString(" ") { "t$it" }) }
        fails("over depth") { SearchQuery.parse("((((((((((a))))))))))") }
    }

    fun offsets() {
        val t = "ab\ncde\n\nfg"
        ok(TextOffsets.lineStarts(t).toList() == listOf(0, 3, 7, 8), "line starts")
        ok(TextOffsets.lineStarts("").toList() == listOf(0), "empty one line")
        ok(TextOffsets.lineStarts("a\n").toList() == listOf(0, 2), "trailing newline opens line")
        ok(TextOffsets.toLineCol(t, 0) == (0 to 0), "origin")
        ok(TextOffsets.toLineCol(t, 2) == (0 to 2), "newline col")
        ok(TextOffsets.toLineCol(t, 3) == (1 to 0), "line start")
        ok(TextOffsets.toLineCol(t, 9) == (3 to 1), "end")
        ok(TextOffsets.toOffset(t, 1, 1) == 4, "offset mid")
        ok(TextOffsets.toOffset(t, 0, 99) == 2, "col clamps to newline")
        ok(TextOffsets.toOffset(t, 3, 5) == 10, "col clamps to end")
        ok(TextOffsets.wordBounds("foo bar", 1) == (0 to 3), "word mid")
        ok(TextOffsets.wordBounds("foo bar", 4) == (4 to 7), "word second")
        ok(TextOffsets.wordBounds("foo bar", 3) == null, "space null")
        ok(TextOffsets.wordBounds("foo bar", 7) == (4 to 7), "end probes last")
        ok(TextOffsets.wordBounds("", 0) == null, "empty null")
        ok(TextOffsets.wordBounds("a_b2 x", 1) == (0 to 4), "word chars")
        ok(TextOffsets.lineStarts("a\r\nb").toList() == listOf(0, 3), "CRLF starts")
        ok(TextOffsets.toLineCol("a\r\nb", 1) == (0 to 1), "CR is content (documented)")
        fails("offset OOB") { TextOffsets.toLineCol(t, 11) }
        fails("line OOB") { TextOffsets.toOffset(t, 9, 0) }
        fails("negative") { TextOffsets.toOffset(t, 0, -1) }
        fails("word OOB") { TextOffsets.wordBounds(t, 11) }
        fails("over cap") { TextOffsets.lineStarts("x".repeat(1_000_001)) }
    }

    fun vault() {
        val a = VaultItem("a1", "note.txt", 12, "a".repeat(64))
        val b = VaultItem("b2", "photo.bin", 100, "b".repeat(64))
        val items = VaultManifest.add(VaultManifest.add(emptyList(), a), b)
        ok(VaultManifest.totalBytes(items) == 112L, "total")
        ok(VaultManifest.totalBytes(emptyList()) == 0L, "total empty")
        val d1 = VaultManifest.digest(items)
        val d2 = VaultManifest.digest(listOf(b, a))
        ok(d1 == d2, "digest input-order independent")
        ok(Regex("[0-9a-f]{64}").matches(d1), "digest shape")
        ok(VaultManifest.digest(listOf(a)) != d1, "digest changes on remove")
        ok(VaultManifest.remove(items, "a1") == listOf(b), "remove")
        fails("dup id") { VaultManifest.add(items, a) }
        fails("bad id") { VaultManifest.add(emptyList(), a.copy(id = "A!")) }
        fails("slash name") { VaultManifest.add(emptyList(), a.copy(name = "a/b")) }
        fails("empty name") { VaultManifest.add(emptyList(), a.copy(name = "")) }
        fails("long name") { VaultManifest.add(emptyList(), a.copy(name = "n".repeat(129))) }
        fails("negative bytes") { VaultManifest.add(emptyList(), a.copy(bytes = -1)) }
        fails("short sha") { VaultManifest.add(emptyList(), a.copy(sha256 = "a".repeat(63))) }
        fails("upper sha") { VaultManifest.add(emptyList(), a.copy(sha256 = "A".repeat(64))) }
        fails("remove unknown") { VaultManifest.remove(items, "zzz") }
        fails("remove bad id") { VaultManifest.remove(items, "A!") }
    }

    fun pdf(bytes: ByteArray) {
        val text = bytes.toString(Charsets.US_ASCII)
        val tr = PdfTrailer.parse(bytes)
        ok(tr.version == "%PDF-1.4", "version")
        ok(tr.size == 4, "size")
        ok(tr.rootRef == "1 0 R", "root")
        ok(tr.startxref == text.indexOf("xref").toLong(), "startxref exact")
        val entries = PdfTrailer.xrefEntries(bytes, tr.startxref, tr.size)
        ok(entries.size == 4, "4 entries")
        ok(entries[0] == XrefEntry(0, 65535, true), "free head")
        ok(entries[1].offset == text.indexOf("1 0 obj").toLong(), "obj1 offset")
        ok(entries[3] == XrefEntry(text.indexOf("3 0 obj").toLong(), 0, false), "obj3 entry")
        fun mutate(old: String, new: String): ByteArray =
            text.replace(old, new).toByteArray(Charsets.US_ASCII)
        fails("bad header") { PdfTrailer.parse(mutate("%PDF-1.4", "%PDX-1.4")) }
        fails("missing EOF") { PdfTrailer.parse(bytes.copyOf(bytes.size - 5)) }
        fails("missing startxref") { PdfTrailer.parse(mutate("startxref", "startxreX")) }
        fails("bad size") { PdfTrailer.parse(mutate("/Size 4", "/Size X")) }
        fails("missing root") { PdfTrailer.parse(mutate("/Root 1 0 R", "/R00t 1 0 R")) }
        fails("xref at wrong offset") { PdfTrailer.xrefEntries(bytes, 0, 4) }
        fails("CRLF entries refused") { PdfTrailer.xrefEntries(mutate(" f \n", " f \r\n"), tr.startxref, 4) }
    }

    fun psd(section: ByteArray) {
        ok(PsdResources.parse(section) == listOf(ImageResource(1000, "Test", 4), ImageResource(1024, "", 3)), "fixture blocks")
        ok(PsdResources.parse(byteArrayOf()).isEmpty(), "empty section")
        fun mutate(at: Int, value: Byte): ByteArray {
            val c = section.copyOf()
            c[at] = value
            return c
        }
        fails("bad signature") { PsdResources.parse(mutate(1, 'X'.code.toByte())) }
        fails("truncated id") { PsdResources.parse(section.copyOf(5)) }
        fails("truncated name") { PsdResources.parse(section.copyOf(7)) }
        fails("non-ASCII name") { PsdResources.parse(mutate(7, 0xFF.toByte())) }
        fails("size overrun") {
            val c = section.copyOf()
            c[12] = 0x7F.toByte()
            PsdResources.parse(c)
        }
        fails("truncated data pad") { PsdResources.parse(section.copyOf(section.size - 1)) }
    }

    fun codec() {
        val enc = listOf(CodecInfo("video/avc", true, false), CodecInfo("video/hevc", true, true))
        val hw = CodecPreference.select(enc, listOf("video/hevc", "video/avc"), true)
        ok(hw == dev.fusion.hw.CodecChoice("video/hevc", true, "hardware video/hevc"), "hw first candidate")
        ok(CodecPreference.select(enc, listOf("video/av1", "video/avc"), true) == null, "hw required null")
        val sw = CodecPreference.select(enc, listOf("video/av1", "video/avc"), false)
        ok(sw == dev.fusion.hw.CodecChoice("video/avc", false, "software fallback video/avc"), "software fallback")
        val ci = CodecPreference.select(enc, listOf("VIDEO/HEVC"), true)
        ok(ci == dev.fusion.hw.CodecChoice("video/hevc", true, "hardware VIDEO/HEVC"), "case-insensitive")
        ok(CodecPreference.select(emptyList(), listOf("video/avc"), false) == null, "empty probe null")
        fails("empty candidates") { CodecPreference.select(enc, emptyList(), false) }
    }

    fun phase() {
        var p = emptyMap<String, Long>()
        p = PhaseLedger.record(p, "boot", 100)
        p = PhaseLedger.record(p, "first_frame", 300)
        ok(PhaseLedger.total(p) == 400L, "total")
        ok(PhaseLedger.total(emptyMap()) == 0L, "total empty")
        ok(PhaseLedger.percent(p, "first_frame") == 0.75, "percent")
        ok(PhaseLedger.percent(p, "missing") == 0.0, "percent missing")
        ok(PhaseLedger.percent(emptyMap(), "boot") == 0.0, "percent empty")
        ok(PhaseLedger.slowest(p) == "first_frame", "slowest")
        ok(PhaseLedger.slowest(mapOf("b" to 100, "a" to 100)) == "a", "slowest tie alphabetical")
        ok(PhaseLedger.slowest(emptyMap()) == null, "slowest empty")
        fails("dup phase") { PhaseLedger.record(p, "boot", 50) }
        fails("bad name") { PhaseLedger.record(p, "A b", 50) }
        fails("negative ms") { PhaseLedger.record(p, "neg", -1) }
        fails("over ms") { PhaseLedger.record(p, "huge", 3_600_001) }
        fails("over phases") {
            var q = emptyMap<String, Long>()
            repeat(65) { q = PhaseLedger.record(q, "p$it", 1) }
        }
    }

    fun robots(text: String) {
        val groups = RobotsRules.parse(text)
        ok(groups.size == 2, "2 groups")
        ok(groups[0].agents == listOf("*"), "star agents")
        ok(groups[0].allow == listOf("/private/public") && groups[0].disallow == listOf("/private/"), "star rules")
        ok(groups[1].agents == listOf("fusion-test"), "named agents")
        ok(RobotsRules.allowed(groups, "anything", "/index"), "star default allow")
        ok(!RobotsRules.allowed(groups, "anything", "/private/x"), "star disallow")
        ok(RobotsRules.allowed(groups, "anything", "/private/public"), "longest allow wins")
        ok(RobotsRules.allowed(groups, "fusion-test", "/public/a"), "named allow")
        ok(!RobotsRules.allowed(groups, "fusion-test", "/other"), "named root disallow")
        ok(!RobotsRules.allowed(groups, "fusion-test", "/"), "named root exact")
        ok(!RobotsRules.allowed(groups, "Googlebot/2.1", "/private/x"), "versioned UA falls to star")
        val empty = RobotsRules.parse("User-agent: *\nDisallow:\n")
        ok(empty[0].disallow.isEmpty(), "empty disallow ignored")
        ok(RobotsRules.allowed(empty, "x", "/anything"), "empty disallow allows")
        val misc = RobotsRules.parse("# comment\nX-foo: bar\ngarbage\nUser-agent: a\nDisallow: /x\n")
        ok(misc.size == 1 && misc[0].agents == listOf("a"), "unknown lines skipped")
        val sep = RobotsRules.parse("User-agent: a\nDisallow: /x\nUser-agent: b\nDisallow: /y\n")
        ok(sep.size == 2, "rules split groups")
        val joint = RobotsRules.parse("User-agent: a\nUser-agent: b\nDisallow: /z\n")
        ok(joint.size == 1 && joint[0].agents == listOf("a", "b"), "consecutive UAs join")
        val ci = RobotsRules.parse("USER-AGENT: BOT\nDISALLOW: /x\n")
        ok(!RobotsRules.allowed(ci, "bot", "/x") && !RobotsRules.allowed(ci, "BOT", "/x"), "case-insensitive")
        val tie = RobotsRules.parse("User-agent: *\nDisallow: /p\nAllow: /p\n")
        ok(RobotsRules.allowed(tie, "x", "/p"), "tie goes to Allow")
        fails("over lines") { RobotsRules.parse("x\n".repeat(10001)) }
        fails("over groups") {
            RobotsRules.parse((0..256).joinToString("\n") { "User-agent: u$it\nDisallow: /$it" })
        }
        fails("over rules") {
            RobotsRules.parse("User-agent: *\n" + (0..4096).joinToString("\n") { "Disallow: /$it" })
        }
    }
}

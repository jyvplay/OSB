// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.a11y.ContrastRatio
import dev.fusion.alerts.AlertThrottle
import dev.fusion.backup.RetentionPlan
import dev.fusion.backup.Snapshot
import dev.fusion.beam.BeamEntry
import dev.fusion.beam.ShareManifest
import dev.fusion.cast.dlna.DidlLite
import dev.fusion.commands.AliasTable
import dev.fusion.filesui.FileRow
import dev.fusion.filesui.FileSort
import dev.fusion.filesui.SortKey
import dev.fusion.ooxml.CsvDialect
import dev.fusion.ooxml.CsvSeparator
import dev.fusion.ooxml.CsvTable
import dev.fusion.settings.LocaleMatch
import dev.fusion.shell.UsbAllowlist
import dev.fusion.shell.VidPid
import dev.fusion.storage.QuotaLedger
import java.nio.file.Files
import java.nio.file.Path

/**
 * R29 consumer: executes the 10 production kernels against real fixtures.
 * Host JVM only; NOT Android, device, service, anonymity or acceptance evidence.
 */
object R29Checks {
    private var n = 0
    private fun ok(cond: Boolean, label: String = "") {
        n++
        check(cond) { "R29 check$n: $label" }
    }
    private fun refuse(block: () -> Any?, label: String) {
        n++
        try {
            block()
        } catch (_: Exception) {
            return
        }
        error("R29 accepted invalid input at$n: $label")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val dir = Path.of(args[0])
        share(dir); quota(); throttle(); filesort(); alias(); contrast(); locale(); retention(); didl(dir); dialect(dir)
        usballow()
        println("R29 PASS: $n assertions across 10 kernels + UsbAllowlist authored-support (in-memory fake prefs). " +
            "Host JVM only; NOT Android, device, service, anonymity or acceptance evidence.")
    }

    private fun share(dir: Path) {
        val entries = Files.readAllLines(dir.resolve("shares.txt"))
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                val p = line.split('|')
                BeamEntry(p[0], p[1].toLong(), p[2], p[3])
            }
        val m = ShareManifest.of(entries)
        ok(m.entries.size == 5, "fixture entry count")
        ok(m.totalBytes == 184320L + 2457600L + 512L + 1073741824L, "total bytes")
        ok(m.find("notes.txt")?.mime == "text/plain", "find")
        ok(m.find("photo été.jpg")?.bytes == 2457600L, "unicode name")
        ok(m.digest().length == 64 && m.digest() == ShareManifest.of(entries.reversed()).digest(), "digest order-free")
        val rt = ShareManifest.readFrame(m.writeFrame())
        ok(rt.entries == m.entries, "frame round-trip")
        refuse({ ShareManifest.readFrame(m.writeFrame().copyOf(9)) }, "truncated frame")
        refuse({ ShareManifest.of(emptyList()) }, "empty manifest")
        refuse({ ShareManifest.of(listOf(BeamEntry("../x", 1, "text/plain", "a".repeat(64)))) }, "separator name")
        refuse({ ShareManifest.of(listOf(BeamEntry("x", 1, "TEXT/PLAIN", "a".repeat(64)))) }, "upper mime")
        refuse({
            ShareManifest.of(listOf(
                BeamEntry("a", 1, "text/plain", "a".repeat(64)),
                BeamEntry("a", 1, "text/plain", "b".repeat(64))))
        }, "duplicate name")
    }

    private fun quota() {
        val q = QuotaLedger.empty()
        q.setQuota("drafts", 1000); q.setQuota("cache", 500)
        ok(q.limit("drafts") == 1000L && q.free("drafts") == 1000L, "fresh scope")
        ok(q.allocate("drafts", 600) && q.usage("drafts") == 600L, "allocate")
        ok(!q.allocate("drafts", 401) && q.usage("drafts") == 600L, "over-quota denied")
        ok(!q.allocate("nope", 1), "unknown scope denied")
        q.release("drafts", 200)
        ok(q.free("drafts") == 600L, "release")
        q.release("drafts", 99999)
        ok(q.usage("drafts") == 0L, "release floors at zero")
        val rt = QuotaLedger.readFrame(q.writeFrame())
        ok(rt.limit("cache") == 500L && rt.usage("drafts") == 0L, "snapshot round-trip")
        refuse({ q.setQuota("drafts", -1) }, "negative quota")
        refuse({ q.setQuota("Drafts", 10) }, "upper scope")
        q.setQuota("drafts", 100)
        ok(q.allocate("drafts", 100), "refill to cap")
        refuse({ q.setQuota("drafts", 50) }, "quota below usage")
        refuse({ q.removeScope("drafts") }, "remove scope in use")
        q.release("drafts", 100)
        ok(q.removeScope("drafts") && q.limit("drafts") == null, "remove empty scope")
    }

    private fun throttle() {
        val t = AlertThrottle.of(3.0, 1.0)
        ok(t.tryAcquire("rule.a", 0) && t.tryAcquire("rule.a", 0) && t.tryAcquire("rule.a", 0), "burst of 3")
        ok(!t.tryAcquire("rule.a", 0), "burst exhausted")
        ok(t.tryAcquire("rule.b", 0), "per-key isolation")
        ok(!t.tryAcquire("rule.a", 500), "half token insufficient")
        ok(t.tryAcquire("rule.a", 1000), "one token refilled")
        ok(!t.tryAcquire("rule.a", 1000), "refill consumed")
        ok(t.tryAcquire("rule.a", 60_000), "long idle refills to cap")
        val tok = t.tokens("rule.a", 60_000)
        ok(tok != null && tok >= 1.99 && tok <= 2.01, "token observation")
        ok(t.tokens("missing", 0) == null, "unknown key null")
        ok(t.tryAcquire("rule.a", 0), "backwards clock clamped, no bonus")
        refuse({ AlertThrottle.of(0.0, 1.0) }, "zero capacity")
        refuse({ t.tryAcquire("bad key!", 0) }, "bad key shape")
    }

    private fun filesort() {
        val rows = listOf(
            FileRow("b.TXT", 30, 300, false),
            FileRow("A.txt", 10, 100, false),
            FileRow("docs", 0, 50, true),
            FileRow("a.TXT", 20, 200, false),
            FileRow("pic.PNG", 40, 400, false)
        )
        val byName = FileSort.sort(rows, SortKey.NAME, true)
        ok(byName.map { it.name } == listOf("docs", "A.txt", "a.TXT", "b.TXT", "pic.PNG"), "name asc dirs-first")
        val noDir = FileSort.sort(rows, SortKey.NAME, true, dirsFirst = false)
        ok(noDir.first().name == "A.txt", "dirs-first off")
        val bySize = FileSort.sort(rows, SortKey.SIZE, false)
        ok(bySize.map { it.name } == listOf("docs", "pic.PNG", "b.TXT", "a.TXT", "A.txt"), "size desc")
        val byKind = FileSort.sort(rows, SortKey.KIND, true)
        ok(byKind.map { it.name } == listOf("docs", "pic.PNG", "A.txt", "a.TXT", "b.TXT"), "kind asc")
        val byMod = FileSort.sort(rows, SortKey.MODIFIED, true)
        ok(byMod.map { it.name } == listOf("docs", "A.txt", "a.TXT", "b.TXT", "pic.PNG"), "modified asc")
        ok(FileSort.parse("kind:desc") == (SortKey.KIND to false), "descriptor parse")
        ok(FileRow("x", 0, 0, false).ext == "" && FileRow(".h", 0, 0, false).ext == "", "ext edge cases")
        ok(FileSort.sort(emptyList(), SortKey.NAME, true).isEmpty(), "empty input")
        refuse({ FileSort.parse("name") }, "descriptor without direction")
        refuse({ FileSort.parse("color:asc") }, "unknown key")
        val tieA = FileSort.sort(
            listOf(FileRow("s", 5, 1, false), FileRow("S", 5, 2, false)), SortKey.SIZE, true, false)
        val tieB = FileSort.sort(
            listOf(FileRow("S", 5, 2, false), FileRow("s", 5, 1, false)), SortKey.SIZE, true, false)
        ok(tieA.map { it.name } == listOf("S", "s") && tieB.map { it.name } == listOf("S", "s"),
            "ties input-order independent")
    }

    private fun alias() {
        val a = AliasTable.empty()
        a.define("ll", listOf("ls", "-l"))
        a.define("la", listOf("ll", "-a"))
        ok(a.expand(listOf("ll", "/tmp")) == listOf("ls", "-l", "/tmp"), "simple expansion")
        ok(a.expand(listOf("la")) == listOf("ls", "-l", "-a"), "chained expansion")
        ok(a.expand(listOf("ls")) == listOf("ls"), "non-alias passthrough")
        a.define("x", listOf("y")); a.define("y", listOf("x"))
        refuse({ a.expand(listOf("x")) }, "two-cycle")
        a.define("self", listOf("self"))
        refuse({ a.expand(listOf("self")) }, "self-cycle")
        var deep = AliasTable.empty()
        for (i in 0..8) deep.define("a$i", listOf("a${i + 1}"))
        deep.define("a9", listOf("end"))
        refuse({ deep.expand(listOf("a0")) }, "depth cap")
        ok(a.undefine("x") && a.expand(listOf("x")) == listOf("x"), "undefine")
        refuse({ a.define("Bad", listOf("x")) }, "upper alias name")
        refuse({ a.define("ok", emptyList()) }, "empty expansion")
    }

    private fun contrast() {
        val bw = ContrastRatio.ratioHex("#ffffff", "#000000")
        ok(bw > 20.999 && bw < 21.001, "black/white is 21")
        ok(ContrastRatio.ratioHex("#123456", "#123456") == 1.0, "identical is 1")
        ok(ContrastRatio.ratioHex("#000", "#fff") == ContrastRatio.ratioHex("#ffffff", "#000000"), "short hex")
        ok(ContrastRatio.ratioHex("#767676", "#ffffff") >= 4.5, "gray pair AA")
        ok(!ContrastRatio.meets("#777777", "#ffffff") && ContrastRatio.meets("#767676", "#ffffff"), "#76 boundary")
        ok(ContrastRatio.meets("#777777", "#ffffff", largeText = true), "large-text leniency")
        ok(!ContrastRatio.meets("#767676", "#ffffff", aaa = true), "AAA strict")
        ok(ContrastRatio.ratioHex("#ff0000", "#00ff00") == ContrastRatio.ratioHex("#00ff00", "#ff0000"), "order-free")
        refuse({ ContrastRatio.parse("#ffff") }, "alpha form refused")
        refuse({ ContrastRatio.parse("red") }, "named color refused")
        refuse({ ContrastRatio.parse("#gggggg") }, "non-hex refused")
    }

    private fun locale() {
        ok(LocaleMatch.match("en-US", listOf("en-US", "fr-FR")) == "en-US", "exact")
        ok(LocaleMatch.match("en-us", listOf("en-US")) == "en-US", "case normalization")
        ok(LocaleMatch.match("en-GB", listOf("en-US", "fr-FR")) == "en-US", "same-language fallback")
        ok(LocaleMatch.match("de", listOf("en-US", "fr-FR")) == "en-US", "list default")
        ok(LocaleMatch.match("fr", listOf("fr-FR", "fr-CA")) == "fr-FR", "first same-language wins")
        ok(LocaleMatch.match("en", emptyList()) == null, "empty supported")
        ok(LocaleMatch.parse("pt-BR").toString() == "pt-BR", "tag round-trip")
        refuse({ LocaleMatch.parse("en--US") }, "empty subtag")
        refuse({ LocaleMatch.parse("e1-US") }, "digit in language")
        refuse({ LocaleMatch.parse("en-US-variant") }, "third subtag refused")
        refuse({ LocaleMatch.parse("en_US") }, "underscore refused")
    }

    private fun retention() {
        val snaps = listOf(
            Snapshot("a", day = 10, week = 1, month = 0),
            Snapshot("b", day = 10, week = 1, month = 0),
            Snapshot("c", day = 9, week = 1, month = 0),
            Snapshot("d", day = 3, week = 0, month = 0),
            Snapshot("e", day = 40, week = 5, month = 1)
        )
        val daily = RetentionPlan.of(2, 0, 0).select(snaps)
        ok(daily == setOf("a", "b", "e"), "daily keeps whole newest days")
        val week = RetentionPlan.of(0, 1, 0).select(snaps)
        ok(week == setOf("e"), "weekly keeps latest of newest week")
        val gfs = RetentionPlan.of(2, 2, 2).select(snaps)
        ok(gfs == setOf("a", "b", "e"), "gfs union drops c and d")
        ok(RetentionPlan.of(0, 0, 0).select(snaps).isEmpty(), "zero plan keeps nothing")
        ok(RetentionPlan.of(9, 9, 9).select(emptyList()).isEmpty(), "empty input")
        val unsorted = RetentionPlan.of(2, 0, 0).select(snaps.reversed())
        ok(unsorted == setOf("a", "b", "e"), "order-independent")
        refuse({ RetentionPlan.of(1, 0, 0).select(snaps + Snapshot("a", 11, 1, 0)) }, "duplicate id")
        refuse({ RetentionPlan.of(-1, 0, 0) }, "negative keep")
        refuse({ RetentionPlan.of(367, 0, 0) }, "keep over cap")
        refuse({ RetentionPlan.of(1, 0, 0).select(listOf(Snapshot("z", -1, 0, 0))) }, "negative day")
    }

    private fun didl(dir: Path) {
        val (audio, containers) = DidlLite.parse(Files.readString(dir.resolve("didl_audio.xml")))
        ok(containers == 1 && audio.size == 2, "audio item/container counts")
        ok(audio[0].title == "Night Owl & Friends", "entity unescape")
        ok(audio[0].mime == "audio/mpeg" && audio[0].size == 8421376L, "audio res fields")
        ok(audio[0].durationSec == 252.0, "duration 0:04:12")
        ok(audio[0].restricted && !audio[1].restricted, "restricted flags")
        ok(audio[1].durationSec == null && audio[1].size == null, "optional res attrs")
        val (video, vc) = DidlLite.parse(Files.readString(dir.resolve("didl_video.xml")))
        ok(vc == 0 && video.size == 1, "video counts")
        ok(video[0].title == "Launch \"Day\"", "quote entity")
        ok(video[0].durationSec == 5399.5, "duration 1:29:59.5")
        ok(video[0].url.endsWith("token=a%26b"), "url preserved")
        refuse({ DidlLite.parse("<item id=\"1\" parentID=\"0\"><dc:title>t</dc:title></item>") }, "missing class+res")
        refuse({
            DidlLite.parse("<item id=\"1\" parentID=\"0\"><dc:title>t</dc:title>" +
                "<upnp:class>c</upnp:class><res protocolInfo=\"rtsp:*:x:*\">u</res></item>")
        }, "non-http-get proto")
    }

    private fun dialect(dir: Path) {
        val semi = Files.readString(dir.resolve("semi.csv"))
        val tab = Files.readString(dir.resolve("tab.csv"))
        val pipe = Files.readString(dir.resolve("pipe.csv"))
        ok(CsvDialect.sniff(semi) == CsvDialect.Sniff(CsvSeparator.SEMICOLON, 3), "sniff semicolon")
        ok(CsvDialect.sniff(tab) == CsvDialect.Sniff(CsvSeparator.TAB, 3), "sniff tab")
        ok(CsvDialect.sniff(pipe) == CsvDialect.Sniff(CsvSeparator.PIPE, 3), "sniff pipe")
        ok(CsvDialect.sniff("just one column\nsecond line") ==
            CsvDialect.Sniff(CsvSeparator.SINGLE, 1), "single column")
        val st = CsvTable.parse(CsvDialect.translate(semi, CsvDialect.sniff(semi).separator).toByteArray())
        ok(st.rows.size == 4, "semi row count")
        ok(st.rows[1][0] == "Doe; Jane" && st.rows[1][2] == "lives on 5th; likes pie", "semi quoted separators")
        ok(st.rows[3][2] == "multi\nline", "multiline quoted field")
        val tt = CsvTable.parse(CsvDialect.translate(tab, CsvDialect.sniff(tab).separator).toByteArray())
        ok(tt.rows[3][1] == "cat\tdog" && tt.rows[2][2] == "7.0", "tab pipeline")
        val pt = CsvTable.parse(CsvDialect.translate(pipe, CsvDialect.sniff(pipe).separator).toByteArray())
        ok(pt.rows[2] == listOf("4", "5", "6"), "pipe pipeline")
        refuse({ CsvDialect.translate("\"oops\nno close", CsvSeparator.SEMICOLON) }, "unterminated quote")
        ok(CsvDialect.translate("a,b", CsvSeparator.COMMA) == "a,b", "comma passthrough")
    }

    private class FakePrefs : android.content.SharedPreferences {
        val map = HashMap<String, MutableSet<String>>()
        override fun getStringSet(key: String, def: MutableSet<String>?): MutableSet<String>? =
            map[key]?.toMutableSet() ?: def
        override fun edit(): android.content.SharedPreferences.Editor =
            object : android.content.SharedPreferences.Editor {
                override fun putStringSet(key: String, values: MutableSet<String>?) =
                    apply { if (values == null) map.remove(key) else map[key] = values.toMutableSet() }
                override fun apply() {}
            }
    }

    private fun usballow() {
        ok(VidPid.parse("1234:abcd") == (0x1234 to 0xabcd), "vidpid parse")
        ok(VidPid.parse("1234:ABCD") == null, "vidpid upper refused")
        ok(VidPid.parse("123:abcd") == null && VidPid.parse("xy") == null, "vidpid shape refused")
        ok(VidPid.format(0x1234, 0xabcd) == "1234:abcd", "vidpid format")
        val prefs = FakePrefs()
        val a = UsbAllowlist(prefs)
        ok(a.snapshot().isEmpty(), "fresh allowlist empty")
        ok(a.add(0x1234, 0xabcd) && a.contains(0x1234, 0xabcd), "allowlist add+contains")
        ok(a.add(0x1234, 0xabcd), "duplicate add true")
        ok(UsbAllowlist(prefs).contains(0x1234, 0xabcd), "shared prefs backing")
        ok(a.remove(0x1234, 0xabcd) && !a.contains(0x1234, 0xabcd), "allowlist remove")
        ok(!a.remove(0x1234, 0xabcd), "remove missing false")
        prefs.map[UsbAllowlist.KEY] = mutableSetOf("1234:abcd", "junk!!")
        ok(a.snapshot() == setOf("1234:abcd"), "malformed stored entries filtered")
        a.clear()
        ok(a.snapshot().isEmpty(), "allowlist clear")
        for (i in 0 until UsbAllowlist.MAX_ENTRIES) check(a.add(i, i)) { "fill $i" }
        ok(a.snapshot().size == UsbAllowlist.MAX_ENTRIES, "allowlist fills to cap")
        ok(!a.add(999, 999), "add past cap false")
        ok(a.add(0, 0), "duplicate add past cap true")
        refuse({ a.add(-1, 0) }, "negative vid")
        refuse({ a.add(0x10000, 0) }, "vid over ffff")
    }
}

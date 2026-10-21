// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.automation.Recurrence
import dev.fusion.browser.model.CookieJar
import dev.fusion.files.TrashLedger
import dev.fusion.find.FrecencyLedger
import dev.fusion.maps.TileMath
import dev.fusion.media.ChapterMarks
import dev.fusion.notes.FrontMatter
import dev.fusion.security.ConsentLedger
import dev.fusion.terminal.Scrollback
import dev.fusion.windowing.SnapZones
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/**
 * R28 consumer: executes the 10 production kernels against real fixtures.
 * Host JVM only; NOT Android, device, service, anonymity or acceptance evidence.
 */
object R28Checks {
    private var n = 0
    private fun ok(cond: Boolean, label: String = "") {
        n++
        check(cond) { "R28 check$n: $label" }
    }
    private fun refuse(block: () -> Any?, label: String) {
        n++
        try {
            block()
        } catch (_: Exception) {
            return
        }
        error("R28 accepted invalid input at$n: $label")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val dir = Path.of(args[0])
        trash(dir); snap(); frecency(); tiles(); chapters(dir); consent(); cookies(dir); recur(); front(dir); scroll()
        println("R28 PASS: $n production-kernel assertions across 10 kernels. " +
            "Host JVM only; NOT Android, device, service, anonymity or acceptance evidence.")
    }

    private fun trash(dir: Path) {
        val entries = Files.readAllLines(dir.resolve("trash.txt")).filter { it.isNotBlank() }.map { line ->
            val p = line.split('|')
            TrashLedger.Entry(p[0], p[1], p[2].toLong(), p[3].toLong(), p[4].toInt())
        }
        ok(entries.size == 3)
        val now = 1761000000L + 8 * 86400L // b2 (7-day retain) expired; a1/c3 live
        ok(TrashLedger.purgeOrder(entries, now, 1L shl 40) == listOf("b2"))
        ok(TrashLedger.purgeOrder(entries, now, 1000L) == listOf("b2", "c3", "a1")) // expired first, then largest
        ok(TrashLedger.purgeOrder(entries, 1761000000L, 1L shl 40).isEmpty())
        val kept = TrashLedger.validateRestore(entries, "a1", setOf("other.txt"), "photo-restored.png")
        ok(kept.id == "a1")
        refuse({ TrashLedger.validateRestore(entries, "a1", setOf("x.png"), "x.png") }, "live collision")
        refuse({ TrashLedger.validateRestore(entries, "zz", emptySet(), "ok.png") }, "unknown id")
        refuse({ TrashLedger.validateRestore(entries, "a1", emptySet(), "../evil") }, "path escape")
        refuse({ TrashLedger.Entry("bad id!", "n", 1, 1, 30) }, "bad id")
        refuse({ TrashLedger.purgeOrder(entries + entries, now, 1L shl 40) }, "duplicate ids")
    }

    private fun snap() {
        val work = SnapZones.Rect(0, 0, 1920, 1080)
        ok(SnapZones.snap(work, 960, 540, 8) == SnapZones.Zone.NONE)
        ok(SnapZones.snap(work, 3, 540, 8) == SnapZones.Zone.LEFT_HALF)
        ok(SnapZones.snap(work, 1917, 540, 8) == SnapZones.Zone.RIGHT_HALF)
        ok(SnapZones.snap(work, 960, 2, 8) == SnapZones.Zone.TOP_HALF)
        ok(SnapZones.snap(work, 960, 1078, 8) == SnapZones.Zone.BOTTOM_HALF)
        ok(SnapZones.snap(work, 2, 2, 8) == SnapZones.Zone.TOP_LEFT) // corners beat edges
        ok(SnapZones.snap(work, 1918, 1079, 8) == SnapZones.Zone.BOTTOM_RIGHT)
        ok(SnapZones.snap(work, 8, 540, 8) == SnapZones.Zone.LEFT_HALF) // boundary snaps
        ok(SnapZones.snap(work, 9, 540, 8) == SnapZones.Zone.NONE)
        ok(SnapZones.apply(work, SnapZones.Zone.LEFT_HALF, 100, 100) == SnapZones.Rect(0, 0, 960, 1080))
        ok(SnapZones.apply(work, SnapZones.Zone.RIGHT_HALF, 100, 100) == SnapZones.Rect(960, 0, 960, 1080))
        ok(SnapZones.apply(work, SnapZones.Zone.TOP_LEFT, 100, 100) == SnapZones.Rect(0, 0, 960, 540))
        ok(SnapZones.apply(work, SnapZones.Zone.FULL, 100, 100, 40) == SnapZones.Rect(0, 40, 1920, 1040))
        refuse({ SnapZones.apply(work, SnapZones.Zone.TOP_LEFT, 1000, 100) }, "min-size violation")
        refuse({ SnapZones.snap(work, -1, 540, 8) }, "pointer outside")
        refuse({ SnapZones.snap(work, 5, 540, 0) }, "threshold zero")
    }

    private fun frecency() {
        var ledger = FrecencyLedger.empty()
        ok(ledger.score("ghost", 100) == 0.0)
        ledger = ledger.visit("a", 0)
        ok(ledger.score("a", 0) == 1.0)
        ok(ledger.score("a", FrecencyLedger.HALF_LIFE_SECONDS) == 0.5) // exact half-life decay
        ledger = ledger.visit("a", 0)
        ok(ledger.score("a", 0) == 2.0)
        ledger = ledger.visit("b", 100)
        ok(ledger.rank(100).take(2) == listOf("a", "b")) // repeats beat one recent visit
        ok(ledger.rank(0, 1) == listOf("a"))
        var full = FrecencyLedger.empty()
        for (i in 0 until 64) full = full.visit("t%02d".format(i), 0)
        full = full.visit("new", 100)
        ok(full.targets().size == 64 && "new" in full.targets() && "t00" !in full.targets()) // lowest evicted
        refuse({ FrecencyLedger.empty().visit("a", 10).visit("a", 5) }, "time travel")
        refuse({ FrecencyLedger.empty().visit("has space", 1) }, "bad id")
        refuse({ FrecencyLedger.empty().rank(0, 0) }, "limit zero")
    }

    private fun tiles() {
        ok(TileMath.tileOf(0.0, 0.0, 0) == TileMath.Tile(0, 0, 0))
        ok(TileMath.quadkeyOf(TileMath.Tile(0, 0, 0)) == "")
        ok(TileMath.tileOf(0.0, 0.0, 1) == TileMath.Tile(1, 1, 1))
        ok(TileMath.quadkeyOf(TileMath.Tile(1, 1, 1)) == "3")
        ok(TileMath.tileOfQuadkey("3") == TileMath.Tile(1, 1, 1))
        val deep = TileMath.Tile(550, 335, 10)
        ok(TileMath.tileOfQuadkey(TileMath.quadkeyOf(deep)) == deep) // quadkey round-trip
        val b = TileMath.boundsOf(deep)
        ok(b.lonMin < b.lonMax && b.latMin < b.latMax)
        ok(TileMath.tileOf((b.lonMin + b.lonMax) / 2, (b.latMin + b.latMax) / 2, 10) == deep) // bounds round-trip
        ok(TileMath.tileOf(13.4, 95.0, 5) == TileMath.tileOf(13.4, TileMath.MAX_LAT, 5)) // latitude clamps
        ok(TileMath.tileOf(180.0, 0.0, 2).x == 3) // seam clamps into range
        refuse({ TileMath.tileOf(181.0, 0.0, 2) }, "lon out of range")
        refuse({ TileMath.tileOf(0.0, 0.0, 20) }, "zoom too high")
        refuse({ TileMath.tileOfQuadkey("4") }, "bad quadkey digit")
    }

    private fun chapters(dir: Path) {
        val marks = ChapterMarks.parse(Files.readString(dir.resolve("chapters.xml")))
        ok(marks.chapters.size == 3)
        ok(marks.chapters.map { it.startMs } == listOf(0L, 205500L, 4325000L))
        ok(marks.chapters[1].title == "Main topic" && marks.chapters[1].href == "https://example.com/notes")
        refuse({ ChapterMarks.parse("<psc:chapters xmlns:psc=\"http://podlove.org/simple-chapters\"/>") }, "no chapters")
        refuse({
            ChapterMarks.parse("<psc:chapters xmlns:psc=\"http://podlove.org/simple-chapters\">" +
                "<psc:chapter start=\"00:01:00\" title=\"b\"/><psc:chapter start=\"00:00:30\" title=\"a\"/></psc:chapters>")
        }, "non-monotonic")
        refuse({
            ChapterMarks.parse("<psc:chapters xmlns:psc=\"http://podlove.org/simple-chapters\">" +
                "<psc:chapter start=\"soon\" title=\"a\"/></psc:chapters>")
        }, "bad start")
        refuse({
            ChapterMarks.parse("<psc:chapters xmlns:psc=\"http://podlove.org/simple-chapters\">" +
                "<psc:chapter start=\"00:00:01\"/></psc:chapters>")
        }, "missing title")
        refuse({ ChapterMarks.parse("<broken") }, "malformed xml")
    }

    private fun consent() {
        var ledger = ConsentLedger.empty()
        val (issued, receipt) = ledger.issue("handoff.youtube", 1000000L, 300L)
        ledger = issued
        ok(ledger.outstanding() == 1 && receipt.id.length == 32)
        ok(receipt.expiresAt == 1000000L + 300000L)
        ledger = ledger.consume(receipt.id, "handoff.youtube", 1000001L)
        ok(ledger.outstanding() == 0)
        val (l2, r2) = ledger.issue("export.png", 2000000L, 30L)
        refuse({ l2.consume(r2.id, "export.other", 2000001L) }, "scope mismatch")
        refuse({ l2.consume(r2.id, "export.png", 2000000L + 30000L) }, "consume at expiry")
        val okEdge = l2.consume(r2.id, "export.png", 2000000L + 29999L)
        refuse({ okEdge.consume(r2.id, "export.png", 2000001L) }, "replay")
        val (l3, r3) = ConsentLedger.empty().issue("usb.observe", 3000000L, 30L)
        ok(l3.prune(3000000L + 30001L).outstanding() == 0) // expiry prunes
        refuse({ ConsentLedger.empty().issue("Bad Scope!", 0L, 60L) }, "bad scope")
        refuse({ ConsentLedger.empty().issue("ok.scope", 0L, 29L) }, "ttl too small")
        refuse({ ConsentLedger.empty().issue("ok.scope", 0L, 3601L) }, "ttl too big")
        refuse({ l3.consume("0".repeat(32), "usb.observe", 3000001L) }, "unknown id")
    }

    private fun cookies(dir: Path) {
        var jar = CookieJar.empty()
        val now = 1761000000L
        for (line in Files.readAllLines(dir.resolve("cookies.txt")).filter { it.isNotBlank() }) {
            jar = jar.store(line.trim(), "www.example.com", "/", now)
        }
        ok(CookieJar.stored(jar).size == 3)
        ok(jar.headerFor("www.example.com", "/", false, now) == "session=abc123; prefs=dark") // Secure gated on http
        ok(jar.headerFor("www.example.com", "/", true, now) == "session=abc123; prefs=dark")
        ok(jar.headerFor("www.example.com", "/app", true, now) == "session=abc123; prefs=dark; appmode=full")
        ok(jar.headerFor("www.example.com", "/application", true, now) == "session=abc123; prefs=dark") // path boundary
        ok(jar.headerFor("other.com", "/", true, now) == "")
        ok(jar.headerFor("sub.example.com", "/", true, now) == "prefs=dark") // domain cookie roams, host-only stays
        jar = jar.store("session=gone; Path=/; Max-Age=0", "www.example.com", "/", now)
        ok("session=" !in jar.headerFor("www.example.com", "/", true, now)) // deletion wins
        refuse({ CookieJar.empty().store("x=1; Domain=evil.com", "www.example.com", "/", now) }, "foreign domain")
        refuse({ CookieJar.empty().store("no-equals-here", "h.test", "/", now) }, "no pair")
        var big = CookieJar.empty()
        for (i in 0 until 65) big = big.store("n$i=v; Path=/; Max-Age=60", "h.test", "/", now)
        ok(CookieJar.stored(big).size == 64 && CookieJar.stored(big).none { it.name == "n0" }) // oldest evicted
    }

    private fun recur() {
        val start = LocalDate.of(2026, 10, 21) // Wednesday
        val daily = Recurrence.parse("RRULE:FREQ=DAILY;INTERVAL=2;COUNT=5").expand(start)
        ok(daily == listOf(21, 23, 25, 27, 29).map { LocalDate.of(2026, 10, it) })
        val weekly = Recurrence.parse("RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=4").expand(start)
        ok(weekly == listOf(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 26),
            LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 2)))
        val monthly = Recurrence.parse("RRULE:FREQ=MONTHLY;COUNT=3").expand(LocalDate.of(2026, 1, 31))
        ok(monthly == listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 5, 31))) // gaps skipped
        val until = Recurrence.parse("RRULE:FREQ=DAILY;UNTIL=20261023").expand(start)
        ok(until == listOf(21, 22, 23).map { LocalDate.of(2026, 10, it) })
        refuse({ Recurrence.parse("RRULE:FREQ=DAILY;BYDAY=MO;COUNT=2") }, "BYDAY on DAILY")
        refuse({ Recurrence.parse("RRULE:FREQ=DAILY;COUNT=2;UNTIL=20261023") }, "COUNT+UNTIL")
        refuse({ Recurrence.parse("RRULE:FREQ=YEARLY;COUNT=2") }, "bad FREQ")
        refuse({ Recurrence.parse("RRULE:FREQ=DAILY;COUNT=367") }, "COUNT too big")
        refuse({ Recurrence.parse("RRULE:FREQ=DAILY;COUNT=0") }, "COUNT zero")
        refuse({ Recurrence.parse("FREQ=DAILY;COUNT=2") }, "missing prefix")
    }

    private fun front(dir: Path) {
        val doc = FrontMatter.parse(Files.readString(dir.resolve("note-fm.md")))
        ok(doc.values["title"] == FrontMatter.Value.Str("Trip notes"))
        ok(doc.values["draft"] == FrontMatter.Value.Bool(false))
        ok(doc.values["rating"] == FrontMatter.Value.IntNum(5L))
        ok(doc.values["tags"] == FrontMatter.Value.ListStr(listOf("travel", "japan", "food")))
        ok(doc.body.trim() == "# Body here")
        val plain = FrontMatter.parse("# No header\ntext")
        ok(plain.values.isEmpty() && plain.body.startsWith("# No header"))
        refuse({ FrontMatter.parse("---\ntitle: x\n") }, "unterminated")
        refuse({ FrontMatter.parse("---\na: 1\na: 2\n---\n") }, "duplicate key")
        refuse({ FrontMatter.parse("---\n  nested: 1\n---\n") }, "nesting")
        refuse({ FrontMatter.parse("---\nBad Key: 1\n---\n") }, "bad key")
        refuse({ FrontMatter.parse("---\nn: [a, , b]\n---\n") }, "empty list item")
        refuse({ FrontMatter.parse("---\nn: 5000000000\n---\n") }, "int out of range")
        refuse({ FrontMatter.parse("---\nnolines\n---\n") }, "no colon")
    }

    private fun scroll() {
        var buf = Scrollback.empty()
        buf = buf.push("hello\nworld")
        ok(buf.size() == 2 && buf.line(0) == "hello" && buf.line(1) == "world")
        buf = buf.push("0123456789abcdef".repeat(2)) // 32 cols
        val narrow = buf.reflow(8)
        ok(narrow.window(4) == listOf("01234567", "89abcdef", "01234567", "89abcdef")) // exact greedy wrap
        ok(narrow.reflow(8).window(6) == narrow.window(6)) // idempotent at fixed width
        ok(buf.reflow(256).size() == 3)
        var big = Scrollback.empty()
        for (i in 0 until 2005) big = big.push("l$i")
        ok(big.size() == 2000 && big.line(0) == "l5") // ring evicts oldest
        val trunc = Scrollback.empty().push("x".repeat(300))
        ok(trunc.line(0).length == 256 && trunc.truncatedCols == 44L)
        ok(Scrollback.empty().push("a\n\nb").reflow(8).window(3) == listOf("a", "", "b")) // empty lines survive
        refuse({ Scrollback.empty().push("has\ttab") }, "tab refused")
        refuse({ Scrollback.empty().push("has\u0007bell") }, "control refused")
        refuse({ buf.reflow(7) }, "width too small")
    }
}

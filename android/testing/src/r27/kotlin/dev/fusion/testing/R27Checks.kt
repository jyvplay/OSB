// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.automation.CronSpec
import dev.fusion.browser.model.ContentBlocker
import dev.fusion.designsystem.TypeScale
import dev.fusion.documents.MarkdownDoc
import dev.fusion.launcher.DeskPlane
import dev.fusion.launcher.DeskPoint
import dev.fusion.launcher.TabletopArrange
import dev.fusion.launcher.TabletopScene
import dev.fusion.maps.Gazetteer
import dev.fusion.maps.GpxTrack
import dev.fusion.media.PodcastFeed
import dev.fusion.media.StreamPolicy
import dev.fusion.security.FirewallPolicy
import dev.fusion.security.UsbInventory
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

/** Separate consumer of the R27 production kernels. Host JVM only; not Android/device evidence. */
object R27Checks {
    private var checks = 0
    private fun ok(b: Boolean, why: String = "") { checks++; check(b) { "R27 check$checks: $why" } }
    private fun refuse(f: () -> Unit, why: String = "") { checks++; check(runCatching(f).isFailure) { "R27 accepted invalid input at$checks: $why" } }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1) { "Usage: R27Checks <fixture-dir>" }
        val dir = Path.of(args[0])
        arrange(); podcast(dir); gazetteer(); blocker(dir); markdown(dir)
        firewall(); inventory(dir); typescale(); cron(); variants(dir)
        println("R27 PASS: $checks production-kernel assertions across 10 kernels. Host JVM only; NOT Android, device, service, anonymity or acceptance evidence.")
    }

    private fun arrange() {
        ok(TabletopArrange.snap(DeskPoint(100.0, -100.0)) == DeskPoint(88.0, -88.0))
        ok(TabletopArrange.snap(DeskPoint(43.9, 44.1)) == DeskPoint(0.0, 88.0))
        val tidy = TabletopArrange.tidy(listOf(2L to DeskPoint(500.0, 10.0), 1L to DeskPoint(10.0, 500.0), 3L to DeskPoint(9.0, 9.0)), 2)
        ok(tidy[3L] == DeskPoint(0.0, 0.0) && tidy[2L] == DeskPoint(88.0, 0.0) && tidy[1L] == DeskPoint(0.0, 88.0))
        refuse({ TabletopArrange.tidy(listOf(1L to DeskPoint(0.0, 0.0), 1L to DeskPoint(88.0, 0.0)), 2) }, "dup id")
        refuse({ TabletopArrange.tidy(emptyList(), 2) }, "empty")
        ok(TabletopArrange.firstFree(setOf(DeskPoint(0.0, 0.0)), 2) == DeskPoint(88.0, 0.0))
        ok(TabletopArrange.firstFree(emptySet(), 1) == DeskPoint(0.0, 0.0))
        val column = (0 until 512).map { r -> DeskPoint(0.0, r * 88.0) }.toSet()
        ok(TabletopArrange.firstFree(column, 1) == DeskPoint(0.0, 512 * 88.0))
        refuse({ TabletopArrange.firstFree(column + DeskPoint(0.0, 512 * 88.0), 1) }, "occupancy beyond pin capacity")
        ok(TabletopArrange.firstFree(setOf(DeskPoint(DeskPoint.LIMIT, DeskPoint.LIMIT)), 1,
            DeskPoint(DeskPoint.LIMIT, DeskPoint.LIMIT)) == null)
        // Tidy output always fits the engine: 512 snapped pins accepted by TabletopScene.
        val many = (1L..512L).map { it to DeskPoint((it * 37 % 4000).toDouble(), (it * 53 % 4000).toDouble()) }
        val placed = TabletopArrange.tidy(many, 32)
        val scene = TabletopScene(placed.map { (id, point) -> dev.fusion.launcher.DeskPin(id, "files", DeskPlane.TABLE, point) })
        ok(scene.pins.size == 512)
    }

    private fun podcast(dir: Path) {
        val rss = PodcastFeed.parse(Files.readString(dir.resolve("sample-podcast.xml")))
        ok(rss.format == "rss" && rss.title == "Open Field Recordings")
        ok(rss.episodes.size == 2 && rss.skippedNonMedia == 1)
        ok(rss.episodes[0].mime == "audio/mpeg" && rss.episodes[0].lengthBytes == 4823456L)
        ok(rss.episodes.all { it.mediaUrl.startsWith("https://") })
        val atom = PodcastFeed.parse(Files.readString(dir.resolve("sample-atom.xml")))
        ok(atom.format == "atom" && atom.episodes.size == 2 && atom.skippedNonMedia == 0)
        ok(atom.episodes[1].mime == "audio/ogg")
        refuse({ PodcastFeed.parse("<rss><channel><title>T</title></channel></rss>") }, "no items")
        refuse({ PodcastFeed.parse("<html></html>") }, "not a feed")
        refuse({ PodcastFeed.parse("<rss><channel><title>T</title><item><title>X</title><enclosure url=\"ftp://x/y.mp3\" length=\"1\" type=\"audio/mpeg\"/></item></channel></rss>") }, "ftp")
        refuse({ PodcastFeed.parse("<rss><channel><title>T</title><item><enclosure url=\"https://x/y.mp3\" length=\"1\" type=\"audio/mpeg\"/></item></channel></rss>") }, "no title/desc")
    }

    private fun gazetteer() {
        ok(Gazetteer.PLACES.size >= 40)
        ok(Gazetteer.search("york").map { it.name } == listOf("New York"))
        ok(Gazetteer.search("united states").size >= 8)
        ok(Gazetteer.search("  TOKYO ").single().country == "Japan")
        refuse({ Gazetteer.search("x") }, "short query")
        val raleigh = Gazetteer.exact("Raleigh")
        ok(raleigh != null && raleigh.latitude == 36.0 && raleigh.longitude == -79.0)
        val near = Gazetteer.nearest(36.0, -79.0, 3)
        ok(near.first().name == "Raleigh")
        // NYC↔London ≈ 5570km; whole-degree rounding keeps us within ±500km.
        val nyc = Gazetteer.exact("New York")!!
        val london = Gazetteer.exact("London")!!
        val d = GpxTrack.haversine(GpxTrack.Point(nyc.latitude, nyc.longitude, null), GpxTrack.Point(london.latitude, london.longitude, null))
        ok(abs(d - 5570000.0) < 500000.0)
        ok("±50 km" in Gazetteer.disclosure())
    }

    private fun blocker(dir: Path) {
        val compiled = ContentBlocker.compile(Files.readString(dir.resolve("blocklist.txt")))
        ok(!compiled.decide("tracker.example.net") && !compiled.decide("sub.ads.example.net"))
        ok(!compiled.decide("beacon.example.org") && !compiled.decide("a.b.metrics.example.org"))
        ok(compiled.decide("safe.metrics.example.org"))
        ok(compiled.decide("example.com"))
        ok(!compiled.decide("TRACKER.EXAMPLE.NET.")) // case + trailing dot normalized, still blocked
        refuse({ ContentBlocker.compile("0.0.0.0 localhost") }, "localhost")
        refuse({ ContentBlocker.compile("@@||x^") }, "bad domain")
        refuse({ ContentBlocker.compile("||dup.example^ \n@@||dup.example^") }, "overlap")
        refuse({ ContentBlocker.compile("example.com") }, "unsupported syntax")
        refuse({ ContentBlocker.compile("") }, "empty")
        refuse({ compiled.decide("") }, "empty host")
    }

    private fun markdown(dir: Path) {
        val doc = MarkdownDoc.parse(Files.readString(dir.resolve("sample.md")))
        val kinds = doc.blocks.map { it::class.simpleName }
        ok(kinds == listOf("Heading", "Paragraph", "Code", "BulletList", "OrderedList", "Quote", "Rule", "Table", "Paragraph"))
        val heading = doc.blocks[0] as MarkdownDoc.Block.Heading
        ok(heading.level == 1 && heading.text == "Field Notes")
        val code = doc.blocks[2] as MarkdownDoc.Block.Code
        ok(code.info == "kotlin" && "hello" in code.text)
        val table = doc.blocks[7] as MarkdownDoc.Block.Table
        ok(table.header == listOf("name", "age") && table.rows == listOf(listOf("ada", "36")))
        ok("*raw inline*" in (doc.blocks[1] as MarkdownDoc.Block.Paragraph).text)
        ok(doc.strippedHtmlTags == 2)
        refuse({ MarkdownDoc.parse("") }, "empty")
        refuse({ MarkdownDoc.parse("```\nunclosed") }, "unclosed fence")
        refuse({ MarkdownDoc.parse("| a | b |\n| - | - |\n| 1 |") }, "ragged table")
        ok(MarkdownDoc.parse("| a | b |\n| - |\n| 1 | 2 |").strippedHtmlTags == 0) // delimiter mismatch: paragraphs, not a table
        refuse({ MarkdownDoc.parse("```js ` bad\nx\n```") }, "backtick info")
    }

    private fun firewall() {
        refuse({ FirewallPolicy.generate(authorityAttested = false) }, "no authority")
        val proposal = FirewallPolicy.generate(authorityAttested = true)
        ok(proposal.invariants.size == 10)
        ok("policy drop;" in proposal.text && "redirect to :9040" in proposal.text && "redirect to :5353" in proposal.text)
        ok("REVIEW PROPOSAL ONLY" in proposal.text)
        ok(proposal.text.lines().none { it.trim() == "accept" })
        val custom = FirewallPolicy.generate(FirewallPolicy.TorPorts(9140, 5453, 9150), authorityAttested = true)
        ok("redirect to :9140" in custom.text && "redirect to :5453" in custom.text)
        refuse({ FirewallPolicy.generate(FirewallPolicy.TorPorts(9040, 9040, 9050), authorityAttested = true) }, "dup ports")
        refuse({ FirewallPolicy.generate(FirewallPolicy.TorPorts(80, 5353, 9050), authorityAttested = true) }, "privileged port")
    }

    private fun inventory(dir: Path) {
        val a = UsbInventory.parse(Files.readString(dir.resolve("inventory-a.txt")))
        val b = UsbInventory.parse(Files.readString(dir.resolve("inventory-b.txt")))
        ok(a.size == 3 && b.size == 4)
        ok(a[1].interfaces.size == 1 && a[0].interfaces.isEmpty())
        ok(a[1].keyboardCapable == false && a[0].keyboardCapable == null)
        val diff = UsbInventory.diff(a, b)
        ok(diff.arrivals.size == 2 && diff.removals.size == 1)
        ok(diff.removals.single().vid == 0x0781)
        val high = diff.findings.filter { it.severity == UsbInventory.Severity.HIGH }
        val unknown = diff.findings.filter { it.severity == UsbInventory.Severity.UNKNOWN }
        ok(high.size == 1 && "046d:c31c" in high.single().text)
        ok(unknown.size == 1 && "1a2b:3c4d" in unknown.single().text)
        val changed = UsbInventory.diff(a, a.map { if (it.vid == 0x046d) it.copy(label = "Evil Spoof") else it })
        ok(changed.findings.any { it.severity == UsbInventory.Severity.UNKNOWN && "spoofable" in it.text })
        refuse({ UsbInventory.parse("") }, "empty")
        refuse({ UsbInventory.parse("not a bus line") }, "bad line")
        refuse({ UsbInventory.parse("Bus 002 Device 003: ID 046d:c52b X { 03:01 }") }, "bad iface")
        refuse({ UsbInventory.parse("Bus 002 Device 003: ID 046d:c52b X { 03:01:01 ") }, "unbalanced")
    }

    private fun typescale() {
        ok(TypeScale.BASELINE.size == 15)
        val body = TypeScale.style("bodyLarge")
        ok(body.sizeSp == 16.0 && body.lineHeightSp == 24.0 && !body.bold && !body.large)
        ok(TypeScale.style("displayLarge").large && TypeScale.style("headlineSmall").large)
        ok(!TypeScale.style("titleLarge").large && TypeScale.style("titleMedium").large.not())
        ok(TypeScale.phiSize(16.0, 1) == 26.0 && TypeScale.phiSize(16.0, 0) == 16.0)
        ok(TypeScale.phiSize(16.0, -1) == 10.0)
        ok(TypeScale.fit(TypeScale.style("bodyLarge"), 10, 240.0, 1.0) == TypeScale.Fit.FITS)
        ok(TypeScale.fit(TypeScale.style("bodyLarge"), 10, 240.0, 2.0) == TypeScale.Fit.SCROLLS)
        ok(TypeScale.fit(TypeScale.style("labelSmall"), 1, 16.0, 1.0) == TypeScale.Fit.FITS)
        refuse({ TypeScale.style("nope") })
        refuse({ TypeScale.phiSize(2.0, 1) })
    }

    private fun cron() {
        val daily = CronSpec.parse("30 9 * * *")
        // 2026-10-21 is a Wednesday; next 09:30 UTC after 2026-10-21T09:00Z is same day.
        val start = LocalDateTime.of(2026, 10, 21, 9, 0).toEpochSecond(ZoneOffset.UTC) / 60
        val fire = CronSpec.nextFire(daily, start)
        ok(fire == LocalDateTime.of(2026, 10, 21, 9, 30).toEpochSecond(ZoneOffset.UTC) / 60)
        ok(CronSpec.matches(daily, fire!!) && !CronSpec.matches(daily, fire + 1))
        val weekly = CronSpec.parse("0 12 * * MON")
        val monday = CronSpec.nextFire(weekly, start)
        ok(monday == LocalDateTime.of(2026, 10, 26, 12, 0).toEpochSecond(ZoneOffset.UTC) / 60)
        val names = CronSpec.parse("0 0 1 JAN *")
        ok(CronSpec.nextFire(names, start) == LocalDateTime.of(2027, 1, 1, 0, 0).toEpochSecond(ZoneOffset.UTC) / 60)
        val stepped = CronSpec.parse("*/15 9-17 * * 1-5")
        ok(stepped.minutes.size == 4 && stepped.hours.size == 9)
        // Vixie OR: 13th OR Friday both fire.
        val or = CronSpec.parse("0 0 13 * 5")
        val friday = LocalDateTime.of(2026, 10, 23, 0, 0).toEpochSecond(ZoneOffset.UTC) / 60
        val thirteenth = LocalDateTime.of(2026, 11, 13, 0, 0).toEpochSecond(ZoneOffset.UTC) / 60
        ok(CronSpec.matches(or, friday) && CronSpec.matches(or, thirteenth))
        ok("Vixie" in CronSpec.describe(or))
        refuse({ CronSpec.parse("0 30 9 * * *") }, "quartz 6-field")
        refuse({ CronSpec.parse("* * * * ?") }, "quartz ?")
        refuse({ CronSpec.parse("0 0 L * *") }, "quartz L")
        refuse({ CronSpec.parse("61 * * * *") }, "bad minute")
        refuse({ CronSpec.parse("0 0 * * FUNDAY") }, "bad name")
    }

    private fun variants(dir: Path) {
        val list = StreamPolicy.parseVariants(Files.readString(dir.resolve("variant.m3u8")))
        ok(list.size == 3)
        ok(list[0].bandwidthBps == 800000L && list[0].width == 640 && list[0].height == 360)
        ok(list[2].codecs == null && list[2].label == "1920x1080 · 6.0 Mbps")
        ok(list[0].label == "640x360 · 800 kbps")
        ok(StreamPolicy.choose(list, 1) == list[1])
        refuse({ StreamPolicy.choose(list, 3) }, "index")
        refuse({ StreamPolicy.choose(emptyList(), 0) }, "empty")
        refuse({ StreamPolicy.parseVariants("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://x/k\"\n#EXT-X-STREAM-INF:BANDWIDTH=1\nhttps://x/v") }, "encrypted")
        refuse({ StreamPolicy.parseVariants("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100\n") }, "missing uri")
        refuse({ StreamPolicy.parseVariants("#EXTM3U\n#EXT-X-STREAM-INF:RESOLUTION=640x360\nhttps://x/v") }, "no bandwidth")
        refuse({ StreamPolicy.parseVariants("#EXTM3U\n") }, "no variants")
    }
}

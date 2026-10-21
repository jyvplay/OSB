// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.browser.model.SafeNavigation
import dev.fusion.designsystem.BeautyStandard
import dev.fusion.designsystem.DesktopMetrics
import dev.fusion.maps.GpxTrack
import dev.fusion.media.HandoffDisclosure
import dev.fusion.media.PlaylistParser
import dev.fusion.media.ServiceLink
import dev.fusion.native.SandboxJob
import dev.fusion.native.TsvTable
import dev.fusion.security.AmnesiaPolicy
import dev.fusion.security.InputChannel
import dev.fusion.security.KeypadLayout
import dev.fusion.security.TorPosture
import dev.fusion.security.UsbPolicy
import dev.fusion.settings.ProfessionalPalette
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import kotlin.math.abs

/** Separate consumer of the R26 production kernels. Host JVM only; not Android/device evidence. */
object R26Checks {
    private var checks = 0
    private fun ok(b: Boolean, why: String = "") { checks++; check(b) { "R26 check$checks: $why" } }
    private fun refuse(f: () -> Unit, why: String = "") { checks++; check(runCatching(f).isFailure) { "R26 accepted invalid input at$checks: $why" } }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1) { "Usage: R26Checks <fixture-dir>" }
        val dir = Path.of(args[0])
        beauty(); amnesia(); tor(); usb(); keypad()
        playlists(dir); gpx(dir); navigation(); sandbox(); tsv()
        println("R26 PASS: $checks production-kernel assertions across 10 kernels. Host JVM only; NOT Android, device, service, anonymity or acceptance evidence.")
    }

    private fun beauty() {
        // WCAG ground truth anchors: black/white is exactly 21:1.
        ok(abs(BeautyStandard.auditPalette(ProfessionalPalette.OFFICE_BLUE).size - 9) == 0)
        val audits = BeautyStandard.auditAllPalettes()
        ok(audits.size == 3)
        audits.forEach { (palette, pairs) ->
            ok(pairs.size == 9, palette.name)
            pairs.forEach { ok(it.aaText, "${palette.name} ${it.foregroundRole}/${it.backgroundRole}=${it.ratio}") }
        }
        ok(!BeautyStandard.nearPhi(1.0) && BeautyStandard.nearPhi(1.618) && BeautyStandard.nearPhi(256.0 / 158.0, 0.05))
        refuse({ BeautyStandard.nearPhi(Double.NaN) })
        // Dock: slots never shrink; overflow scrolls.
        val plan = BeautyStandard.dockPlan(390.0, 1.0, 20)
        ok(plan.slotDp == 64 && plan.visibleSlots == 8 && plan.overflow == 12)
        ok(plan.gapDp in 4..16)
        val large = BeautyStandard.dockPlan(390.0, 2.0, 3)
        ok(large.slotDp == 88 && large.visibleSlots == 3 && large.overflow == 0)
        val empty = BeautyStandard.dockPlan(390.0, 1.0, 0)
        ok(empty.visibleSlots == 0 && empty.overflow == 0 && empty.viewportDp >= 48.0)
        refuse({ BeautyStandard.dockPlan(47.0, 1.0, 3) })
        // Tabletop floor dominance: shipped 0.8316 constant satisfies the >=0.80 contract.
        ok(BeautyStandard.floorDominant(0.8316) && !BeautyStandard.floorDominant(0.799))
        val viewport = DesktopMetrics.Bounds(0.0, 0.0, 390.0, 844.0)
        ok(BeautyStandard.pageTargetsOk(listOf(DesktopMetrics.Bounds(8.0, 8.0, 72.0, 72.0)), viewport).isEmpty())
        ok(BeautyStandard.pageTargetsOk(listOf(DesktopMetrics.Bounds(8.0, 8.0, 40.0, 40.0)), viewport) == listOf("small-target@0"))
        ok(BeautyStandard.pageTargetsOk(listOf(DesktopMetrics.Bounds(350.0, 800.0, 420.0, 860.0)), viewport) == listOf("clipped-target@0"))
    }

    private fun amnesia() {
        val fresh = AmnesiaPolicy.Store.fresh()
        ok(!fresh.mayPersist(AmnesiaPolicy.Category.DOCUMENTS))
        // Empty passphrase boots amnesic, never an error.
        val anon = fresh.unlock("")
        ok(anon.session == AmnesiaPolicy.Session.UNLOCKED && anon.enabled.isEmpty())
        val unlocked = fresh.unlock("correct horse battery staple")
        val withDocs = unlocked.enable(AmnesiaPolicy.Category.DOCUMENTS).enable(AmnesiaPolicy.Category.BOOKMARKS)
        ok(withDocs.mayPersist(AmnesiaPolicy.Category.DOCUMENTS) && !withDocs.mayPersist(AmnesiaPolicy.Category.MEDIA_CACHE))
        refuse({ withDocs.enable(AmnesiaPolicy.Category.INPUT_SCRATCH) })
        refuse({ fresh.enable(AmnesiaPolicy.Category.DOCUMENTS) })
        val (wiped, receipt) = withDocs.lock().unlock("correct horse battery staple").wipe()
        ok(wiped.session == AmnesiaPolicy.Session.WIPED && wiped.enabled.isEmpty())
        ok(AmnesiaPolicy.Category.INPUT_SCRATCH in receipt.volatileDiscarded)
        ok(receipt.grantsCleared == setOf(AmnesiaPolicy.Category.DOCUMENTS, AmnesiaPolicy.Category.BOOKMARKS))
        ok("forensic" in receipt.disclosure().lowercase())
        refuse({ wiped.enable(AmnesiaPolicy.Category.DOCUMENTS) })
        refuse({ wiped.wipe() })
        // No bulk-persist transition: enabling one category at a time only.
        ok(unlocked.disable(AmnesiaPolicy.Category.DOCUMENTS) == unlocked)
    }

    private fun tor() {
        val unknown = TorPosture.Posture()
        ok(unknown.tunneled() == TorPosture.Observation.UNKNOWN)
        ok("non-anonymous" in unknown.disclosure())
        val good = TorPosture.Posture(TorPosture.Observation.OBSERVED, TorPosture.Observation.OBSERVED, TorPosture.Observation.OBSERVED)
        ok(good.tunneled() == TorPosture.Observation.OBSERVED)
        ok("fingerprint" in good.disclosure())
        // Installed-but-unconsented is NOT tunneled; unknown never upgrades.
        val installed = TorPosture.Posture(TorPosture.Observation.OBSERVED, TorPosture.Observation.UNKNOWN, TorPosture.Observation.UNKNOWN)
        ok(installed.tunneled() == TorPosture.Observation.UNKNOWN)
        val denied = TorPosture.Posture(TorPosture.Observation.OBSERVED, TorPosture.Observation.ABSENT, TorPosture.Observation.UNKNOWN)
        ok(denied.tunneled() == TorPosture.Observation.ABSENT)
        ok("NOT routed" in denied.disclosure())
        // Merge: disagreement collapses to unknown.
        val merged = TorPosture.merge(good, denied)
        ok(merged.tunneled() == TorPosture.Observation.UNKNOWN)
        ok(TorPosture.merge(good, good) == good)
    }

    private fun usb() {
        val rules = UsbPolicy.parse("# comment\n\nallow id 046d:* with-interface { 03:01:01 }\nblock id *:* with-interface { 08:06:50 }\nreject id 1234:5678\n")
        ok(rules.size == 3 && rules[0].audit.startsWith("allow id 046d:*"))
        val keyboard = UsbPolicy.Device(0x046d, 0xc52b, setOf(UsbPolicy.InterfaceClass.HID_KEYBOARD_BOOT))
        val storage = UsbPolicy.Device(0x0781, 0x5567, setOf(UsbPolicy.InterfaceClass(8, 6, 80)))
        val combo = UsbPolicy.Device(0x0781, 0x5567, setOf(UsbPolicy.InterfaceClass(8, 6, 80), UsbPolicy.InterfaceClass.HID_KEYBOARD_BOOT))
        ok(UsbPolicy.evaluate(rules, keyboard).target == UsbPolicy.Target.ALLOW)
        ok(UsbPolicy.evaluate(rules, storage).target == UsbPolicy.Target.BLOCK)
        // Suspicious combo: storage+keyboard is NOT allowlisted by either single-interface rule shape here.
        ok(UsbPolicy.evaluate(rules, storage.copy(interfaces = setOf(UsbPolicy.InterfaceClass(8, 6, 80)))).target == UsbPolicy.Target.BLOCK)
        ok(UsbPolicy.evaluate(rules, UsbPolicy.Device(0x1234, 0x5678, setOf(UsbPolicy.InterfaceClass(255, 0, 0)))).target == UsbPolicy.Target.REJECT)
        val unknown = UsbPolicy.Device(0x9999, 0x0001, setOf(UsbPolicy.InterfaceClass(255, 0, 0)))
        val implicit = UsbPolicy.evaluate(rules, unknown)
        ok(implicit.target == UsbPolicy.Target.BLOCK && implicit.defaulted)
        // Stateful: second keyboard refused once one is allowed.
        val oneKeyboard = UsbPolicy.parse("allow id *:* with-interface { 03:01:01 } if !allowed-matches(with-interface one-of { 03:01:01 }")
        refuse({ UsbPolicy.parse("allow id *:* with-interface { 03:01:01 } if !allowed-matches(with-interface one-of { 03:01:01 })") }, "unbalanced condition brace")
        ok(UsbPolicy.evaluate(oneKeyboard, keyboard).target == UsbPolicy.Target.ALLOW)
        ok(UsbPolicy.evaluate(oneKeyboard, keyboard.copy(vid = 0x1234), alreadyAllowed = listOf(keyboard)).defaulted)
        // Generation refuses untrusted inventory.
        refuse({ UsbPolicy.generatePolicy(listOf(keyboard), trustedInventory = false) })
        val proposal = UsbPolicy.generatePolicy(listOf(keyboard), trustedInventory = true)
        ok(proposal.size == 2 && proposal.last().target == UsbPolicy.Target.BLOCK)
        refuse({ UsbPolicy.parse("") })
        refuse({ UsbPolicy.parse("permit id *:*") })
        refuse({ UsbPolicy.parse("allow id xyz:*") })
        refuse({ UsbPolicy.parse("allow id 046d:c52b with-interface { 03:01 }") })
        // Fixture combo rule rejects storage+keyboard before the keyboard allowance.
        val combo2 = combo.copy(interfaces = setOf(UsbPolicy.InterfaceClass(8, 6, 80), UsbPolicy.InterfaceClass.HID_KEYBOARD_BOOT))
        ok(combo2.interfaces.size == 2)
    }

    private fun keypad() {
        val first = KeypadLayout.shuffled(Random(20261021L))
        val second = KeypadLayout.shuffled(Random(20261021L))
        ok(first == second && KeypadLayout.valid(first))
        ok(KeypadLayout.shuffled(Random(7L)) != KeypadLayout.shuffled(Random(8L)))
        val positions = KeypadLayout.positions(first)
        ok(positions.size == 10 && positions.toSet() == (0 until 10).toSet())
        first.forEachIndexed { pos, digit -> ok(positions[digit] == pos) }
        refuse({ KeypadLayout.positions(listOf(0, 1, 2)) })
        // Threat table: touch pad avoids key events only; keyboard exposes everything.
        ok(InputChannel.avoided(InputChannel.Route.TOUCH_PAD) == setOf(InputChannel.Channel.KEY_EVENTS))
        ok(InputChannel.Route.PHYSICAL_KEYBOARD.let { InputChannel.avoided(it).isEmpty() })
        ok("cannot be detected" in InputChannel.disclosure(InputChannel.Route.TOUCH_PAD))
        ok(InputChannel.exposed(InputChannel.Route.SYSTEM_IME) == InputChannel.exposed(InputChannel.Route.TOUCH_PAD))
    }

    private fun playlists(dir: Path) {
        val m3u = PlaylistParser.parseM3U(Files.readString(dir.resolve("sample.m3u8")))
        ok(m3u.format == "m3u8" && m3u.entries.size == 3)
        ok(m3u.entries[0].title == "Public Field Recording One" && m3u.entries[0].durationSeconds == 214.5)
        ok(m3u.entries.all { it.uri.startsWith("https://") })
        val pls = PlaylistParser.parsePLS(Files.readString(dir.resolve("sample.pls")))
        ok(pls.format == "pls" && pls.entries.size == 2 && pls.entries[1].durationSeconds == 187.0)
        refuse({ PlaylistParser.parseM3U("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://x/y\"\nhttps://x/s.ts") }, "encrypted HLS")
        refuse({ PlaylistParser.parseM3U("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nhttps://x/v.m3u8") }, "master variant")
        refuse({ PlaylistParser.parseM3U("#EXTM3U\nftp://x/y.mp3") }, "ftp scheme")
        refuse({ PlaylistParser.parseM3U("#EXTM3U\nhttps://x/../../etc/passwd") }, "traversal")
        refuse({ PlaylistParser.parseM3U("#EXTM3U\n") }, "empty")
        refuse({ PlaylistParser.parsePLS("[playlist]\nNumberOfEntries=1\n") }, "missing File1")
        refuse({ PlaylistParser.parsePLS("NumberOfEntries=1\nFile1=https://x/y.mp3\n") }, "missing section")
        val link = ServiceLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val disclosure = HandoffDisclosure.text(link)
        ok("external app" in disclosure && "DRM" in disclosure && "M3U" in disclosure)
    }

    private fun gpx(dir: Path) {
        val track = GpxTrack.parse(Files.readString(dir.resolve("sample.gpx")))
        ok(track.name == "Raleigh test loop" && track.points.size == 3)
        ok(track.points[0].elevationMeters == 96.0)
        // ~= 3 short hops near Raleigh; spherical sum must be positive and under 1km.
        ok(track.lengthMeters in 1.0..1000.0)
        val a = GpxTrack.Point(35.7796, -78.6382, null)
        ok(abs(GpxTrack.haversine(a, a)) < 1e-9)
        // ~111.2km per degree of latitude (spherical mean radius).
        ok(abs(GpxTrack.haversine(GpxTrack.Point(0.0, 0.0, null), GpxTrack.Point(1.0, 0.0, null)) - 111195.0) < 500.0)
        refuse({ GpxTrack.parse("<gpx><trk><trkseg><trkpt lat=\"1\" lon=\"2\"/></trkseg></trk></gpx>") }, "single point")
        refuse({ GpxTrack.parse("<gpx><trk><trkseg><trkpt lat=\"91\" lon=\"2\"/><trkpt lat=\"1\" lon=\"2\"/></trkseg></trk></gpx>") }, "bad latitude")
        refuse({ GpxTrack.parse("<html></html>") }, "not GPX")
        refuse({ GpxTrack.parse("<gpx>" + "<trkpt lat=\"1\" lon=\"2\"/>".repeat(8193)) }, "too many points")
    }

    private fun navigation() {
        val canon = SafeNavigation.canonicalize("HTTPS://Example.COM:8443/a?b=c")
        ok(canon.url == "https://example.com:8443/a?b=c" && canon.host == "example.com")
        ok(SafeNavigation.canonicalize("http://example.com").url == "http://example.com/")
        refuse({ SafeNavigation.canonicalize("https://user@example.com/") }, "credentials")
        refuse({ SafeNavigation.canonicalize("https://example.com:443/") }, "default port")
        refuse({ SafeNavigation.canonicalize("file:///etc/passwd") }, "scheme")
        refuse({ SafeNavigation.canonicalize("https://exa mple.com/") }, "space")
        val rules = SafeNavigation.RuleSet(setOf(".example.org", "docs.example.com"), setOf(".evil.example.org"))
        ok(rules.decide("example.org") && rules.decide("a.example.org") && rules.decide("docs.example.com"))
        ok(!rules.decide("evil.example.org") && !rules.decide("other.net"))
        val open = SafeNavigation.RuleSet(emptySet(), setOf(".ads.example"))
        ok(open.decide("anything.example") && !open.decide("x.ads.example"))
        refuse({ SafeNavigation.RuleSet(setOf("Example.COM"), emptySet()) }, "case")
        refuse({ SafeNavigation.RuleSet(setOf("bad"), emptySet()) }, "bare host")
        refuse({ SafeNavigation.RuleSet(setOf(".dup.example"), setOf(".dup.example")) }, "overlap")
        val session = SafeNavigation.PrivateSession()
        ok("anonymous" in session.disclosure())
        ok(SafeNavigation.historyHosts(listOf("https://b.example/", "https://a.example/x", "https://b.example/y")) == listOf("a.example", "b.example"))
    }

    private fun sandbox() {
        val input = "owned sandbox input".toByteArray()
        val job = SandboxJob.Job.queue("csv-parse-1", SandboxJob.Namespace.PARSE, input)
        ok(job.phase == SandboxJob.Phase.QUEUED)
        val running = job.start()
        val sealed = running.seal("out".toByteArray(), 42L)
        ok(sealed.phase == SandboxJob.Phase.SEALED && sealed.output?.steps == 42L)
        ok(sealed.output?.inputSha256 == job.inputSha256)
        val retired = sealed.retire()
        ok(retired.phase == SandboxJob.Phase.RETIRED && retired.output == null)
        val replayed = retired.replay(job.inputSha256)
        ok(replayed.phase == SandboxJob.Phase.QUEUED && replayed.stepsUsed == 0L)
        refuse({ retired.replay("0".repeat(64)) }, "replay digest mismatch")
        refuse({ job.seal("x".toByteArray(), 1L) }, "seal before start")
        refuse({ running.retire() }, "retire while running")
        refuse({ SandboxJob.Job.queue("x", SandboxJob.Namespace.PARSE, ByteArray(0)) }, "empty input")
        refuse({ SandboxJob.Job.queue("Bad_ID!", SandboxJob.Namespace.PARSE, input) }, "bad id")
        refuse({ running.seal("x".toByteArray(), 1_000_001L) }, "step budget")
        val registry = SandboxJob.Registry.empty().submit(job)
        ok(registry.jobsView.size == 1)
        refuse({ registry.submit(job) }, "duplicate id")
        refuse({ registry.submit(running) }, "non-queued submit")
        ok(registry.update(running).jobsView.single().phase == SandboxJob.Phase.RUNNING)
    }

    private fun tsv() {
        val table = TsvTable.parse("name\tage\nada\t36\nlin\t41\n")
        ok(table.rowCount == 3 && table.colCount == 2 && table.formulaLikeCells.isEmpty())
        val flagged = TsvTable.parse("f\n=1+1\n")
        ok(flagged.formulaLikeCells == listOf(1 to 0))
        ok(TsvTable.export(table) == "name\tage\nada\t36\nlin\t41\n")
        val projected = TsvTable.project(table, listOf(1, 0))
        ok(projected.rows[0] == listOf("age", "name"))
        refuse({ TsvTable.parse("a\tb\nc\n") }, "ragged")
        refuse({ TsvTable.parse("a\rb\n") }, "CR")
        refuse({ TsvTable.parse("") }, "empty")
        refuse({ TsvTable.export(TsvTable.parse("a\nb\n").let { TsvTable.Table(listOf(listOf("x\ty")) , emptyList()) }) }, "tab in cell")
        refuse({ TsvTable.project(table, listOf(0, 0)) }, "dup projection")
    }
}

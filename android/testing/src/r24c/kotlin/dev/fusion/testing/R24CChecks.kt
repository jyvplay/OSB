// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.launcher.*
import dev.fusion.maps.*
import dev.fusion.media.*
import dev.fusion.security.InputRearm
import dev.fusion.windowing.ViewportCommit
import dev.fusion.settings.*
import dev.fusion.designsystem.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.*

/** Separately compiled behavioral consumer. Uses real files/processes, not fake Android classes. */
object R24CChecks {
    private var checks = 0
    private fun ok(value: Boolean, message: String = "") { checks++; check(value) { "R24C check $checks failed: $message" } }
    private fun close(a: Double, b: Double, epsilon: Double = 1e-7) = ok(abs(a - b) <= epsilon, "$a != $b")
    private fun refuses(action: () -> Unit) { checks++; check(runCatching(action).isFailure) { "R24C accepted invalid input at $checks" } }
    @JvmStatic fun main(args: Array<String>) {
        if (args.firstOrNull() == "race-write") {
            try { TabletopStore(Path.of(args[1])).update(0, DeskAction.Add("editor", DeskPlane.TABLE, DeskPoint(0.0, 0.0))); println("COMMITTED") }
            catch (_: Exception) { println("CONFLICT_OR_BUSY"); kotlin.system.exitProcess(2) }
            return
        }
        tabletop(); geometry(); appearance(); linksAndPlayback(); inputChanges(); viewportAdmission(); maps(args.firstOrNull())
        println("R24C PASS: $checks assertions. Production geometry/codec/file races/map data/media policy/input latch; NOT native UI, audio output, hardware integrity or full-spec acceptance.")
    }
    private fun viewportAdmission() {
        // Different window origins, physical-pixel sizes, every clipped edge and independent focus/IME facts.
        for (width in listOf(240, 411, 1080, 3840)) for (height in listOf(180, 430, 2160, 2400)) {
            for ((x, y) in listOf(0 to 0, 100 to 60, -300 to 40)) {
                val full = ViewportCommit.Pixels(x, y, x + width, y + height)
                for (edge in 0..3) for (cut in listOf(-100, -2, -1, 0, 1, 2, 80, 3000)) {
                    val visible = when (edge) {
                        0 -> full.copy(left = full.left + cut)
                        1 -> full.copy(top = full.top + cut)
                        2 -> full.copy(right = full.right - cut)
                        else -> full.copy(bottom = full.bottom - cut)
                    }
                    for (focused in listOf(false, true)) for (ime in listOf(false, true))
                        ok(ViewportCommit.admit(focused, ime, full, visible) == (focused && !ime && cut <= 1))
                }
            }
        }
        val full = ViewportCommit.Pixels(0, 70, 1080, 2300)
        ok(ViewportCommit.admit(true, false, full, full))
        // The fourth native failure shape: parent says no IME, but a dialog owns focus / clips its frame.
        ok(!ViewportCommit.admit(false, false, full, full.copy(bottom = 1500)))
        ok(!ViewportCommit.admit(true, false, full, full.copy(bottom = 1500)))
        ok(!ViewportCommit.admit(true, true, full, full))
        for (bad in listOf(null, ViewportCommit.Pixels(0, 0, 0, 0),
            ViewportCommit.Pixels(Int.MIN_VALUE, 0, Int.MAX_VALUE, 100),
            ViewportCommit.Pixels(0, 0, 100001, 100), ViewportCommit.Pixels(0, 20, 100, 19))) {
            ok(!ViewportCommit.admit(true, false, bad, full))
            ok(!ViewportCommit.admit(true, false, full, bad))
        }
    }
    private fun tabletop() {
        val initial = TabletopDocument.initial(); val snapshot = TabletopCodec.encode(initial)
        ok(initial.scene.pins.size == 8)
        refuses { (initial.scene.pins as MutableList).clear() }
        val owned = initial.scene.pins.toMutableList(); val scene = TabletopScene(owned); owned.clear(); ok(scene.pins.size == 8)
        val destination = DeskPoint(70.0, 100.0)
        val moved = TabletopEngine.apply(initial, DeskAction.MovePin(1, DeskPlane.BACK, destination))
        ok(moved.revision == 1L && moved.scene.pins.first().plane == DeskPlane.BACK && moved.scene.pins.first().point == destination)
        ok(TabletopCodec.encode(initial).contentEquals(snapshot))
        ok(TabletopEngine.apply(moved, DeskAction.MovePin(1, DeskPlane.BACK, destination)) === moved)
        var doc = TabletopEngine.apply(initial, DeskAction.Pile(setOf(1L, 2L), "Work"))
        val pileId = doc.scene.piles.single().id
        ok(doc.scene.pins.count { it.pile == pileId } == 2)
        refuses { TabletopEngine.apply(doc, DeskAction.MovePin(1, DeskPlane.LEFT, destination)) }
        doc = TabletopEngine.apply(doc, DeskAction.MovePile(pileId, DeskPlane.LEFT, destination))
        ok(doc.scene.pins.filter { it.pile == pileId }.all { it.plane == DeskPlane.LEFT && it.point == destination })
        doc = TabletopEngine.apply(doc, DeskAction.Focus(DeskPlane.LEFT))
        doc = TabletopEngine.apply(doc, DeskAction.Camera(DeskPlane.LEFT, DeskCamera(DeskPoint(500.0, -300.0), 2.0)))
        val savedScene = doc.scene
        doc = TabletopEngine.apply(doc, DeskAction.SaveView("Left wall")); val view = doc.views.single()
        refuses { TabletopEngine.apply(doc, DeskAction.SaveView("LEFT WALL")) }
        doc = TabletopEngine.apply(doc, DeskAction.Unpile(pileId))
        ok(doc.scene.piles.isEmpty() && doc.scene.pins.size == 8 && doc.scene.pins.all { it.pile == null })
        ok(doc.views.single().scene == savedScene && doc.scene != savedScene)
        doc = TabletopEngine.apply(doc, DeskAction.LoadView(view.id)); ok(doc.scene == savedScene)
        doc = TabletopEngine.apply(doc, DeskAction.RemovePin(1)); ok(doc.scene.piles.isEmpty() && doc.scene.pins.single { it.id == 2L }.pile == null)
        doc = TabletopEngine.apply(doc, DeskAction.DeleteView(view.id)); ok(doc.views.isEmpty())
        refuses { TabletopEngine.apply(doc, DeskAction.LoadView(view.id)) }
        refuses { TabletopEngine.apply(doc, DeskAction.Pile(setOf(2L), "One")) }
        refuses { TabletopEngine.apply(doc, DeskAction.Pile(setOf(2L, 99L), "Unknown")) }
        refuses { TabletopEngine.apply(doc, DeskAction.SaveView(" ")) }
        refuses { TabletopEngine.apply(doc, DeskAction.Add("javascript:bad", DeskPlane.TABLE, destination)) }
        refuses { TabletopScene(listOf(initial.scene.pins.first(), initial.scene.pins.first())) }
        refuses { TabletopScene(listOf(initial.scene.pins.first().copy(pile = 999))) }
        refuses { DeskPoint(Double.NaN, 1.0) }; refuses { DeskPoint(1_000_001.0, 0.0) }
        refuses { DeskCamera(zoom = 0.0) }; refuses { DeskCamera(zoom = Double.POSITIVE_INFINITY) }
        for (bytes in listOf(snapshot, TabletopCodec.encode(doc), TabletopCodec.encode(moved))) {
            ok(TabletopCodec.encode(TabletopCodec.decode(bytes)).contentEquals(bytes))
            for (size in 0 until bytes.size) refuses { TabletopCodec.decode(bytes.copyOf(size)) }
            for (index in bytes.indices step 7) { val bad = bytes.copyOf(); bad[index] = (bad[index].toInt() xor 1).toByte(); refuses { TabletopCodec.decode(bad) } }
            refuses { TabletopCodec.decode(bytes + byteArrayOf(0)) }
        }
        // Correct-checksum hostile payloads reach the parser rather than just the digest check.
        fun resign(bytes: ByteArray): ByteArray { val p = bytes.copyOf(bytes.size - 32); return p + MessageDigest.getInstance("SHA-256").digest(p) }
        val nan = snapshot.copyOf(); ByteBuffer.wrap(nan).putDouble(28, Double.NaN); refuses { TabletopCodec.decode(resign(nan)) }
        val future = snapshot.copyOf(); ByteBuffer.wrap(future).putInt(4, 2); refuses { TabletopCodec.decode(resign(future)) }
        val huge = snapshot.copyOf(); ByteBuffer.wrap(huge).putInt(124, Int.MAX_VALUE); refuses { TabletopCodec.decode(resign(huge)) }
        refuses { TabletopCodec.decode(ByteArray(TabletopCodec.MAX_BYTES + 1)) }
        var capacity = initial
        repeat(504) { capacity = TabletopEngine.apply(capacity, DeskAction.Add("notes", DeskPlane.RIGHT, destination)) }
        ok(capacity.scene.pins.size == 512)
        refuses { TabletopEngine.apply(capacity, DeskAction.Add("notes", DeskPlane.RIGHT, destination)) }
        repeat(16) { capacity = TabletopEngine.apply(capacity, DeskAction.SaveView("View $it")) }
        ok(capacity.views.size == 16 && TabletopCodec.encode(capacity).size < TabletopCodec.MAX_BYTES)
        ok(TabletopCodec.decode(TabletopCodec.encode(capacity)) == capacity)
        refuses { TabletopEngine.apply(capacity, DeskAction.SaveView("Overflow")) }
        val directory = Files.createTempDirectory("fusion-tabletop-contract-")
        try {
            val first = TabletopStore(directory); val second = TabletopStore(directory)
            ok(first.load() == initial && second.load() == initial)
            val written = first.update(0, DeskAction.MovePin(1, DeskPlane.RIGHT, destination))
            ok(second.load() == written)
            val file = directory.resolve("tabletop-v1.bin"); val before = Files.readAllBytes(file)
            refuses { second.update(0, DeskAction.RemovePin(1)) }; ok(Files.readAllBytes(file).contentEquals(before))
            refuses { first.update(written.revision, DeskAction.Pile(setOf(1, 2), "Cross-plane")) }
            ok(Files.readAllBytes(file).contentEquals(before))
            Files.write(file, before.copyOf(before.size - 1)); val corrupt = Files.readAllBytes(file)
            refuses { first.load() }; refuses { first.update(1, DeskAction.RemovePin(1)) }; ok(Files.readAllBytes(file).contentEquals(corrupt))
            Files.delete(file); val other = directory.resolve("other.bin"); Files.write(other, before); Files.createSymbolicLink(file, other)
            refuses { first.load() }; ok(Files.readAllBytes(other).contentEquals(before)); Files.delete(file)
            val race = directory.resolve("race")
            val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
            val command = listOf(java, "-cp", System.getProperty("java.class.path"), R24CChecks::class.java.name, "race-write", race.toString())
            val a = ProcessBuilder(command).redirectErrorStream(true).start(); val b = ProcessBuilder(command).redirectErrorStream(true).start()
            try {
                ok(a.waitFor(30, TimeUnit.SECONDS)); ok(b.waitFor(30, TimeUnit.SECONDS))
                val exits = listOf(a.exitValue(), b.exitValue()).sorted(); ok(exits == listOf(0, 2), "Real concurrent writer exits: $exits")
                ok(TabletopStore(race).load().revision == 1L && TabletopStore(race).load().scene.pins.size == 9)
            } finally { if (a.isAlive) a.destroyForcibly(); if (b.isAlive) b.destroyForcibly() }
        } finally { Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
    }
    private fun geometry() {
        val quad = listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0).map { (x, y) -> TabletopProjection.project(DeskPlane.TABLE, x, y) }
        val area = abs(quad.indices.sumOf { i -> val a = quad[i]; val b = quad[(i + 1) % 4]; a.x * b.y - b.x * a.y }) / 2
        close(area, .8316); ok(area > .8); close(area, TabletopProjection.TABLE_AREA)
        for (plane in DeskPlane.entries) for (focus in listOf(false, true)) for (u in 0..20) for (v in 0..19) {
            val x = u / 20.0; val y = v / 20.0; val p = TabletopProjection.project(plane, x, y, focus)
            val round = TabletopProjection.inverse(plane, p.x, p.y, focus)
            close(round.x, x); close(round.y, y)
            if (!focus && u in 1..19 && v in 1..19) ok(TabletopProjection.planeAt(p.x, p.y) == plane)
        }
        refuses { TabletopProjection.inverse(DeskPlane.LEFT, 0.0, 1.0) }
        refuses { TabletopProjection.project(DeskPlane.TABLE, Double.NaN, 0.0) }
        for (w in listOf(240.0, 360.0, 600.0, 1920.0)) for (h in listOf(180.0, 400.0, 1080.0)) for (z in listOf(.5, 1.0, 3.0)) {
            val camera = DeskCamera(DeskPoint(200.0, -100.0), z)
            for (plane in DeskPlane.entries) for (focus in listOf(false, true)) {
                val world = DeskPoint(210.0, -90.0); val p = TabletopProjection.toScreen(world, plane, camera, w, h, focus)
                val r = TabletopProjection.toWorld(p.x, p.y, plane, camera, w, h, focus); close(r.x, world.x); close(r.y, world.y)
            }
        }
    }
    private fun appearance() {
        for (palette in ProfessionalPalette.entries) {
            val colors = ProfessionalPalettes.colors(palette)
            colors.textPairs().forEach { (a, b) -> ok(Contrast.ratio(a, b) >= 4.5, palette.id) }
            ok(Contrast.ratio(colors.outline, colors.background) >= 3)
            for (layout in DesktopLayout.entries) for (scene in DesktopScene.entries) for (zoom in listOf(false, true)) {
                val a = Appearance(palette, layout, zoom, scene); ok(AppearancePreferences.read(AppearancePreferences.encode(a)) == a)
                ok(AppearancePreferences.canonical(AppearancePreferences.encode(a)))
            }
        }
        ok(!AppearancePreferences.canonical(mapOf("desktop_scene" to "pretend-3d")))
        for (width in listOf(48.0, 120.0, 240.0, 360.0, 600.0, 1920.0, 3840.0)) for (font in listOf(.5, 1.0, 1.5, 2.0, 5.0))
            for (keyboard in listOf(false, true)) for (count in 0..64) {
                val dock = DesktopMetrics.dock(width, font, keyboard, count)
                ok(dock.slot >= 48 && dock.height >= 48 && dock.glyph * 1.25 <= dock.slot)
                ok(dock.viewport >= 48 && dock.viewport <= width && dock.gap >= 8)
            }
        val parent = DesktopMetrics.Bounds(0.0, 0.0, 360.0, 640.0)
        ok(DesktopMetrics.targetViolations(DesktopMetrics.Bounds(0.0, 0.0, 48.0, 48.0), parent).isEmpty())
        ok(DesktopMetrics.targetViolations(DesktopMetrics.Bounds(-1.0, 0.0, 30.0, 48.0), parent).toSet() == setOf("small-target", "clipped-target"))
        refuses { DesktopMetrics.dock(Double.NaN, 1.0, false, 8) }
    }
    private fun linksAndPlayback() {
        val youtube = "https://www.youtube.com/watch?v=abcdefghijk"
        for (url in listOf(youtube, "$youtube&utm_source=test#fragment", "https://youtu.be/abcdefghijk?si=tracking", "https://m.youtube.com/shorts/abcdefghijk")) {
            val link = ServiceLink.parse(url); ok(link.service == MediaService.YOUTUBE && link.url == youtube)
        }
        val spotify = "https://open.spotify.com/track/0123456789ABCDEFGHIJKL"
        ok(ServiceLink.parse("$spotify?si=tracking").url == spotify)
        ok(ServiceLink.parse("https://soundcloud.com/artist/track?utm=tag").url == "https://soundcloud.com/artist/track")
        ok(ServiceLink.parse("https://soundcloud.com/artist/sets/collection").service == MediaService.SOUNDCLOUD)
        val bad = listOf("javascript:alert(1)", "http://youtu.be/abcdefghijk", "https://youtu.be.evil.test/abcdefghijk", "https://youtube.com@evil.test/watch?v=abcdefghijk",
            "https://user@youtube.com/watch?v=abcdefghijk", "https://youtube.com:443/watch?v=abcdefghijk", "https://youtube.com/watch?v=abcdefghijk&v=abcdefghijk",
            "https://youtube.com/watch?v=abc%2fdefghi", "https://youtube.com/watch?v=abcdefghijk/", "https://youtube.com./watch?v=abcdefghijk", "https://open.spotify.com/track/short",
            "https://soundcloud.com/artist/track?secret_token=x", "https://soundcloud.com/connect/redirect", "https://soundcloud.com/artist/%2e%2e", "https://youtube.com\\@evil.test/watch?v=abcdefghijk", "$youtube\n")
        bad.forEach { refuses { ServiceLink.parse(it) } }
        val playback = PlaybackEpoch(); val a = playback.begin()
        ok(!playback.play(a)); ok(playback.prepared(a)); ok(playback.phase == PlaybackEpoch.Phase.READY)
        ok(playback.play(a)); ok(playback.pause()); ok(!playback.pause()); ok(playback.play(a)); ok(playback.ended(a)); ok(playback.play(a))
        playback.close(); ok(!playback.prepared(a) && !playback.play(a) && !playback.ended(a) && !playback.fail(a))
        val b = playback.begin(); ok(!playback.prepared(a)); ok(playback.prepared(b)); ok(playback.phase == PlaybackEpoch.Phase.READY)
        playback.close(); ok(playback.phase == PlaybackEpoch.Phase.CLOSED)
    }
    private fun inputChanges() {
        for (word in 0 until 65536) {
            val guard = InputRearm(); var remembered = guard.epoch
            repeat(8) { index ->
                when ((word ushr (2 * index)) and 3) {
                    0 -> { val old = guard.epoch; guard.changed(); ok(!guard.armed && !guard.permits(old)) }
                    1 -> { val expected = remembered == guard.epoch; ok(guard.restart(remembered) == expected) }
                    2 -> remembered = guard.epoch
                    3 -> ok(guard.permits(remembered) == (guard.armed && remembered == guard.epoch))
                }
            }
        }
    }
    private fun maps(asset: String?) {
        val origin = EarthGeometry.project(GeoPoint(0.0, 0.0)); close(origin.x, .5); close(origin.y, .5)
        close(EarthGeometry.meters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 180.0)), Math.PI * EarthGeometry.MEAN_RADIUS_METERS, 1e-5)
        close(EarthGeometry.meters(GeoPoint(40.7128, -74.006), GeoPoint(51.5074, -.1278)), 5_570_229.873656523, .001)
        for (lat in -84..84 step 3) for (lon in -179..179 step 3) {
            val p = GeoPoint(lat.toDouble(), lon.toDouble()); val v = EarthGeometry.project(p); val r = EarthGeometry.unproject(v.x, v.y)
            close(p.latitude, r.latitude); close(p.longitude, r.longitude)
            close(EarthGeometry.meters(p, p), 0.0)
        }
        ok(EarthGeometry.project(GeoPoint(90.0, 0.0)).y == 0.0)
        close(EarthGeometry.unproject(1.0, .5).longitude, -180.0)
        val camera = MapCamera(GeoPoint(0.0, 179.0), 4)
        val across = camera.screen(GeoPoint(0.0, -179.0), 600.0, 400.0); ok(across.x in 300.0..330.0)
        val r = camera.at(across.x, across.y, 600.0, 400.0); close(r.longitude, -179.0); close(r.latitude, 0.0)
        refuses { GeoPoint(Double.NaN, 0.0) }; refuses { GeoPoint(0.0, 181.0) }; refuses { MapCamera(zoom = 19) }
        refuses { camera.pan(Double.POSITIVE_INFINITY, 0.0) }; refuses { EarthGeometry.unproject(1e15, 0.0) }
        var book = MapBookmarks().add("Raleigh", GeoPoint(35.7796, -78.6382)).add("London", GeoPoint(51.5074, -.1278))
        val bytes = MapBookmarkCodec.encode(book); ok(MapBookmarkCodec.decode(bytes) == book)
        for (n in 0 until bytes.size) refuses { MapBookmarkCodec.decode(bytes.copyOf(n)) }
        refuses { MapBookmarkCodec.decode(bytes + byteArrayOf(1)) }
        refuses { (book.pins as MutableList).clear() }
        book = book.remove(1); ok(book.pins.single().name == "London")
        repeat(255) { book = book.add("Pin $it", GeoPoint(0.0, 0.0)) }; ok(book.pins.size == 256)
        refuses { book.add("Overflow", GeoPoint(0.0, 0.0)) }
        ok(MapBookmarkCodec.decode(MapBookmarkCodec.encode(book)) == book)
        if (asset != null) {
            val land = Files.newInputStream(Path.of(asset)).use(LandOutlines::read)
            ok(land.size == 128 && land.sumOf { it.size } == 5143)
            ok(land.any { ring -> ring.any { it.longitude < -100 && it.latitude > 40 } })
            ok(land.any { ring -> ring.any { it.longitude > 110 && it.latitude < -25 } })
            refuses { (land as MutableList).clear() }
        } else error("Actual pinned coastline asset required; not replaced with generated map data")
        refuses { LandOutlines.read("FUSION-LAND-1\n>\nNaN,0\n".byteInputStream()) }
        refuses { LandOutlines.read(ByteArray(LandOutlines.MAX_BYTES + 1).inputStream()) }
    }
}

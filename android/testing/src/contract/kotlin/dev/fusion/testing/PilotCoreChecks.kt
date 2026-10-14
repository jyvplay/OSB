package dev.fusion.testing

import dev.fusion.windowing.*
import dev.fusion.model.*
import dev.fusion.documents.*
import dev.fusion.perf.*
import dev.fusion.beam.QualityGovernor
import java.io.*
import kotlin.random.Random
import kotlin.math.min

/** Actual JVM execution of production code. No Android substitutes or protocol emulators. */
object PilotCoreChecks {
    private var assertions = 0
    private fun verify(condition: Boolean, description: String) {
        assertions++
        check(condition) { description }
    }
    private fun rejects(description: String, action: () -> Unit) {
        var failed = false
        try { action() } catch (_: Exception) { failed = true }
        verify(failed, description)
    }
    private fun invariant(s: DesktopState) {
        verify(s.windows.size <= WindowEngine.MAX_WINDOWS, "bounded windows")
        verify(s.windows.map { it.id }.distinct().size == s.windows.size, "unique IDs")
        verify(s.windows.map { it.z } == s.windows.indices.toList(), "dense total z-order")
        verify(s.activeId == null || s.windows.any { it.id == s.activeId && !it.minimized && it.workspace == s.workspace }, "active window is visible in active workspace")
        for (w in s.windows) {
            val b = w.bounds
            verify(b.finite, "finite geometry")
            verify(b.width >= s.density.minWidth && b.height >= s.density.minHeight, "density minimums")
            verify(b.x + b.width >= min(48f, s.screenWidth) && b.x <= s.screenWidth - min(48f, s.screenWidth), "title horizontally reachable")
            verify(b.y >= 0 && b.y <= maxOf(0f, s.screenHeight - min(48f, s.screenHeight)), "title vertically reachable")
        }
    }
    @JvmStatic fun main(args: Array<String>) {
        var s = DesktopState()
        val open = WindowCommand.Open("editor", "Editor", Bounds(0f, 0f, 500f, 400f), "once")
        s = WindowEngine.reduce(s, open)
        verify(s == WindowEngine.reduce(s, open), "retryable Open must be idempotent")
        verify(s == WindowEngine.reduce(s, WindowCommand.Focus(1)), "Focus must be idempotent")
        s = WindowEngine.reduce(s, WindowCommand.SwitchWorkspace(1))
        s = WindowEngine.reduce(s, WindowCommand.Open("files", "Files", Bounds(0f, 0f, 400f, 300f)))
        s = WindowEngine.reduce(s, WindowCommand.Focus(1))
        verify(s.workspace == 0 && s.activeId == 1L, "cross-workspace Focus must reveal target")
        s = WindowEngine.reduce(s, WindowCommand.Minimize(2))
        verify(s.activeId == 1L, "minimizing a background window must not steal focus")
        s = WindowEngine.reduce(s, WindowCommand.Close(2))
        verify(s.activeId == 1L, "closing background window preserves focus")
        s = WindowEngine.reduce(s, WindowCommand.SetDensity(WindowDensity.TOUCH))
        s = WindowEngine.reduce(s, WindowCommand.TileWindow(1, Tile.LEFT, 360f, 640f))
        verify(s.windows.single().bounds.width >= 320f, "narrow tiling must retain touch minimum")
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            verify(WindowEngine.reduce(s, WindowCommand.SetViewport(bad, 600f)) == s, "reject invalid viewport")
            verify(WindowEngine.reduce(s, WindowCommand.Move(1, bad, 0f, 800f, 600f)) == s, "reject invalid pointer delta")
            verify(WindowEngine.reduce(s, WindowCommand.SetBounds(1, Bounds(bad, 0f, 400f, 300f))) == s, "reject invalid bounds")
        }
        val random = Random(20261013)
        repeat(5_000) { step ->
            val id = if (s.windows.isEmpty()) 999L else s.windows[random.nextInt(s.windows.size)].id
            val command = when (random.nextInt(12)) {
                0 -> WindowCommand.Open("editor", "Document $step", Bounds(random.nextFloat()*1000-500, 20f, 500f, 400f), "event-$step")
                1 -> WindowCommand.Close(id)
                2 -> WindowCommand.Focus(id)
                3 -> WindowCommand.Minimize(id)
                4 -> WindowCommand.SetViewport(listOf(40f, 320f, 800f, 1920f)[random.nextInt(4)], 600f)
                5 -> WindowCommand.SetDensity(WindowDensity.entries[random.nextInt(3)])
                6 -> WindowCommand.SetBounds(id, Bounds(random.nextFloat()*2000-1000, random.nextFloat()*2000-1000, random.nextFloat()*2000, random.nextFloat()*1000))
                7 -> WindowCommand.TileWindow(id, Tile.entries[random.nextInt(3)], s.screenWidth, s.screenHeight)
                8 -> WindowCommand.SwitchWorkspace(random.nextInt(-3, 6))
                9 -> WindowCommand.MoveToWorkspace(id, random.nextInt(-3, 6))
                10 -> WindowCommand.FocusNext(random.nextBoolean())
                else -> WindowCommand.Resize(id, random.nextFloat()*100-50, random.nextFloat()*100-50, s.screenWidth, s.screenHeight)
            }
            val next = WindowEngine.reduce(s, command)
            verify(next == WindowEngine.reduce(s, command), "deterministic transition")
            s = next; invariant(s)
        }
        val encoded = ByteArrayOutputStream().also { DesktopCodec.write(s, it) }.toByteArray()
        verify(DesktopCodec.read(ByteArrayInputStream(encoded)) == WindowEngine.restore(s), "desktop recovery roundtrip")
        rejects("truncated desktop rejected") { DesktopCodec.read(ByteArrayInputStream(encoded.copyOf(encoded.size - 1))) }
        val corrupt = encoded.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        rejects("checksum corruption rejected") { DesktopCodec.read(ByteArrayInputStream(corrupt)) }
        rejects("oversized desktop rejected before allocation") { DesktopCodec.read(ByteArrayInputStream(byteArrayOf(127,127,127,127))) }
        rejects("duplicate IDs rejected") { WindowEngine.restore(DesktopState(listOf(
            ShellWindow(1,"editor","one",Bounds(0f,0f,300f,200f),0), ShellWindow(1,"files","two",Bounds(0f,0f,300f,200f),1)))) }
        s = DesktopState()
        repeat(100) { s = WindowEngine.reduce(s, WindowCommand.Open("editor", "Window", Bounds(0f,0f,300f,200f))) }
        verify(s.windows.size == WindowEngine.MAX_WINDOWS, "window cap enforced")
        val start = s.activeId
        repeat(s.windows.size) { s = WindowEngine.reduce(s, WindowCommand.FocusNext()) }
        verify(s.activeId == start, "stable switcher visits every window exactly once")
        println("PASS WM-01/02/05/09 SH-08: reducer invariants, replay, reachability, density, recovery (5,000 transitions)")

        for (module in PilotPolicy.modules) {
            verify(PilotPolicy.mayRun(module.id, emptySet()) == (module.access == PilotAccess.CANDIDATE), "default admission is explicit")
            verify(PilotPolicy.mayRun(module.id, setOf(module.id)) == (module.access != PilotAccess.BLOCKED), "consent never overrides blocked gates")
        }
        verify(!PilotPolicy.mayRun("unknown", setOf("unknown")), "unknown module blocked")
        println("PASS DP-10 SH-07: all module gates, default state, explicit consent, unknown module")
        verify(ThermalPolicy.fromPlatform(1) == ThermalState.WARM, "light is warm")
        verify(ThermalPolicy.fromPlatform(2) == ThermalState.HOT, "moderate disables effects")
        verify(ThermalPolicy.fromPlatform(3) == ThermalState.THROTTLED, "severe throttles")
        verify(ThermalPolicy.fromPlatform(4) == ThermalState.CRITICAL, "critical must stop capture, not reduce quality")
        verify(ThermalPolicy.fromPlatform(-1) == ThermalState.UNKNOWN, "unknown thermal stays unknown")
        val frames = FrameBudget(20)
        verify(frames.p95() == null && frames.jank() == null, "no samples means unmeasured")
        (1..20).forEach { frames.add(it.toFloat()) }
        verify(frames.p95() == 19f, "nearest-rank p95")
        verify(frames.jank(19f) == .05f, "jank fraction")
        rejects("invalid frame sample rejected") { frames.add(Float.NaN) }
        verify(QualityGovernor.selectQuality(180, ThermalState.CRITICAL, true, true, true).mustStop, "critical cast policy must stop")
        verify(QualityGovernor.selectQuality(180, ThermalState.UNKNOWN, true, true, true).mustStop, "unknown thermal cannot choose optimal tier")
        verify(QualityGovernor.selectQuality(180, ThermalState.COOL, true).mustStop, "unknown hardware codec cannot stream")
        verify(QualityGovernor.selectQuality(180, ThermalState.COOL, true, false, true).codec == "AVC", "receiver HEVC alone cannot select sender HEVC")
        println("PASS RG-01/04 CA-13/14: fail-closed codec/thermal policy, thermal mapping and honest empty/percentile statistics")

        val text = "\uFEFFlocal draft\r\n猫 😀\n"
        verify(TextBytes.decode(TextBytes.encode(text)) == text, "UTF-8 exact roundtrip")
        rejects("malformed UTF-8 rejected") { TextBytes.decode(byteArrayOf(0xC3.toByte(), 0x28)) }
        rejects("text cap enforced") { TextBytes.encode("猫".repeat(TextBytes.MAX_BYTES / 2)) }
        val doc = TextDocument("doc-1", "draft.txt", "content://source/1", text, TextBytes.digest("old"))
        verify(doc.saved("older snapshot").dirty, "late edits stay dirty")
        val session = EditorSession(listOf(doc), doc.id, PendingCopy(doc.id,doc.name,doc.sourceUri,"frozen picker payload"))
        val bytes = ByteArrayOutputStream().also { SessionCodec.write(session,it) }.toByteArray()
        verify(SessionCodec.read(ByteArrayInputStream(bytes)) == session, "pending save payload survives serialization")
        rejects("corrupt draft rejected") { SessionCodec.read(ByteArrayInputStream(bytes.copyOf(bytes.size-1))) }
        val destination = File.createTempFile("fusion-contract-", ".txt")
        try {
            VerifiedCopy.write(TextBytes.encode(text), { destination.outputStream() }, { destination.inputStream() })
            verify(destination.readBytes().contentEquals(TextBytes.encode(text)), "real filesystem copy verified")
            rejects("mismatched provider readback rejected") {
                VerifiedCopy.write(TextBytes.encode(text), { ByteArrayOutputStream() }, { ByteArrayInputStream(byteArrayOf()) })
            }
        } finally { check(destination.delete()) }
        println("PASS PR-01 FF-15 FM-18: strict text, durable pending payload, actual temporary-file write/readback and rejection paths")
        println("PASS: $assertions assertions. JVM contracts only; no Android, physical device, network receiver, or latency claim.")
    }
}

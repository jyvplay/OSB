package dev.fusion.testing

import dev.fusion.settings.*
import dev.fusion.media.*
import dev.fusion.launcher.*
import dev.fusion.backup.*
import dev.fusion.format.image.*
import dev.fusion.native.TextFormats
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import java.util.Random

object SubsetChecks {
    private var checks = 0
    private fun eq(expected: Any?, actual: Any?) { checks++; check(expected == actual) { "check $checks expected=$expected actual=$actual" } }
    private fun yes(value: Boolean) = eq(true, value)
    private fun reject(block: () -> Unit) {
        checks++
        try { block() } catch (_: IllegalArgumentException) { return } catch (_: IllegalStateException) { return }
        catch (_: java.io.IOException) { return } catch (_: java.nio.charset.CharacterCodingException) { return }
        error("Attack accepted: $checks")
    }
    private fun reseal(b: ByteArray): ByteArray {
        MessageDigest.getInstance("SHA-256").digest(b.copyOf(b.size - 32)).copyInto(b, b.size - 32); return b
    }
    @JvmStatic fun main(args: Array<String>) {
        settings(); subtitles(); images(); backups(); launcher(); utf8()
        println("R12 continuation subsets: $checks assertions passed across five new module kernels + UTF8 repair.")
    }
    fun settings() {
        val aliases = mutableListOf("FILE"); val defs = mutableListOf(SettingDef("theme", "TITLE", aliases), SettingDef("density", "Density"))
        val c = SettingsCatalog(defs); defs.clear(); aliases.clear()
        val old = Locale.getDefault()
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR")); eq("theme", c.search("file").single().id); eq("theme", c.search("title").single().id) }
        finally { Locale.setDefault(old) }
        val values = mapOf("theme" to "α🙂\n=\u0000", "density" to "")
        eq(values, c.decode(c.encode(values))); eq(emptyMap<String, String>(), c.decode(c.encode(emptyMap())))
        val bytes = c.encode(mapOf("theme" to "dark")); eq("FSET1\ntheme=ZGFyaw==\n", String(bytes))
        // Header-only is a valid empty partial snapshot, not a detectable truncation.
        for (i in 1 until bytes.size) if (i != 6) reject { c.decode(bytes.copyOf(i)) }
        eq(emptyMap<String, String>(), c.decode(bytes.copyOf(6)))
        reject { c.decode(bytes + 0) }; reject { c.decode("FSET1\nunknown=eA==\n".toByteArray()) }
        reject { c.decode("FSET1\ntheme=eA==\ntheme=eA==\n".toByteArray()) }
        reject { c.decode("FSET1\ntheme=eA\n".toByteArray()) }
        reject { c.decode("FSET1\ntheme=/w==\n".toByteArray()) }
        reject { c.encode(mapOf("theme" to "\uD800")) }
        reject { c.encode(mapOf("theme" to "a".repeat(4097))) }
        eq(4096, c.decode(c.encode(mapOf("theme" to "a".repeat(4096))))["theme"]!!.length)
        reject { SettingsCatalog(listOf(SettingDef("a", "A"), SettingDef("a", "B"))) }
        reject { c.decode(ByteArray(SettingsCatalog.MAX_FRAME + 1)) }
        // A snapshot is an export, not an instruction: unknown IDs/aliases cannot create preferences.
        reject { c.encode(mapOf("FILE" to "dark")) }
        reject { c.decode("FSET1\n\n".toByteArray()) }
    }
    fun subtitles() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nα🙂\nsecond\n\n2\n00:00:01,500 --> 00:00:03,000\n<script>literal</script>\n"
        val s = SubRip.parse(text.toByteArray())
        eq(0, s.at(999).size); eq(1, s.at(1000).size); eq(2, s.at(1500).size)
        eq(1, s.at(2000).size); eq(0, s.at(3000).size)
        eq(s.cues(), SubRip.parse(s.encode()).cues())
        eq(s.cues(), SubRip.parse(("\uFEFF" + text.replace("\n", "\r\n")).toByteArray()).cues())
        eq(0, SubRip.parse(byteArrayOf()).cues().size)
        eq(s.cues(), SubRip.read(text.byteInputStream()).cues())
        reject { SubRip.read(ByteArray(SubRip.MAX_BYTES + 1).inputStream()) }
        val file = java.nio.file.Files.createTempFile("real-subtitles", ".srt")
        try {
            java.nio.file.Files.write(file, text.toByteArray())
            java.nio.file.Files.newInputStream(file).use { eq(s.cues(), SubRip.read(it).cues()) }
        } finally { java.nio.file.Files.delete(file) }
        reject { s.at(-1) }; reject { SubRip.parse(byteArrayOf(0xff.toByte())) }
        for (bad in listOf(text.replace("01,000", "60,000"), text.replace("02,000", "01,000"), text.replace("\n2\n", "\n1\n"), text.replace(" --> ", "->"), text.replace("α", "\u0000"), "1\n00:00:00,000 --> 00:00:00,001\n")) reject { SubRip.parse(bad.toByteArray()) }
        reject { SubRip.parse(ByteArray(SubRip.MAX_BYTES + 1)) }
        reject { SubRip.parse("1\n00:00:00,000 --> 00:00:00,001\n${"x".repeat(8192)}".toByteArray()) }
        reject { SubRip.parse("1\n00:00:00,000 --> 00:00:00,001\n${List(33) { "x" }.joinToString("\n")}".toByteArray()) }
        val maximum = SubRip.parse("1\n99:59:59,998 --> 99:59:59,999\nx".toByteArray())
        eq(1, maximum.at(359999998).size); eq(0, maximum.at(Long.MAX_VALUE).size)
        val many = (1..4096).joinToString("\n\n") { "$it\n00:00:00,000 --> 00:00:00,001\nx" }
        eq(4096, SubRip.parse(many.toByteArray()).at(0).size)
        reject { SubRip.parse((many + "\n\n4097\n00:00:00,000 --> 00:00:00,001\nx").toByteArray()) }
    }
    fun images() {
        val input = intArrayOf(1, 2, 3, 4, 5, 6); val im = ArgbImage(3, 2, input); input[0] = 99
        eq(listOf(4, 1, 5, 2, 6, 3), im.rotateClockwise().pixels().toList())
        eq(listOf(3, 6, 2, 5, 1, 4), im.rotateClockwise(-1).pixels().toList())
        eq(listOf(2, 3, 5, 6), im.crop(1, 0, 3, 2).pixels().toList())
        eq(listOf(3, 2, 1, 6, 5, 4), im.flip(true).pixels().toList())
        eq(listOf(4, 5, 6, 1, 2, 3), im.flip(false).pixels().toList())
        im.pixels()[0] = 88; eq(1, im.pixels()[0])
        reject { im.crop(Int.MIN_VALUE, 0, Int.MAX_VALUE, 1) }; reject { im.crop(1, 0, 1, 1) }
        reject { ArgbImage(Int.MAX_VALUE, Int.MAX_VALUE, intArrayOf()) }; reject { ArgbImage(0, 1, intArrayOf()) }
        reject { ArgbImage(2, 2, intArrayOf(1)) }
        val r = Random(120013)
        repeat(300) {
            val w = r.nextInt(32) + 1; val h = r.nextInt(32) + 1
            val p = IntArray(w * h) { r.nextInt() }; val a = ArgbImage(w, h, p)
            yes(p.contentEquals(a.rotateClockwise().rotateClockwise().rotateClockwise().rotateClockwise().pixels()))
            yes(p.contentEquals(a.flip(true).flip(true).pixels()))
            yes(p.contentEquals(a.flip(false).flip(false).pixels()))
            yes(p.contentEquals(a.crop(0, 0, w, h).pixels()))
            eq(h, a.rotateClockwise().width); eq(w, a.rotateClockwise().height)
            yes(a.rotateClockwise(Int.MIN_VALUE).pixels().contentEquals(p))
        }
    }
    fun backups() {
        val map = mapOf("a.md" to byteArrayOf(0, 1, -1), "B.md" to "α🙂".toByteArray())
        val frame = BackupContent.encode(map); val restored = BackupContent.decode(frame)
        eq(map.keys, restored.keys); map.forEach { (k, v) -> yes(v.contentEquals(restored[k])) }
        yes(frame.contentEquals(BackupContent.encode(map.entries.reversed().associate { it.toPair() })))
        for (i in frame.indices) reject { BackupContent.decode(frame.copyOf(i)) }
        for (i in frame.indices) reject { BackupContent.decode(frame.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }) }
        reject { BackupContent.decode(frame + 0) }
        reject { BackupContent.decode(reseal(frame.copyOf().also { ByteBuffer.wrap(it).putInt(4, Int.MAX_VALUE) })) }
        reject { BackupContent.decode(reseal(frame.copyOf().also { ByteBuffer.wrap(it).putInt(8, Int.MAX_VALUE) })) }
        reject { BackupContent.decode(reseal(frame.copyOf().also { it[12] = '/'.code.toByte() })) }
        for (name in listOf("../x.md", "x/y.md", "x\\y.md", ".x.md", "a..md", "α.md", "x.md\u0000")) reject { BackupContent.encode(mapOf(name to byteArrayOf())) }
        reject { BackupContent.encode(mapOf("a.md" to byteArrayOf(), "A.md" to byteArrayOf())) }
        reject { BackupContent.encode((0..64).associate { "f$it.md" to byteArrayOf() }) }
        reject { BackupContent.encode(mapOf("a.md" to ByteArray(BackupContent.MAX_FILE_BYTES + 1))) }
        val max = (0 until 32).associate { "f$it.md" to ByteArray(BackupContent.MAX_FILE_BYTES) }
        eq(32, BackupContent.decode(BackupContent.encode(max)).size)
        reject { BackupContent.encode(max + ("extra.md" to byteArrayOf(1))) }
        eq(0, BackupContent.decode(BackupContent.encode(emptyMap())).size)
        restored["a.md"]!![0] = 44; eq(0.toByte(), BackupContent.decode(frame)["a.md"]!![0])
        // Not authenticated: a deliberately recomputed checksum on valid changed data IS accepted.
        val forged = frame.copyOf(); forged[forged.size - 33] = 42
        yes(BackupContent.decode(reseal(forged))["a.md"]!!.last() == 42.toByte())
    }
    fun launcher() {
        val mutable = mutableListOf("b", "missing", "a"); val pins = PinnedCatalog(mutable); mutable.clear()
        val catalog = mutableListOf(LaunchTarget("a", "FILE"), LaunchTarget("b", "Beta"), LaunchTarget("c", "C", true), LaunchTarget("d", "D"))
        eq(listOf("b", "a", "c"), pins.shelf(catalog).map { it.key }); eq(listOf("b", "missing", "a"), pins.pins())
        eq(listOf("a", "b", "missing"), pins.move("a", 0).pins()); eq(listOf("missing", "a", "b"), pins.move("b", 2).pins())
        eq(listOf("b", "missing", "a"), pins.pin("a").pins()); eq(listOf("b", "a"), pins.unpin("missing").pins())
        val old = Locale.getDefault()
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR")); eq("a", pins.search(catalog, "file").single().key) } finally { Locale.setDefault(old) }
        catalog.add(LaunchTarget("missing", "Restored")); eq(listOf("b", "missing", "a", "c"), pins.shelf(catalog).map { it.key })
        reject { pins.shelf(catalog + catalog.first()) }; reject { pins.move("missing", -1) }; reject { pins.move("no", 0) }
        reject { PinnedCatalog(listOf("a", "a")) }; reject { pins.pin("\n") }
        reject { PinnedCatalog((0..128).map { "p$it" }) }
        eq(128, PinnedCatalog((0..127).map { "p$it" }).pins().size)
        reject { pins.search((0..4096).map { LaunchTarget("k$it", "a") }, "") }
    }
    fun utf8() {
        for (s in listOf("", "hello", "α🙂\u0000", "\uFEFF")) yes(TextFormats.isProbablyUtf8(s.toByteArray()))
        for (b in listOf(byteArrayOf(-1), byteArrayOf(0xc0.toByte(), 0x80.toByte()), byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()), byteArrayOf(0xf4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()), "🙂".toByteArray().copyOf(3))) eq(false, TextFormats.isProbablyUtf8(b))
    }
}

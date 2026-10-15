package dev.fusion.testing

import dev.fusion.settings.*
import dev.fusion.media.*
import dev.fusion.backup.*
import dev.fusion.launcher.*
import dev.fusion.format.image.*
import dev.fusion.native.TextFormats
import java.nio.ByteBuffer
import java.util.Base64

/** Test-only pipe adapter; not a production RPC or an Android mock. */
object SubsetOracle {
    @JvmStatic fun main(args: Array<String>) {
        val enc = Base64.getEncoder(); val dec = Base64.getDecoder()
        val settings = SettingsCatalog(listOf(SettingDef("theme", "Theme"), SettingDef("density", "Density")))
        generateSequence(::readLine).forEach { line ->
            try {
                val f = line.split('\t')
                val result = when (f[0]) {
                    "I" -> {
                        val bytes = ByteBuffer.wrap(dec.decode(f[4])); val p = IntArray(bytes.remaining() / 4) { bytes.int }
                        val im = ArgbImage(f[1].toInt(), f[2].toInt(), p).rotateClockwise(f[3].toInt())
                        val out = ByteBuffer.allocate(p.size * 4); im.pixels().forEach { out.putInt(it) }
                        "${im.width},${im.height},${enc.encodeToString(out.array())}"
                    }
                    "S" -> enc.encodeToString(settings.encode(mapOf("theme" to String(dec.decode(f[1]), Charsets.UTF_8), "density" to String(dec.decode(f[2]), Charsets.UTF_8))))
                    "D" -> enc.encodeToString(settings.encode(settings.decode(dec.decode(f[1]))))
                    "B" -> enc.encodeToString(BackupContent.encode(f.drop(1).associate { it.substringBefore(':') to dec.decode(it.substringAfter(':')) }))
                    "R" -> enc.encodeToString(BackupContent.encode(BackupContent.decode(dec.decode(f[1]))))
                    "T" -> SubRip.parse(dec.decode(f[1])).at(f[2].toLong()).joinToString(",") { enc.encodeToString(it.text.toByteArray(Charsets.UTF_8)) }
                    "L" -> {
                        val pins = if (f[1].isEmpty()) emptyList() else f[1].split(',')
                        val targets = if (f[2].isEmpty()) emptyList() else f[2].split(',').map { v -> LaunchTarget(v.substringBefore(':'), v.substringBefore(':'), v.substringAfter(':') == "1") }
                        PinnedCatalog(pins).shelf(targets).joinToString(",") { it.key }
                    }
                    "V" -> TextFormats.isProbablyUtf8(dec.decode(f[1])).toString()
                    else -> error("Unknown test operation")
                }
                println(result)
            } catch (_: Exception) { println("REFUSED") }
        }
    }
}

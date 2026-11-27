// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import java.nio.file.Files
import java.nio.file.Path
import dev.fusion.windowing.GeometryClamp
import dev.fusion.index.BoyerMooreSearch

/** Compile repaired production files together, then execute actual APIs/limits. */
object R176BuildSurfaceChecks {
    @JvmStatic fun main(args: Array<String>) {
        val dir = Path.of(args[0])
        when (args.getOrNull(1)) {
            "geometry" -> {
                Files.readAllLines(dir.resolve("geometry.tsv")).forEachIndexed { index, line ->
                    val f = line.split('\t')
                    val result = try {
                        val clamp = GeometryClamp.empty()
                        if (f[0].isNotEmpty()) for (area in f[0].split(';')) {
                            val a = area.split(',').map(String::toInt)
                            clamp.addWorkArea(a[0], a[1], a[2], a[3])
                        }
                        val p = f[1].split(',').map(String::toInt)
                        val r = clamp.clamp(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7])
                        "${r.left},${r.top},${r.right},${r.bottom}"
                    } catch (_: IllegalArgumentException) { "REF" }
                    println("$index\t$result")
                }
                return
            }
            "search" -> {
                Files.readAllLines(dir.resolve("search.tsv")).forEachIndexed { index, line ->
                    val f = line.split('\t')
                    fun latin1(hex: String): String = hex.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
                    val result = BoyerMooreSearch.from(latin1(f[1])).indexOf(latin1(f[0]))
                    println("$index\t$result")
                }
                return
            }
        }
        Files.readAllLines(dir.resolve("limits.tsv")).forEach { line ->
            val f = line.split('\t')
            val constant = Class.forName(f[0]).getField(f[1]).get(null) as Number
            check(constant.toLong() == f[2].toLong())
            println("CONST\t${f[0]}.${f[1]}\t$constant")
        }
        var checks = 0
        fun yes(value: Boolean) { check(value); checks++ }
        fun refuses(action: () -> Unit) {
            try { action() } catch (_: IllegalArgumentException) { checks++; return }
            error("expected refusal")
        }
        yes(dev.fusion.documents.DocumentWordCountEstimator.count("one two").words == 2)
        yes(dev.fusion.documents.PlainTextNormalizer.plain().normalize("a  b") == "a b")
        yes(!dev.fusion.beam.BeamOfferValidator.empty().validate("one", "sender", 1, "text/plain", 0).valid)
        val cpu = dev.fusion.capabilities.CpuCapabilities.empty().parse("vendor_id: Vendor\nmodel name: CPU\nprocessor: 0\nflags: sse sse2 unknown")
        yes(cpu.vendor == "Vendor" && cpu.coreCount == 1 && cpu.featureFlags == setOf("sse", "sse2"))
        val lens = dev.fusion.lens.OcrPreprocessing.empty()
        yes(lens.thresholdOnly(byteArrayOf(0, 127, 128.toByte(), 255.toByte()), 128)
            .contentEquals(byteArrayOf(0, 0, 255.toByte(), 255.toByte())))
        yes(lens.preprocess(byteArrayOf(0, -1, 0, -1), 2, 2).pixels.contentEquals(byteArrayOf(0, -1, 0, -1)))
        refuses { lens.preprocess(ByteArray(4096), 1, 4096, targetWidth = 4096) }
        yes(dev.fusion.notes.BacklinkResolver.empty().resolve("[[Note]]").single().display == null)
        yes(dev.fusion.terminal.AnsiParser.empty().plainText("hello\u001b[31mred\u001b[0m") == "hellored")
        yes(dev.fusion.security.ConstantTimeCompare.equals(byteArrayOf(1), byteArrayOf(1)))
        yes(!dev.fusion.security.ConstantTimeCompare.equals(byteArrayOf(1), byteArrayOf(2)))
        val bom = dev.fusion.files.BomDetector.empty().detect(byteArrayOf(-1, -2, 0, 0, 65))
        yes(bom.charset == "UTF-32LE" && bom.withoutBom.contentEquals(byteArrayOf(65)))
        val capture = dev.fusion.capture.CaptureSegmentQuota.empty(100)
        yes(capture.registerSegment("one", 60) && !capture.registerSegment("two", 60))
        yes(capture.usedBytes() == 60L)
        val history = dev.fusion.editor.EditorTransactionEngine.create("ab")
        yes(history.applyTransaction("insert", listOf(dev.fusion.editor.EditorTransactionEngine.EditOp.Insert(1, "X"))))
        yes(history.text() == "aXb" && history.undo() == "insert" && history.text() == "ab")
        yes(history.redo() == "insert" && history.text() == "aXb")
        val selection = dev.fusion.editor.TextSelectionRange.create(10, 2, 7)
        yes(selection.length() == 5 && selection.contains(2) && !selection.contains(7))
        val backup = dev.fusion.backup.BackupManifest.empty("1")
        backup.addEntry("a", 17, "0".repeat(64)); yes(backup.manifest().totalSize == 17L)
        val clipboard = dev.fusion.continuity.ClipboardEntryStore.empty()
        yes(clipboard.addEntry("one", byteArrayOf(65), "text/plain", System.currentTimeMillis()))
        yes(clipboard.entryCount() == 1)
        val recorder = dev.fusion.automation.MacroRecorder.empty()
        recorder.startRecording(); recorder.addStep("tap"); yes(recorder.stopRecording().steps.size == 1)
        yes(dev.fusion.documents.DocumentLineIndex.build("a\nb").lineCount() == 2)
        // Supplied-input parsers, not claims of interoperability or decoding.
        yes(dev.fusion.castv2.CastV2Channel.empty().parse(byteArrayOf()).isEmpty())
        yes(!dev.fusion.dlna.DlnaDevice.empty().isMediaRenderer("<root/>"))
        yes(dev.fusion.image.PngChunkReader.empty().read(byteArrayOf()).chunks.isEmpty())
        yes(dev.fusion.commands.CommandRouter.validName("open-file"))
        yes(!dev.fusion.commands.CommandRouter.validName("Bad name"))
        val router = dev.fusion.commands.CommandRouter.empty()
        router.register("open-file", "target", 1, setOf("safe"))
        yes(router.resolve("open-file", listOf("a"), setOf("safe")) == "target")
        refuses { router.resolve("open-file", listOf("a"), emptySet()) }
        refuses { router.resolve("open-file", listOf("a", "b"), setOf("safe")) }
        refuses { router.register("open-file", "duplicate") }
        println("R176_BUILD_SURFACE_OK behavior_assertions=$checks")
    }
}

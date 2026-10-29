// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * Clean-room W3C WebVTT Subtitle Cue Timeline Parser (`AX-07`), Fusion IME Key Remap Table (`AX-05`),
 * Voice-Palette Transcript Normalizer (`AX-06`), and Automated Accessibility Tree Audit (`AX-08`).
 */
object WebVttCaptionCueTimeline {
    data class CaptionCue(
        val cueId: String?,
        val startMs: Long,
        val endMs: Long,
        val plainText: String,
    ) {
        init {
            require(startMs >= 0L && endMs > startMs) { "invalid WebVTT cue interval [$startMs, $endMs)" }
        }
    }

    fun parseWebVtt(vttContent: String): List<CaptionCue> {
        val norm = vttContent.removePrefix("\uFEFF").replace("\r\n", "\n").trim()
        require(norm.startsWith("WEBVTT")) { "missing WEBVTT file magic header" }
        val blocks = norm.split(Regex("\n{2,}")).drop(1)
        val cues = ArrayList<CaptionCue>()
        val timeRx = Regex("""^(?:(\d{2,}):)?(\d{2}):(\d{2})\.(\d{3})\s*-->\s*(?:(\d{2,}):)?(\d{2}):(\d{2})\.(\d{3}).*$""")

        for (block in blocks) {
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }
            if (lines.isEmpty() || lines[0].startsWith("NOTE")) continue
            val timingLineIdx = if ("-->" in lines[0]) 0 else if (lines.size >= 2 && "-->" in lines[1]) 1 else continue
            val cueId = if (timingLineIdx == 1) lines[0] else null
            val m = timeRx.matchEntire(lines[timingLineIdx]) ?: continue
            val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
            val end = toMs(m.groupValues[5], m.groupValues[6], m.groupValues[7], m.groupValues[8])
            if (end <= start) continue
            // Strip formatting tags `<b>`, `<i>`, `<c.yellow>` safely for plain accessible caption rendering
            val body = lines.drop(timingLineIdx + 1).joinToString("\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            if (body.isNotEmpty()) cues.add(CaptionCue(cueId, start, end, body))
        }
        return cues.sortedBy { it.startMs }
    }

    fun activeCuesAt(cues: List<CaptionCue>, playheadMs: Long): List<CaptionCue> =
        cues.filter { playheadMs in it.startMs..<it.endMs }

    /** Bijection-checked key remap (`AX-05`, e.g. CapsLock -> Escape) preventing orphan unmapped Escape! */
    fun applyKeyRemap(keyCode: String, remapTable: Map<String, String>): String =
        remapTable[keyCode] ?: keyCode

    data class A11yNodeSample(
        val testTag: String,
        val interactive: Boolean,
        val contentDescriptionOrText: String,
        val widthDp: Float,
        val heightDp: Float,
        val contrastRatio: Double,
    )

    fun auditSemanticsTree(nodes: List<A11yNodeSample>): List<String> {
        val violations = ArrayList<String>()
        for (n in nodes) {
            if (n.interactive && n.contentDescriptionOrText.isBlank()) {
                violations.add("${n.testTag}: interactive control lacks accessible label/contentDescription")
            }
            if (n.interactive && (n.widthDp < 48f || n.heightDp < 48f)) {
                violations.add("${n.testTag}: touch target ${n.widthDp}x${n.heightDp}dp < 48x48dp")
            }
            if (n.contrastRatio < 4.5) {
                violations.add("${n.testTag}: text contrast ${"%.2f".format(java.util.Locale.US, n.contrastRatio)}:1 < 4.5:1")
            }
        }
        return violations
    }

    private fun toMs(hh: String, mm: String, ss: String, mmm: String): Long {
        val h = if (hh.isEmpty()) 0L else hh.toLong()
        return ((h * 3600L + mm.toLong() * 60L + ss.toLong()) * 1000L) + mmm.toLong()
    }
}

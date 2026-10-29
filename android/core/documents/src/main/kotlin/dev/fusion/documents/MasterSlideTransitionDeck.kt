// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Clean-room PresentationML (`ECMA-376 Part 1 §19.3`) Master-Slide placeholder inheritance,
 * slide transition timing (`fade`, `push`, `wipe`, `morph`), and speaker-notes deck builder.
 */
object MasterSlideTransitionDeck {
    const val SCOPE =
        "Pure-JVM PresentationML master-slide placeholder inheritance, transition timing XML synthesizer, and presenter-notes timeline planner."

    enum class TransitionKind { NONE, FADE, PUSH, WIPE, MORPH }

    data class PlaceholderBox(
        val slotId: String,
        val xEmu: Long,
        val yEmu: Long,
        val wEmu: Long,
        val hEmu: Long,
        val fontPt: Int,
    )

    data class MasterTheme(
        val themeName: String,
        val backgroundHex: String,
        val accentHex: String,
        val placeholders: Map<String, PlaceholderBox>,
    )

    data class SlideSpec(
        val title: String,
        val bullets: List<String>,
        val speakerNotes: String,
        val transition: TransitionKind = TransitionKind.FADE,
        val durationMs: Int = 350,
        val advanceAfterSec: Int = 45,
        val overrideBoxes: Map<String, PlaceholderBox> = emptyMap(),
    )

    data class CompiledSlide(
        val index1: Int,
        val resolvedTitleBox: PlaceholderBox,
        val resolvedBodyBox: PlaceholderBox,
        val slideXml: String,
        val notesXml: String,
    )

    data class CompiledDeck(
        val slides: List<CompiledSlide>,
        val totalTargetDurationSec: Int,
        val masterXml: String,
    )

    fun defaultWidescreenMaster(
        themeName: String = "Fusion Obsidian 16:9",
        backgroundHex: String = "0F172A",
        accentHex: String = "38BDF8",
    ): MasterTheme = MasterTheme(
        themeName = themeName,
        backgroundHex = backgroundHex,
        accentHex = accentHex,
        placeholders = mapOf(
            "title" to PlaceholderBox("title", 609_600L, 457_200L, 10_972_800L, 1_143_000L, 36),
            "body" to PlaceholderBox("body", 609_600L, 1_828_800L, 10_972_800L, 4_572_000L, 22),
        ),
    )

    fun compileDeck(master: MasterTheme, specs: List<SlideSpec>): CompiledDeck {
        require(specs.size in 1..256) { "slide count ${specs.size} outside 1..256" }
        val masterTitle = master.placeholders["title"] ?: error("master missing title placeholder")
        val masterBody = master.placeholders["body"] ?: error("master missing body placeholder")

        val compiled = specs.mapIndexed { idx, sp ->
            require(sp.durationMs in 0..10_000 && sp.advanceAfterSec in 1..3600) { "invalid slide timing" }
            val tBox = sp.overrideBoxes["title"] ?: masterTitle
            val bBox = sp.overrideBoxes["body"] ?: masterBody
            val transTag = when (sp.transition) {
                TransitionKind.NONE -> ""
                TransitionKind.FADE -> "<p:fade thruBlk=\"0\"/>"
                TransitionKind.PUSH -> "<p:push dir=\"l\"/>"
                TransitionKind.WIPE -> "<p:wipe dir=\"r\"/>"
                TransitionKind.MORPH -> "<p159:morph option=\"byObject\"/>"
            }
            val spd = when {
                sp.durationMs <= 250 -> "fast"
                sp.durationMs <= 600 -> "med"
                else -> "slow"
            }
            val sXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p159="http://schemas.microsoft.com/office/powerpoint/2015/09/main">""")
                append("""<p:cSld><p:spTree>""")
                append("""<p:sp><p:nvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>""")
                append("""<p:spPr><a:xfrm><a:off x="${tBox.xEmu}" y="${tBox.yEmu}"/><a:ext cx="${tBox.wEmu}" cy="${tBox.hEmu}"/></a:xfrm></p:spPr>""")
                append("""<p:txBody><a:p><a:r><a:rPr sz="${tBox.fontPt * 100}"/><a:t>${esc(sp.title)}</a:t></a:r></a:p></p:txBody></p:sp>""")
                append("""<p:sp><p:nvSpPr><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr>""")
                append("""<p:spPr><a:xfrm><a:off x="${bBox.xEmu}" y="${bBox.yEmu}"/><a:ext cx="${bBox.wEmu}" cy="${bBox.hEmu}"/></a:xfrm></p:spPr><p:txBody>""")
                for (b in sp.bullets) {
                    append("""<a:p><a:r><a:rPr sz="${bBox.fontPt * 100}"/><a:t>${esc(b)}</a:t></a:r></a:p>""")
                }
                append("""</p:txBody></p:sp></p:spTree></p:cSld>""")
                if (transTag.isNotEmpty()) {
                    append("""<p:transition spd="$spd" advTm="${sp.advanceAfterSec * 1000}">$transTag</p:transition>""")
                }
                append("""</p:sld>""")
            }
            val nXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:notes xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>${esc(sp.speakerNotes)}</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:notes>"""
            CompiledSlide(idx + 1, tBox, bBox, sXml, nXml)
        }
        val mXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sldMaster xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="${esc(master.themeName)}"><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val="${master.backgroundHex.uppercase()}"/></a:solidFill></p:bgPr></p:bg></p:cSld></p:sldMaster>"""
        return CompiledDeck(compiled, specs.sumOf { it.advanceAfterSec }, mXml)
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

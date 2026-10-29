package dev.fusion.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Clean-room OOXML Presentation (`.pptx`) Slide Master / Slide Layout placeholder inheritance
 * and `<p:timing>` entrance/emphasis/exit animation timeline writer & inspector (Apache-2.0).
 */
object PptxMasterLayoutAnimationEngine {
    const val SCOPE =
        "ECMA-376 PresentationML slideMaster1 + slideLayout1 placeholder inheritance + <p:timing> animation timeline .pptx writer/inspector"

    enum class AnimationPreset(val presetClass: String, val presetId: Int) {
        APPEAR("entr", 1),
        FADE_IN("entr", 10),
        FLY_IN_BOTTOM("entr", 2),
        PULSE_EMPHASIS("emph", 26),
        FADE_OUT("exit", 10),
    }

    data class ShapeAnimation(
        val targetShapeId: Int,
        val preset: AnimationPreset,
        val delayMs: Int = 0,
        val durationMs: Int = 450,
    ) {
        init {
            require(targetShapeId >= 2) { "targetShapeId must be >= 2" }
            require(delayMs >= 0 && durationMs in 1..30_000) { "Invalid animation timing" }
        }
    }

    data class MasterThemeSpec(
        val themeName: String,
        val backgroundHex: String = "0F172A",
        val accentHex: String = "38BDF8",
        val titleFont: String = "Inter",
        val bodyFont: String = "Inter",
    ) {
        init {
            require(HEX_RE.matches(backgroundHex) && HEX_RE.matches(accentHex)) { "Invalid 6-hex RGB color" }
        }
    }

    data class AnimatedSlide(
        val title: String,
        val subtitleOrBullets: List<String>,
        val speakerNotes: String = "",
        val animations: List<ShapeAnimation> = emptyList(),
    )

    data class PptxMasterInspection(
        val masterThemeName: String,
        val masterBgHex: String,
        val slideCount: Int,
        val totalAnimationSteps: Int,
        val layoutPlaceholderTypes: List<String>,
        val slideTitles: List<String>,
    )

    private val HEX_RE = Regex("^[0-9A-Fa-f]{6}$")
    private const val P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main"
    private const val A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun writeDeck(master: MasterThemeSpec, slides: List<AnimatedSlide>): ByteArray {
        require(slides.isNotEmpty() && slides.size <= 128) { "slides must contain 1..128 slides" }
        val parts = LinkedHashMap<String, String>()
        parts["[Content_Types].xml"] = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>""")
            append("""<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
            append("""<Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>""")
            append("""<Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>""")
            for (i in slides.indices) {
                append("""<Override PartName="/ppt/slides/slide${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
            }
            append("</Types>")
        }
        parts["_rels/.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$R_NS/officeDocument" Target="ppt/presentation.xml"/></Relationships>"""
        parts["ppt/presentation.xml"] = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:presentation xmlns:p="$P_NS" xmlns:a="$A_NS" xmlns:r="$R_NS">""")
            append("""<p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rMaster1"/></p:sldMasterIdLst>""")
            append("<p:sldIdLst>")
            for (i in slides.indices) append("""<p:sldId id="${256 + i}" r:id="rSlide${i + 1}"/>""")
            append("</p:sldIdLst>")
            append("""<p:sldSz cx="12192000" cy="6858000"/></p:presentation>""")
        }
        parts["ppt/_rels/presentation.xml.rels"] = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            append("""<Relationship Id="rMaster1" Type="$R_NS/slideMaster" Target="slideMasters/slideMaster1.xml"/>""")
            for (i in slides.indices) {
                append("""<Relationship Id="rSlide${i + 1}" Type="$R_NS/slide" Target="slides/slide${i + 1}.xml"/>""")
            }
            append("</Relationships>")
        }
        parts["ppt/slideMasters/slideMaster1.xml"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sldMaster xmlns:p="$P_NS" xmlns:a="$A_NS" xmlns:r="$R_NS" name="${esc(master.themeName)}"><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val="${master.backgroundHex.uppercase()}"/></a:solidFill></p:bgPr></p:bg><p:spTree><p:sp><p:nvSpPr><p:cNvPr id="2" name="MasterTitle"/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:txBody><a:bodyPr/><a:p><a:r><a:rPr><a:latin typeface="${esc(master.titleFont)}"/></a:rPr><a:t>Master Title</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld><p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rLayout1"/></p:sldLayoutIdLst></p:sldMaster>"""
        parts["ppt/slideMasters/_rels/slideMaster1.xml.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rLayout1" Type="$R_NS/slideLayout" Target="../slideLayouts/slideLayout1.xml"/></Relationships>"""
        parts["ppt/slideLayouts/slideLayout1.xml"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sldLayout xmlns:p="$P_NS" xmlns:a="$A_NS" xmlns:r="$R_NS" type="titleAndBody"><p:cSld name="Title and Content"><p:spTree><p:sp><p:nvSpPr><p:cNvPr id="2" name="Title Placeholder"/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr></p:sp><p:sp><p:nvSpPr><p:cNvPr id="3" name="Body Placeholder"/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr></p:sp><p:sp><p:nvSpPr><p:cNvPr id="4" name="Slide Number"/><p:nvPr><p:ph type="sldNum" idx="12"/></p:nvPr></p:nvSpPr></p:sp></p:spTree></p:cSld></p:sldLayout>"""
        parts["ppt/slideLayouts/_rels/slideLayout1.xml.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rMaster1" Type="$R_NS/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>"""

        for ((idx, slide) in slides.withIndex()) {
            val sNum = idx + 1
            parts["ppt/slides/slide$sNum.xml"] = buildSlideXml(slide)
            parts["ppt/slides/_rels/slide$sNum.xml.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rLayout1" Type="$R_NS/slideLayout" Target="../slideLayouts/slideLayout1.xml"/></Relationships>"""
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos, StandardCharsets.UTF_8).use { zos ->
            for ((path, xml) in parts) {
                val raw = xml.toByteArray(StandardCharsets.UTF_8)
                val crc = CRC32().apply { update(raw) }.value
                val ze = ZipEntry(path).apply {
                    time = 315532800000L
                    method = ZipEntry.STORED
                    size = raw.size.toLong()
                    compressedSize = raw.size.toLong()
                    this.crc = crc
                }
                zos.putNextEntry(ze)
                zos.write(raw)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    fun inspectDeck(bytes: ByteArray): PptxMasterInspection {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes), StandardCharsets.UTF_8).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                require(!e.name.contains("..") && !e.name.endsWith("vbaProject.bin", ignoreCase = true)) {
                    "Refusing unsafe or macro PPTX entry: ${e.name}"
                }
                entries[e.name] = zis.readBytes()
            }
        }
        val masterRoot = BoundedXml.parse(entries["ppt/slideMasters/slideMaster1.xml"] ?: error("Missing slideMaster1.xml"))
        val layoutRoot = BoundedXml.parse(entries["ppt/slideLayouts/slideLayout1.xml"] ?: error("Missing slideLayout1.xml"))
        var bgHex = ""
        val phTypes = mutableListOf<String>()
        fun walkMaster(n: BoundedXml.Node) {
            if (n.name.local == "srgbClr") bgHex = n.attr("val") ?: bgHex
            for (c in n.children) walkMaster(c)
        }
        fun walkLayout(n: BoundedXml.Node) {
            if (n.name.local == "ph") n.attr("type")?.let(phTypes::add)
            for (c in n.children) walkLayout(c)
        }
        walkMaster(masterRoot)
        walkLayout(layoutRoot)

        val slideKeys = entries.keys.filter { Regex("^ppt/slides/slide[0-9]+\\.xml$").matches(it) }
            .sortedBy { it.removePrefix("ppt/slides/slide").removeSuffix(".xml").toInt() }
        var totalAnim = 0
        val titles = mutableListOf<String>()
        for (sk in slideKeys) {
            val sRoot = BoundedXml.parse(entries.getValue(sk))
            var firstTitle: String? = null
            fun walkSlide(n: BoundedXml.Node) {
                if (n.name.local == "cTn" && n.attributes.entries.any { it.key.local == "presetClass" }) totalAnim++
                if (n.name.local == "sp" && firstTitle == null) {
                    val sb = StringBuilder()
                    fun collectT(x: BoundedXml.Node) {
                        if (x.name.local == "t") sb.append(x.text)
                        for (c in x.children) collectT(c)
                    }
                    collectT(n)
                    if (sb.isNotEmpty()) firstTitle = sb.toString()
                }
                for (c in n.children) walkSlide(c)
            }
            walkSlide(sRoot)
            titles.add(firstTitle ?: "")
        }
        return PptxMasterInspection(
            masterThemeName = masterRoot.attr("name") ?: "",
            masterBgHex = bgHex,
            slideCount = slideKeys.size,
            totalAnimationSteps = totalAnim,
            layoutPlaceholderTypes = phTypes,
            slideTitles = titles,
        )
    }

    private fun buildSlideXml(slide: AnimatedSlide): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sld xmlns:p="$P_NS" xmlns:a="$A_NS" xmlns:r="$R_NS"><p:cSld><p:spTree>""")
        append("""<p:sp><p:nvSpPr><p:cNvPr id="2" name="Title 1"/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:txBody><a:bodyPr/><a:p><a:r><a:t>${esc(slide.title)}</a:t></a:r></a:p></p:txBody></p:sp>""")
        append("""<p:sp><p:nvSpPr><p:cNvPr id="3" name="Content 2"/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:txBody><a:bodyPr/>""")
        for (b in slide.subtitleOrBullets) append("""<a:p><a:r><a:t>${esc(b)}</a:t></a:r></a:p>""")
        append("</p:txBody></p:sp></p:spTree></p:cSld>")
        if (slide.animations.isNotEmpty()) {
            append("""<p:timing><p:tnLst><p:par><p:cTn id="1" dur="indefinite" nodeType="tmRoot"><p:childTnLst><p:seq concurrent="1" nextAc="seek"><p:cTn id="2" dur="indefinite" nodeType="mainSeq"><p:childTnLst>""")
            for ((aIdx, anim) in slide.animations.withIndex()) {
                val cId = 10 + aIdx * 2
                append("""<p:par><p:cTn id="$cId" presetID="${anim.preset.presetId}" presetClass="${anim.preset.presetClass}" dur="${anim.durationMs}"><p:stCondLst><p:cond delay="${anim.delayMs}"/></p:stCondLst><p:childTnLst><p:set><p:cBhvr><p:cTn id="${cId + 1}" dur="1"/><p:tgtEl><p:spTgt spid="${anim.targetShapeId}"/></p:tgtEl></p:cBhvr></p:set></p:childTnLst></p:cTn></p:par>""")
            }
            append("</p:childTnLst></p:cTn></p:seq></p:childTnLst></p:cTn></p:par></p:tnLst></p:timing>")
        }
        append("</p:sld>")
    }

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

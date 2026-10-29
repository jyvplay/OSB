// SPDX-License-Identifier: Apache-2.0
package dev.fusion.native

/**
 * Clean-room `formats.yaml` engine, fidelity-grade, and process-boundary registry (`FF-14`).
 *
 * Parses and validates canonical `formats.yaml` declarations and ships the built-in 24-format
 * capability table distinguishing read-only preview vs lossless round-trip vs isolated-process
 * boundaries so the UI never over-claims fidelity.
 */
object FormatsRegistryCatalog {
    enum class FidelityGrade { FULL_ROUNDTRIP, STRUCTURED_SUBSET, READ_ONLY_PREVIEW, UNSUPPORTED_BOUNDARY }
    enum class IsolationBoundary { IN_PROCESS_PURE_JVM, ISOLATED_SANDBOX_SERVICE, PLATFORM_HARDWARE_CODEC, EXTERNAL_HANDOFF_ONLY }

    data class FormatEntry(
        val id: String,
        val extensions: List<String>,
        val mimeType: String,
        val engine: String,
        val grade: FidelityGrade,
        val boundary: IsolationBoundary,
        val macrosExecuted: Boolean = false,
        val boundaryNote: String,
    ) {
        init {
            require(id.matches(Regex("[a-z0-9_-]{2,32}"))) { "invalid format id '$id'" }
            require(extensions.isNotEmpty() && extensions.all { it.startsWith(".") }) { "extensions must start with '.'" }
            require(!macrosExecuted) { "FF-17 violation: format '$id' cannot enable macro execution" }
        }
    }

    val DEFAULT_REGISTRY: List<FormatEntry> = listOf(
        FormatEntry("markdown", listOf(".md", ".markdown"), "text/markdown", "FusionMarkdownAst", FidelityGrade.FULL_ROUNDTRIP, IsolationBoundary.IN_PROCESS_PURE_JVM, false, "Lossless CommonMark + GFM tables/tasklists"),
        FormatEntry("plaintext", listOf(".txt", ".log", ".csv", ".tsv"), "text/plain", "PieceTableUtf8", FidelityGrade.FULL_ROUNDTRIP, IsolationBoundary.IN_PROCESS_PURE_JVM, false, "UTF-8/BOM piece-table edit + CSV RFC 4180"),
        FormatEntry("docx", listOf(".docx"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "FusionOoxmlWriter", FidelityGrade.STRUCTURED_SUBSET, IsolationBoundary.ISOLATED_SANDBOX_SERVICE, false, "Paragraphs/tables/styles/footnotes/TOC/redlines preserved; VBA/OLE/SmartArt preserved opaque or warned"),
        FormatEntry("xlsx", listOf(".xlsx"), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "FusionSpreadsheetEngine", FidelityGrade.STRUCTURED_SUBSET, IsolationBoundary.ISOLATED_SANDBOX_SERVICE, false, "130 functions + LAMBDA/spill/charts/sparklines/pivots; VBA/PowerQuery disabled"),
        FormatEntry("pptx", listOf(".pptx"), "application/vnd.openxmlformats-officedocument.presentationml.presentation", "PresentationDeckModel", FidelityGrade.STRUCTURED_SUBSET, IsolationBoundary.ISOLATED_SANDBOX_SERVICE, false, "Slides/shapes/notes/transitions subset; embedded media/SmartArt read-only"),
        FormatEntry("odt", listOf(".odt"), "application/vnd.oasis.opendocument.text", "OdfWriterCodec", FidelityGrade.STRUCTURED_SUBSET, IsolationBoundary.ISOLATED_SANDBOX_SERVICE, false, "ODF 1.3 text/styles/tracked-changes subset"),
        FormatEntry("ods", listOf(".ods"), "application/vnd.oasis.opendocument.spreadsheet", "OdfSpreadsheetCodec", FidelityGrade.STRUCTURED_SUBSET, IsolationBoundary.ISOLATED_SANDBOX_SERVICE, false, "ODF 1.3 table cells/OpenFormula subset"),
        FormatEntry("pdf", listOf(".pdf"), "application/pdf", "IsolatedPdfPreview", FidelityGrade.READ_ONLY_PREVIEW, IsolationBoundary.ISOLATED_SANDBOX_SERVICE, false, "Readonly render + AcroForm/outline/ByteRange inspect + incremental comment append"),
        FormatEntry("epub", listOf(".epub"), "application/epub+zip", "Epub3Package", FidelityGrade.READ_ONLY_PREVIEW, IsolationBoundary.ISOLATED_SANDBOX_SERVICE, false, "EPUB 3.3 OPF/spine/XHTML reader; DRM refused"),
        FormatEntry("ai_pdf", listOf(".ai"), "application/postscript", "AiPdfCompatibilityBridge", FidelityGrade.READ_ONLY_PREVIEW, IsolationBoundary.ISOLATED_SANDBOX_SERVICE, false, "PDF-compatible Illustrator streams only; legacy PostScript-only .ai refused"),
        FormatEntry("psd", listOf(".psd"), "image/vnd.adobe.photoshop", "PsdHeaderLayerReader", FidelityGrade.READ_ONLY_PREVIEW, IsolationBoundary.ISOLATED_SANDBOX_SERVICE, false, "Composite/layer metadata preview; Adjustment/SmartObject editing out of scope"),
        FormatEntry("heic", listOf(".heic", ".heif", ".avif"), "image/heic", "PlatformHeicCodec", FidelityGrade.READ_ONLY_PREVIEW, IsolationBoundary.PLATFORM_HARDWARE_CODEC, false, "ISOBMFF box inspect + Android hardware MediaCodec/ImageDecoder where present"),
        FormatEntry("png_jpeg_webp", listOf(".png", ".jpg", ".jpeg", ".webp"), "image/png", "SafeImageCodec", FidelityGrade.STRUCTURED_SUBSET, IsolationBoundary.IN_PROCESS_PURE_JVM, false, "EXIF/GPS strip + crop/tone/LUT"),
        FormatEntry("zip_7z_tar", listOf(".zip", ".7z", ".tar", ".gz"), "application/zip", "SafeArchiveEngine", FidelityGrade.STRUCTURED_SUBSET, IsolationBoundary.ISOLATED_SANDBOX_SERVICE, false, "Slip/bomb/symlink guarded; WinZip AE-2 inspect"),
    )

    fun toCanonicalYaml(entries: List<FormatEntry> = DEFAULT_REGISTRY): String = buildString {
        append("# Fusion Shell FF-14 canonical formats.yaml\nformats:\n")
        for (e in entries) {
            append("  - id: ${e.id}\n")
            append("    extensions: [${e.extensions.joinToString(", ")}]\n")
            append("    mime: ${e.mimeType}\n")
            append("    engine: ${e.engine}\n")
            append("    grade: ${e.grade.name}\n")
            append("    boundary: ${e.boundary.name}\n")
            append("    macros_executed: ${e.macrosExecuted}\n")
            append("    note: \"${e.boundaryNote.replace("\"", "\\\"")}\"\n")
        }
    }

    fun parseYaml(yaml: String): List<FormatEntry> {
        val out = ArrayList<FormatEntry>()
        val blocks = yaml.lines()
            .filter { it.trim().isNotEmpty() && !it.trim().startsWith("#") && it.trim() != "formats:" }
        var cur = LinkedHashMap<String, String>()
        fun flush() {
            if (cur.isEmpty()) return
            val exts = cur.getValue("extensions").removePrefix("[").removeSuffix("]")
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }
            out.add(
                FormatEntry(
                    id = cur.getValue("id"),
                    extensions = exts,
                    mimeType = cur.getValue("mime"),
                    engine = cur.getValue("engine"),
                    grade = FidelityGrade.valueOf(cur.getValue("grade")),
                    boundary = IsolationBoundary.valueOf(cur.getValue("boundary")),
                    macrosExecuted = cur["macros_executed"]?.toBooleanStrict() ?: false,
                    boundaryNote = cur.getValue("note").removeSurrounding("\"").replace("\\\"", "\""),
                ),
            )
            cur = LinkedHashMap()
        }
        for (line in blocks) {
            val t = line.trim()
            if (t.startsWith("- ")) {
                flush()
                val kv = t.removePrefix("- ").split(':', limit = 2)
                cur[kv[0].trim()] = kv[1].trim()
            } else if (':' in t) {
                val kv = t.split(':', limit = 2)
                cur[kv[0].trim()] = kv[1].trim()
            }
        }
        flush()
        return out
    }

    fun resolveByFilename(filename: String, entries: List<FormatEntry> = DEFAULT_REGISTRY): FormatEntry? {
        val lower = filename.trim().lowercase()
        return entries.firstOrNull { e -> e.extensions.any { lower.endsWith(it) } }
    }
}

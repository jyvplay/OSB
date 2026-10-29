// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

import java.nio.charset.StandardCharsets

/**
 * Clean-room Adobe Illustrator `.ai` PDF-Compatibility Boundary Detector (`FF-04`).
 *
 * Modern Illustrator files saved with "Create PDF Compatible File" are valid `%PDF-1.x` containers
 * carrying `/PieceInfo << /Illustrator ... >>` private data, whereas legacy `.ai` files saved
 * without PDF compatibility are raw `%!PS-Adobe-` PostScript/EPS streams.
 *
 * Per `FF-04`:
 * - PDF-compatible `.ai` streams route safely to the isolated read-only PDF preview pipeline.
 * - Non-PDF PostScript-only `.ai` streams are explicitly refused fail-closed with a clear boundary reason.
 */
object AiPdfCompatibilityBridge {
    enum class AiStreamKind {
        PDF_COMPATIBLE_ILLUSTRATOR,
        PLAIN_PDF_WITHOUT_AI_PIECEINFO,
        LEGACY_POSTSCRIPT_REFUSED,
        UNRECOGNIZED_REFUSED,
    }

    data class AiBoundaryVerdict(
        val kind: AiStreamKind,
        val canRouteToPdfPreview: Boolean,
        val pdfVersion: String?,
        val artboardMediaBox: List<Double>?,
        val userFacingBoundaryReason: String,
    )

    fun inspect(bytes: ByteArray): AiBoundaryVerdict {
        if (bytes.size < 8) {
            return AiBoundaryVerdict(
                AiStreamKind.UNRECOGNIZED_REFUSED,
                false,
                null,
                null,
                "File is shorter than 8 bytes; cannot verify PDF-compatible Illustrator header.",
            )
        }
        val windowLen = minOf(bytes.size, 65_536)
        val ascii = String(bytes, 0, windowLen, StandardCharsets.ISO_8859_1)
        val trimmed = ascii.trimStart()

        if (trimmed.startsWith("%!PS-Adobe") || trimmed.startsWith("\u00C5\u00D0\u00D3\u00C6")) {
            return AiBoundaryVerdict(
                AiStreamKind.LEGACY_POSTSCRIPT_REFUSED,
                false,
                null,
                null,
                "FF-04 boundary: PostScript/EPS-only .ai file lacks an embedded PDF stream; re-save with 'Create PDF Compatible File' enabled.",
            )
        }

        val pdfMatch = Regex("""^%PDF-(\d+\.\d+)""").find(trimmed)
            ?: return AiBoundaryVerdict(
                AiStreamKind.UNRECOGNIZED_REFUSED,
                false,
                null,
                null,
                "Unrecognized .ai header (neither %PDF-1.x nor %!PS-Adobe).",
            )

        val ver = pdfMatch.groupValues[1]
        val hasAiPieceInfo = ascii.contains("/Illustrator") || ascii.contains("AIPrivateData")
        val mediaBox = Regex("""/MediaBox\s*\[\s*([-0-9.]+)\s+([-0-9.]+)\s+([-0-9.]+)\s+([-0-9.]+)\s*]""")
            .find(ascii)
            ?.groupValues
            ?.drop(1)
            ?.mapNotNull { it.toDoubleOrNull() }

        return if (hasAiPieceInfo) {
            AiBoundaryVerdict(
                AiStreamKind.PDF_COMPATIBLE_ILLUSTRATOR,
                true,
                ver,
                mediaBox,
                "PDF-$ver compatible Illustrator container verified; routes to read-only isolated PDF preview (private Illustrator edit objects remain read-only).",
            )
        } else {
            AiBoundaryVerdict(
                AiStreamKind.PLAIN_PDF_WITHOUT_AI_PIECEINFO,
                true,
                ver,
                mediaBox,
                "Standard PDF-$ver stream without /Illustrator PieceInfo; routes to read-only isolated PDF preview.",
            )
        }
    }
}

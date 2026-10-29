// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

/**
 * Pure-JVM **ISO 32000-2 Interactive AcroForm (`/Tx`, `/Btn`, `/Ch`) Field Dictionary Builder
 * & XFA-Free Security Validator** for `:format:pdf` (strictly rejecting deprecated XML Forms
 * Architecture `/XFA` script vectors per PDF 2.0!).
 */
object PdfAcroFormFieldFiller {
    const val SCOPE =
        "ISO 32000-2 AcroForm (/Tx, /Btn, /Ch) interactive form dictionary serializer with strict PDF 2.0 /XFA script rejection."

    enum class FieldType(val pdfName: String) { TEXT("/Tx"), CHECKBOX("/Btn"), CHOICE("/Ch") }

    data class AcroFieldValue(
        val fieldName: String,
        val type: FieldType,
        val value: String,
        val readOnly: Boolean = false,
    )

    fun containsForbiddenLegacyXfa(rawPdfAsciiSnippet: String): Boolean =
        Regex("""/XFA\b""").containsMatchIn(rawPdfAsciiSnippet)

    fun serializeAcroFormObject(objectId: Int, fields: List<AcroFieldValue>): String {
        require(fields.isNotEmpty() && objectId >= 1)
        val sb = StringBuilder()
        val childRefs = fields.indices.map { "${objectId + 1 + it} 0 R" }.joinToString(" ")
        sb.append("$objectId 0 obj\n<< /Fields [$childRefs] /NeedAppearances true >>\nendobj\n")
        fields.forEachIndexed { idx, f ->
            val fid = objectId + 1 + idx
            val ff = if (f.readOnly) " /Ff 1" else ""
            val vExpr = if (f.type == FieldType.CHECKBOX) {
                if (f.value.equals("true", true) || f.value == "1" || f.value.equals("Yes", true)) "/Yes" else "/Off"
            } else "(${escapePdf(f.value)})"
            sb.append("$fid 0 obj\n<< /FT ${f.type.pdfName} /T (${escapePdf(f.fieldName)}) /V $vExpr$ff >>\nendobj\n")
        }
        return sb.toString()
    }

    private fun escapePdf(s: String): String =
        s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
}

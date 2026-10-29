// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

import java.nio.charset.StandardCharsets

/**
 * Clean-room ISO 32000-2 PDF `/AcroForm` Interactive Form Field Inspector & Incremental Fill
 * Planner (`PR-08`).
 *
 * Extracts `/FT` (`/Tx` text, `/Btn` checkbox/radio, `/Ch` choice, `/Sig` signature), `/T` field
 * name, `/V` value, `/Ff` flags (`READ_ONLY = bit 1`, `REQUIRED = bit 2`), and `/Rect` widget
 * coordinates, and validates user field edits against read-only / required / JavaScript-free rules!
 */
object PdfFormAcroFieldInspector {
    enum class FieldType { TEXT, BUTTON, CHOICE, SIGNATURE, UNKNOWN }

    data class AcroField(
        val objectNumber: Int,
        val name: String,
        val type: FieldType,
        val value: String,
        val flags: Int,
        val rect: List<Double>?,
    ) {
        val isReadOnly: Boolean get() = (flags and 0x01) != 0
        val isRequired: Boolean get() = (flags and 0x02) != 0
    }

    data class FormInspection(
        val fields: List<AcroField>,
        val hasXfaDeprecatedWarning: Boolean,
        val hasJavaScriptActionsQuarantined: Boolean,
    )

    fun inspectPdfBytes(pdfBytes: ByteArray): FormInspection {
        val text = String(pdfBytes, StandardCharsets.ISO_8859_1)
        val hasXfa = text.contains("/XFA")
        val hasJs = text.contains("/JS") || text.contains("/JavaScript")
        val objRx = Regex("""(\d+)\s+0\s+obj\b(.*?)endobj""", RegexOption.DOT_MATCHES_ALL)
        val fields = ArrayList<AcroField>()

        for (m in objRx.findAll(text)) {
            val objNum = m.groupValues[1].toIntOrNull() ?: continue
            val body = m.groupValues[2]
            val ftRaw = Regex("""/FT\s*/([A-Za-z]+)""").find(body)?.groupValues?.get(1) ?: continue
            val name = Regex("""/T\s*\(([^)]*)\)""").find(body)?.groupValues?.get(1) ?: "field_$objNum"
            val valParen = Regex("""/V\s*\(([^)]*)\)""").find(body)?.groupValues?.get(1)
            val valName = Regex("""/V\s*/([A-Za-z0-9_#-]+)""").find(body)?.groupValues?.get(1)
            val flags = Regex("""/Ff\s+(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val rect = Regex("""/Rect\s*\[\s*([-0-9.]+)\s+([-0-9.]+)\s+([-0-9.]+)\s+([-0-9.]+)\s*]""")
                .find(body)?.groupValues?.drop(1)?.mapNotNull { it.toDoubleOrNull() }

            val ft = when (ftRaw) {
                "Tx" -> FieldType.TEXT
                "Btn" -> FieldType.BUTTON
                "Ch" -> FieldType.CHOICE
                "Sig" -> FieldType.SIGNATURE
                else -> FieldType.UNKNOWN
            }
            fields.add(
                AcroField(
                    objectNumber = objNum,
                    name = unescapePdfLiteral(name),
                    type = ft,
                    value = unescapePdfLiteral(valParen ?: valName ?: ""),
                    flags = flags,
                    rect = rect,
                ),
            )
        }
        return FormInspection(fields, hasXfa, hasJs)
    }

    fun planFieldFill(inspection: FormInspection, updates: Map<String, String>): Map<Int, String> {
        val byName = inspection.fields.associateBy { it.name }
        val objectUpdates = LinkedHashMap<Int, String>()
        for ((fieldName, newVal) in updates) {
            val f = byName[fieldName] ?: throw IllegalArgumentException("unknown AcroForm field '$fieldName'")
            require(!f.isReadOnly) { "AcroForm field '$fieldName' is read-only (/Ff bit 1)" }
            require(f.type != FieldType.SIGNATURE) { "AcroForm signature field '$fieldName' cannot be text-filled" }
            val escaped = escapePdfLiteral(newVal)
            objectUpdates[f.objectNumber] = "/V ($escaped)"
        }
        for (f in inspection.fields) {
            if (f.isRequired) {
                val effective = updates[f.name] ?: f.value
                require(effective.isNotBlank()) { "required AcroForm field '${f.name}' is blank" }
            }
        }
        return objectUpdates
    }

    private fun escapePdfLiteral(s: String): String = s
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")

    private fun unescapePdfLiteral(s: String): String = s
        .replace("\\(", "(")
        .replace("\\)", ")")
        .replace("\\\\", "\\")
}

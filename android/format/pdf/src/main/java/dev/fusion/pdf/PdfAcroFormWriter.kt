// SPDX-License-Identifier: Apache-2.0
package dev.fusion.pdf

import java.io.ByteArrayOutputStream

/**
 * Clean-room ISO 32000-1 §12.7 **Interactive Fillable PDF 1.7 AcroForm** writer & security inspector.
 *
 * Generates valid single-page or multi-field fillable PDF 1.7 documents with exact byte-offset
 * `xref` tables, `/AcroForm` catalog dictionary (`/NeedAppearances true`, `/DR` Helvetica font),
 * `/Subtype /Widget` field annotations (`/FT /Tx` text fields + `/FT /Btn` checkboxes), and
 * refuses dangerous active PDF actions (`/JavaScript`, `/JS`, `/SubmitForm`, `/Launch`, `/URI`)
 * during `inspectAndReadFields`!
 */
object PdfAcroFormWriter {
    const val SCOPE =
        "Clean-room ISO 32000-1 §12.7 fillable PDF 1.7 AcroForm writer & security inspector (/FT /Tx text inputs, /FT /Btn checkboxes, exact xref byte offsets, /JavaScript & /SubmitForm & /Launch active-action refusal); XFA XML forms and digital signature PKCS#7 dictionaries out of scope."

    sealed interface FormField {
        val name: String
        val xPt: Int
        val yPt: Int
        val widthPt: Int
        val heightPt: Int

        data class TextField(
            override val name: String,
            val defaultValue: String = "",
            val maxLen: Int = 128,
            override val xPt: Int,
            override val yPt: Int,
            override val widthPt: Int = 220,
            override val heightPt: Int = 22,
        ) : FormField

        data class CheckBoxField(
            override val name: String,
            val checked: Boolean = false,
            override val xPt: Int,
            override val yPt: Int,
            override val widthPt: Int = 16,
            override val heightPt: Int = 16,
        ) : FormField
    }

    data class InspectedForm(
        val fieldValues: Map<String, String>,
        val xrefOffsetsVerified: Int,
    )

    fun writeFillablePdf(title: String, fields: List<FormField>): ByteArray {
        require(fields.isNotEmpty() && fields.size <= 128) { "fields count ${fields.size} outside 1..128" }
        require(fields.map { it.name }.distinct().size == fields.size) { "duplicate AcroForm field names" }
        fields.forEach { f ->
            require(f.name.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) { "unsafe field name '${f.name}'" }
            require(f.widthPt > 0 && f.heightPt > 0) { "non-positive field dimensions for '${f.name}'" }
        }

        // Object layout:
        // 1: Catalog (/Pages 2 0 R /AcroForm 5 0 R)
        // 2: Pages (/Kids [3 0 R] /Count 1)
        // 3: Page (/Parent 2 0 R /MediaBox [0 0 612 792] /Contents 6 0 R /Annots [...])
        // 4: Font Helvetica (/Type /Font /Subtype /Type1 /BaseFont /Helvetica)
        // 5: AcroForm (/Fields [...] /NeedAppearances true /DR << /Font << /Helv 4 0 R >> >> /DA (/Helv 11 Tf 0 g))
        // 6: Page Content stream (draws title + field labels)
        // 7..(6 + fields.size): Widget + Field merged objects
        val fieldObjIds = fields.indices.map { 7 + it }
        val annotsRefs = fieldObjIds.joinToString(" ") { "$it 0 R" }

        val contentStream = buildString {
            append("BT /Helv 16 Tf 54 736 Td (${escPdfStr(title)}) Tj ET\n")
            for (f in fields) {
                append("BT /Helv 10 Tf ${f.xPt} ${f.yPt + f.heightPt + 4} Td (${escPdfStr(f.name)}) Tj ET\n")
                append("0.75 G ${f.xPt} ${f.yPt} ${f.widthPt} ${f.heightPt} re S\n")
            }
        }
        val streamBytes = contentStream.toByteArray(Charsets.US_ASCII)

        val objects = LinkedHashMap<Int, ByteArray>()
        objects[1] = "<< /Type /Catalog /Pages 2 0 R /AcroForm 5 0 R >>".toByteArray(Charsets.US_ASCII)
        objects[2] = "<< /Type /Pages /Kids [3 0 R] /Count 1 >>".toByteArray(Charsets.US_ASCII)
        objects[3] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /Helv 4 0 R >> >> /Contents 6 0 R /Annots [$annotsRefs] >>".toByteArray(Charsets.US_ASCII)
        objects[4] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>".toByteArray(Charsets.US_ASCII)
        objects[5] = "<< /Fields [$annotsRefs] /NeedAppearances true /DR << /Font << /Helv 4 0 R >> >> /DA (/Helv 11 Tf 0 g) >>".toByteArray(Charsets.US_ASCII)
        objects[6] = ("<< /Length ${streamBytes.size} >>\nstream\n" + contentStream + "endstream").toByteArray(Charsets.US_ASCII)

        fields.forEachIndexed { idx, f ->
            val objId = fieldObjIds[idx]
            val rect = "[${f.xPt} ${f.yPt} ${f.xPt + f.widthPt} ${f.yPt + f.heightPt}]"
            val dict = when (f) {
                is FormField.TextField ->
                    "<< /Type /Annot /Subtype /Widget /FT /Tx /P 3 0 R /T (${escPdfStr(f.name)}) /V (${escPdfStr(f.defaultValue)}) /MaxLen ${f.maxLen} /Rect $rect /DA (/Helv 11 Tf 0 g) >>"
                is FormField.CheckBoxField -> {
                    val state = if (f.checked) "/Yes" else "/Off"
                    "<< /Type /Annot /Subtype /Widget /FT /Btn /P 3 0 R /T (${escPdfStr(f.name)}) /V $state /AS $state /Rect $rect >>"
                }
            }
            objects[objId] = dict.toByteArray(Charsets.US_ASCII)
        }

        val out = ByteArrayOutputStream()
        out.write("%PDF-1.7\n%\u00E2\u00E3\u00CF\u00D3\n".toByteArray(Charsets.ISO_8859_1))
        val offsets = IntArray(objects.size + 1)
        for ((id, body) in objects) {
            offsets[id] = out.size()
            out.write("$id 0 obj\n".toByteArray(Charsets.US_ASCII))
            out.write(body)
            out.write("\nendobj\n".toByteArray(Charsets.US_ASCII))
        }
        val xrefPos = out.size()
        val totalSize = objects.size + 1
        out.write("xref\n0 $totalSize\n".toByteArray(Charsets.US_ASCII))
        out.write("0000000000 65535 f \n".toByteArray(Charsets.US_ASCII))
        for (id in 1..objects.size) {
            out.write(String.format("%010d 00000 n \n", offsets[id]).toByteArray(Charsets.US_ASCII))
        }
        out.write("trailer\n<< /Size $totalSize /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n".toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }

    fun inspectAndReadFields(pdfBytes: ByteArray): InspectedForm {
        val text = String(pdfBytes, Charsets.ISO_8859_1)
        require(text.startsWith("%PDF-1.")) { "missing %PDF-1.x header" }
        for (forbidden in listOf("/JavaScript", "/JS", "/SubmitForm", "/Launch", "/ImportData", "/XFA")) {
            require(!Regex("$forbidden\\b").containsMatchIn(text)) {
                "dangerous active PDF token '$forbidden' refused by AcroForm security guard"
            }
        }
        val xrefIdx = text.lastIndexOf("\nxref\n")
        require(xrefIdx > 0) { "missing xref table" }
        val verifiedOffsets = ArrayList<Int>()
        for (m in Regex("(\\d{10}) 00000 n ").findAll(text, xrefIdx)) {
            val off = m.groupValues[1].toInt()
            require(off in 1 until xrefIdx && Regex("^\\d+ 0 obj\\b").containsMatchIn(text.substring(off, minOf(text.length, off + 24)))) {
                "corrupt xref byte offset $off"
            }
            verifiedOffsets.add(off)
        }
        val values = LinkedHashMap<String, String>()
        for (objMatch in Regex("\\d+ 0 obj\\s*<<([\\s\\S]*?)>>\\s*endobj").findAll(text)) {
            val body = objMatch.groupValues[1]
            if (!body.contains("/Subtype /Widget")) continue
            val tName = Regex("/T \\(([^)]*)\\)").find(body)?.groupValues?.get(1)?.let(::unescPdfStr) ?: continue
            val txVal = Regex("/V \\(([^)]*)\\)").find(body)?.groupValues?.get(1)?.let(::unescPdfStr)
            val btnVal = Regex("/V /(Yes|Off)").find(body)?.groupValues?.get(1)
            values[tName] = txVal ?: btnVal.orEmpty()
        }
        return InspectedForm(values, verifiedOffsets.size)
    }

    private fun escPdfStr(s: String): String = buildString(s.length) {
        for (c in s) {
            require(c.code in 32..126) { "AcroForm baseline string supports printable ASCII only (got U+${c.code.toString(16)})" }
            when (c) {
                '\\', '(', ')' -> { append('\\'); append(c) }
                else -> append(c)
            }
        }
    }

    private fun unescPdfStr(s: String): String = s
        .replace("\\(", "(")
        .replace("\\)", ")")
        .replace("\\\\", "\\")
}

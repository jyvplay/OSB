// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import java.io.File

/**
 * R81 Writer table geometry contracts: explicit column widths (`w:tblW` + `w:tblGrid/w:gridCol`,
 * `w:tblLayout` fixed) and a repeated header row (`w:trPr/w:tblHeader`).
 *
 * Host JVM only. The probe writes real `.docx` packages plus `tables.tsv`; the independent reader is
 * tools/check_r81_writer_tables.py, which opens the packages with python-docx and inspects the raw
 * OPC parts. Nothing here is Word rendering evidence, and no caller sets these fields yet.
 */
object R81WriterTableChecks {
    private var assertions = 0

    private inline fun check(ok: Boolean, message: () -> String) {
        assertions++
        require(ok) { "FAILED: " + message() }
    }

    private fun fails(message: String, block: () -> Unit) {
        assertions++
        val failure = runCatching(block).exceptionOrNull()
        require(failure != null) { "expected a refusal for $message" }
        require(failure is IllegalArgumentException) { "refusal for $message must be IllegalArgumentException, was $failure" }
    }

    private fun body(): List<DocxPackage.Block> = listOf(
        DocxPackage.Block.Heading(1, listOf(DocxPackage.Run("Table geometry"))),
        DocxPackage.Block.Paragraph(listOf(DocxPackage.Run("Body before the table."))),
    )

    private fun plain(rows: List<List<String>>) = DocxPackage.Block.Table(rows)

    private fun tablesOf(blocks: List<DocxPackage.Block>) = blocks.filterIsInstance<DocxPackage.Block.Table>()

    /** Byte-preserving view: ISO-8859-1 maps every byte to one char, unlike UTF-8 decoding. */
    private fun text(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    private fun tableXml(bytes: ByteArray): String =
        text(bytes).substringAfter("<w:tbl>").substringBefore("</w:tbl>")

    private fun write(out: File, name: String, blocks: List<DocxPackage.Block>, layout: DocxPackage.Layout? = null): ByteArray {
        val document = DocxPackage.Document(title = name, blocks = blocks, layout = layout)
        val bytes = DocxPackage.write(document)
        File(out, "$name.docx").writeBytes(bytes)
        val readBack = DocxPackage.read(bytes).blocks.filterIsInstance<DocxPackage.Block.Table>()
        check(readBack == tablesOf(blocks)) { "$name table round trip: $readBack" }
        return bytes
    }

    /**
     * Rebuilds a package with one part rewritten. Needed because the writer stores every entry with
     * an exact CRC32, so a byte edit in place would only prove that the CRC check works.
     */
    private fun rezip(source: ByteArray, mutate: (String, ByteArray) -> ByteArray?): ByteArray {
        val parts = LinkedHashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(source)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                parts[entry.name] = input.readBytes()
            }
        }
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip ->
            for ((name, bytes) in parts) {
                val changed = mutate(name, bytes) ?: continue
                val entry = java.util.zip.ZipEntry(name).apply {
                    method = java.util.zip.ZipEntry.STORED
                    size = changed.size.toLong()
                    compressedSize = changed.size.toLong()
                    crc = java.util.zip.CRC32().apply { update(changed) }.value
                    time = 315532800000L
                }
                zip.putNextEntry(entry)
                zip.write(changed)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun record(rows: MutableList<String>, name: String, tables: List<DocxPackage.Block.Table>) {
        tables.forEachIndexed { index, table ->
            rows.add(
                listOf(
                    name,
                    index,
                    table.rows[0].size,
                    table.columnWidthsTwips?.joinToString(",") ?: "-",
                    if (table.repeatHeaderRow) 1 else 0,
                    table.rows.size,
                    table.rows[0][0],
                ).joinToString("\t"),
            )
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()

        // --- the unchanged path: no widths and no header repeat keeps the pinned table bytes ----
        val auto = plain(listOf(listOf("Region", "Units"), listOf("North", "17")))
        val autoBytes = write(out, "auto-table", body() + auto)
        check(tableXml(autoBytes).startsWith(
            "<w:tblPr/><w:tblGrid><w:gridCol/><w:gridCol/></w:tblGrid>" +
            "<w:tr><w:tc><w:p><w:r><w:t xml:space=\"preserve\">Region</w:t></w:r></w:p></w:tc>" +
            "<w:tc><w:p><w:r><w:t xml:space=\"preserve\">Units</w:t></w:r></w:p></w:tc></w:tr>"),
        ) {
            "an auto table keeps the legacy table prefix and first row: ${tableXml(autoBytes)}"
        }
        check("tblW" !in tableXml(autoBytes) && "tblLayout" !in tableXml(autoBytes)) {
            "an auto table must not declare a width or a fixed layout"
        }
        check("tblHeader" !in tableXml(autoBytes)) { "an auto table must not repeat a header row" }
        record(rows, "auto-table", listOf(auto))

        // --- explicit widths ------------------------------------------------------------------
        val sized = DocxPackage.Block.Table(
            rows = listOf(listOf("Name", "Qty", "Price"), listOf("Widget", "3", "9.50")),
            columnWidthsTwips = listOf(3_000, 1_500, 2_250),
        )
        val sizedBytes = write(out, "sized-table", body() + sized)
        val sizedXml = tableXml(sizedBytes)
        check(sizedXml.startsWith(
            "<w:tblPr><w:tblW w:w=\"6750\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/></w:tblPr>",
        )) { "tblW must carry the column sum and precede tblLayout: $sizedXml" }
        check(sizedXml.contains("<w:tblGrid><w:gridCol w:w=\"3000\"/><w:gridCol w:w=\"1500\"/><w:gridCol w:w=\"2250\"/></w:tblGrid>")) {
            "grid columns must carry the declared widths in order: $sizedXml"
        }
        check("tblHeader" !in sizedXml) { "a sized table without the flag must not repeat its header" }
        record(rows, "sized-table", listOf(sized))

        // --- repeated header row on its own ----------------------------------------------------
        val repeated = DocxPackage.Block.Table(
            rows = listOf(listOf("Key", "Value"), listOf("a", "1"), listOf("b", "2")),
            repeatHeaderRow = true,
        )
        val repeatedBytes = write(out, "repeat-header", body() + repeated)
        val repeatedXml = tableXml(repeatedBytes)
        check(repeatedXml.contains("<w:tr><w:trPr><w:tblHeader/></w:trPr><w:tc>")) {
            "the first row must carry w:trPr/w:tblHeader: $repeatedXml"
        }
        check(repeatedXml.split("<w:tblHeader/>").size == 2) { "exactly one row repeats the header" }
        check("<w:tblPr/><w:tblGrid><w:gridCol/><w:gridCol/></w:tblGrid>" in repeatedXml) {
            "repeating a header must not invent column widths"
        }
        record(rows, "repeat-header", listOf(repeated))

        // --- both together --------------------------------------------------------------------
        val both = DocxPackage.Block.Table(
            rows = listOf(listOf("Stage", "Owner"), listOf("Draft", "Ada"), listOf("Review", "Grace")),
            columnWidthsTwips = listOf(2_400, 4_800),
            repeatHeaderRow = true,
        )
        val bothBytes = write(out, "sized-repeat", body() + both)
        val bothXml = tableXml(bothBytes)
        check(bothXml.startsWith("<w:tblPr><w:tblW w:w=\"7200\" w:type=\"dxa\"/>")) { "declared total width" }
        check(bothXml.contains("<w:gridCol w:w=\"2400\"/><w:gridCol w:w=\"4800\"/>")) { "declared grid widths" }
        check(bothXml.split("<w:tblHeader/>").size == 2) { "one repeated header" }
        record(rows, "sized-repeat", listOf(both))

        // --- a wide table and the total-width boundary -----------------------------------------
        val wide = DocxPackage.Block.Table(rows = List(2) { List(6) { "c$it" } }, columnWidthsTwips = List(6) { 5_000 })
        val wideBytes = write(out, "wide-table", body() + wide)
        check(tableXml(wideBytes).contains("<w:tblW w:w=\"30000\" w:type=\"dxa\"/>")) { "the wide table declares a 30000 twip total" }
        val boundary = DocxPackage.Block.Table(rows = listOf(listOf("l", "r")), columnWidthsTwips = listOf(240, 31_440))
        val boundaryBytes = write(out, "boundary-width", listOf(boundary))
        check(tableXml(boundaryBytes).contains("<w:tblW w:w=\"31680\" w:type=\"dxa\"/>")) { "boundary total" }
        record(rows, "wide-table", listOf(wide))
        record(rows, "boundary-width", listOf(boundary))

        // --- two tables in one document keep their own geometry ---------------------------------
        val first = DocxPackage.Block.Table(
            rows = listOf(listOf("A<1> & more", "B"), listOf("2", "3")),
            columnWidthsTwips = listOf(3_600, 3_600),
        )
        val second = DocxPackage.Block.Table(rows = listOf(listOf("plain", "auto")), repeatHeaderRow = true)
        val twoBytes = write(out, "two-tables", body() + first + DocxPackage.Block.CodeBlock(listOf("gap")) + second)
        val twoXml = tableXml(twoBytes)
        check(twoXml.contains("A&lt;1&gt; &amp; more")) { "cell text keeps XML escaping" }
        val twoDoc = text(twoBytes)
        check(twoDoc.split("<w:tblGrid>").size == 3) { "both tables carry their own grid" }
        check(twoDoc.split("<w:tblW w:w=\"7200\"").size == 2) { "only the first table declares a width" }
        check(twoDoc.split("<w:tblHeader/>").size == 2) { "only the second table repeats a header" }
        record(rows, "two-tables", listOf(first, second))

        // --- a table inside a laid-out section --------------------------------------------------
        val laidOut = DocxPackage.Block.Table(
            rows = listOf(listOf("Page", "Note")),
            columnWidthsTwips = listOf(1_800, 7_200),
            repeatHeaderRow = true,
        )
        val layout = DocxPackage.Layout(pageSetup = DocxPackage.PageSetup.a4(), footer = "Page {PAGE}")
        val layoutBytes = write(out, "layout-table", listOf(laidOut), layout)
        check(DocxPackage.readLayout(layoutBytes) == layout) { "a table does not disturb the section layout" }
        check(tableXml(layoutBytes).contains("<w:gridCol w:w=\"1800\"/><w:gridCol w:w=\"7200\"/>")) { "layout table widths" }
        record(rows, "layout-table", listOf(laidOut))

        // --- a single-cell table ---------------------------------------------------------------
        val single = DocxPackage.Block.Table(rows = listOf(listOf("alone")), columnWidthsTwips = listOf(12_000))
        val singleBytes = write(out, "single-cell", listOf(single))
        check(tableXml(singleBytes).contains("<w:gridCol w:w=\"12000\"/>")) { "single column width" }
        record(rows, "single-cell", listOf(single))

        // --- refusals ---------------------------------------------------------------------------
        fails("width count mismatch") {
            DocxPackage.Block.Table(listOf(listOf("a", "b")), columnWidthsTwips = listOf(3_000))
        }
        fails("width below the minimum") {
            DocxPackage.Block.Table(listOf(listOf("a")), columnWidthsTwips = listOf(239))
        }
        fails("width above the maximum") {
            DocxPackage.Block.Table(listOf(listOf("a")), columnWidthsTwips = listOf(31_681))
        }
        fails("total width above the maximum") {
            DocxPackage.Block.Table(listOf(listOf("a", "b")), columnWidthsTwips = listOf(31_680, 240))
        }
        fails("non-rectangular rows still refused with widths") {
            DocxPackage.Block.Table(listOf(listOf("a", "b"), listOf("c")), columnWidthsTwips = listOf(3_000, 3_000))
        }
        assertions++
        check(DocxPackage.Block.Table(listOf(listOf("a")), columnWidthsTwips = listOf(240)).columnWidthsTwips == listOf(240)) {
            "the minimum column width is accepted"
        }
        assertions++
        val maxCols = List(2) { List(DocxPackage.MAX_TABLE_COLS) { "x" } }
        check(
            DocxPackage.Block.Table(maxCols, columnWidthsTwips = List(DocxPackage.MAX_TABLE_COLS) { 240 }).columnWidthsTwips?.size ==
                DocxPackage.MAX_TABLE_COLS,
        ) { "a full-width table at the minimum column width is accepted" }

        // --- lenient import of foreign geometry -------------------------------------------------
        val stripped = rezip(bothBytes) { name, bytes ->
            if (name != "word/document.xml") bytes else {
                text(bytes).replace(Regex(" w:w=\"(2400|4800)\""), "").toByteArray(Charsets.ISO_8859_1)
            }
        }
        assertions++
        val strippedTables = DocxPackage.read(stripped).blocks.filterIsInstance<DocxPackage.Block.Table>()
        check(strippedTables.single().columnWidthsTwips == null) {
            "a table whose grid columns carry no width imports as auto-fit, not as a contradiction"
        }
        check(strippedTables.single().repeatHeaderRow) { "the header repeat survives a width-less import" }
        val dropped = rezip(bothBytes) { name, bytes ->
            if (name != "word/document.xml") bytes else {
                text(bytes).replace("<w:gridCol w:w=\"4800\"/>", "").toByteArray(Charsets.ISO_8859_1)
            }
        }
        assertions++
        check(
            DocxPackage.read(dropped).blocks.filterIsInstance<DocxPackage.Block.Table>()
                .single().columnWidthsTwips == null,
        ) { "a grid that does not cover every column imports as auto-fit" }

        File(out, "tables.tsv").writeText(rows.joinToString("\n") + "\n")
        println("R81_WRITER_TABLE_OK assertions=$assertions documents=${out.listFiles { f -> f.name.endsWith(".docx") }?.size}")
    }
}

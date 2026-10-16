package dev.fusion.ooxml

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed interface LiteralCell {
    data class Text(val value: String): LiteralCell
    data class Number(val value: BigDecimal): LiteralCell
    data class Bool(val value: Boolean): LiteralCell
    data object Empty: LiteralCell
}
data class LiteralWorksheet(val name:String,val rows:List<List<LiteralCell>>)
data class WorkbookExport(val bytes: ByteArray, val limitations: List<String>)
/** Original literal-cell XLSX writer. No LibreOffice source, import/render/recalc or parity claim.
 * Creates a NEW package. Never transforms an existing document or silently drops its features.
 */
object LiteralWorkbook {
    fun export(sheetName:String,rows:List<List<LiteralCell>>):WorkbookExport = exportSheets(listOf(LiteralWorksheet(sheetName,rows)))
    /** Caller must not mutate collections during capture. No existing package is imported or modified. */
    fun exportSheets(input:List<LiteralWorksheet>):WorkbookExport {
        require(input.size in 1..16)
        // Check structural budgets BEFORE defensive copying; do not traverse an over-limit row set.
        input.forEach { require(it.rows.size<=1000);require(it.rows.all {row->row.size<=128}) }
        require(input.sumOf {it.rows.sumOf {row->row.size.toLong()}}<=20000L)
        val sheets=input.map {LiteralWorksheet(it.name,it.rows.map {row->row.toList()})}
        require(sheets.sumOf {it.rows.sumOf {row->row.size.toLong()}}<=20000L)
        sheets.forEachIndexed { index,s ->
            require(s.name.length in 1..31 && s.name.none {it in "[]:*?/\\" || it.isISOControl()} && !s.name.startsWith("'") && !s.name.endsWith("'"))
            require(sheets.take(index).none {it.name.equals(s.name,ignoreCase=true)}) {"Duplicate worksheet name"}
            require(s.rows.size<=1000 && s.rows.all {it.size<=128})
        }
        var textBytes=0
        fun xml(s: String): String {
            require(s.length<=32767 && !Regex("_[xX][0-9a-fA-F]{4}_").containsMatchIn(s)) { "Unsupported length or OOXML escaped-string token" }
            val bytes=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(s)).remaining()
            textBytes+=bytes;require(textBytes<=2*1024*1024)
            require(s.codePoints().allMatch { it==9 || it==10 || it==13 || it in 32..0xd7ff || it in 0xe000..0xfffd || it in 0x10000..0x10ffff })
            return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;").replace("\r","&#13;")
        }
        val names=sheets.map {xml(it.name)}
        fun column(index: Int): String { var n=index+1;var result="";while(n>0) { n--;result=('A'.code+n%26).toChar()+result;n/=26 };return result }
        val worksheets=sheets.map {sheet -> buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
            sheet.rows.forEachIndexed { r,row ->
                append("<row r=\"").append(r+1).append("\">")
                row.forEachIndexed { c,cell ->
                    val ref=column(c)+(r+1)
                    when(cell) {
                        is LiteralCell.Text -> append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(cell.value)}</t></is></c>")
                        is LiteralCell.Number -> {
                            require(cell.value.precision()<=15 && cell.value.scale() in -100..100)
                            append("<c r=\"$ref\" t=\"n\"><v>${cell.value.toPlainString()}</v></c>")
                        }
                        is LiteralCell.Bool -> append("<c r=\"$ref\" t=\"b\"><v>${if(cell.value) 1 else 0}</v></c>")
                        LiteralCell.Empty -> Unit
                    }
                };append("</row>")
            };append("</sheetData></worksheet>")
        }
        }
        val parts=linkedMapOf(
            "[Content_Types].xml" to """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>"""+
                sheets.indices.joinToString("") {"""<Override PartName="/xl/worksheets/sheet${it+1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""}+"</Types>",
            "_rels/.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>"""+
                names.mapIndexed {i,name->"""<sheet name="$name" sheetId="${i+1}" r:id="rId${i+1}"/>"""}.joinToString("")+"</sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">"""+
                sheets.indices.joinToString("") {"""<Relationship Id="rId${it+1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it+1}.xml"/>"""}+"</Relationships>")
        worksheets.forEachIndexed {i,sheet->parts["xl/worksheets/sheet${i+1}.xml"]=sheet}
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for((path,content) in parts) {
                val bytes=content.toByteArray(Charsets.UTF_8)
                // A 1980 UTC epoch leaks the default timezone through DOS fields/extra-field presence.
                // Use the fixed Unix epoch: pre-DOS dates clamp, and any extended timestamp is fixed UTC zero.
                // No source creation time is inferred. setTimeLocal is API35, not our min34 contract.
                // [U] verify metadata behavior on API 34/36/37 and independent ZIP readers.
                val entry=ZipEntry(path).apply { method=ZipEntry.STORED;size=bytes.size.toLong();compressedSize=size;crc=CRC32().also { it.update(bytes) }.value;time=0L }
                zip.putNextEntry(entry);zip.write(bytes);zip.closeEntry()
            }
        }
        require(out.size()<=16*1024*1024)
        return WorkbookExport(out.toByteArray(),listOf("New literal-only workbook (${sheets.size} sheets); no import, styles, formulas, macros, layout or recalculation.","Formula-like text remains literal. Numeric consumers may use binary floating point; no decimal fidelity promise."))
    }
}

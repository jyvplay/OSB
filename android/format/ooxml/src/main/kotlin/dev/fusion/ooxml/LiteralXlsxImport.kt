package dev.fusion.ooxml
import dev.fusion.archive.StrictZip
import java.security.MessageDigest

/** Bounded, deliberately lossy text projection. NOT an Office editor, XML schema validator or fidelity parser.
 * Only this explicit subset is admitted. New literal exports omit types/styles/layout/package metadata.
 * Numbers/booleans retain raw lexical encodings as TEXT, not rendered Excel values or calendar dates.
 */
object LiteralXlsxImport {
    const val MAX_INPUT=2*1024*1024
    const val LOSS="VALUES-ONLY LOSS REPORT: new text projection; source types, styles, layout, sheet name and package metadata omitted. Numeric/boolean encodings remain raw text; no date interpretation. Empty gaps preserved to last admitted cell. Not an Office backup, anonymizer or save-back editor."
    // Compile once per isolated process, not once per cell. Dense 8192-cell workbooks otherwise
    // repeatedly compile tens of thousands of patterns inside the unchanged 5-second worker lease.
    private val escapedToken=Regex("_x[0-9a-f]{4}_",RegexOption.IGNORE_CASE)
    private val rowIndex=Regex("[1-9][0-9]{0,2}")
    private val cellCoordinate=Regex("([A-Z]{1,2})([1-9][0-9]{0,2})")
    private val stringIndex=Regex("0|[1-9][0-9]{0,3}")
    private val number=Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]{1,4})?")
    private const val S="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val R="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val P="http://schemas.openxmlformats.org/package/2006/relationships"
    private const val T="http://schemas.openxmlformats.org/package/2006/content-types"
    private val types=mapOf("xl/workbook.xml" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
        "xl/worksheets/sheet1.xml" to "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml",
        "xl/styles.xml" to "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml",
        "xl/sharedStrings.xml" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml",
        "xl/theme/theme1.xml" to "application/vnd.openxmlformats-officedocument.theme+xml",
        "docProps/core.xml" to "application/vnd.openxmlformats-package.core-properties+xml",
        "docProps/app.xml" to "application/vnd.openxmlformats-officedocument.extended-properties+xml")
    private val required=setOf("[Content_Types].xml","_rels/.rels","xl/workbook.xml","xl/_rels/workbook.xml.rels","xl/worksheets/sheet1.xml")
    private fun BoundedXml.Node.isName(local:String,ns:String=S)=name==BoundedXml.Name(ns,local)
    private fun BoundedXml.Node.blank() {require(text.all {it in " \t\r\n"})}
    private fun BoundedXml.Node.attrs(vararg names:String) {require(attributes.keys.all {it.ns.isEmpty() && it.local in names})}
    private fun BoundedXml.Node.childrenOnly(vararg names:String) {blank();require(children.all {it.name.ns==S && it.name.local in names})}
    private fun BoundedXml.Node.one(name:String)=children.single {it.isName(name)}
    private fun string(n:BoundedXml.Node):String {
        n.childrenOnly("t");require(n.children.size<=1)
        val t=n.children.singleOrNull() ?: return ""
        require(t.children.isEmpty() && t.attributes.all {(k,v)->k==BoundedXml.Name("http://www.w3.org/XML/1998/namespace","space") && v in setOf("default","preserve")})
        return t.text.also {require(it.length<=4096 && !escapedToken.containsMatchIn(it))}
    }
    fun read(bytes:ByteArray):CsvTable {
        require(bytes.size<=MAX_INPUT)
        val parts=StrictZip.read(bytes,types.keys+required);require(parts.keys.containsAll(required))
        val xml=parts.mapValues {BoundedXml.parse(it.value)}
        val ct=xml.getValue("[Content_Types].xml");require(ct.isName("Types",T));ct.blank()
        val overrides=HashSet<String>();val defaults=HashSet<String>()
        for(n in ct.children) {
            require(n.children.isEmpty());n.blank()
            when(n.name) {
                BoundedXml.Name(T,"Override")->{n.attrs("PartName","ContentType");val path=requireNotNull(n.attr("PartName"));require(path.startsWith('/'));val key=path.drop(1);require(key in parts && overrides.add(key) && types[key]!=null && n.attr("ContentType")==types[key])}
                BoundedXml.Name(T,"Default")->{n.attrs("Extension","ContentType");val ext=n.attr("Extension");require(ext in setOf("xml","rels") && defaults.add(ext!!));require(n.attr("ContentType")==if(ext=="xml") "application/xml" else "application/vnd.openxmlformats-package.relationships+xml")}
                else->error("Unsupported content type")
            }
        }
        require(overrides==parts.keys.intersect(types.keys))
        fun relationships(path:String,targets:Map<String,String>):Map<String,String> {
            val root=xml.getValue(path);require(root.isName("Relationships",P));root.blank();val byId=HashMap<String,String>();val used=HashSet<String>()
            for(n in root.children) {
                require(n.isName("Relationship",P) && n.children.isEmpty());n.blank();n.attrs("Id","Type","Target","TargetMode")
                require(n.attr("TargetMode")==null || n.attr("TargetMode")=="Internal")
                val id=requireNotNull(n.attr("Id"));require(id.length in 1..128)
                val target=requireNotNull(n.attr("Target"));val normalized=if(target.startsWith('/')) target.drop(1) else (if(path.startsWith("xl/")) "xl/" else "")+target
                require(normalized in parts && targets[normalized]!=null && n.attr("Type")==targets[normalized] && used.add(normalized))
                require(byId.put(id,normalized)==null)
            }
            require(used==targets.keys.intersect(parts.keys));return byId
        }
        relationships("_rels/.rels",mapOf("xl/workbook.xml" to "$R/officeDocument","docProps/core.xml" to "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties","docProps/app.xml" to "$R/extended-properties"))
        val rels=relationships("xl/_rels/workbook.xml.rels",mapOf("xl/worksheets/sheet1.xml" to "$R/worksheet","xl/styles.xml" to "$R/styles","xl/sharedStrings.xml" to "$R/sharedStrings","xl/theme/theme1.xml" to "$R/theme"))
        val book=xml.getValue("xl/workbook.xml");require(book.isName("workbook"));book.childrenOnly("fileVersion","workbookPr","workbookProtection","bookViews","sheets","definedNames","calcPr")
        require(book.children.map {it.name}.distinct().size==book.children.size)
        book.children.filter {it.isName("definedNames") || it.isName("workbookProtection")}.forEach {require(it.children.isEmpty() && it.attributes.isEmpty());it.blank()}
        val sheets=book.one("sheets");sheets.childrenOnly("sheet");val sheet=sheets.children.single();require(sheet.children.isEmpty());sheet.blank()
        require(sheet.attr("state")==null || sheet.attr("state")=="visible")
        require(rels[sheet.attr("id",R)]=="xl/worksheets/sheet1.xml")
        xml["xl/styles.xml"]?.let {styles->
            require(styles.isName("styleSheet"))
            styles.children.filter {it.isName("numFmts")}.forEach {require(it.children.isEmpty()) {"Custom numeric/date formats unsupported"}}
            styles.children.filter {it.isName("cellXfs")}.flatMap {it.children}.forEach {require(it.attr("numFmtId")==null || it.attr("numFmtId")=="0")}
        }
        val strings=xml["xl/sharedStrings.xml"]?.let {sst->
            require(sst.isName("sst"));sst.childrenOnly("si");require(sst.children.size<=8192);sst.children.map(::string)
        } ?: emptyList()
        val ws=xml.getValue("xl/worksheets/sheet1.xml");require(ws.isName("worksheet"));ws.childrenOnly("sheetPr","dimension","sheetViews","sheetFormatPr","sheetData","pageMargins")
        require(ws.children.map {it.name}.distinct().size==ws.children.size)
        ws.children.filter {it.isName("sheetPr")}.forEach {it.childrenOnly("outlinePr","pageSetUpPr","tabColor")}
        val data=ws.one("sheetData");data.childrenOnly("row");val cells=LinkedHashMap<Pair<Int,Int>,String>();var previousRow=0;var height=0;var width=0
        for(row in data.children) {
            row.attrs("r","spans");row.childrenOnly("c")
            val raw=requireNotNull(row.attr("r"));require(raw.matches(rowIndex));val r=raw.toInt();require(r in previousRow+1..256);previousRow=r
            var previousColumn=0
            for(c in row.children) {
                c.attrs("r","s","t");c.childrenOnly("v","is");require(c.attr("s")==null || c.attr("s")=="0")
                val coordinate=cellCoordinate.matchEntire(requireNotNull(c.attr("r"))) ?: error("Cell reference refused")
                require(coordinate.groupValues[2].toInt()==r);val col=coordinate.groupValues[1].fold(0) {v,ch->v*26+ch.code-'A'.code+1};require(col in previousColumn+1..32);previousColumn=col
                val kind=c.attr("t") ?: "n"
                val value=if(kind=="inlineStr") {
                    require(c.children.size<=1 && c.children.all {it.isName("is")});c.children.singleOrNull()?.let(::string) ?: ""
                } else {
                    require(kind in setOf("n","b","s") && c.children.size<=1 && c.children.all {it.isName("v")})
                    val v=c.children.singleOrNull();if(v==null) {require(kind!="s");""} else {
                        require(v.children.isEmpty() && v.attributes.isEmpty());val text=v.text;require(text.length<=4096)
                        when(kind) {
                            "s"->{require(text.matches(stringIndex));strings[text.toInt()]}
                            "b"->{require(text=="0" || text=="1");text}
                            else->{require(text.matches(number));text}
                        }
                    }
                }
                cells[r to col]=value;height=maxOf(height,r);width=maxOf(width,col)
            }
        }
        return CsvTable.of(List(height) {r->List(width) {c->cells[(r+1) to (c+1)] ?: ""}})
    }
}
/** Frozen request and data-only reply validation. Correlation does NOT authenticate child computation. */
class OwnedXlsxRequest(input:ByteArray) {
    private val frozen=input.also {require(it.size<=LiteralXlsxImport.MAX_INPUT)}.copyOf()
    val size:Int get()=frozen.size
    val digest=MessageDigest.getInstance("SHA-256").digest(frozen).joinToString("") {"%02x".format(it)}
    fun bytes()=frozen.copyOf()
    fun accept(size:Int,digest:String,frame:ByteArray):CsvTable {require(size==this.size && digest==this.digest);return CsvTable.readFrame(frame)}
}

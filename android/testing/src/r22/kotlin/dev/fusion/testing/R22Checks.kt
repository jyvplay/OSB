package dev.fusion.testing
import dev.fusion.archive.StrictZip
import dev.fusion.ooxml.*
import dev.fusion.browser.model.*
object R22Checks {
 @JvmStatic fun main(args:Array<String>) {
  var n=0;fun ok(b:Boolean) {check(b);n++}
  fun no(f:()->Unit) {var refused=false;try {f()} catch(_:Exception) {refused=true};ok(refused)}
  val xml=BoundedXml.parse("<p:root xmlns:p='urn:x' a='1'><p:t> α&amp;&lt; </p:t></p:root>".toByteArray())
  ok(xml.name==BoundedXml.Name("urn:x","root"));ok(xml.children.single().text==" α&< ")
  for(raw in listOf("<!DOCTYPE a [<!ENTITY x 'hi'>]><a>&x;</a>","<a><![CDATA[x]]></a>","<a><!--x--></a>","<?evil x?><a/>","<a>&missing;</a>","<a><b></a>","<?xml version='1.1'?><a/>","<?xml version='1.0' encoding='UTF-16'?><a/>")) no {BoundedXml.parse(raw.toByteArray())}
  ok(BoundedXml.parse(("<a>".repeat(32)+"</a>".repeat(32)).toByteArray()).name.local=="a")
  no {BoundedXml.parse(("<a>".repeat(33)+"</a>".repeat(33)).toByteArray())}
  no {BoundedXml.parse(byteArrayOf(0xff.toByte()))};no {BoundedXml.parse(ByteArray(1048577))}
  val book=LiteralWorkbook.export("Values",listOf(listOf(LiteralCell.Text("=1+1")))).bytes
  val names=setOf("[Content_Types].xml","_rels/.rels","xl/workbook.xml","xl/_rels/workbook.xml.rels","xl/worksheets/sheet1.xml")
  val parts=StrictZip.read(book,names);ok(parts.size==5);ok(String(parts.getValue("xl/worksheets/sheet1.xml")).contains("=1+1"))
  no {StrictZip.read(book+0,names)};no {StrictZip.read(book.copyOf(book.size-1),names)}
  val corrupt=book.copyOf();corrupt[14]=(corrupt[14].toInt() xor 1).toByte();no {StrictZip.read(corrupt,names)}
  val flag=book.copyOf();flag[6]=(flag[6].toInt() or 8).toByte();no {StrictZip.read(flag,names)}
  no {StrictZip.read(book,names-"xl/workbook.xml")}
  ok(LiteralXlsxImport.read(book).rows==listOf(listOf("=1+1")))
  val sparse=LiteralWorkbook.export("Values",listOf(listOf(LiteralCell.Text("α"),LiteralCell.Text("")),listOf(LiteralCell.Text(""),LiteralCell.Text("last")))).bytes
  ok(LiteralXlsxImport.read(sparse).rows==listOf(listOf("α",""),listOf("","last")))
  ok(LiteralXlsxImport.read(CsvTable.of(List(256) {List(32) {""}}).workbook().bytes).rows.size==256)
  val owned=OwnedXlsxRequest(book);val copy=owned.bytes();copy[0]=0
  ok(owned.bytes().contentEquals(book));no {owned.accept(0,owned.digest,CsvTable.of(emptyList()).frame())}
  no {owned.accept(owned.size,"0".repeat(64),CsvTable.of(emptyList()).frame())}
  // Exact residual: child digest/size claims do not certify semantic correctness.
  ok(owned.accept(owned.size,owned.digest,CsvTable.of(listOf(listOf("FORGED"))).frame()).rows[0][0]=="FORGED")
  ok(dev.fusion.automation.WorksheetInput.capture(java.io.ByteArrayInputStream(ByteArray(2097152))).size==2097152)
  no {dev.fusion.automation.WorksheetInput.capture(java.io.ByteArrayInputStream(ByteArray(2097153)))}
  no {dev.fusion.automation.WorksheetInput.capture(object:java.io.InputStream() {override fun read()=0;override fun read(b:ByteArray,o:Int,n:Int)=0})}
  val session=BrowserConsentSession("r22");val tab=session.open("https://example.com/");val kind=PermissionLeases.Kind.CAMERA
  val pending=session.request(tab,kind);val granted=requireNotNull(session.decide(session.request(tab,kind),true,0,1000))
  session.suspendDocument(tab);ok(session.decide(pending,true,0,1000)==null);ok(!session.consume(granted,tab,kind,1))
  no {session.request(tab,kind)};session.suspendDocument(tab);no {session.request(tab,kind)}
  session.navigate(tab,"https://example.com/")
  ok(session.consume(requireNotNull(session.decide(session.request(tab,kind),true,0,1000)),tab,kind,1))
  no {session.suspendDocument(Long.MAX_VALUE)}
  println("R22: $n actual public-consumer assertions PASS; not Office/XML/ZIP universal acceptance")
 }
}

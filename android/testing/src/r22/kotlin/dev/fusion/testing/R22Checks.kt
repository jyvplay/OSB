package dev.fusion.testing
import dev.fusion.archive.StrictZip
import dev.fusion.ooxml.*
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
  println("R22: $n actual public-consumer assertions PASS; not Office/XML/ZIP universal acceptance")
 }
}

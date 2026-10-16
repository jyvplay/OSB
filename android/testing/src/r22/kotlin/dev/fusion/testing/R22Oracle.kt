package dev.fusion.testing
import dev.fusion.archive.StrictZip
import dev.fusion.ooxml.BoundedXml
import java.util.Base64
object R22Oracle {
 @JvmStatic fun main(args:Array<String>) {
  val decoder=Base64.getDecoder();val encoder=Base64.getEncoder()
  generateSequence(::readLine).forEach {line->
   try {
    val fields=line.split('\t');val bytes=decoder.decode(fields[1])
    val result=when(fields[0]) {
     "Z"->StrictZip.read(bytes,fields[2].split(',').toSet()).toSortedMap().map {(name,data)->name+":"+encoder.encodeToString(data)}.joinToString("\n")
     "E"->{val table=dev.fusion.ooxml.LiteralXlsxImport.read(bytes).project(fields[2].split(',').map {it.toInt()});encoder.encodeToString(table.workbook().bytes)}
     "W"->{val table=dev.fusion.ooxml.LiteralXlsxImport.read(bytes);table.rows.joinToString("\n") {row->row.joinToString(",") {encoder.encodeToString(it.toByteArray())}}}
     "X"->{val node=BoundedXml.parse(bytes);node.name.ns+"|"+node.name.local+"|"+node.text+"|"+node.children.size}
     else->error("Unknown operation")
    }
    println("OK\t"+encoder.encodeToString(result.toByteArray()))
   } catch(error:Exception) {if(args.contains("--diagnose")) error.printStackTrace();println("REFUSED")}
  }
 }
}

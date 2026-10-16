package dev.fusion.testing
import dev.fusion.ooxml.CsvTable
import java.util.Base64
object R21Oracle {
 @JvmStatic fun main(args:Array<String>) {generateSequence(::readLine).forEach {line->
  try {val p=line.split('\t');val t=CsvTable.parse(Base64.getDecoder().decode(p[0]));val selected=if(p.size==1) t else t.project(p[1].split(',').map {it.toInt()})
   println(Base64.getEncoder().encodeToString(selected.frame())+"\t"+Base64.getEncoder().encodeToString(selected.workbook().bytes))
  } catch(_:IllegalArgumentException) {println("REFUSED")}
 }}
}

package dev.fusion.testing
import dev.fusion.automation.*
import java.util.Base64
object R18Oracle {
    @JvmStatic fun main(args:Array<String>) {
        val b=Base64.getDecoder();val e=Base64.getEncoder()
        generateSequence(::readLine).forEach {line ->
            val p=line.split('\t')
            if(p[0]=="S") {
                val request=dev.fusion.ooxml.OwnedSheetRequest.create(dev.fusion.ooxml.SheetFrame.decode(b.decode(p[1])))
                val values=request.accept(p[2].toInt(),p[3],b.decode(p[4]))
                println(values.entries.joinToString(",") {it.key+"="+it.value});return@forEach
            }
            val book=TaskFrame.decode(b.decode(p[1]))
            when(p[0]) {
                "C" -> println(e.encodeToString(TaskFrame.encode(book)))
                "Q" -> println(TaskQuery.select(book,b.decode(p[2]).toString(Charsets.UTF_8),TaskScope.valueOf(p[3]),TaskOrder.valueOf(p[4])).joinToString(",") {it.id})
                "E" -> println(e.encodeToString(TaskFrame.encode(book.edit(p[2],b.decode(p[3]).toString(Charsets.UTF_8),LocalTask.date(p[4])))))
                "A" -> println(e.encodeToString(TaskFrame.encode(book.archive(p[2],p[3]=="1"))))
                else -> error("Unknown reference request")
            }
        }
    }
}

package dev.fusion.testing
import dev.fusion.automation.*
import dev.fusion.ooxml.*
import java.util.Base64
import java.math.BigDecimal
object R19Oracle {
    @JvmStatic fun main(args:Array<String>) {
        val decoder=Base64.getDecoder();val encoder=Base64.getEncoder()
        fun book(s:String)=TaskFrame.decode(decoder.decode(s))
        fun wire(b:TaskBook)=encoder.encodeToString(TaskFrame.encode(b))
        generateSequence(::readLine).forEach {line->
            val p=line.split('\t')
            val result=when(p[0]) {
                "M"->{val before=book(p[1]);val plan=TaskMergePreview.inspect(before,book(p[2]));val after=plan.apply(before)
                    "${plan.added.size}:${plan.identical}:"+wire(after)+":"+(plan.change?.let {wire(it.undo(after))}?:"-")}
                "U"->{val before=book(p[1]);val after=before.edit(before.tasks.first().id,String(decoder.decode(p[2]),Charsets.UTF_8),LocalTask.date(p[3]))
                    val change=requireNotNull(TaskChange.prepare(before,after));wire(after)+":"+wire(change.undo(after))}
                "T"->encoder.encodeToString(TaskWorkbook.export(book(p[1])).bytes)
                "W"->{val text=String(decoder.decode(p[2]),Charsets.UTF_8)
                    val sheets=(1..p[1].toInt()).map {i->LiteralWorksheet("Sheet$i",listOf(listOf(LiteralCell.Text(text),LiteralCell.Number(BigDecimal(p[3])),LiteralCell.Bool(i%2==0))))}
                    encoder.encodeToString(LiteralWorkbook.exportSheets(sheets).bytes)}
                else->error("Unknown operation")
            };println(result)
        }
    }
}

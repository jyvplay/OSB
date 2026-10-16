package dev.fusion.testing
import dev.fusion.automation.*
import dev.fusion.ooxml.*
import java.io.*
import java.time.LocalDate
import java.util.UUID
import java.math.BigDecimal
import java.util.zip.ZipInputStream

object R19Checks {
    private var n=0
    private fun eq(a:Any?,b:Any?) {n++;check(a==b){"#$n $a != $b"}}
    private fun no(block:()->Unit) {n++;try {block()}catch(_:Exception){return};error("Accepted attack #$n")}
    private fun task(i:Long)=LocalTask(UUID(0,i).toString(),"Task $i",LocalDate.of(2028,2,29),done=i%2==0L)
    @JvmStatic fun main(args:Array<String>) {
        val original=TaskBook(10,listOf(task(1),task(2)))
        val changed=original.edit(task(1).id,"Revised",null)
        val token=requireNotNull(TaskChange.prepare(original,changed))
        val undone=token.undo(changed);eq(12L,undone.revision);eq(original.tasks,undone.tasks)
        no {token.undo(undone)};no {token.undo(TaskBook(changed.revision,changed.tasks.reversed()))}
        no {token.undo(TaskBook(changed.revision,changed.tasks.map {it.copy(title="different")}))}
        eq(null,TaskChange.prepare(original,TaskBook(original.revision,original.tasks)))
        no {TaskChange.prepare(original,TaskBook(11,original.tasks))};no {TaskChange.prepare(original,TaskBook(13,changed.tasks))}
        val edge=TaskBook(Long.MAX_VALUE-2,listOf(task(1)))
        val edgeChange=requireNotNull(TaskChange.prepare(edge,edge.edit(task(1).id,"Changed",null)))
        eq(Long.MAX_VALUE,edgeChange.undo(edgeChange.after).revision)
        val near=TaskBook(Long.MAX_VALUE-1,listOf(task(1)));no {TaskChange.prepare(near,near.edit(task(1).id,"Changed",null))}
        val reserved=TaskBook(Long.MAX_VALUE-1,listOf(task(1).copy(archived=true)))
        eq(Long.MAX_VALUE,reserved.archive(task(1).id,false).revision) // Old restore remains possible, without undo.
        no {TaskChange.prepare(reserved,reserved.archive(task(1).id,false))}
        val archive=requireNotNull(TaskChange.prepare(edge,edge.archive(task(1).id,true)))
        eq(edge.tasks,archive.undo(archive.after).tasks);eq(Long.MAX_VALUE,archive.undo(archive.after).revision)
        val incoming=TaskBook(800,listOf(task(2),task(3).copy(archived=true)))
        val preview=TaskMergePreview.inspect(original,incoming)
        eq(1,preview.identical);eq(listOf(task(3).copy(archived=true)),preview.added)
        val merged=preview.apply(original);eq(11L,merged.revision);eq(original.tasks+incoming.tasks.last(),merged.tasks)
        eq(original.tasks,requireNotNull(preview.change).undo(merged).tasks)
        no {preview.apply(TaskBook(10,listOf(task(7))))};no {preview.apply(original.edit(task(1).id,"Other",null))}
        no {(preview.added as MutableList<LocalTask>).clear()}
        val identical=TaskMergePreview.inspect(original,TaskBook(Long.MAX_VALUE,original.tasks))
        eq(2,identical.identical);eq(null,identical.change);eq(true,identical.apply(original)===original)
        val empty=TaskMergePreview.inspect(original,TaskBook());eq(true,empty.apply(original)===original)
        no {TaskMergePreview.inspect(original,TaskBook(0,listOf(task(1).copy(title="conflict"))))}
        val full=TaskBook(200,(1L..128L).map {task(it).copy(archived=true)})
        no {TaskMergePreview.inspect(full,TaskBook(0,listOf(task(129))))}
        eq(128,TaskMergePreview.inspect(TaskBook(),full).added.size)
        for(i in 0 until 100) {
            val before=TaskBook(i.toLong(),listOf(task(1).copy(done=i%2==0)))
            val next=before.edit(task(1).id,"Edit $i",if(i%2==0) null else LocalDate.of(9999,12,31))
            val edit=requireNotNull(TaskChange.prepare(before,next));eq(before.tasks,edit.undo(next).tasks)
            val plan=TaskMergePreview.inspect(before,TaskBook(Long.MAX_VALUE-1,listOf(task(2))))
            eq(i+1L,plan.apply(before).revision);eq(before.tasks,requireNotNull(plan.change).undo(plan.apply(before)).tasks)
        }
        val frame=TaskFrame.encode(merged);eq(merged.tasks,TaskStreams.read(frame.inputStream()).tasks)
        for(i in frame.indices) no {TaskStreams.read(frame.copyOf(i).inputStream())}
        val oversized=ByteArrayInputStream(ByteArray(TaskFrame.MAX_BYTES+100))
        no {TaskStreams.bytes(oversized)};eq(99,oversized.available())
        eq(TaskFrame.MAX_BYTES,TaskStreams.bytes(ByteArray(TaskFrame.MAX_BYTES).inputStream()).size)
        no {TaskStreams.bytes(object:InputStream(){override fun read()=error("single read");override fun read(b:ByteArray,o:Int,l:Int)=0})}
        no {TaskStreams.read((frame+byteArrayOf(1)).inputStream())}
        val sheets=(1..16).map {LiteralWorksheet("Sheet$it",listOf(listOf(LiteralCell.Text("=1+1"),LiteralCell.Number(BigDecimal("12.25")),LiteralCell.Bool(true))))}
        val exported=LiteralWorkbook.exportSheets(sheets);val entries=mutableMapOf<String,ByteArray>()
        ZipInputStream(exported.bytes.inputStream()).use {zip->while(true){val e=zip.nextEntry?:break;entries[e.name]=zip.readBytes()}}
        eq(20,entries.size);eq(true,entries.getValue("xl/worksheets/sheet16.xml").toString(Charsets.UTF_8).contains("=1+1"))
        eq(false,entries.values.any {it.toString(Charsets.UTF_8).contains("<f>")})
        no {LiteralWorkbook.exportSheets(emptyList())};no {LiteralWorkbook.exportSheets(sheets+sheets[0])}
        no {LiteralWorkbook.exportSheets(listOf(sheets[0],sheets[0].copy(name="sHEET1")))}
        no {LiteralWorkbook.exportSheets(listOf(sheets[0].copy(name="bad/name")))}
        no {LiteralWorkbook.exportSheets(listOf(sheets[0].copy(name="\uD800")))}
        no {LiteralWorkbook.exportSheets(listOf(sheets[0].copy(rows=listOf(listOf(LiteralCell.Text("_x0041_"))))))}
        val manyRows=List(100) {List(100) {LiteralCell.Empty}}
        eq(true,LiteralWorkbook.exportSheets(listOf(LiteralWorksheet("One",manyRows),LiteralWorksheet("Two",manyRows))).bytes.isNotEmpty())
        no {LiteralWorkbook.exportSheets(listOf(LiteralWorksheet("One",manyRows),LiteralWorksheet("Two",manyRows+listOf(listOf(LiteralCell.Empty)))))}
        val textRows=List(33) {listOf(LiteralCell.Text("x".repeat(32767)))}
        no {LiteralWorkbook.exportSheets(listOf(LiteralWorksheet("One",textRows),LiteralWorksheet("Two",textRows)))}
        var visited=false
        val tooMany=object:AbstractList<List<LiteralCell>>() {override val size=1001;override fun get(index:Int):List<LiteralCell>{visited=true;error("Over-limit rows visited")}}
        no {LiteralWorkbook.exportSheets(listOf(LiteralWorksheet("Data",tooMany)))};eq(false,visited)
        val lazyCells=object:AbstractList<LiteralCell>() {override val size=128;override fun get(index:Int):LiteralCell{visited=true;error("Over-budget cells copied")}}
        no {LiteralWorkbook.exportSheets(listOf(LiteralWorksheet("Data",List(200){lazyCells})))};eq(false,visited)
        no {TaskStreams.bytes(byteArrayOf().inputStream(),0)};no {TaskStreams.bytes(byteArrayOf().inputStream(),512*1024+1)}
        val largeReport=TaskWorkbook.export(TaskBook(0,(1L..128L).map {task(it).copy(title="'".repeat(256))}))
        eq(true,largeReport.bytes.size>TaskFrame.MAX_BYTES);eq(true,largeReport.bytes.size<=512*1024)
        eq(largeReport.bytes.size,TaskStreams.bytes(largeReport.bytes.inputStream(),512*1024).size)
        no {TaskStreams.read(largeReport.bytes.inputStream())}
        no {TaskWorkbook.export(TaskBook(0,listOf(task(1).copy(title="_x0041_"))))}
        no {TaskWorkbook.export(TaskBook(0,listOf(task(1).copy(title="\uFFFF"))))}
        eq(true,TaskWorkbook.export(TaskBook()).bytes.isNotEmpty())
        println("R19: $n assertions PASS; bounded undo/merge/stream and literal multi-sheet export. Not Android, Office parity or release acceptance.")
    }
}

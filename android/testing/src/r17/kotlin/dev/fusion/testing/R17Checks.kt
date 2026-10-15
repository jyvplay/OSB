package dev.fusion.testing
import dev.fusion.automation.*
import java.time.LocalDate
import java.util.UUID
object R17Checks {
    private var checks=0
    private fun eq(a:Any?,b:Any?) {checks++;check(a==b){"#$checks $a != $b"}}
    private fun no(f:()->Unit) {checks++;try {f()} catch(_:Exception) {return};error("Attack accepted #$checks")}
    @JvmStatic fun main(args:Array<String>) {
        val task=LocalTask("00000000-0000-0000-0000-000000000001","Plan 🙂",LocalDate.of(2028,2,29))
        val book=TaskBook().add(task);eq(1L,book.revision)
        eq(listOf(task),TaskFrame.decode(TaskFrame.encode(book)).tasks)
        val marked=book.mark(task.id,true);eq(true,marked.tasks[0].done);eq(2L,marked.revision)
        eq(false,marked.mark(task.id,false).tasks[0].done);eq(true,marked===marked.mark(task.id,true))
        no {book.add(task)};no {book.mark(UUID.randomUUID().toString(),true)}
        no {TaskBook(-1)};no {TaskBook(Long.MAX_VALUE).add(task)}
        val mutable=mutableListOf(task);val copy=TaskBook(0,mutable);mutable.clear();eq(1,copy.tasks.size)
        no {(copy.tasks as MutableList<LocalTask>).clear()}
        for(date in listOf("0001-01-01","9999-12-31","2000-02-29","2028-02-29","")) eq(date.ifEmpty {null},LocalTask.date(date)?.toString())
        for(date in listOf("0000-01-01","10000-01-01","2025-02-29","2026-13-01","2026-1-01"," 2026-01-01","2026-01-01Z")) no {LocalTask.date(date)}
        for(title in listOf(""," ","x\ny","x\u0000y","\uD800","x".repeat(257))) no {task.copy(title=title)}
        no {task.copy(id="1-1-1-1-1")};no {task.copy(due=LocalDate.of(0,1,1))}
        val full=TaskBook(1,(0 until 128).map {LocalTask(UUID(0,it.toLong()).toString(),"界".repeat(256),LocalDate.of(9999,12,31))})
        eq(128,TaskFrame.decode(TaskFrame.encode(full)).tasks.size);no {full.add(task.copy(id=UUID(1,1).toString()))}
        val frame=TaskFrame.encode(book)
        for(i in frame.indices) {val corrupt=frame.copyOf();corrupt[i]=(corrupt[i].toInt() xor 1).toByte();no {TaskFrame.decode(corrupt)}}
        for(n in 0 until frame.size) no {TaskFrame.decode(frame.copyOf(n))}
        no {TaskFrame.decode(frame+byteArrayOf(0))};no {TaskFrame.decode(ByteArray(TaskFrame.MAX_BYTES+1))}
        repeat(100) {n ->val t=task.copy(id=UUID(0,n.toLong()).toString(),title="Task $n 界",due=LocalDate.of(1900+n,3,1),done=n%2==0);val b=TaskBook(n.toLong(),listOf(t));val d=TaskFrame.decode(TaskFrame.encode(b));eq(b.revision,d.revision);eq(b.tasks,d.tasks)}
        fun recalc(bytes:ByteArray):ByteArray {
            val raw=bytes.copyOfRange(0,bytes.size-4)
            return raw+java.nio.ByteBuffer.allocate(4).putInt(java.util.zip.CRC32().apply{update(raw)}.value.toInt()).array()
        }
        val invalidFlag=frame.copyOf();invalidFlag[52]=2;no {TaskFrame.decode(recalc(invalidFlag))}
        val invalidRevision=frame.copyOf();java.nio.ByteBuffer.wrap(invalidRevision,4,8).putLong(-1);no {TaskFrame.decode(recalc(invalidRevision))}
        val invalidCount=frame.copyOf();java.nio.ByteBuffer.wrap(invalidCount,12,4).putInt(129);no {TaskFrame.decode(recalc(invalidCount))}
        val invalidUtf=frame.copyOf();invalidUtf[55]=0xff.toByte();no {TaskFrame.decode(recalc(invalidUtf))}
        // Explicitly demonstrate CRC is NOT authentication: a different valid snapshot imports.
        val forged=TaskFrame.encode(TaskBook(book.revision,listOf(task.copy(title="changed"))))
        eq("changed",TaskFrame.decode(forged).tasks.single().title)
        println("R17: $checks assertions PASS; immutable tasks and bounded plaintext codec, not Android/reminder/full Tasks acceptance.")
    }
}

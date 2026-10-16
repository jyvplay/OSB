package dev.fusion.testing

import dev.fusion.automation.*
import java.nio.ByteBuffer
import java.time.LocalDate
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32

object R18Checks {
    private var checks=0
    private fun eq(a:Any?,b:Any?) {checks++;check(a==b){"#$checks $a != $b"}}
    private fun no(block:()->Unit) {checks++;try {block()} catch(_:IllegalArgumentException) {return} catch(_:ArithmeticException) {return} catch(_:java.io.IOException) {return};error("Accepted attack #$checks")}
    private fun crc(frame:ByteArray):ByteArray {val raw=frame.copyOf(frame.size-4);return raw+ByteBuffer.allocate(4).putInt(CRC32().apply{update(raw)}.value.toInt()).array()}
    @JvmStatic fun main(args:Array<String>) {
        val old=Base64.getDecoder().decode("RlRTMQAAAAAAAAAHAAAAATAwMDAwMDAwLTAwMDAtMDAwMC0wMDAwLTAwMDAwMDAwMDAwMQEAC0xlZ2FjeSDwn5mCAAAAAAAAUvt+O7AB")
        val legacy=TaskFrame.decode(old);eq(7L,legacy.revision);eq("Legacy 🙂",legacy.tasks.single().title);eq(false,legacy.tasks.single().archived)
        val t=legacy.tasks.single();val migrated=TaskFrame.encode(legacy);eq("FTS2",migrated.copyOf(4).toString(Charsets.US_ASCII));eq(legacy.tasks,TaskFrame.decode(migrated).tasks)
        val edited=legacy.edit(t.id,"Edited",null);eq(t.id,edited.tasks[0].id);eq(true,edited.tasks[0].done);eq(null,edited.tasks[0].due);eq(8L,edited.revision)
        eq(true,edited===edited.edit(t.id,"Edited",null))
        val archived=edited.archive(t.id,true);eq(true,archived.tasks.single().archived);eq(9L,archived.revision)
        eq(edited.tasks,archived.archive(t.id,false).tasks);eq(10L,archived.archive(t.id,false).revision)
        no {archived.edit(t.id,"Not allowed",null)};no {archived.mark(t.id,false)};no {TaskBook().add(t.copy(archived=true))}
        for(b in listOf(legacy,archived)) {no {b.archive(UUID(1,2).toString(),true)};no {b.edit(UUID(1,2).toString(),"x",null)}}
        val max=TaskBook(Long.MAX_VALUE,listOf(t));eq(true,max===max.archive(t.id,false));eq(true,max===max.edit(t.id,t.title,t.due))
        no {max.archive(t.id,true)};no {max.edit(t.id,"new",null)}
        val original=TaskFrame.encode(legacy)
        no {legacy.edit(t.id,"\uD800",null)};no {legacy.edit(t.id," ",null)};no {legacy.edit(t.id,"x",LocalDate.of(0,1,1))};eq(true,original.contentEquals(TaskFrame.encode(legacy)))
        for(version in listOf(0x46545331,0x46545332)) for(flag in 0..255) {
            val wire=old.copyOf();ByteBuffer.wrap(wire).putInt(version);wire[52]=flag.toByte();val fixed=crc(wire)
            if(flag<=(if(version==0x46545331) 1 else 3)) {
                val value=TaskFrame.decode(fixed).tasks.single();eq(flag and 1 != 0,value.done);eq(flag and 2 != 0,value.archived)
            } else no {TaskFrame.decode(fixed)}
        }
        val unknown=old.copyOf();unknown[3]=0x33;no {TaskFrame.decode(crc(unknown))}
        val all=(0 until 128).map {i ->LocalTask(UUID(0,i.toLong()).toString(),"界".repeat(256),if(i%3==0) null else LocalDate.of(2028,1,1).plusDays((i%5).toLong()),i%2==0,i%4==0)}
        val full=TaskBook(100,all);eq(104340,TaskFrame.encode(full).size);eq(all,TaskFrame.decode(TaskFrame.encode(full)).tasks)
        no {full.add(t.copy(id=UUID(2,3).toString()))};eq(128,full.archive(all[1].id,true).tasks.size)
        for(scope in TaskScope.entries) for(order in TaskOrder.entries) {
            val selected=TaskQuery.select(full,"",scope,order)
            val ids=selected.map {it.id};eq(ids.size,ids.toSet().size);eq(100L,full.revision)
            eq(true,selected.all {when(scope) {TaskScope.ACTIVE->!it.archived;TaskScope.OPEN->!it.archived&&!it.done;TaskScope.COMPLETED->!it.archived&&it.done;TaskScope.ARCHIVED->it.archived}})
            if(order==TaskOrder.DUE) eq(true,selected.zipWithNext().all { (a,b) ->val ad=a.due;val bd=b.due;ad!=null && (bd==null || ad<=bd) || ad==null && bd==null })
        }
        val sameDate=TaskBook(0,listOf(t.copy(id=UUID(0,1).toString(),title="First"),t.copy(id=UUID(0,2).toString(),title="Second")))
        eq(sameDate.tasks,TaskQuery.select(sameDate,"",TaskScope.ACTIVE,TaskOrder.DUE))
        val savedLocale=Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            eq(listOf("INDEX"),TaskQuery.select(TaskBook(0,listOf(t.copy(title="INDEX"))),"index").map {it.title})
        } finally {Locale.setDefault(savedLocale)}
        val literal=TaskBook(0,listOf(t.copy(title="a.*[b]🙂")))
        eq(1,TaskQuery.select(literal,".*[").size);eq(0,TaskQuery.select(literal,"a.*z").size)
        for(q in listOf("\uD800","\n","x".repeat(257))) no {TaskQuery.select(full,q)}
        eq(128,TaskQuery.select(TaskBook(0,all.map{it.copy(archived=false)}),"界".repeat(256)).size)
        // Repeated operations preserve data and versioned roundtrip; archived state survives serialization.
        var book=legacy
        repeat(100) {i ->
            book=book.edit(t.id,"Task $i",LocalDate.of(2028,2,29)).archive(t.id,true)
            book=TaskFrame.decode(TaskFrame.encode(book));eq(true,book.tasks.single().archived)
            book=book.archive(t.id,false);eq("Task $i",book.tasks.single().title);eq(true,book.tasks.single().done)
        }
        println("R18: $checks assertions PASS; editing/archive/query/versioned codec, not reminders or full Tasks acceptance.")
    }
}

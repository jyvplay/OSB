package dev.fusion.testing
import dev.fusion.automation.*
import java.util.Base64
object R17Oracle {
    @JvmStatic fun main(args:Array<String>) {
        generateSequence(::readLine).forEach {line ->
            val p=line.split('\t');val b=Base64.getDecoder()
            if(p[0]=="E") {
                val t=LocalTask(p[1],b.decode(p[2]).toString(Charsets.UTF_8),LocalTask.date(p[3]),p[4]=="1")
                println(Base64.getEncoder().encodeToString(TaskFrame.encode(TaskBook(p[5].toLong(),listOf(t)))))
            } else {
                val book=TaskFrame.decode(b.decode(p[1]));val t=book.tasks.single()
                println(listOf(book.revision,t.id,Base64.getEncoder().encodeToString(t.title.toByteArray()),t.due?.toString()?:"",if(t.done) 1 else 0).joinToString("\t"))
            }
        }
    }
}

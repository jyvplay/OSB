package dev.fusion.automation

import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDate
import java.util.UUID

class TaskConflict : IllegalStateException("Tasks changed; reload before editing")

/** One-app-process private store. All instances share a lock; NOT cross-process locking.
 * Caller uses an app-private/no-backup path and calls off main thread. No SAF/source overwrite.
 * AtomicFile + verified reopen is not a power-loss/directory-fsync theorem. An error after
 * publication means outcome uncertain: reload, never assume rollback or blindly retry.
 * [U] verify on API 34/36/37 and physical storage before release acceptance.
 */
class TaskRepository(private val base: File) {
    private val file=AtomicFile(base)
    fun load(): TaskBook = synchronized(lock) { read() }
    fun add(expectedRevision: Long, title: String, due: LocalDate?): TaskBook = synchronized(lock) {
        val current=read();if(current.revision!=expectedRevision) throw TaskConflict()
        save(current.add(LocalTask(UUID.randomUUID().toString(),title,due)))
    }
    fun mark(expectedRevision: Long, id: String, done: Boolean): TaskBook = synchronized(lock) {
        val current=read();if(current.revision!=expectedRevision) throw TaskConflict()
        val next=current.mark(id,done);if(next===current) current else save(next)
    }
    private fun read(): TaskBook {
        val input=try { file.openRead() } catch(e:FileNotFoundException) {
            if(!base.exists() && !File(base.path+".bak").exists() && !File(base.path+".new").exists()) return TaskBook()
            throw e
        }
        return input.use {
            val out=ByteArrayOutputStream();val buf=ByteArray(8192)
            while(true) { val n=it.read(buf);if(n<0) break;if(out.size()+n>TaskFrame.MAX_BYTES) throw IOException("Task store over limit");out.write(buf,0,n) }
            TaskFrame.decode(out.toByteArray())
        }
    }
    private fun save(book: TaskBook): TaskBook {
        val bytes=TaskFrame.encode(book);val stream=file.startWrite()
        try { stream.write(bytes);file.finishWrite(stream) } catch(e:Exception) { file.failWrite(stream);throw e }
        val observed=read()
        if(!TaskFrame.encode(observed).contentEquals(bytes)) throw IOException("Task write could not be confirmed")
        return observed
    }
    private companion object { val lock=Any() }
}

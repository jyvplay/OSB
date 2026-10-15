package dev.fusion.automation

import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.util.Collections
import java.util.UUID
import java.util.zip.CRC32

/** Date-only local tasks. No reminder, timezone, recurrence or calendar-provider semantics. */
data class LocalTask(val id: String, val title: String, val due: LocalDate? = null, val done: Boolean = false) {
    init {
        require(UUID.fromString(id).toString() == id) { "Canonical UUID required" }
        validate(title,due)
    }
    companion object {
        fun validate(title: String, due: LocalDate?) {
            require(title.length in 1..256 && title.isNotBlank() && title.none { it.isISOControl() })
            strictBytes(title);require(due == null || due.year in 1..9999)
        }
        fun date(text: String): LocalDate? {
            if (text.isEmpty()) return null
            require(text.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
            return LocalDate.parse(text).also { require(it.year in 1..9999) }
        }
        internal fun strictBytes(text: String): ByteArray {
            val b=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text))
            return ByteArray(b.remaining()).also(b::get)
        }
    }
}
/** Defensive immutable snapshot. Stale writers must compare revision in the storage adapter. */
class TaskBook(val revision: Long = 0, tasks: List<LocalTask> = emptyList()) {
    val tasks: List<LocalTask> = Collections.unmodifiableList(ArrayList(tasks.also {require(it.size<=128)}))
    init { require(revision >= 0 && this.tasks.size <= 128);require(this.tasks.map { it.id }.toSet().size == this.tasks.size) }
    fun add(task: LocalTask) = TaskBook(Math.incrementExact(revision), tasks + task)
    fun mark(id: String, done: Boolean): TaskBook {
        val task=requireNotNull(tasks.find { it.id == id }) { "Unknown task" }
        if (task.done == done) return this
        return TaskBook(Math.incrementExact(revision), tasks.map { if(it.id == id) it.copy(done=done) else it })
    }
}

/** Canonical bounded plaintext; CRC detects accidents, NOT malicious modification. */
object TaskFrame {
    const val MAX_BYTES = 128 * 1024
    private const val MAGIC = 0x46545331 // FTS1
    fun encode(book: TaskBook): ByteArray {
        val buffer=ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC);out.writeLong(book.revision);out.writeInt(book.tasks.size)
            for(task in book.tasks) {
                out.write(task.id.toByteArray(Charsets.US_ASCII));out.writeByte(if(task.done) 1 else 0)
                val title=LocalTask.strictBytes(task.title);out.writeShort(title.size);out.write(title)
                out.writeLong(task.due?.toEpochDay() ?: Long.MIN_VALUE)
            }
        }
        val raw=buffer.toByteArray();require(raw.size+4 <= MAX_BYTES)
        return raw + ByteBuffer.allocate(4).putInt(CRC32().apply { update(raw) }.value.toInt()).array()
    }
    fun decode(frame: ByteArray): TaskBook {
        require(frame.size in 20..MAX_BYTES)
        val raw=frame.copyOfRange(0,frame.size-4)
        require(CRC32().apply { update(raw) }.value.toInt()==ByteBuffer.wrap(frame,frame.size-4,4).int)
        val input=DataInputStream(raw.inputStream());require(input.readInt()==MAGIC)
        val revision=input.readLong();val count=input.readInt();require(count in 0..128)
        val tasks=ArrayList<LocalTask>()
        repeat(count) {
            val id=ByteArray(36).also(input::readFully).toString(Charsets.US_ASCII)
            val flag=input.readUnsignedByte();require(flag in 0..1)
            val n=input.readUnsignedShort();require(n in 1..1024)
            val title=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(ByteArray(n).also(input::readFully))).toString()
            val day=input.readLong()
            tasks+=LocalTask(id,title,if(day==Long.MIN_VALUE) null else LocalDate.ofEpochDay(day),flag==1)
        }
        require(input.available()==0)
        val book=TaskBook(revision,tasks);require(encode(book).contentEquals(frame));return book
    }
}

package dev.fusion.notes

import java.io.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Local recovery only: no URI, pending command, trash receipt or authority to replay a mutation. */
data class NoteDraft(val name: String = "", val text: String = "", val savedText: String = "", val revision: NoteRevision? = null)

/** Strict, bounded v1 checkpoint. SHA-256 detects corruption, NOT a malicious same-UID writer. */
object NoteDraftCodec {
    const val MAX_FRAME = 2 * NoteFiles.MAX_BYTES + 1024
    private const val MAGIC = 0x4e445231 // NDR1
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun utf8(text: String, limit: Int): ByteArray {
        require(text.length <= limit) { "Draft field exceeds character bound" }
        val b=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text))
        require(b.remaining() <= limit) { "Draft field exceeds UTF-8 bound" }
        return ByteArray(b.remaining()).also(b::get)
    }
    private fun validate(d: NoteDraft) {
        require(d.name.length <= 64)
        utf8(d.name,256);utf8(d.text,NoteFiles.MAX_BYTES)
        val saved=utf8(d.savedText,NoteFiles.MAX_BYTES)
        if(d.revision==null) require(d.savedText.isEmpty()) { "Unsaved note cannot claim a saved base" }
        else {
            NoteFiles.validateName(d.name)
            require(d.revision.name==d.name)
            val digest=sha(saved).joinToString("") { "%02x".format(it) }
            require(d.revision.sha256==digest) { "Revision does not bind saved base" }
        }
    }
    fun write(d: NoteDraft, output: OutputStream) {
        validate(d)
        val body=ByteArrayOutputStream()
        DataOutputStream(body).apply {
            fun field(text: String, limit: Int) { val b=utf8(text,limit);writeInt(b.size);write(b) }
            field(d.name,256);field(d.text,NoteFiles.MAX_BYTES);field(d.savedText,NoteFiles.MAX_BYTES)
            writeBoolean(d.revision!=null)
            d.revision?.let { field(it.sha256,64) }
            flush()
        }
        val bytes=body.toByteArray()
        check(bytes.size+40 <= MAX_FRAME)
        DataOutputStream(output).apply { writeInt(MAGIC);writeInt(bytes.size);write(bytes);write(sha(bytes));flush() }
    }
    fun read(input: InputStream): NoteDraft {
        val frame=DataInputStream(input)
        require(frame.readInt()==MAGIC) { "Unknown draft version" }
        val length=frame.readInt();require(length in 13..MAX_FRAME-40) { "Invalid draft length" }
        val body=ByteArray(length);frame.readFully(body)
        val expected=ByteArray(32);frame.readFully(expected)
        require(MessageDigest.isEqual(sha(body),expected)) { "Draft digest mismatch" }
        require(frame.read()==-1) { "Trailing checkpoint bytes" }
        val data=DataInputStream(ByteArrayInputStream(body))
        fun field(limit: Int): String {
            val size=data.readInt();require(size in 0..limit && size<=data.available()) { "Invalid field length" }
            val b=ByteArray(size);data.readFully(b)
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString()
        }
        val name=field(256);val text=field(NoteFiles.MAX_BYTES);val saved=field(NoteFiles.MAX_BYTES)
        val flag=data.readUnsignedByte();require(flag in 0..1) { "Invalid revision flag" }
        val revision=if(flag==1) NoteRevision(name,field(64)) else null
        require(data.available()==0) { "Trailing payload bytes" }
        return NoteDraft(name,text,saved,revision).also(::validate)
    }
}

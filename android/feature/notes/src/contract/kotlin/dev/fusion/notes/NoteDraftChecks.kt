package dev.fusion.notes

import java.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.MessageDigest

object NoteDraftChecks {
    private var checks=0
    private fun expect(v:Boolean) { checks++;check(v) { "Draft check $checks" } }
    private fun refused(f:()->Unit) { checks++;try { f() } catch(_:Exception) { return };error("Draft attack accepted: $checks") }
    private fun encoded(d:NoteDraft)=ByteArrayOutputStream().also { NoteDraftCodec.write(d,it) }.toByteArray()
    private fun decoded(b:ByteArray)=NoteDraftCodec.read(ByteArrayInputStream(b))
    private fun reseal(b:ByteArray):ByteArray {
        val size=ByteBuffer.wrap(b,4,4).int
        MessageDigest.getInstance("SHA-256").digest(b.copyOfRange(8,8+size)).copyInto(b,8+size)
        return b
    }
    @JvmStatic fun main(args:Array<String>) {
        val root=Files.createTempDirectory("note-draft-contract-")
        try {
            val files=NoteFiles(root);val saved=files.save("界","saved\r\n🙂\u0000",null)
            val cases=listOf(NoteDraft(),NoteDraft("unfinished name","draft\r\nα🙂\u0000"),
                NoteDraft(saved.name,"unsaved",saved.text,saved.revision),NoteDraft("n","x".repeat(NoteFiles.MAX_BYTES)))
            for(d in cases) { val b=encoded(d);expect(decoded(b)==d);expect(b.size<=NoteDraftCodec.MAX_FRAME) }
            val frame=encoded(cases[2]);for(i in frame.indices) refused { decoded(frame.copyOf(i)) }
            for(i in frame.indices) refused { decoded(frame.copyOf().also { it[i]=(it[i].toInt() xor 1).toByte() }) }
            refused { decoded(frame+0) };refused { decoded(frame.copyOf().also { ByteBuffer.wrap(it,4,4).putInt(Int.MAX_VALUE) }) }
            refused { encoded(NoteDraft("n","\uD800")) }
            refused { encoded(NoteDraft("n","界".repeat(NoteFiles.MAX_BYTES/3+1))) }
            refused { encoded(NoteDraft("n","x","orphan base")) }
            refused { encoded(cases[2].copy(revision=NoteRevision("elsewhere",saved.revision.sha256))) }
            refused { encoded(cases[2].copy(savedText="changed")) }
            refused { encoded(NoteDraft("x".repeat(65))) }
            // Attacker recomputes checksum: structural/UTF-8 checks must independently refuse.
            val small=encoded(NoteDraft("a","b"))
            refused { decoded(reseal(small.copyOf().also { ByteBuffer.wrap(it,8,4).putInt(-1) })) }
            refused { decoded(reseal(small.copyOf().also { it[12]=0xff.toByte() })) }
            refused { decoded(reseal(small.copyOf().also { it[it.size-33]=2 })) }
            // A real stale recovered base cannot bless a changed saved note.
            val restored=decoded(frame);val newer=files.save(saved.name,"external",saved.revision)
            refused { files.save(restored.name,restored.text,restored.revision) }
            expect(files.load(saved.name)==newer)
            expect(decoded(encoded(restored))==restored)
            println("Notes draft codec: $checks assertions passed (strict frame/UTF-8/corruption/resealed attacks and real-file stale base).")
        } finally { root.toFile().deleteRecursively() }
    }
}

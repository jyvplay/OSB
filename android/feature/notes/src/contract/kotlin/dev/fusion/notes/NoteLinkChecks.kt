package dev.fusion.notes

import java.nio.file.Files
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest

object NoteLinkChecks {
    private var checks=0
    private fun expect(value:Boolean) { checks++;check(value) { "Note link check $checks failed" } }
    private fun refused(block:()->Unit) { var failed=false;try { block() } catch(_:Exception) { failed=true };expect(failed) }
    private fun snapshot(name:String,text:String):NoteSnapshot = NoteSnapshot(name,text,NoteRevision(name,
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }))
    @JvmStatic fun main(args:Array<String>) {
        expect(NoteLinks.references("[[Main]] [[Other]] [[Main]] [[猫]]")==setOf("Main","Other","猫"))
        expect(NoteLinks.references("\\[[escaped]] [[live]]")==setOf("live"))
        expect(NoteLinks.references("`[[code]]` [[live]] ``[[also_code]]``")==setOf("live"))
        expect(NoteLinks.references("~~~ kotlin\n[[code]]\n~~~\n[[live]]")==setOf("live"))
        expect(NoteLinks.references("   ```\n[[code]]\n   ```\n[[live]]")==setOf("live"))
        expect(NoteLinks.references("```\n[[hidden]]").isEmpty())
        expect(NoteLinks.references("[[../escape]] [[name.md]] [[name|alias]] [[bad space]]").isEmpty())
        expect(NoteLinks.references("[["+"a".repeat(65)+"]]").isEmpty())
        expect(NoteLinks.references("[["+"猫".repeat(64)+"]]").isEmpty())
        expect(NoteLinks.references("[["+"a".repeat(64)+"]]").single().length==64)
        expect(NoteLinks.references("[label](Other.md)").isEmpty()) // Explicit unsupported adjacent syntax.
        refused { NoteLinks.references("[[a]]".repeat(NoteLinks.MAX_OCCURRENCES+1)) }
        refused { NoteLinks.references("\uD800") }
        val graph=NoteLinks.build(sequenceOf(snapshot("Main","[[Other]] [[Missing]]"),snapshot("Other","[[Main]] [[Other]]")))
        expect(graph.backlinks("Main")==listOf("Other"))
        expect(graph.backlinks("Other")==listOf("Main")) // Self references are not incoming backlinks.
        expect(graph.unresolved("Main")==setOf("Missing"))
        refused { (graph.outgoing as MutableMap<String,Set<String>>).clear() }
        refused { (graph.outgoing.getValue("Main") as MutableSet<String>).clear() }
        refused { NoteLinks.build(sequenceOf(snapshot("Main","x"),snapshot("Main","y"))) }
        refused { NoteLinks.build(sequenceOf(snapshot("Main","x").copy(text="changed"))) }
        refused { NoteLinks.build((0..256).asSequence().map { snapshot("Note$it","") }) }
        val large="x".repeat(NoteFiles.MAX_BYTES)
        refused { NoteLinks.build((0..32).asSequence().map { snapshot("N$it",large) }) }
        expect(NoteLinks.build((0 until 32).asSequence().map { snapshot("N$it",large) }).outgoing.size==32)
        val dir=Files.createTempDirectory("fusion-backlinks-")
        try {
            val files=NoteFiles(dir)
            val main=files.save("Main","[[Other]] [[Missing]]",null)
            val other=files.save("Other","[[Main]]",null)
            val before=Files.readAllBytes(dir.resolve("Main.md"))
            val saved=files.links()
            expect(saved.backlinks("Main")==listOf("Other"))
            expect(saved.revisions["Main"]==main.revision)
            expect(Files.readAllBytes(dir.resolve("Main.md")).contentEquals(before))
            files.save("Other","No links now",other.revision)
            expect(saved.backlinks("Main")==listOf("Other")) // Snapshot does not mutate after a later publication.
            expect(files.links().backlinks("Main").isEmpty())
            FileChannel.open(dir.resolve(".notes.lock"),WRITE).use { channel -> channel.lock().use { refused { files.links() } } }
        } finally { dir.toFile().deleteRecursively() }
        for(i in 1..200) {
            val name="Note_$i";expect(NoteLinks.references("before [[$name]] after")==setOf(name))
            expect(NoteLinks.references("`[[$name]]` [[Other]]")==setOf("Other"))
        }
        println("PASS note links: $checks assertions; declared wiki grammar, immutable graph/revisions, byte/reference limits, real saved-file scan and lock refusal. Not full CommonMark, attachments, OCR or encryption.")
    }
}

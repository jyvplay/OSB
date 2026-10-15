package dev.fusion.notes

import java.nio.file.Files
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real files, atomic moves, symlinks and OS-lock contention; no mocked provider or simulated crash. */
object NoteTrashChecks {
    private var checks=0
    private fun expect(value:Boolean) { checks++;check(value) { "Trash check $checks failed" } }
    private fun refused(block:()->Unit) { checks++;try { block() } catch(_:Exception) { return };error("Expected refusal $checks") }
    private fun file(root:java.nio.file.Path,e:TrashedNote)=root.resolve(".trash-v1/${e.name}--${e.token}_${e.revision.sha256}.md")
    @JvmStatic fun main(args:Array<String>) {
        val parent=Files.createTempDirectory("fusion-real-note-trash-")
        try {
            val root=parent.resolve("normal");val store=NoteFiles(root)
            expect(store.trash().isEmpty())
            val original=store.save("Main","saved α\r\n\u0000",null)
            val entry=store.delete("Main",original.revision)
            expect(!Files.exists(root.resolve("Main.md")))
            expect(Files.readAllBytes(file(root,entry)).contentEquals(original.text.toByteArray()))
            expect(NoteFiles(root).trash()==listOf(entry)) // Real reopen, not retained ViewModel identity.
            refused { (store.trash() as MutableList<TrashedNote>).clear() }
            expect(store.restore(entry)==original)
            expect(store.trash().isEmpty());refused { store.restore(entry) }
            val next=store.delete("Main",original.revision)
            expect(next.token!=entry.token)
            refused { store.restore(entry) }
            refused { store.restore(next.copy(token="../outside")) }
            refused { store.restore(next.copy(name="Other")) }
            refused { store.restore(next.copy(revision=NoteRevision("Other",next.revision.sha256))) }
            val newer=store.save("Main","newer saved content",null)
            refused { store.restore(next) };expect(store.load("Main")==newer);expect(store.trash()==listOf(next))
            val newestTrash=store.delete("Main",newer.revision)
            expect(store.restore(next)==original)
            refused { store.restore(newestTrash) } // Even genuine history never overwrites a live name.
            expect(store.load("Main")==original)
            val same=store.save("Same","same",null);val sameTrash=store.delete("Same",same.revision)
            store.save("Same","same",null);refused { store.restore(sameTrash) }
            val longName="界".repeat(42)
            val unicode=store.save(longName,"🙂",null);expect(store.restore(store.delete(longName,unicode.revision))==unicode)
            FileChannel.open(root.resolve(".notes.lock"),WRITE).use { channel -> channel.lock().use {
                refused { store.trash() };refused { store.restore(newestTrash) };refused { store.delete("Main",original.revision) }
            } }
            val tamperRoot=parent.resolve("tamper");val tamper=NoteFiles(tamperRoot)
            val note=tamper.save("Test","original",null);val receipt=tamper.delete("Test",note.revision)
            Files.writeString(file(tamperRoot,receipt),"altered")
            refused { tamper.trash() };refused { tamper.restore(receipt) }
            expect(Files.readString(file(tamperRoot,receipt))=="altered")
            Files.delete(file(tamperRoot,receipt));val victim=parent.resolve("victim.txt");Files.writeString(victim,"outside")
            Files.createSymbolicLink(file(tamperRoot,receipt),victim)
            refused { tamper.trash() };refused { tamper.restore(receipt) };expect(Files.readString(victim)=="outside")
            val symlinkRoot=parent.resolve("symlink");val symlink=NoteFiles(symlinkRoot)
            val protected=symlink.save("Kept","kept",null)
            Files.createSymbolicLink(symlinkRoot.resolve(".trash-v1"),tamperRoot)
            refused { symlink.delete("Kept",protected.revision) };expect(symlink.load("Kept")==protected)
            val capacityRoot=parent.resolve("capacity");val capacity=NoteFiles(capacityRoot)
            repeat(NoteFiles.MAX_TRASH) { i ->
                val n=capacity.save("N$i","body$i",null)
                capacity.delete(n.name,n.revision)
            }
            expect(capacity.trash().size==NoteFiles.MAX_TRASH)
            val kept=capacity.save("Kept","retained",null)
            refused { capacity.delete("Kept",kept.revision) };expect(capacity.load("Kept")==kept)
            val oldest=capacity.trash().first();expect(capacity.restore(oldest).name==oldest.name)
            expect(capacity.trash().size==NoteFiles.MAX_TRASH-1)
            val fullRoot=parent.resolve("live-capacity");val full=NoteFiles(fullRoot)
            val n=full.save("Restorable","r",null);val r=full.delete(n.name,n.revision)
            repeat(NoteFiles.MAX_NOTES) { Files.writeString(fullRoot.resolve("N$it.md"),"existing") }
            refused { full.restore(r) };expect(Files.exists(file(fullRoot,r)))
            val raceRoot=parent.resolve("race");val race=NoteFiles(raceRoot)
            val rn=race.save("Race","retained",null);val re=race.delete(rn.name,rn.revision)
            val start=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
            try {
                val results=(1..2).map { pool.submit<Boolean> {
                    start.await();try { NoteFiles(raceRoot).restore(re);true } catch(_:Exception) { false }
                } }
                start.countDown();expect(results.count { it.get(10,TimeUnit.SECONDS) }==1)
                expect(race.load("Race")==rn);expect(race.trash().isEmpty())
            } finally { pool.shutdownNow() }
            println("PASS note trash: $checks assertions; real same-filesystem moves, reopen/restore, revision/name/receipt binding, no overwrite, capacity, Unicode, symlink/tamper refusal and competing OS-lock restorers. Not SAF or physical-power-loss acceptance.")
        } finally { parent.toFile().deleteRecursively() }
    }
}

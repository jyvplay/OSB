package dev.fusion.notes

import java.nio.file.Files
import java.nio.file.StandardOpenOption.APPEND
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real production file I/O. No Android substitutes, mocked disk or simulated crash. */
object NotesChecks {
    private var checks = 0
    private fun expect(value: Boolean) { checks++; check(value) { "Notes check $checks failed" } }
    private inline fun refused(block: () -> Unit) { checks++; try { block() } catch (_: Exception) { return }; error("Expected refusal at check $checks") }
    @JvmStatic fun main(args: Array<String>) {
        val parent = Files.createTempDirectory("fusion-notes-contract-")
        try {
            val root = parent.resolve("notes")
            val store = NoteFiles(root)
            val retainedTemporary=root.resolve(".note-write-retained.tmp")
            Files.writeString(retainedTemporary,"earlier unconfirmed attempt")
            val victim = parent.resolve("victim.md")
            Files.writeString(victim, "outside unchanged")
            expect(store.list().isEmpty())
            for (name in listOf("", "../victim", "/tmp/victim", "..", ".", "a/b", "a\\b", "a\u0000", "x".repeat(65), "😀")) {
                refused { store.load(name) }; refused { store.save(name,"bad",null) }
                refused { store.delete(name, NoteRevision(name, "bad")) }
            }
            expect(Files.readString(victim) == "outside unchanged")
            val first = store.save("first", "α\r\nzero\u0000\n", null)
            expect(store.load("first") == first)
            expect(store.list() == listOf("first"))
            refused { store.save("first", "silent overwrite", null) }
            val second = store.save("second", first.text, null)
            // Same bytes are not the same note: patch-specific identity-binding attack.
            refused { store.save("second", "wrong target", first.revision) }
            refused { store.delete("second", first.revision) }
            expect(store.load("second") == second)
            val updated = store.save("first", "updated", first.revision)
            refused { store.save("first", "stale edit", first.revision) }
            refused { store.delete("first", first.revision) }
            expect(store.load("first") == updated)
            refused { store.save("invalid", "\uD800", null) }
            refused { store.save("too_big", "a".repeat(NoteFiles.MAX_BYTES+1), null) }
            refused { store.save("multibyte", "界".repeat(NoteFiles.MAX_BYTES/3+1), null) }
            val maximum = store.save("maximum", "a".repeat(NoteFiles.MAX_BYTES), null)
            expect(store.load("maximum").text.length == NoteFiles.MAX_BYTES)
            Files.writeString(root.resolve("maximum.md"), "x", APPEND)
            refused { store.load("maximum") }
            Files.write(root.resolve("malformed.md"), byteArrayOf(0xc0.toByte(), 0x80.toByte()))
            refused { store.load("malformed") }
            Files.createSymbolicLink(root.resolve("linked.md"), victim)
            refused { store.load("linked") }; refused { store.save("linked", "wrong", null) }
            refused { store.list() }
            expect(Files.readString(victim) == "outside unchanged")
            Files.delete(root.resolve("linked.md")); Files.delete(root.resolve("malformed.md")); Files.delete(root.resolve("maximum.md"))
            store.delete("second", second.revision)
            refused { store.save("second", "resurrect", second.revision) }
            refused { store.delete("second", second.revision) }
            // Competing real repository instances and real OS locks, not an interleaving model.
            val peer = NoteFiles(root)
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val results = listOf(store to "writer-a", peer to "writer-b").map { (repository,text) -> pool.submit<Boolean> {
                    check(start.await(5,TimeUnit.SECONDS))
                    try { repository.save("first",text,updated.revision); true }
                    catch (_: NotesBusy) { false }
                    catch (_: NoteConflict) { false }
                } }
                start.countDown()
                expect(results.count { it.get(10,TimeUnit.SECONDS) } == 1)
                expect(store.load("first").text in setOf("writer-a","writer-b"))
            } finally { pool.shutdownNow() }
            expect(Files.readString(retainedTemporary)=="earlier unconfirmed attempt")
            Files.delete(retainedTemporary)
            expect(Files.list(root).use { stream -> stream.noneMatch { it.fileName.toString().startsWith(".note-write-") } })
            val limitedRoot = parent.resolve("limited")
            val limited = NoteFiles(limitedRoot)
            repeat(NoteFiles.MAX_NOTES) { Files.writeString(limitedRoot.resolve("n$it.md"), "legacy") }
            expect(limited.list().size == NoteFiles.MAX_NOTES)
            refused { limited.save("overflow", "no", null) }
            val legacy = limited.load("n0")
            expect(limited.save("n0", "allowed update at capacity", legacy.revision).text == "allowed update at capacity")
            Files.writeString(limitedRoot.resolve("extra.md"), "external")
            refused { limited.list() }
            println("PASS: $checks notes assertions; real bounded UTF-8 files, atomic publish/readback, symlink/path/refused overwrite, stale/cross-note revisions and competing OS-lock writers. No Android or power-loss acceptance.")
        } finally { parent.toFile().deleteRecursively() }
    }
}

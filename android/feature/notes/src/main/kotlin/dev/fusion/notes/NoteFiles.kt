package dev.fusion.notes

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest

/** A revision is an optimistic concurrency token, not authentication or encryption. */
data class NoteRevision(val name: String, val sha256: String)
data class NoteSnapshot(val name: String, val text: String, val revision: NoteRevision)
class NoteConflict(message: String) : IllegalStateException(message)
class NotesBusy : IllegalStateException("Another notes operation is active. Retry explicitly.")

/**
 * PR-02 bounded UTF-8 Markdown files in a trusted app-private directory.
 * All cooperating writers take the same OS file lock; stale edits are refused.
 * File force -> atomic rename (no non-atomic fallback) -> exact readback -> directory force.
 * Not a backup, encryption, arbitrary-writer CAS, or a physical-power-loss proof.
 * // [U] verify on API 34/36/37: Android filesystem atomic move, locks and directory force.
 */
class NoteFiles(directory: Path) {
    companion object {
        const val MAX_BYTES = 256 * 1024
        const val MAX_NOTES = 256
        fun validateName(name: String) {
            require(name.length in 1..64 && name.toByteArray(Charsets.UTF_8).size <= 128 &&
                name.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                "Use 1–64 letters/digits, - or _ (at most 128 UTF-8 bytes). No paths."
            }
        }
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        private fun encode(text: String): ByteArray {
            require(text.length <= MAX_BYTES) { "Note exceeds 256 KiB" }
            val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text))
            require(encoded.remaining() <= MAX_BYTES) { "Note exceeds 256 KiB UTF-8" }
            return ByteArray(encoded.remaining()).also(encoded::get)
        }
        private fun decode(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }
    private val root: Path
    init {
        Files.createDirectories(directory)
        require(Files.isDirectory(directory, NOFOLLOW_LINKS)) { "Notes directory must not be a symbolic link" }
        root = directory.toRealPath()
    }
    private fun path(name: String): Path { validateName(name); return root.resolve("$name.md") }
    private fun <T> locked(operation: () -> T): T {
        FileChannel.open(root.resolve(".notes.lock"), CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
            val lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { throw NotesBusy() }
            if (lock == null) throw NotesBusy()
            lock.use { return operation() }
        }
    }
    private fun namesUnlocked(): List<String> {
        val names = ArrayList<String>()
        Files.newDirectoryStream(root, "*.md").use { entries ->
            for (entry in entries) {
                require(names.size < MAX_NOTES) { "Notes inventory exceeds 256; nothing was removed" }
                require(Files.isRegularFile(entry, NOFOLLOW_LINKS)) { "Non-regular note refused" }
                val name = entry.fileName.toString().removeSuffix(".md")
                validateName(name)
                names.add(name)
            }
        }
        return names.sorted()
    }
    fun list(): List<String> = locked { namesUnlocked() }
    /** Saved-note graph under the same cooperative writer lock; unsaved drafts are deliberately excluded. */
    fun links(): NoteLinkGraph = locked {
        NoteLinks.build(namesUnlocked().asSequence().map { name -> snapshot(name,bytesUnlocked(path(name))) })
    }
    private fun bytesUnlocked(file: Path): ByteArray {
        require(Files.isRegularFile(file, NOFOLLOW_LINKS)) { "Note is missing or not a regular file" }
        FileChannel.open(file, READ, NOFOLLOW_LINKS).use { channel ->
            require(channel.size() <= MAX_BYTES) { "Note exceeds 256 KiB" }
            val result = ByteArrayOutputStream()
            val buffer = ByteBuffer.allocate(8192)
            while (true) {
                buffer.clear()
                val count = channel.read(buffer)
                if (count == -1) break
                require(result.size() + count <= MAX_BYTES) { "Note grew beyond 256 KiB" }
                result.write(buffer.array(), 0, count)
            }
            return result.toByteArray()
        }
    }
    private fun snapshot(name: String, bytes: ByteArray) = NoteSnapshot(name, decode(bytes), NoteRevision(name, digest(bytes)))
    fun load(name: String): NoteSnapshot { val file = path(name); return locked { snapshot(name, bytesUnlocked(file)) } }
    private fun checkRevision(name: String, file: Path, expected: NoteRevision?) {
        require(expected == null || expected.name == name) { "Revision belongs to another note" }
        if (Files.exists(file, NOFOLLOW_LINKS)) {
            val bytes = bytesUnlocked(file)
            if (expected == null || expected.sha256 != digest(bytes)) throw NoteConflict("Note already exists or changed; reopen before saving/deleting.")
        } else if (expected != null) throw NoteConflict("Note was removed; save under a new name explicitly.")
    }
    fun save(name: String, text: String, expected: NoteRevision?): NoteSnapshot {
        val file = path(name)
        val bytes = encode(text) // Validate everything before creating a temporary file.
        return locked {
            checkRevision(name, file, expected)
            if (expected == null) require(namesUnlocked().size < MAX_NOTES) { "256-note limit reached" }
            // The JDK returns an already-created fresh file. A failed exclusive create
            // must never make our cleanup delete someone else's pre-existing path.
            val temporary = Files.createTempFile(root, ".note-write-", ".tmp")
            var published = false
            try {
                FileChannel.open(temporary, WRITE, NOFOLLOW_LINKS).use { channel ->
                    val buffer = ByteBuffer.wrap(bytes)
                    while (buffer.hasRemaining()) channel.write(buffer)
                    channel.force(true)
                }
                // No fallback to a truncating write if atomic publication is unsupported.
                Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
                published = true
                check(bytesUnlocked(file).contentEquals(bytes)) { "Published note failed exact readback" }
                FileChannel.open(root, READ).use { it.force(true) }
                snapshot(name, bytes)
            } catch (e: Exception) {
                throw java.io.IOException("Save not confirmed; ${if (published) "new bytes may be published" else "inspect the note before retrying"}. Reopen to reconcile.", e)
            } finally {
                // Only our fresh temporary path; never the note or an unrelated document.
                Files.deleteIfExists(temporary)
            }
        }
    }
    /** Explicit irreversible deletion; UI must obtain confirmation. No trash capability claimed. */
    fun delete(name: String, expected: NoteRevision) {
        val file = path(name)
        locked {
            checkRevision(name, file, expected)
            Files.delete(file)
            try { FileChannel.open(root, READ).use { it.force(true) } }
            catch (e: Exception) { throw java.io.IOException("Note removed, but directory persistence is unconfirmed; refresh the inventory.", e) }
        }
    }
}

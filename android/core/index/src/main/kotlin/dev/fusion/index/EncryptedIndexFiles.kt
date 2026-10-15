package dev.fusion.index

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import javax.crypto.SecretKey

/** Optimistic token, not authentication or rollback/generation detection. */
data class IndexRevision(val scope: String, val ciphertextSha256: String)
data class SavedIndex(val documents: List<Doc>, val revision: IndexRevision)
class IndexBusy : IllegalStateException("Another encrypted index operation holds the file lock")
class IndexConflict : IllegalStateException("Encrypted index changed; explicitly reopen before replacing or removing it")

/**
 * One encrypted snapshot in trusted private storage. All writers must cooperate on the OS lock.
 * No cleartext temporary files, keys, query logs or plaintext sidecars are written.
 * No fallback from atomic replacement. Unconfirmed publication requires an explicit reopen.
 * Root/same-UID directory substitution, hardlinks, noncooperating writers, rollback and physical
 * power-loss durability are outside this contract. This is not a SAF/provider implementation.
 * // [U] verify on API 34/36/37: NIO locking/atomic move/directory force and Keystore provider.
 */
class EncryptedIndexFiles(directory: Path, private val scope: String) {
    private val root: Path
    private val file: Path
    init {
        EncryptedIndexCodec.validateScope(scope)
        Files.createDirectories(directory)
        require(Files.isDirectory(directory,NOFOLLOW_LINKS)) { "Index directory cannot be a symbolic link" }
        root = directory.toRealPath()
        file = root.resolve("index.fsi")
    }
    private fun <T> locked(action: () -> T): T {
        FileChannel.open(root.resolve(".index.lock"),CREATE,WRITE,NOFOLLOW_LINKS).use { channel ->
            val lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { throw IndexBusy() }
            if (lock == null) throw IndexBusy()
            lock.use { return action() }
        }
    }
    private fun bytes(): ByteArray {
        require(Files.isRegularFile(file,NOFOLLOW_LINKS)) { "Encrypted index is not a regular file" }
        FileChannel.open(file,READ,NOFOLLOW_LINKS).use { channel ->
            require(channel.size() in 36..EncryptedIndexCodec.MAX_FILE_BYTES.toLong()) { "Encrypted index size refused" }
            val out = ByteArrayOutputStream()
            val buffer = ByteBuffer.allocate(8192)
            while (true) {
                buffer.clear(); val count = channel.read(buffer)
                if (count < 0) break
                require(out.size().toLong()+count <= EncryptedIndexCodec.MAX_FILE_BYTES) { "Encrypted index grew beyond limit" }
                out.write(buffer.array(),0,count)
            }
            return out.toByteArray()
        }
    }
    private fun revision(bytes: ByteArray) = IndexRevision(scope,
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    private fun checkExpected(expected: IndexRevision?) {
        require(expected == null || expected.scope == scope) { "Revision belongs to another storage scope" }
        if (Files.exists(file,NOFOLLOW_LINKS)) {
            if (expected == null || revision(bytes()) != expected) throw IndexConflict()
        } else if (expected != null) throw IndexConflict()
    }
    fun load(key: SecretKey): SavedIndex? = locked {
        if (!Files.exists(file,NOFOLLOW_LINKS)) null
        else {
            val data=bytes()
            SavedIndex(EncryptedIndexCodec.open(data,key,scope),revision(data))
        }
    }
    fun save(documents: List<Doc>, key: SecretKey, expected: IndexRevision?): SavedIndex {
        val owned=EncryptedIndexCodec.admitted(documents)
        val data=EncryptedIndexCodec.seal(owned,key,scope)
        // Refuse an incompatible provider/codec before touching an existing ciphertext.
        check(EncryptedIndexCodec.open(data,key,scope)==owned) { "Candidate index failed prepublication validation" }
        return locked {
            checkExpected(expected)
            val temporary=Files.createTempFile(root,".index-write-",".tmp")
            var published=false
            try {
                FileChannel.open(temporary,WRITE,NOFOLLOW_LINKS).use { channel ->
                    val buffer=ByteBuffer.wrap(data)
                    while (buffer.hasRemaining()) channel.write(buffer)
                    channel.force(true)
                }
                Files.move(temporary,file,ATOMIC_MOVE,REPLACE_EXISTING)
                published=true
                val observed=bytes()
                check(observed.contentEquals(data)) { "Encrypted readback differs" }
                check(EncryptedIndexCodec.open(observed,key,scope)==owned) { "Decrypted readback differs" }
                FileChannel.open(root,READ).use { it.force(true) }
                SavedIndex(owned,revision(observed))
            } catch (error: Exception) {
                throw IOException("Index save unconfirmed; ${if(published) "new ciphertext may be published" else "previous or new state must be inspected"}. Reopen before retry.",error)
            } finally { Files.deleteIfExists(temporary) }
        }
    }
    /** Remove this index only after explicit owner confirmation. Not secure erasure of storage media. */
    fun remove(expected: IndexRevision) = locked {
        checkExpected(expected)
        Files.delete(file)
        try { FileChannel.open(root,READ).use { it.force(true) } }
        catch(error: Exception) { throw IOException("Index removed, but directory persistence is unconfirmed",error) }
    }
}

/**
 * Search only after authenticated load. Plaintext exists in process memory while unlocked.
 * lock() drops this session's references; it cannot revoke returned hits or prove JVM zeroization.
 * No key is retained by the session and no query is persisted. Source discovery/consent/secure-
 * content exclusions are a separate required adapter boundary, not inferred from Doc.source.
 */
class EncryptedSearchSession {
    private var index: LocalIndex? = null
    private var revision: IndexRevision? = null
    @Synchronized fun lock() { index=null; revision=null }
    @Synchronized fun isUnlocked() = index != null
    @Synchronized fun currentRevision() = revision
    @Synchronized fun unlock(store: EncryptedIndexFiles, key: SecretKey): Int {
        lock() // Wrong key, corruption or IO failure must not leave old plaintext searchable.
        val snapshot=store.load(key) ?: throw IllegalStateException("No saved encrypted index; build explicitly")
        val candidate=LocalIndex()
        for(doc in snapshot.documents) candidate.upsert(doc)
        index=candidate; revision=snapshot.revision
        return candidate.size()
    }
    @Synchronized fun search(query: String, limit: Int = 20): List<Hit> =
        checkNotNull(index) { "Encrypted search session is locked" }.search(query,limit)
}

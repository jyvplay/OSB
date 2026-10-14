package dev.fusion.index

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** Real JCE, file I/O and competing OS locks. No Android or fake provider substituted. */
object EncryptedIndexChecks {
    private var checks=0
    private fun expect(ok: Boolean) { checks++; check(ok) { "Encrypted index check $checks failed" } }
    private inline fun refused(action: () -> Unit) { checks++; try { action() } catch(_: Exception) { return }; error("Expected refusal at $checks") }
    private fun key()=KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun malformed(plain: ByteArray, key: SecretKey): ByteArray {
        val header=ByteBuffer.allocate(8).putInt(0x46534958).putInt(1).array()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key)
        cipher.updateAAD(header); cipher.updateAAD("notes".toByteArray())
        return header+cipher.iv+cipher.doFinal(plain)
    }
    @JvmStatic fun main(args: Array<String>) {
        val key=key(); val other=key()
        val docs=listOf(Doc("1","Needle café","private-body-α\r\n😀\u0000","owned-notes"),Doc("2","Another","needle","owned-notes"))
        val sealed=EncryptedIndexCodec.seal(docs,key,"notes")
        expect(EncryptedIndexCodec.open(sealed,key,"notes")==docs)
        expect(!sealed.contentEquals(EncryptedIndexCodec.seal(docs,key,"notes")))
        expect(!String(sealed,Charsets.ISO_8859_1).contains("private-body")) // Sample only, not a confidentiality proof.
        refused { EncryptedIndexCodec.open(sealed,other,"notes") }
        refused { EncryptedIndexCodec.open(sealed,key,"other-scope") }
        refused { EncryptedIndexCodec.seal(docs,key,"../path") }
        refused { EncryptedIndexCodec.seal(docs,SecretKeySpec(ByteArray(16),"AES"),"notes") }
        refused { EncryptedIndexCodec.open(sealed+byteArrayOf(0),key,"notes") }
        refused { EncryptedIndexCodec.open(ByteArray(EncryptedIndexCodec.MAX_FILE_BYTES+1),key,"notes") }
        // Every byte position, including header, nonce, ciphertext and tag.
        for(i in sealed.indices) {
            val changed=sealed.copyOf(); changed[i]=(changed[i].toInt() xor 1).toByte()
            refused { EncryptedIndexCodec.open(changed,key,"notes") }
            refused { EncryptedIndexCodec.open(sealed.copyOf(i),key,"notes") }
        }
        refused { EncryptedIndexCodec.seal(docs+docs.first(),key,"notes") }
        refused { EncryptedIndexCodec.seal(listOf(docs.first().copy(body="\uD800")),key,"notes") }
        refused { EncryptedIndexCodec.seal(List(513) { docs.first().copy(id="$it") },key,"notes") }
        refused { EncryptedIndexCodec.seal(List(17) { docs.first().copy(id="$it",body="a".repeat(65536)) },key,"notes") }
        val capacity=List(512) { docs.first().copy(id="$it",body="body") }
        expect(EncryptedIndexCodec.open(EncryptedIndexCodec.seal(capacity,key,"notes"),key,"notes")==capacity)
        expect(EncryptedIndexCodec.open(EncryptedIndexCodec.seal(emptyList(),key,"notes"),key,"notes").isEmpty())
        val caller=docs.toMutableList()
        val admitted=EncryptedIndexCodec.admitted(caller); caller.clear()
        expect(admitted==docs)
        refused { (admitted as MutableList<Doc>).clear() }
        refused { (EncryptedIndexCodec.open(sealed,key,"notes") as MutableList<Doc>).clear() }
        // A valid authentication tag is necessary but not a substitute for bounded schema parsing.
        for(plain in listOf(byteArrayOf(),ByteBuffer.allocate(8).putInt(2).putInt(0).array(),
            ByteBuffer.allocate(8).putInt(1).putInt(Int.MAX_VALUE).array(),
            ByteBuffer.allocate(12).putInt(1).putInt(1).putInt(Int.MAX_VALUE).array(),
            ByteBuffer.allocate(9).putInt(1).putInt(0).put(0).array())) {
            refused { EncryptedIndexCodec.open(malformed(plain,key),key,"notes") }
        }
        val invalidUtf8=ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeInt(1);out.writeInt(1);out.writeInt(2);out.write(byteArrayOf(0xc0.toByte(),0x80.toByte()))
        } }.toByteArray()
        refused { EncryptedIndexCodec.open(malformed(invalidUtf8,key),key,"notes") }
        // Patch attack: a SecretKey implementation may lend, not transfer, its encoding array.
        val original=ByteArray(32) { (it+1).toByte() };val backing=original.copyOf()
        val lent=object: SecretKey {
            override fun getAlgorithm()="AES"
            override fun getFormat()="RAW"
            override fun getEncoded()=backing
        }
        val protected=EncryptedIndexCodec.seal(docs,lent,"notes")
        expect(backing.contentEquals(original))
        expect(EncryptedIndexCodec.open(protected,SecretKeySpec(original,"AES"),"notes")==docs)
        val directory=Files.createTempDirectory("fusion-encrypted-index-")
        try {
            val root=directory.resolve("primary")
            val store=EncryptedIndexFiles(root,"notes")
            val peer=EncryptedIndexFiles(root,"notes")
            val session=EncryptedSearchSession()
            expect(store.load(key)==null)
            refused { session.search("needle") }
            refused { session.unlock(store,key) }
            expect(!session.isUnlocked())
            val retained=root.resolve(".index-write-retained.tmp");Files.writeString(retained,"retained unknown attempt")
            val first=store.save(docs,key,null)
            expect(store.load(key)==first)
            expect(Files.readString(retained)=="retained unknown attempt")
            refused { store.save(docs,key,null) }
            refused { store.save(docs,key,first.revision.copy(scope="other")) }
            expect(session.unlock(store,key)==2)
            expect(session.search("needle").map { it.doc.id }==listOf("1","2"))
            expect(session.search("café").single().doc.id=="1")
            refused { session.search("x".repeat(LocalIndex.MAX_QUERY+1)) }
            refused { session.search("x",101) }
            val returned=session.search("needle")
            session.lock();expect(!session.isUnlocked());expect(session.currentRevision()==null)
            refused { session.search("needle") }
            expect(returned.isNotEmpty()) // Explicit boundary: locking cannot revoke plaintext already returned.
            session.unlock(store,key)
            refused { session.unlock(store,other) }
            expect(!session.isUnlocked())
            refused { session.search("needle") }
            // Two real writers, one original revision: at most one can confirm replacement.
            val start=CountDownLatch(1); val pool=Executors.newFixedThreadPool(2)
            try {
                val futures=listOf(store,peer).mapIndexed { n,s -> pool.submit<Boolean> {
                    check(start.await(5,TimeUnit.SECONDS))
                    try { s.save(listOf(docs.first().copy(title="writer$n")),key,first.revision);true }
                    catch(_:IndexBusy) { false }
                    catch(_:IndexConflict) { false }
                } }
                start.countDown();expect(futures.count { it.get(10,TimeUnit.SECONDS) }==1)
            } finally { pool.shutdownNow() }
            refused { store.remove(first.revision) }
            val current=checkNotNull(store.load(key));expect(current.documents.single().title in setOf("writer0","writer1"))
            val cipherPath=root.resolve("index.fsi");val bytes=Files.readAllBytes(cipherPath)
            val changed=bytes.copyOf();changed[changed.lastIndex]=(changed.last().toInt() xor 1).toByte()
            Files.write(cipherPath,changed)
            refused { store.load(key) }
            refused { store.save(docs,key,current.revision) }
            expect(Files.readAllBytes(cipherPath).contentEquals(changed))
            Files.write(cipherPath,bytes)
            // Authentic old encrypted snapshots remain authentic: no anti-rollback claim.
            val old=bytes.copyOf()
            val newer=store.save(docs,key,current.revision)
            Files.write(cipherPath,old);expect(store.load(key)==current)
            refused { store.remove(newer.revision) }
            store.remove(current.revision);expect(store.load(key)==null)
            val victim=directory.resolve("victim.bin");Files.write(victim,sealed)
            Files.createSymbolicLink(cipherPath,victim)
            refused { store.load(key) };refused { store.save(docs,key,null) }
            expect(Files.readAllBytes(victim).contentEquals(sealed))
            Files.delete(cipherPath)
            val symbolic=directory.resolve("link");Files.createSymbolicLink(symbolic,root)
            refused { EncryptedIndexFiles(symbolic,"notes") }
            Files.list(root).use { paths -> expect(paths.map { it.fileName.toString() }.sorted().toList()==listOf(".index-write-retained.tmp",".index.lock")) }
        } finally { directory.toFile().deleteRecursively() }
        println("PASS: $checks encrypted-index assertions; real JCE authenticated schema, corruption/wrong-scope refusal, actual encrypted files/OS locks, revision conflicts and locked-session literal search. Not Android Keystore, FTS, anti-rollback, secure erase, or S4 acceptance.")
    }
}

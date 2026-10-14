package dev.fusion.shell

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fusion.index.Doc
import dev.fusion.index.EncryptedIndexFiles
import dev.fusion.index.EncryptedSearchSession
import dev.fusion.security.IndexKeys
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Actual Keystore/filesystem obligations; AUTHORED ONLY until run on Android. No S4 promotion. */
@RunWith(AndroidJUnit4::class)
class EncryptedIndexDeviceTest {
    @Test fun encryptedIndexUsesExistingKeyAndFailsClosedAfterKeyLoss() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val token=UUID.randomUUID().toString()
        val directory=File(context.noBackupFilesDir,"index-test-$token")
        val alias="fusion.index.test.$token"
        val keyStore=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val keys=IndexKeys(alias)
            try { keys.existing(); fail("Reading cannot create a missing key") } catch(_:IllegalStateException) { }
            assertFalse(keyStore.containsAlias(alias))
            val key=keys.createForNewIndex()
            val store=EncryptedIndexFiles(directory.toPath(),"owned-notes")
            val documents=listOf(Doc("test-note","Test title","Keystore body α","owned-notes"))
            val saved=store.save(documents,key,null)
            val freshKey=IndexKeys(alias).existing()
            assertEquals(saved,store.load(freshKey))
            val session=EncryptedSearchSession()
            assertEquals(1,session.unlock(store,freshKey))
            assertEquals("test-note",session.search("keystore").single().doc.id)
            session.lock()
            try { session.search("keystore");fail("Locked session must refuse") } catch(_:IllegalStateException) { }
            val retained=File(directory,"index.fsi").readBytes()
            keyStore.deleteEntry(alias)
            try { keys.existing();fail("Lost keys must not be replaced on read") } catch(_:IllegalStateException) { }
            assertFalse(keyStore.containsAlias(alias))
            assertArrayEquals(retained,File(directory,"index.fsi").readBytes())
        } finally {
            keyStore.deleteEntry(alias)
            directory.deleteRecursively() // Only this test's fresh random path.
        }
    }
}

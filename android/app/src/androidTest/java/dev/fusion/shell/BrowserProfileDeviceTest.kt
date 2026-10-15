package dev.fusion.shell

import android.content.*
import android.os.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fusion.shell.probes.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Real installed WebView provider, two actual processes, same origin. No fake renderer or network site. */
@RunWith(AndroidJUnit4::class)
class BrowserProfileDeviceTest {
    private class Probe(context:Context,type:Class<out BrowserProfileProbe>):java.io.Closeable {
        private val context=context.applicationContext
        private val thread=HandlerThread("browser-profile-result").apply { start() }
        private val connectionReady=CompletableFuture<Messenger>()
        private var pending:Pair<String,CompletableFuture<Bundle>>?=null
        private val replies=Messenger(object:Handler(thread.looper) {
            override fun handleMessage(message:Message) {
                val data=message.data
                synchronized(this@Probe) {
                    pending?.let { (id,future) -> if(data.getString("id")==id) {
                        if(message.sendingUid!=Process.myUid()) future.completeExceptionally(IllegalStateException("Wrong UID"))
                        else future.complete(data)
                    } }
                }
            }
        })
        private val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder) { connectionReady.complete(Messenger(binder)) }
            override fun onServiceDisconnected(name:ComponentName) {
                synchronized(this@Probe) { pending?.second?.completeExceptionally(IllegalStateException("Probe died")) }
            }
        }
        private var bound=false
        init {
            try {
                bound=context.bindService(Intent(context,type),connection,Context.BIND_AUTO_CREATE)
                check(bound);connectionReady.get(10,TimeUnit.SECONDS)
            } catch(error:Exception) { close();throw error }
        }
        fun request(operation:Int,value:String=""):Bundle {
            val id=UUID.randomUUID().toString();val future=CompletableFuture<Bundle>()
            synchronized(this) { check(pending==null);pending=id to future }
            try {
                connectionReady.get().send(Message.obtain(null,operation).apply {
                    data=Bundle().apply { putString("id",id);putString("value",value) };replyTo=replies
                })
                val result=future.get(10,TimeUnit.SECONDS)
                check(result.getString("error")==null) { "Real WebView probe failed: ${result.getString("error")}" }
                assertFalse(result.getString("provider").isNullOrBlank())
                assertEquals(Process.myUid(),result.getInt("uid")) // Explicitly NOT a different-UID security claim.
                return result
            } finally { synchronized(this) { pending=null } }
        }
        override fun close() {
            if(bound) { context.unbindService(connection);bound=false }
            thread.quitSafely()
        }
    }
    @Test fun sameOriginCookiesAndDomStorageRemainInTheirOwnProcessProfiles() {
        org.junit.Assume.assumeTrue("Installed WebView provider required; absent is UNVERIFIED, not a pass", android.webkit.WebView.getCurrentWebViewPackage()!=null)
        val context=ApplicationProvider.getApplicationContext<Context>()
        Probe(context,BrowserProfileAProbe::class.java).use { a ->
            Probe(context,BrowserProfileBProbe::class.java).use { b ->
                val first=a.request(BrowserProfileProbe.WRITE,"Alpha")
                assertEquals("token=Alpha",first.getString("cookie"))
                assertEquals("token=Alpha|Alpha",first.getString("page"))
                val blank=b.request(BrowserProfileProbe.CLEAR)
                assertEquals("",blank.getString("cookie"));assertEquals("|",blank.getString("page"))
                assertNotEquals(first.getInt("pid"),blank.getInt("pid"))
                assertNotEquals(first.getString("suffix"),blank.getString("suffix"))
                assertEquals(first.getString("provider"),blank.getString("provider"))
                val second=b.request(BrowserProfileProbe.WRITE,"Bravo")
                assertEquals("token=Bravo|Bravo",second.getString("page"))
                assertEquals("token=Alpha|Alpha",a.request(BrowserProfileProbe.READ).getString("page"))
                assertEquals("|",a.request(BrowserProfileProbe.CLEAR).getString("page"))
                assertEquals("token=Bravo|Bravo",b.request(BrowserProfileProbe.READ).getString("page"))
                b.request(BrowserProfileProbe.CLEAR)
            }
        }
    }
}

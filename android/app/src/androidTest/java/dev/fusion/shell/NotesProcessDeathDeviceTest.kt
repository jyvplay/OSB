package dev.fusion.shell

import android.content.*
import android.os.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fusion.shell.probes.NotesRecoveryProcessProbe
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Real dedicated writer-process death and a fresh PID; not main-Activity force-stop or power loss. */
@RunWith(AndroidJUnit4::class)
class NotesProcessDeathDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    private class Probe(private val context:Context):java.io.Closeable {
        private val connected=CompletableFuture<IBinder>()
        private val dead=CompletableFuture<Unit>()
        private var bound=false
        private val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder) {
                binder.linkToDeath({ dead.complete(Unit) },0);connected.complete(binder)
            }
            override fun onServiceDisconnected(name:ComponentName) { dead.complete(Unit) }
        }
        init {
            try { bound=context.bindService(Intent(context,NotesRecoveryProcessProbe::class.java),connection,Context.BIND_AUTO_CREATE);check(bound);connected.get(10,TimeUnit.SECONDS) }
            catch(e:Exception) { close();throw e }
        }
        fun request(code:Int,text:String=""):Pair<Int,String> {
            val data=Parcel.obtain();val reply=Parcel.obtain()
            try {
                data.writeInterfaceToken(NotesRecoveryProcessProbe.TOKEN)
                if(code==NotesRecoveryProcessProbe.WRITE) data.writeString(text)
                check(connected.get().transact(code,data,reply,0));reply.readException()
                return reply.readInt() to requireNotNull(reply.readString())
            } finally { data.recycle();reply.recycle() }
        }
        fun awaitDeath() { dead.get(10,TimeUnit.SECONDS) }
        override fun close() { if(bound) { context.unbindService(connection);bound=false } }
    }
    @Test fun acknowledgedDraftCheckpointSurvivesActualWriterProcessDeath() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val text="checkpoint α🙂 "+UUID.randomUUID();var priorPid=-1
        Probe(context).use { writer ->
            val saved=writer.request(NotesRecoveryProcessProbe.WRITE,text)
            priorPid=saved.first;assertNotEquals(Process.myPid(),priorPid);assertEquals(text,saved.second)
            assertEquals(saved,writer.request(NotesRecoveryProcessProbe.STOP));writer.awaitDeath()
        }
        Probe(context).use { reader ->
            val restored=reader.request(NotesRecoveryProcessProbe.READ)
            assertNotEquals(priorPid,restored.first);assertNotEquals(Process.myPid(),restored.first)
            assertEquals(text,restored.second)
            println("R12 actual Notes writer death: old=$priorPid new=${restored.first}; exact checkpoint survived")
        }
    }
}

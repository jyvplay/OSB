package dev.fusion.shell

import android.content.*
import android.graphics.Color
import android.os.*
import android.view.*
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.fusion.shell.probes.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BrowserSurfaceDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    private class Remote(private val context:Context,type:Class<out BrowserSurfaceProbe>):java.io.Closeable {
        private val thread=HandlerThread("surface-results").apply { start() }
        private val ready=CompletableFuture<Messenger>()
        private var pending:Pair<String,CompletableFuture<Bundle>>?=null
        private var bound=false
        private val responses=Messenger(object:Handler(thread.looper) {
            override fun handleMessage(message:Message) {
                val data=message.data
                synchronized(this@Remote) { pending?.let { (id,future) ->
                    if(data.getString("id")==id) {
                        if(message.sendingUid==Process.myUid()) future.complete(data)
                        else future.completeExceptionally(IllegalStateException("Wrong sender UID"))
                    }
                } }
            }
        })
        private val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder) { ready.complete(Messenger(binder)) }
            override fun onServiceDisconnected(name:ComponentName) {
                synchronized(this@Remote) { pending?.second?.completeExceptionally(IllegalStateException("Remote died")) }
            }
        }
        init {
            try { bound=context.bindService(Intent(context,type),connection,Context.BIND_AUTO_CREATE);check(bound);ready.get(10,TimeUnit.SECONDS) }
            catch(e:Exception) { close();throw e }
        }
        fun request(op:Int,args:Bundle=Bundle(),expectError:Boolean=false):Bundle {
            val id=UUID.randomUUID().toString();val future=CompletableFuture<Bundle>()
            synchronized(this) { check(pending==null);pending=id to future }
            try {
                ready.get().send(Message.obtain(null,op).apply { data=Bundle(args).apply { putString("id",id) };replyTo=responses })
                val result=future.get(10,TimeUnit.SECONDS)
                if(expectError) check(result.getString("error")!=null)
                else check(result.getString("error")==null) { "Surface probe: ${result.getString("error")}" }
                return result
            } finally { synchronized(this) { pending=null } }
        }
        override fun close() { if(bound) { context.unbindService(connection);bound=false };thread.quitSafely() }
    }
    private fun attach(remote:Remote,surface:SurfaceView):Bundle {
        val future=CompletableFuture<Bundle>()
        main {
            fun capture() {
                if(surface.width>0 && surface.height>0 && surface.hostToken!=null) future.complete(Bundle().apply {
                    putBinder("token",surface.hostToken);putInt("width",surface.width);putInt("height",surface.height)
                    putInt("display",surface.display.displayId)
                })
            }
            surface.holder.addCallback(object:SurfaceHolder.Callback {
                override fun surfaceCreated(holder:SurfaceHolder) { capture() }
                override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int) { capture() }
                override fun surfaceDestroyed(holder:SurfaceHolder) {}
            });capture()
        }
        val result=remote.request(BrowserSurfaceProbe.ATTACH,future.get(10,TimeUnit.SECONDS))
        val pkg=requireNotNull(result.getParcelable("surface",SurfaceControlViewHost.SurfacePackage::class.java))
        main { surface.setChildSurfacePackage(pkg) } // SurfaceView owns package lifetime.
        return result
    }
    private fun clicks(remote:Remote)=JSONObject(remote.request(BrowserSurfaceProbe.READ).getString("dom")!!).getInt("clicks")
    private fun tap(surface:SurfaceView) {
        val pos=IntArray(2);var x=0f;var y=0f
        main { surface.getLocationOnScreen(pos);x=pos[0]+surface.width*.25f;y=pos[1]+surface.height*.25f }
        val now=SystemClock.uptimeMillis()
        for((action,time) in listOf(MotionEvent.ACTION_DOWN to now,MotionEvent.ACTION_UP to now+50)) {
            val event=MotionEvent.obtain(now,time,action,x,y,0).apply { source=InputDevice.SOURCE_TOUCHSCREEN }
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true)) } finally { event.recycle() }
        }
        instrumentation.waitForIdleSync()
    }
    private fun awaitClicks(remote:Remote,count:Int) {
        val end=SystemClock.uptimeMillis()+5_000
        do { if(clicks(remote)==count) return;SystemClock.sleep(50) } while(SystemClock.uptimeMillis()<end)
        assertEquals(count,clicks(remote))
    }
    @Test fun realPixelsAndInputRemainIndependentAcrossTwoRemoteWebViewsAndResize() {
        org.junit.Assume.assumeTrue("No provider is unverified, not a pass",android.webkit.WebView.getCurrentWebViewPackage()!=null)
        val context=ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch(BrowserSurfaceActivity::class.java).use { scenario ->
            lateinit var left:SurfaceView;lateinit var right:SurfaceView
            scenario.onActivity { left=it.left;right=it.right;assertNotSame(left,right);assertSame(left.parent,right.parent);assertEquals(2,(left.parent as ViewGroup).childCount) }
            Remote(context,BrowserSurfaceAProbe::class.java).use { a -> Remote(context,BrowserSurfaceBProbe::class.java).use { b ->
                val aa=attach(a,left);val bb=attach(b,right)
                assertNotEquals(aa.getInt("pid"),bb.getInt("pid"));assertNotEquals(Process.myPid(),aa.getInt("pid"))
                assertEquals(Process.myUid(),aa.getInt("uid"));assertEquals(Process.myUid(),bb.getInt("uid"))
                assertFalse(aa.getString("provider").isNullOrBlank());assertEquals(aa.getString("provider"),bb.getString("provider"))
                assertEquals(0,clicks(a));assertEquals(0,clicks(b))
                tap(left);awaitClicks(a,1);assertEquals(0,clicks(b))
                tap(right);awaitClicks(b,1);assertEquals(1,clicks(a))
                // Actual composed pixels, not a generated screenshot or merely a DOM reply.
                val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                try {
                    for(surface in listOf(left,right)) {
                        val p=IntArray(2);var x=0;var y=0
                        main { surface.getLocationOnScreen(p);x=p[0]+surface.width/4;y=p[1]+surface.height/4 }
                        val pixel=bitmap.getPixel(x,y)
                        assertTrue("Remote pixel was ${Integer.toHexString(pixel)}",kotlin.math.abs(Color.red(pixel)-18)<=8 && kotlin.math.abs(Color.green(pixel)-93)<=8 && kotlin.math.abs(Color.blue(pixel)-133)<=8)
                    }
                } finally { bitmap.recycle() }
                val size=Bundle()
                main { size.putInt("width",left.width);size.putInt("height",left.height/2) }
                a.request(BrowserSurfaceProbe.RESIZE,size)
                // Attack the patch: rejected over-budget relayout must leave the live host usable.
                a.request(BrowserSurfaceProbe.RESIZE,Bundle().apply { putInt("width",Int.MAX_VALUE);putInt("height",1) },expectError=true)
                tap(left);awaitClicks(a,2);assertEquals(1,clicks(b))
                println("R12 remote-surface provider=${aa.getString("provider")}; pids=${aa.getInt("pid")},${bb.getInt("pid")}; same UID=${aa.getInt("uid")}")
            } }
        }
    }
}

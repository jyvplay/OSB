package dev.fusion.shell.probes

import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.*
import android.view.SurfaceControlViewHost
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONTokener
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Actual remote rendering/input spike. Same UID, nonexported, debug only. NOT a product browser. */
abstract class BrowserSurfaceProbe : Service() {
    protected abstract val profile: String
    private var host:SurfaceControlViewHost?=null
    private var web:WebView?=null
    private var error:String?=null
    private val watchdog=Executors.newSingleThreadScheduledExecutor()
    companion object { const val ATTACH=1;const val READ=2;const val RESIZE=3 }
    override fun onCreate() {
        super.onCreate()
        try { WebView.setDataDirectorySuffix(profile) } catch(e:Exception) { error=e.toString().take(200) }
        watchdog.schedule({ Process.killProcess(Process.myPid()) },60,TimeUnit.SECONDS)
    }
    private val endpoint by lazy { Messenger(object:Handler(Looper.getMainLooper()) {
        override fun handleMessage(message:Message) {
            if(message.sendingUid!=Process.myUid()) return
            val op=message.what;val data=Bundle(message.data);val reply=message.replyTo ?: return
            val id=data.getString("id") ?: return
            if(id.length!=36) return
            var sent=false
            fun respond(problem:String?=null,dom:String?=null,surface:SurfaceControlViewHost.SurfacePackage?=null) {
                if(sent) return;sent=true
                val result=Bundle().apply {
                    putString("id",id);putString("error",problem?.take(240));putString("dom",dom)
                    putInt("pid",Process.myPid());putInt("uid",Process.myUid())
                    putString("provider",WebView.getCurrentWebViewPackage()?.let { it.packageName+":"+it.versionName })
                    if(surface!=null) putParcelable("surface",surface)
                }
                try { reply.send(Message.obtain(null,op).apply { this.data=result }) }
                finally { surface?.release() } // Recipient owns its parcelled reference, host owns root.
            }
            try {
                error?.let { respond(it);return }
                when(op) {
                    ATTACH -> {
                        check(host==null) { "Already attached" }
                        val width=data.getInt("width");val height=data.getInt("height")
                        require(width in 64..4096 && height in 64..4096)
                        val token=requireNotNull(data.getBinder("token"))
                        val display=requireNotNull(getSystemService(DisplayManager::class.java).getDisplay(data.getInt("display")))
                        // [U] verify API34/36/37, provider upgrades, IME/AX and independent-UID input ownership.
                        val context=createDisplayContext(display)
                        val view=WebView(context);web=view
                        view.settings.apply { javaScriptEnabled=true;blockNetworkLoads=true;allowFileAccess=false;allowContentAccess=false }
                        val remote=SurfaceControlViewHost(context,display,token);host=remote
                        remote.setView(view,width,height)
                        view.webViewClient=object:WebViewClient() {
                            override fun onPageFinished(v:WebView,url:String) {
                                if(sent) return
                                v.postVisualStateCallback(1,object:WebView.VisualStateCallback() {
                                    override fun onComplete(requestId:Long) { respond(surface=remote.surfacePackage) }
                                })
                            }
                        }
                        // One fixed test-owned document; READ cannot increment the counter.
                        view.loadDataWithBaseURL("https://r12.example.invalid/", """
                            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
                            <style>html,body{margin:0;width:100%;height:100%}button{position:fixed;inset:0;width:100%;height:100%;border:0;background:#125d85;color:white;font-size:24px}</style>
                            <button id="hit" onclick="window.clicks++;this.textContent='Actual clicks: '+window.clicks">Remote input target</button>
                            <script>window.clicks=0;</script>
                        """.trimIndent(),"text/html","utf-8",null)
                    }
                    READ -> {
                        val view=requireNotNull(web)
                        view.evaluateJavascript("JSON.stringify({clicks:window.clicks,width:innerWidth,height:innerHeight})") { encoded ->
                            try {
                                val text=JSONTokener(encoded).nextValue() as? String
                                require(text!=null && text.length<=256);respond(dom=text)
                            } catch(e:Exception) { respond(e.toString()) }
                        }
                    }
                    RESIZE -> {
                        val width=data.getInt("width");val height=data.getInt("height")
                        require(width in 64..4096 && height in 64..4096)
                        requireNotNull(host).relayout(width,height)
                        requireNotNull(web).postVisualStateCallback(2,object:WebView.VisualStateCallback() {
                            override fun onComplete(requestId:Long) { respond() }
                        })
                    }
                    else -> respond("Unknown operation")
                }
            } catch(e:Exception) { respond(e.toString()) }
        }
    }) }
    override fun onBind(intent:Intent)=endpoint.binder
    override fun onDestroy() {
        host?.release();web?.destroy();watchdog.shutdownNow();super.onDestroy();Process.killProcess(Process.myPid())
    }
}
class BrowserSurfaceAProbe:BrowserSurfaceProbe() { override val profile="r12_surface_a" }
class BrowserSurfaceBProbe:BrowserSurfaceProbe() { override val profile="r12_surface_b" }

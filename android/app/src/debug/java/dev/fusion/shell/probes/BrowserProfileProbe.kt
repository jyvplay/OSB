package dev.fusion.shell.probes

import android.app.Service
import android.content.Intent
import android.os.*
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Debug-only real platform experiment, never a shipping browser or an isolated-UID boundary. */
abstract class BrowserProfileProbe : Service() {
    protected abstract val suffix: String
    private var initializationError: String?=null
    private var view: WebView?=null
    private var busy=false
    private val watchdog=Executors.newSingleThreadScheduledExecutor()
    companion object {
        const val READ=1;const val WRITE=2;const val CLEAR=3
        private const val ORIGIN="https://r11b.example.invalid/"
    }
    override fun onCreate() {
        super.onCreate()
        // [U] verify on API 34/36/37 and each installed provider before choosing this route.
        try { WebView.setDataDirectorySuffix(suffix) }
        catch(error: Exception) { initializationError=error.toString().take(200) }
        watchdog.schedule({ Process.killProcess(Process.myPid()) },30,TimeUnit.SECONDS)
    }
    private val endpoint by lazy { Messenger(object: Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if(message.sendingUid!=Process.myUid()) return
            // Copy fields: Handler recycles Message before asynchronous callbacks complete.
            val operation=message.what;val id=message.data.getString("id") ?: return
            val value=message.data.getString("value") ?: ""
            val reply=message.replyTo ?: return
            var sent=false
            var ownsRequest=false
            fun respond(error:String?=null,page:String?=null) {
                if(sent) return
                sent=true;if(ownsRequest) busy=false
                val data=Bundle().apply {
                    putString("id",id);putInt("uid",Process.myUid());putInt("pid",Process.myPid())
                    putString("suffix",suffix);putString("error",error?.take(240));putString("page",page)
                    if(error==null) {
                        putString("cookie",CookieManager.getInstance().getCookie(ORIGIN) ?: "")
                        putString("provider",WebView.getCurrentWebViewPackage()?.let { it.packageName+":"+it.versionName })
                    }
                }
                runCatching { reply.send(Message.obtain(null,operation).apply { this.data=data }) }
            }
            if(busy || id.length!=36 || operation !in READ..CLEAR || !value.matches(Regex("[a-zA-Z0-9_-]{0,24}"))) {
                respond("Busy or invalid request");return
            }
            busy=true;ownsRequest=true
            initializationError?.let { respond(it);return }
            try {
                val cookies=CookieManager.getInstance()
                fun loadPage() {
                    try {
                        view?.destroy()
                        val web=WebView(this@BrowserProfileProbe);view=web
                        web.settings.apply {
                            javaScriptEnabled=true;domStorageEnabled=true
                            blockNetworkLoads=true;allowFileAccess=false;allowContentAccess=false
                        }
                        web.webViewClient=object: WebViewClient() {
                            override fun onPageFinished(v:WebView,url:String) {
                                if(sent || v!==view) return
                                val mutation=when(operation) {
                                    WRITE -> "localStorage.setItem('probe',${JSONObject.quote(value)});"
                                    CLEAR -> "localStorage.removeItem('probe');"
                                    else -> ""
                                }
                                v.evaluateJavascript("(function(){${mutation}return document.cookie+'|'+(localStorage.getItem('probe')||'');})()") { encoded ->
                                    try {
                                        val actual=JSONTokener(encoded).nextValue() as? String
                                        if(actual==null || actual.length>256) respond("Invalid DOM result") else respond(page=actual)
                                    } catch(error: Exception) { respond(error.toString()) }
                                }
                            }
                        }
                        // Actual renderer/DOM, locally supplied fixed HTML, same origin in BOTH profiles.
                        web.loadDataWithBaseURL(ORIGIN,"<!doctype html><meta charset=utf-8><title>Offline profile experiment</title><p>Test-owned local document</p>","text/html","utf-8",null)
                    } catch(error: Exception) { respond(error.toString()) }
                }
                when(operation) {
                    CLEAR -> cookies.removeAllCookies { cookies.flush();loadPage() }
                    WRITE -> cookies.removeAllCookies {
                        cookies.setCookie(ORIGIN,"token=$value; Path=/; Secure; SameSite=Strict; Max-Age=3600") { accepted ->
                            if(!accepted) respond("Cookie rejected") else { cookies.flush();loadPage() }
                        }
                    }
                    else -> loadPage()
                }
            } catch(error: Exception) { respond(error.toString()) }
        }
    }) }
    override fun onBind(intent: Intent)=endpoint.binder
    override fun onDestroy() {
        view?.destroy();watchdog.shutdownNow();super.onDestroy()
        Process.killProcess(Process.myPid()) // Only this dedicated debug probe process.
    }
}
class BrowserProfileAProbe : BrowserProfileProbe() { override val suffix="r11b_probe_a" }
class BrowserProfileBProbe : BrowserProfileProbe() { override val suffix="r11b_probe_b" }

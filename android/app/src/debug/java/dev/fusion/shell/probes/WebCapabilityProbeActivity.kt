// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell.probes

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.webkit.*
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream

/** Debug-only actual Android WebView capability experiment. Same UID, separate profile/process; NOT security isolation. */
class WebCapabilityProbeActivity : Activity() {
    companion object {private var configured=false;private const val ORIGIN="https://r25-capability.invalid/"}
    private lateinit var web:WebView
    override fun onCreate(state:Bundle?) {
        super.onCreate(null)
        if(!configured) {WebView.setDataDirectorySuffix("r25-capability");configured=true}
        val result=TextView(this).apply {id=dev.fusion.shell.R.id.r25_web_capability_result;text="Waiting for actual controlled WebView page…"}
        web=WebView(this)
        web.settings.apply {javaScriptEnabled=true;allowFileAccess=false;allowContentAccess=false;domStorageEnabled=false;blockNetworkLoads=true;javaScriptCanOpenWindowsAutomatically=false}
        web.webChromeClient=object:WebChromeClient() {override fun onPermissionRequest(request:PermissionRequest){request.deny()}}
        web.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=request.url.toString()!=ORIGIN
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse {
                val admitted=request.url.toString()==ORIGIN && request.isForMainFrame
                val html=if(admitted) "<!doctype html><meta charset=utf-8><title>R25 actual renderer</title><p id=canary>pending</p><script>document.getElementById('canary').textContent='R25 native JS '+(6*7);</script>" else "Blocked by controlled experiment"
                val headers=mapOf("Cross-Origin-Opener-Policy" to "same-origin","Cross-Origin-Embedder-Policy" to "require-corp","Cache-Control" to "no-store","Content-Security-Policy" to "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'")
                return WebResourceResponse("text/html","UTF-8",if(admitted) 200 else 403,if(admitted) "OK" else "Forbidden",headers,ByteArrayInputStream(html.toByteArray()))
            }
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) {
                if(request.isForMainFrame)result.text=JSONObject().put("native_error",error.errorCode).put("description",error.description.toString().take(200)).toString()
            }
            override fun onPageFinished(view:WebView,url:String) {
                if(url!=ORIGIN)return
                view.evaluateJavascript("JSON.stringify({canary:document.getElementById('canary')&&document.getElementById('canary').textContent,secure:isSecureContext,isolated:typeof crossOriginIsolated==='boolean'?crossOriginIsolated:null,sharedArrayBuffer:typeof SharedArrayBuffer==='function',wasm:typeof WebAssembly==='object',ua:navigator.userAgent})") {encoded->
                    try {
                        val data=JSONObject(JSONTokener(encoded).nextValue() as String)
                        data.put("api",android.os.Build.VERSION.SDK_INT).put("pid",Process.myPid()).put("uid",Process.myUid())
                            .put("webview_package",WebView.getCurrentWebViewPackage()?.packageName).put("webview_version",WebView.getCurrentWebViewPackage()?.versionName)
                            .put("scope","Actual controlled intercepted HTTPS-origin WebView response, not remote TLS/network, native Office loading, profile security or a complete browser")
                        result.text=data.toString()
                    } catch(error:Exception) {result.text="Observed WebView report failure: "+error.javaClass.simpleName}
                }
            }
        }
        val layout=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;addView(result);addView(web,LinearLayout.LayoutParams(-1,0,1f))}
        setContentView(layout);web.loadUrl(ORIGIN)
    }
    override fun onDestroy(){web.stopLoading();web.destroy();super.onDestroy()}
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URI

data class Tab(val id: Long, var url: String, var title: String = "Offline start")

/**
 * Ephemeral HTTPS-only WebView workbench:
 * - Cold-starts strictly at offline `about:blank` (never contacts external servers on open).
 * - Blocks `file://`, `content://`, `javascript:`, `data:` execution and non-HTTPS schemes at navigation gates.
 * - One-tap `Wipe session` purges tabs, history, cache, `CookieManager`, and `WebStorage`.
 * - Honest scope: uses platform WebView without extension engine or desktop Chromium process sandboxing.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen() {
    val tabs = remember { mutableStateListOf(Tab(1, "about:blank")) }
    var active by remember { mutableStateOf(1L) }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready at offline about:blank · enter an explicit https:// URL") }
    val current = tabs.firstOrNull { it.id == active } ?: tabs.first()
    var ref by remember { mutableStateOf<WebView?>(null) }

    fun normalizeHttpsOrRefuse(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed == "about:blank") return trimmed
        if (trimmed.isEmpty() || trimmed.length > 2048 || trimmed.any { it.code !in 0x21..0x7E }) return null
        val candidate = if ("://" in trimmed || trimmed.startsWith("javascript:", true) || trimmed.startsWith("data:", true)) trimmed else "https://$trimmed"
        val parsed = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (!parsed.scheme.equals("https", true) || parsed.userInfo != null || parsed.host.isNullOrBlank()) return null
        return parsed.toASCIIString()
    }

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                draft,
                { if (it.length <= 2048) draft = it },
                Modifier.weight(1f).testTag("browser-url"),
                singleLine = true,
                label = { Text("HTTPS URL") }
            )
            Button(
                onClick = {
                    val safe = normalizeHttpsOrRefuse(draft)
                    if (safe == null) {
                        status = "Navigation refused: only explicit HTTPS URLs without credentials are admitted."
                    } else {
                        current.url = safe
                        status = "Loading $safe"
                        ref?.loadUrl(safe)
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("browser-go")
            ) { Text("Go") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (ref?.canGoBack() == true) ref?.goBack() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
            OutlinedButton(onClick = { if (ref?.canGoForward() == true) ref?.goForward() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Fwd") }
            OutlinedButton(
                onClick = {
                    val id = (tabs.maxOfOrNull { it.id } ?: 0) + 1
                    tabs.add(Tab(id, "about:blank"))
                    active = id
                    draft = ""
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("browser-new-tab")
            ) { Text("+ Tab") }
            Button(
                onClick = {
                    tabs.clear()
                    tabs.add(Tab(1, "about:blank"))
                    active = 1L
                    draft = ""
                    ref?.apply {
                        loadUrl("about:blank")
                        clearHistory()
                        clearCache(true)
                    }
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies(null)
                    status = "Ephemeral session wiped: tabs reset to about:blank, cookies & WebStorage cleared."
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("browser-wipe")
            ) { Text("Wipe session") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEach { t ->
                OutlinedButton(
                    onClick = { active = t.id; draft = if (t.url == "about:blank") "" else t.url },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("browser-tab-${t.id}")
                ) {
                    Text((if (t.id == active) "• " else "") + t.url.take(24))
                }
            }
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val allowed = request.url.scheme == "https" && request.url.userInfo == null
                            if (!allowed) status = "Blocked non-HTTPS navigation (${request.url.scheme})"
                            return !allowed
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            current.title = view.title ?: url
                        }
                    }
                    loadUrl(current.url)
                    ref = this
                }
            },
            update = { w ->
                if (w.url != current.url) w.loadUrl(current.url)
                ref = w
            },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        Text(status, Modifier.testTag("browser-status"), style = MaterialTheme.typography.bodySmall)
    }
}

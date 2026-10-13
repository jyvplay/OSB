package dev.fusion.browser

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

data class Tab(val id: Long, var url: String, var title: String = "")

/**
 * Real WebView browser: multiple tabs, back/forward, JS enabled per WebView default.
 * No ad-block, no extensions, no desktop-UA spoofing claims. History stays in memory.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen() {
    val tabs = remember { mutableStateListOf(Tab(1, "https://www.example.com")) }
    var active by remember { mutableStateOf(1L) }
    var draft by remember { mutableStateOf("https://www.example.com") }
    val current = tabs.firstOrNull { it.id == active } ?: tabs.first()
    var ref by remember { mutableStateOf<WebView?>(null) }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row {
            OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), singleLine = true, label = { Text("URL") })
            Button(onClick = {
                val u = if (draft.startsWith("http")) draft else "https://$draft"
                current.url = u
                ref?.loadUrl(u)
            }) { Text("Go") }
        }
        Row {
            Button(onClick = { if (ref?.canGoBack() == true) ref?.goBack() }, enabled = true) { Text("Back") }
            Button(onClick = { if (ref?.canGoForward() == true) ref?.goForward() }) { Text("Fwd") }
            Button(onClick = {
                val id = (tabs.maxOfOrNull { it.id } ?: 0) + 1
                tabs.add(Tab(id, "https://www.example.com")); active = id; draft = "https://www.example.com"
            }) { Text("+ Tab") }
        }
        Row {
            tabs.forEach { t ->
                androidx.compose.material3.OutlinedButton(onClick = { active = t.id; draft = t.url }) {
                    Text((if (t.id == active) "> " else "") + t.url.take(24))
                }
            }
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
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
        Text("Tabs: ${tabs.size}. Sleeping tabs, split view and extensions are not implemented.")
    }
}

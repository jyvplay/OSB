// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ConsentedEmbedPlayerCard(descriptor: EmbeddedStreamContract.EmbedDescriptor, onClose: () -> Unit) {
    var blockedCount by remember { mutableStateOf(0) }
    var webRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(descriptor.embedUrl) {
        onDispose {
            webRef?.apply {
                loadUrl("about:blank")
                clearHistory()
                clearCache(true)
                destroy()
            }
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
        }
    }

    Column(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Official ${descriptor.service.title} Web Embed (Sandboxed)", fontWeight = FontWeight.SemiBold)
        Text(descriptor.attributionNotice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier
                .fillMaxWidth()
                .height(228.dp) // Strictly >= 200x200dp per YouTube API Terms of Service
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .testTag("embed-player-box")
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportMultipleWindows(false)
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val ok = request.url.scheme == "https" &&
                                    EmbeddedStreamContract.isHostAllowedForEmbed(descriptor, request.url.host)
                                if (!ok) blockedCount++
                                return !ok
                            }

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val ok = request.url.scheme == "https" &&
                                    EmbeddedStreamContract.isHostAllowedForEmbed(descriptor, request.url.host)
                                if (!ok) {
                                    blockedCount++
                                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                                }
                                return null
                            }
                        }
                        loadUrl(descriptor.embedUrl)
                        webRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Blocked cross-origin requests: $blockedCount", style = MaterialTheme.typography.labelSmall)
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("embed-player-close")) {
                Text("Close & wipe cookies")
            }
        }
    }
}

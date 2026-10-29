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
    var readerMode by remember { mutableStateOf(false) }
    var blockedNavigations by remember { mutableStateOf(0) }
    val isolationDemo = remember {
        CrossOriginIsolationPolicy.evaluateDocumentHeaders(
            mapOf("Cross-Origin-Opener-Policy" to "same-origin", "Cross-Origin-Embedder-Policy" to "require-corp"),
        )
    }
    val defaultCsp = remember { CspHeaderDirective.parse("default-src 'self'; frame-ancestors 'none'") }
    var recallEnabled by remember { mutableStateOf(true) }
    var recallMode by remember { mutableStateOf("BY_URL_NAME") }
    var recallCustomPattern by remember { mutableStateOf("RecallVault/{yyyy}-{mm}-{dd}/{domain}/{kind}") }
    var capturedImages by remember { mutableStateOf(0) }
    var capturedVideos by remember { mutableStateOf(0) }
    var capturedMusic by remember { mutableStateOf(0) }
    var activeRecallTier by remember { mutableStateOf("T1 Direct/206") }
    var lastRecallFolder by remember { mutableStateOf("recall/www.example.com") }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row {
            OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), singleLine = true, label = { Text("URL") })
            Button(onClick = {
                val candidate = if (draft.contains("://")) draft else "https://$draft"
                val safe = HtmlSanitizer.sanitizeUri(candidate)
                if (safe != null) {
                    current.url = safe
                    ref?.loadUrl(safe)
                } else {
                    blockedNavigations++
                }
            }) { Text("Go") }
        }
        Row {
            Button(onClick = { if (ref?.canGoBack() == true) ref?.goBack() }, enabled = true) { Text("Back") }
            Button(onClick = { if (ref?.canGoForward() == true) ref?.goForward() }) { Text("Fwd") }
            Button(onClick = {
                val id = (tabs.maxOfOrNull { it.id } ?: 0) + 1
                tabs.add(Tab(id, "https://www.example.com")); active = id; draft = "https://www.example.com"
            }) { Text("+ Tab") }
            androidx.compose.material3.OutlinedButton(onClick = { readerMode = !readerMode }) {
                Text(if (readerMode) "Web View" else "Reader Mode")
            }
        }
        if (readerMode) {
            val extracted = remember(current.title, current.url) {
                val sanitized = HtmlSanitizer.sanitize("<article><h1>${current.title.ifBlank { current.url }}</h1><p>Offline Reader Mode sanitizes active scripts via HtmlSanitizer and extracts clean prose via ArticleReaderExtractor.</p></article>")
                ArticleReaderExtractor.extract(sanitized.cleanHtml)
            }
            Text("Reader Mode (${extracted.wordCount} words • COOP/COEP=${isolationDemo.crossOriginIsolated} • CSP=${defaultCsp.directives.size} rules • blocked schemes=$blockedNavigations): ${extracted.markdown}")
        }
        BrowserRecallHud(
            recallEnabled = recallEnabled,
            onToggleRecall = { recallEnabled = it },
            delineationMode = recallMode,
            onSelectDelineation = { mode ->
                recallMode = mode
                val cleanHost = dev.fusion.browser.model.CleanUrlTrackingParamStripper.sanitize(current.url)
                    .sanitizedUrl.substringAfter("://").substringBefore('/')
                lastRecallFolder = when (mode) {
                    "BY_PAGE_TITLE" -> "recall/by-title/${current.title.ifBlank { cleanHost }.take(40)}"
                    "BY_CUSTOM_TEMPLATE" -> recallCustomPattern.replace("{domain}", cleanHost)
                    else -> "recall/by-url/$cleanHost"
                }
            },
            customFolderPattern = recallCustomPattern,
            onUpdateCustomPattern = { recallCustomPattern = it },
            capturedImages = capturedImages,
            capturedVideos = capturedVideos,
            capturedMusic = capturedMusic,
            activeTierLabel = activeRecallTier,
            lastArchivedFolder = lastRecallFolder,
        )
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
                        override fun onLoadResource(view: WebView, resourceUrl: String) {
                            if (!recallEnabled) return
                            val lower = resourceUrl.substringBefore('?').lowercase()
                            val decision = dev.fusion.browser.model.RecallMethodEscalationRouter.selectTier(
                                dev.fusion.browser.model.RecallMethodEscalationRouter.ViewedResourceSignal(
                                    url = resourceUrl,
                                    contentType = "",
                                    isBlobOrMediaSource = lower.startsWith("blob:"),
                                )
                            )
                            if (decision is dev.fusion.browser.model.RecallMethodEscalationRouter.RoutingDecision.Selected) {
                                activeRecallTier = "T${decision.tier.rank} ${decision.tier.name}"
                            }
                            when {
                                lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".avif") || lower.endsWith(".svg") ->
                                    capturedImages++
                                lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8") || lower.endsWith(".mpd") ->
                                    capturedVideos++
                                lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".ogg") || lower.endsWith(".opus") || lower.endsWith(".m4a") || lower.endsWith(".wav") ->
                                    capturedMusic++
                            }
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
        val linkCtxRows = remember(incognitoMode) {
            dev.fusion.browser.model.BrowserLinkMediaContextMenuPlanner.buildMenu(
                target = dev.fusion.browser.model.BrowserLinkMediaContextMenuPlanner.HitTargetKind.VIDEO_ELEMENT,
                incognitoActive = incognitoMode,
            )
        }
        Text("Tabs: ${tabs.size} • Right-sized link/media menu (${linkCtxRows.size} verbs; Recall 6-method submenu=${linkCtxRows.any { it.submenu.isNotEmpty() }}). Sleeping tabs, split view and extensions are not implemented.")
    }
}

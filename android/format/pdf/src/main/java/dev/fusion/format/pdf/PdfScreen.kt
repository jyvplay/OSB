package dev.fusion.format.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun PdfScreen(context: Context, uri: Uri?, onPick: () -> Unit) {
    var doc by remember(uri) { mutableStateOf<PdfDocument?>(null) }
    var page by remember(uri) { mutableIntStateOf(0) }
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var renderedPage by remember(uri) {mutableIntStateOf(-1)}
    var status by remember(uri) { mutableStateOf("No PDF opened.") }
    var acroSummary by remember(uri) { mutableStateOf("Forms/signatures: no PDF inspected.") }

    DisposableEffect(uri) {
        onDispose { doc?.close(); doc = null }
    }

    LaunchedEffect(uri) {
        doc?.close(); doc = null; bitmap = null
        if (uri == null) { status = "No PDF opened."; acroSummary = "Forms/signatures: no PDF opened."; return@LaunchedEffect }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val sample = ByteArray(65_536)
                val n = stream.read(sample)
                if (n > 0) {
                    val slice = sample.copyOf(n)
                    val forms = PdfFormAcroFieldInspector.inspectPdfBytes(slice)
                    val probeKey = PdfPkcs7SignatureVerifier.generateEd25519KeyPair().public
                    val sig = PdfPkcs7SignatureVerifier.verifyPdfSignature(slice, probeKey)
                    "AcroForm fields=${forms.fields.size} (XFA=${forms.hasXfaDeprecatedWarning}, JSQuarantined=${forms.hasJavaScriptActionsQuarantined}) · sig=${sig.status}"
                } else "Empty PDF stream"
            } ?: "Stream unavailable"
        }.onSuccess { acroSummary = it }
        PdfDocument.open(context, uri).fold(
            onSuccess = { d -> doc = d; status = "${d.pageCount} page(s)" },
            onFailure = { status = it.message ?: "Could not open this PDF" }
        )
    }

    LaunchedEffect(doc, page) {
        val d = doc ?: return@LaunchedEffect
        val requestedPage=page
        bitmap=null;renderedPage=-1
        d.renderPage(requestedPage, 320).fold(
            onSuccess = { if(doc===d && page==requestedPage) {bitmap=it;renderedPage=requestedPage} else it.recycle() },
            onFailure = { if(doc===d && page==requestedPage) status = "Render failed: ${it.message}" }
        )
    }

    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onPick,modifier=Modifier.testTag("pdf-open")) { Text("Open PDF") }
            OutlinedButton(modifier=Modifier.testTag("pdf-prev"),onClick = { if (page > 0) page-- }, enabled = (doc?.pageCount ?: 0) > 0 && page > 0) { Text("Prev") }
            OutlinedButton(
                modifier=Modifier.testTag("pdf-next"),onClick = { if (page + 1 < (doc?.pageCount ?: 0)) page++ },
                enabled = page + 1 < (doc?.pageCount ?: 0)
            ) { Text("Next") }
        }
        Text("$status${if ((doc?.pageCount ?: 0) > 0) "  ·  page ${page + 1}" else ""}",Modifier.testTag("pdf-status"))
        Text(acroSummary, modifier = Modifier.testTag("pdf-acro-summary"), style = MaterialTheme.typography.labelSmall)
        bitmap?.takeIf {renderedPage==page}?.let {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                Image(it.asImageBitmap(), contentDescription = "PDF page ${renderedPage + 1}", Modifier.fillMaxWidth())
            }
        }
        Text("Isolated readonly preview: source≤2MiB,≤1024pages,≤320px per side; no annotation/signing/text extraction. Not full PDF fidelity, anonymous memory or forensic erasure.", style = MaterialTheme.typography.labelSmall)
    }
}

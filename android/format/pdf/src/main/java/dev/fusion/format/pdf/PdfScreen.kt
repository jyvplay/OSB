package dev.fusion.format.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun PdfScreen(context: Context, uri: Uri?, onPick: () -> Unit) {
    var doc by remember { mutableStateOf<PdfDocument?>(null) }
    var page by remember(uri) { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("No PDF opened.") }

    DisposableEffect(uri) {
        onDispose { doc?.close(); doc = null }
    }

    LaunchedEffect(uri) {
        doc?.close(); doc = null; bitmap = null
        if (uri == null) { status = "No PDF opened."; return@LaunchedEffect }
        PdfDocument.open(context, uri).fold(
            onSuccess = { d -> doc = d; status = "${d.pageCount} page(s)" },
            onFailure = { status = it.message ?: "Could not open this PDF" }
        )
    }

    LaunchedEffect(doc, page) {
        val d = doc ?: return@LaunchedEffect
        d.renderPage(page, 1000).fold(
            onSuccess = { bitmap = it },
            onFailure = { status = "Render failed: ${it.message}" }
        )
    }

    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onPick) { Text("Open PDF") }
            OutlinedButton(onClick = { if (page > 0) page-- }, enabled = (doc?.pageCount ?: 0) > 0 && page > 0) { Text("Prev") }
            OutlinedButton(
                onClick = { if (page + 1 < (doc?.pageCount ?: 0)) page++ },
                enabled = page + 1 < (doc?.pageCount ?: 0)
            ) { Text("Next") }
        }
        Text("$status${if ((doc?.pageCount ?: 0) > 0) "  ·  page ${page + 1}" else ""}")
        bitmap?.let {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                Image(it.asImageBitmap(), contentDescription = "PDF page ${page + 1}", Modifier.fillMaxWidth())
            }
        }
        Text("View only: no annotation, signing, or text extraction.", style = MaterialTheme.typography.labelSmall)
    }
}

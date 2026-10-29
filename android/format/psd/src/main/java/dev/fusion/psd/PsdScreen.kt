package dev.fusion.psd

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

/**
 * PSD viewer (FF-08).
 * Renders the flattened composite using the clean-room decoder.
 */
@Composable
fun PsdScreen(
    context: Context,
    uri: Uri?,
    onPickPsd: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("No PSD opened.") }

    LaunchedEffect(uri) {
        bitmap = null
        if (uri == null) {
            status = "No PSD file selected."
            return@LaunchedEffect
        }
        status = "Decoding Photoshop composite..."
        val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        if (stream == null) {
            status = "Cannot open that document."
            return@LaunchedEffect
        }
        stream.use { input ->
            PsdBitmaps.decodeBitmap(input, maxDimension = 1920).fold(
                onSuccess = {
                    bitmap = it
                    status = "Decoded ${it.width}x${it.height} composite."
                },
                onFailure = { status = "PSD decode failed: ${it.message}" }
            )
        }
    }

    Column(
        Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("PSD viewer (composite only)", style = MaterialTheme.typography.titleSmall)
        Button(onClick = onPickPsd) { Text("Select a .psd file") }
        Text(status)

        bitmap?.let { bmp ->
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PSD composite",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        val blendModesReady = remember { PsdBlendAdjustmentCompositor.BlendMode.entries.size }
        Text("Pure-JVM non-destructive blend/adjustment compositor ready ($blendModesReady blend modes).", style = MaterialTheme.typography.labelSmall)
        Text(
            "Decodes the flattened 8-bit RGB composite (raw or PackBits RLE). " +
                "Layers, masks, adjustment layers, 16-bit, CMYK and PSB are not supported.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

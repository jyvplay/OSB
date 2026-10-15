package dev.fusion.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaEntry(val uri: Uri, val name: String, val mime: String)

/** Real MediaStore query. Playback is via system viewer; no fake player. */
suspend fun queryImages(context: Context, limit: Int = 100): List<MediaEntry> = withContext(Dispatchers.IO) {
    val out = mutableListOf<MediaEntry>()
    val cols = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE)
    context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
        val idC = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nC = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val mC = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
        while (c.moveToNext() && out.size < limit) {
            val id = c.getLong(idC)
            out.add(MediaEntry(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), c.getString(nC) ?: "image", c.getString(mC) ?: ""))
        }
    }
    out
}

@Composable
fun MediaScreen(context: Context, onView: (Uri, String) -> Unit) {
    var items by remember { mutableStateOf(emptyList<MediaEntry>()) }
    var msg by remember { mutableStateOf("Needs READ_MEDIA_IMAGES on Android 14+. No picker = no list.") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Button(onClick = { scope.launch {
            try { items = queryImages(context); msg = "${items.size} images (recent first)"; } catch (e: SecurityException) { msg = "Permission missing: ${e.message}" }
        } }) { Text("Load images") }
        Text(msg)
        SubtitlePicker(context)
        LazyColumn(Modifier.weight(1f)) {
            items(items) { e ->
                Text(e.name, Modifier.fillMaxWidth().clickable { onView(e.uri, e.mime) }.padding(8.dp))
            }
        }
        Text("Tap opens the system viewer. No in-app decode claims beyond the platform.")
    }
}

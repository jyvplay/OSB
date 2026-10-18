// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import android.app.Application
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = AtomicFile(File(app.filesDir, "map-bookmarks-v1.bin"))
    var book by mutableStateOf<MapBookmarks?>(null); private set
    var land by mutableStateOf<List<List<GeoPoint>>>(emptyList()); private set
    var camera by mutableStateOf(MapCamera())
    var cursor by mutableStateOf(camera.center)
    var measureFrom by mutableStateOf<GeoPoint?>(null)
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf("Loading offline atlas…"); private set
    init { reload() }
    fun reload() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                land = withContext(Dispatchers.IO) { getApplication<Application>().assets.open("natural-earth-land-v1.txt").use(LandOutlines::read) }
                book = withContext(Dispatchers.IO) { read() }
                status = "Offline land outlines · no location permission or network request"
            } catch (e: Exception) { book = null; status = "Atlas unavailable; saved bytes retained: ${e.message?.take(120)}" }
            finally { busy = false }
        }
    }
    private fun read(): MapBookmarks {
        if (!storage.baseFile.exists() && !File(storage.baseFile.path + ".bak").exists()) return MapBookmarks()
        return storage.openRead().use { input ->
            val bytes = input.readNBytes(MapBookmarkCodec.MAX_BYTES + 1)
            MapBookmarkCodec.decode(bytes)
        }
    }
    fun add(name: String, point: GeoPoint) = change { it.add(name, point) }
    fun remove(id: Long) = change { it.remove(id) }
    private fun change(operation: (MapBookmarks) -> MapBookmarks) {
        val before = book ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val next = withContext(Dispatchers.IO) {
                    val current = read(); require(current == before) { "Bookmarks changed; reload first" }
                    val result = operation(current); val bytes = MapBookmarkCodec.encode(result)
                    val stream = storage.startWrite()
                    try { stream.write(bytes); storage.finishWrite(stream) }
                    catch (e: Exception) { storage.failWrite(stream); throw e }
                    check(read() == result) { "Bookmark readback failed" }; result
                }
                book = next; status = "Saved ${next.pins.size}/256 pins locally · revision ${next.revision}"
            } catch (e: Exception) { status = "Pin change refused: ${e.message?.take(120)}" }
            finally { busy = false }
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.fusion.launcher.DeskAction
import dev.fusion.launcher.TabletopDocument
import dev.fusion.launcher.TabletopStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One owner, real private-file publication. The initial scene is not a corrupt-file fallback. */
class TabletopSessionModel(app: Application) : AndroidViewModel(app) {
    private val store = TabletopStore(app.filesDir.toPath().resolve("tabletop"))
    var document by mutableStateOf<TabletopDocument?>(null); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf("Loading arrangement…"); private set
    init { reload() }
    fun reload() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                document = withContext(Dispatchers.IO) { store.load() }
                status = "Drag a pin to arrange · double-click to open · saved locally"
            } catch (e: Exception) {
                document = null; status = "Arrangement unavailable; original bytes retained. ${e.message?.take(160)}"
            } finally { busy = false }
        }
    }
    fun change(revision: Long, action: DeskAction) {
        if (busy) { status = "An arrangement write is in progress; try again when saved."; return }
        busy = true
        viewModelScope.launch {
            try {
                document = withContext(Dispatchers.IO) { store.update(revision, action) }
                status = "Arrangement saved · revision ${document!!.revision} · ${document!!.scene.pins.size}/512 pins"
            } catch (e: Exception) {
                status = "Change refused: ${e.message?.take(160)}. Reload if another writer changed the file."
            } finally { busy = false }
        }
    }
}

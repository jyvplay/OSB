package dev.fusion.shell

import android.app.Application
import android.util.AtomicFile
import dev.fusion.storage.CheckedAtomicFile
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.fusion.model.PilotPolicy
import dev.fusion.windowing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** SH-08: single writer, IO off main, bounded geometry snapshot; never stores document bodies. */
class DesktopSessionModel(application: Application) : AndroidViewModel(application) {
    var desktop by mutableStateOf(DesktopState())
        private set
    var loading by mutableStateOf(true)
        private set
    var blocked by mutableStateOf(false)
        private set
    var status by mutableStateOf("Loading window layout…")
        private set
    private val file = AtomicFile(File(application.noBackupFilesDir, "desktop-v1.bin"))
    private val mutex = Mutex()
    private val updates = Channel<Unit>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            try {
                desktop = withContext(Dispatchers.IO) {
                    if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) file.openRead().use(DesktopCodec::read)
                    else migrateLegacy()
                }
                if (desktop.commandEpoch == null) {
                    desktop = DesktopCommandStream.start(desktop, UUID.randomUUID().toString())
                    updates.trySend(Unit)
                }
                status = "Window layout restored. Experimental modules require new consent after each Activity session."
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                blocked = true
                status = "Window recovery rejected (${e.javaClass.simpleName}). Snapshot retained; use Pilot safety to reset layout explicitly. Editor recovery is separate."
            } finally { loading = false }
            for (unused in updates) {
                delay(500) // Throttled snapshots even during continuous input; no write on every drag.
                flush()
            }
        }
    }
    fun dispatch(command: WindowCommand) {
        if (loading || blocked) return
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) { "Desktop has one main-thread producer" }
        if (desktop.lastSequence == Long.MAX_VALUE) {
            status = "Window command sequence exhausted; reset layout explicitly. No command applied."
            return
        }
        val envelope = DesktopCommandStream.next(desktop, command)
        val transition = DesktopCommandStream.accept(desktop, envelope)
        check(transition.disposition == CommandDisposition.CONSUMED)
        desktop = transition.state
        updates.trySend(Unit)
    }
    suspend fun flush(): Boolean {
        if (loading || blocked) return false
        return try {
            mutex.withLock {
                val snapshot = desktop
                withContext(Dispatchers.IO) {
                    CheckedAtomicFile.write(file, snapshot, DesktopCodec::write, DesktopCodec::read)
                }
            }
            status = "Layout checkpoint saved locally. This is not a document backup."
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            status = "Layout checkpoint failed (${e.javaClass.simpleName}); previous snapshot retained."
            false
        }
    }
    fun resetLayout() {
        if (loading) return
        viewModelScope.launch {
            loading = true
            try {
                val reset = DesktopCommandStream.start(DesktopState(), UUID.randomUUID().toString())
                mutex.withLock {
                    withContext(Dispatchers.IO) {
                        // Keep rejected bytes for owner inspection; never delete editor drafts.
                        for (suffix in listOf("", ".bak", ".new")) {
                            val source = File(file.baseFile.path + suffix)
                            if (source.exists()) check(source.renameTo(File(source.parentFile, "desktop-retained-${UUID.randomUUID()}.bin")))
                        }
                        CheckedAtomicFile.write(file, reset, DesktopCodec::write, DesktopCodec::read)
                    }
                }
                desktop = reset; blocked = false
                status = "Layout reset. Retained snapshots and editor drafts were not deleted."
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                blocked = true; status = "Reset failed (${e.javaClass.simpleName}); no layout changes accepted."
            } finally { loading = false }
        }
    }
    private fun migrateLegacy(): DesktopState {
        val prefs = getApplication<Application>().getSharedPreferences("fusion", Application.MODE_PRIVATE)
        val raw = prefs.getString("windows", "[]") ?: "[]"
        require(raw.length <= DesktopCodec.MAX_BYTES)
        val array = JSONArray(raw)
        require(array.length() <= WindowEngine.MAX_WINDOWS)
        val windows = (0 until array.length()).mapNotNull { index ->
            val o = array.getJSONObject(index)
            val module = o.getString("module")
            if (PilotPolicy.modules.none { it.id == module }) null else ShellWindow(o.getLong("id"), module, o.getString("title"),
                Bounds(o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("w").toFloat(), o.getDouble("h").toFloat()),
                o.getInt("z"), o.optBoolean("min"), o.optInt("ws"))
        }
        return WindowEngine.restore(DesktopState(windows = windows, workspace = prefs.getInt("workspace", 0)))
    }
}

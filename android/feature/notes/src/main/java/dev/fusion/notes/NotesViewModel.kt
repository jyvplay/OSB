package dev.fusion.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface NoteAction {
    data object New : NoteAction
    data class Open(val name: String) : NoteAction
    data class Restore(val entry: TrashedNote) : NoteAction
    data class Delete(val name: String, val revision: NoteRevision) : NoteAction
}
data class NotesState(
    val names: List<String> = emptyList(), val name: String = "", val text: String = "",
    val savedText: String = "", val revision: NoteRevision? = null, val busy: Boolean = true,
    val status: String = "Loading local notes…", val pending: NoteAction? = null,
    val links: NoteLinkGraph? = null, val trash: List<TrashedNote>? = null,
    val recoveryBlocked: Boolean = false, val recoveryStatus: String = "Loading local draft recovery…"
) {
    val dirty get() = text != savedText || (revision == null && name.isNotEmpty())
}

/** One Activity-scoped owner. Checkpoints preserve the last confirmed draft, not every keystroke. */
class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val store by lazy { NotesStore(getApplication<Application>()) }
    private val mutable = MutableStateFlow(NotesState())
    private val recovery=NotesRecoveryStore(application.noBackupFilesDir)
    private val disk=Mutex()
    private val checkpoints=Channel<Unit>(Channel.CONFLATED)
    private var initialized=false
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                val restored=withContext(Dispatchers.IO) { recovery.read() }
                mutable.value=NotesState(name=restored.name,text=restored.text,savedText=restored.savedText,
                    revision=restored.revision,busy=false,recoveryStatus="Recovered last local checkpoint; saved notes were not modified.")
                initialized=true
                refresh()
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                mutable.value=NotesState(busy=false,recoveryBlocked=true,
                    recoveryStatus="Recovery unreadable; retained without overwrite. Inspect no_backup/notes-recovery-v1.bin in Device Explorer. ${e.message?.take(160)}")
            }
            // Do not cancel an in-flight file commit or debounce forever under continuous typing.
            for(ignored in checkpoints) { delay(500);flush() }
        }
    }
    private fun ready() = initialized && !mutable.value.recoveryBlocked
    private fun checkpoint() {
        if(ready()) {
            mutable.value=mutable.value.copy(recoveryStatus="Draft checkpoint pending; latest edits can be lost if the process stops now.")
            checkpoints.trySend(Unit)
        }
    }
    private fun draft(s: NotesState) = NoteDraft(s.name,s.text,s.savedText,s.revision)
    suspend fun flush(): Boolean {
        if(!ready()) return false
        return try {
            disk.withLock {
                val snapshot=draft(mutable.value)
                withContext(Dispatchers.IO) { recovery.write(snapshot) }
                mutable.value=mutable.value.copy(recoveryStatus=if(draft(mutable.value)==snapshot)
                    "Local draft checkpoint confirmed by exact readback. Saved .md files unchanged by recovery."
                    else "Older draft checkpoint confirmed; newer edits still pending.")
            }
            true
        } catch(e: Exception) {
            if(e is CancellationException) throw e
            mutable.value=mutable.value.copy(recoveryStatus="Draft checkpoint not confirmed: ${e.message?.take(180)}. Keep editing or save explicitly; do not assume recovery.")
            false
        }
    }
    fun checkpointNow() { if(ready() && !mutable.value.busy) viewModelScope.launch { flush() } }
    fun name(value: String) {
        val s=mutable.value
        if (ready() && !s.busy && s.pending==null && s.revision==null && value.length<=64) { mutable.value=s.copy(name=value);checkpoint() }
    }
    fun edit(value: String) {
        val s=mutable.value
        if (ready() && !s.busy && s.pending==null) {
            if(value.length<=NoteFiles.MAX_BYTES) { mutable.value=s.copy(text=value);checkpoint() }
            else mutable.value=s.copy(status="Edit refused: 256 KiB text limit. Existing draft retained.")
        }
    }
    fun refresh() {
        if (!ready() || mutable.value.busy || mutable.value.pending!=null) return
        mutable.value=mutable.value.copy(busy=true)
        viewModelScope.launch {
            try { val names=withContext(Dispatchers.IO) { store.list() }; mutable.value=mutable.value.copy(names=names,status="Local Markdown notes. Recovery checkpoints are separate from explicit Save note.") }
            catch(e: Exception) { failure(e) }
            finally { mutable.value=mutable.value.copy(busy=false) }
        }
    }
    fun refreshLinks() {
        val s=mutable.value
        if(!ready() || s.busy || s.pending!=null) return
        mutable.value=s.copy(busy=true,links=null)
        viewModelScope.launch {
            try {
                val graph=withContext(Dispatchers.IO) { store.links() }
                mutable.value=mutable.value.copy(links=graph,status="Scanned saved-note revisions under the writer lock. Unsaved drafts excluded. Refresh after external changes.")
            } catch(e: Exception) { failure(e) }
            finally { mutable.value=mutable.value.copy(busy=false) }
        }
    }
    fun refreshTrash() {
        val s=mutable.value
        if(!ready() || s.busy || s.pending!=null) return
        mutable.value=s.copy(busy=true,trash=null)
        viewModelScope.launch {
            try {
                val entries=withContext(Dispatchers.IO) { store.trash() }
                mutable.value=mutable.value.copy(trash=entries,status="Saved-note trash checked. No automatic purge; restores never overwrite an existing note.")
            } catch(e: Exception) { failure(e) }
            finally { mutable.value=mutable.value.copy(busy=false) }
        }
    }
    fun request(action: NoteAction) {
        val s=mutable.value
        if(!ready() || s.busy || s.pending!=null) return
        if(action is NoteAction.Delete && (action.name!=s.name || action.revision!=s.revision)) return
        if(s.dirty || action is NoteAction.Delete) mutable.value=s.copy(pending=action)
        else execute(action)
    }
    fun cancel() { mutable.value=mutable.value.copy(pending=null) }
    fun confirm() { val action=mutable.value.pending ?: return; cancel(); execute(action) }
    private fun execute(action: NoteAction) {
        if(action == NoteAction.New) { mutable.value=NotesState(names=mutable.value.names,busy=false,status="New draft; choose a unique name and save explicitly."); checkpoint(); return }
        val s=mutable.value
        if(action is NoteAction.Delete && (action.name!=s.name || action.revision!=s.revision)) return
        mutable.value=s.copy(busy=true,links=if(action is NoteAction.Open) s.links else null)
        viewModelScope.launch {
            try {
                when(action) {
                    is NoteAction.Open -> {
                        val note=withContext(Dispatchers.IO) { store.load(action.name) }
                        mutable.value=mutable.value.copy(name=note.name,text=note.text,savedText=note.text,revision=note.revision,status="Opened local note. No synchronization.")
                    }
                    is NoteAction.Delete -> {
                        withContext(Dispatchers.IO) { store.delete(action.name,action.revision) }
                        // Deletion outcome is retained even if the following inventory read fails.
                        mutable.value=NotesState(names=s.names.filterNot { it==action.name },busy=true,status="Saved revision moved to trash. Unsaved draft was discarded as confirmed.")
                        val names=withContext(Dispatchers.IO) { store.list() }
                        val trash=withContext(Dispatchers.IO) { store.trash() }
                        mutable.value=mutable.value.copy(names=names,trash=trash)
                    }
                    is NoteAction.Restore -> {
                        val note=withContext(Dispatchers.IO) { store.restore(action.entry) }
                        mutable.value=NotesState(names=(s.names+note.name).distinct().sorted(),name=note.name,
                            text=note.text,savedText=note.text,revision=note.revision,busy=true,
                            status="Restored saved revision without overwriting another note.")
                        val trash=withContext(Dispatchers.IO) { store.trash() }
                        mutable.value=mutable.value.copy(trash=trash)
                    }
                    NoteAction.New -> Unit
                }
            } catch(e: Exception) { failure(e) }
            finally { mutable.value=mutable.value.copy(busy=false);checkpoint() }
        }
    }
    fun save() {
        val s=mutable.value
        if(!ready() || s.busy || s.pending!=null) return
        mutable.value=s.copy(busy=true,links=null)
        viewModelScope.launch {
            try {
                val note=withContext(Dispatchers.IO) { store.save(s.name,s.text,s.revision) }
                mutable.value=mutable.value.copy(name=note.name,text=note.text,savedText=note.text,revision=note.revision,links=null,
                    names=(s.names+note.name).distinct().sorted(),status="Saved locally with exact readback. Not a backup or a physical durability guarantee.")
                checkpoint()
            } catch(e: Exception) { failure(e) }
            finally { mutable.value=mutable.value.copy(busy=false) }
        }
    }
    private fun failure(e: Exception) {
        if(e is CancellationException) throw e
        mutable.value=mutable.value.copy(status="Operation not confirmed: ${e.message?.take(240) ?: e.javaClass.simpleName}. Current memory state retained; refresh saved notes and trash to reconcile.")
    }
}

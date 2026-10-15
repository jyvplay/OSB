package dev.fusion.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface NoteAction {
    data object New : NoteAction
    data class Open(val name: String) : NoteAction
    data class Delete(val name: String, val revision: NoteRevision) : NoteAction
}
data class NotesState(
    val names: List<String> = emptyList(), val name: String = "", val text: String = "",
    val savedText: String = "", val revision: NoteRevision? = null, val busy: Boolean = true,
    val status: String = "Loading local notes…", val pending: NoteAction? = null,
    val links: NoteLinkGraph? = null
) {
    val dirty get() = text != savedText || (revision == null && name.isNotEmpty())
}

/** Activity-scoped memory draft survives window recomposition/rotation, NOT process death. */
class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val store by lazy { NotesStore(getApplication<Application>()) }
    private val mutable = MutableStateFlow(NotesState(busy=false))
    val state = mutable.asStateFlow()
    init { refresh() }
    fun name(value: String) {
        val s=mutable.value
        if (!s.busy && s.pending==null && s.revision==null && value.length<=64) mutable.value=s.copy(name=value)
    }
    fun edit(value: String) {
        val s=mutable.value
        if (!s.busy && s.pending==null) {
            if(value.length<=NoteFiles.MAX_BYTES) mutable.value=s.copy(text=value)
            else mutable.value=s.copy(status="Edit refused: 256 KiB text limit. Existing draft retained.")
        }
    }
    fun refresh() {
        if (mutable.value.busy || mutable.value.pending!=null) return
        mutable.value=mutable.value.copy(busy=true)
        viewModelScope.launch {
            try { val names=withContext(Dispatchers.IO) { store.list() }; mutable.value=mutable.value.copy(names=names,status="Local Markdown notes. Drafts are memory-only until saved.") }
            catch(e: Exception) { failure(e) }
            finally { mutable.value=mutable.value.copy(busy=false) }
        }
    }
    fun refreshLinks() {
        val s=mutable.value
        if(s.busy || s.pending!=null) return
        mutable.value=s.copy(busy=true,links=null)
        viewModelScope.launch {
            try {
                val graph=withContext(Dispatchers.IO) { store.links() }
                mutable.value=mutable.value.copy(links=graph,status="Scanned saved-note revisions under the writer lock. Unsaved drafts excluded. Refresh after external changes.")
            } catch(e: Exception) { failure(e) }
            finally { mutable.value=mutable.value.copy(busy=false) }
        }
    }
    fun request(action: NoteAction) {
        val s=mutable.value
        if(s.busy || s.pending!=null) return
        if(action is NoteAction.Delete && (action.name!=s.name || action.revision!=s.revision)) return
        if(s.dirty || action is NoteAction.Delete) mutable.value=s.copy(pending=action)
        else execute(action)
    }
    fun cancel() { mutable.value=mutable.value.copy(pending=null) }
    fun confirm() { val action=mutable.value.pending ?: return; cancel(); execute(action) }
    private fun execute(action: NoteAction) {
        if(action == NoteAction.New) { mutable.value=NotesState(names=mutable.value.names,busy=false,status="New memory-only draft; choose a unique name and save explicitly."); return }
        val s=mutable.value
        if(action is NoteAction.Delete && (action.name!=s.name || action.revision!=s.revision)) return
        mutable.value=s.copy(busy=true)
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
                        mutable.value=NotesState(names=s.names.filterNot { it==action.name },busy=true,status="Note deleted permanently.")
                        val names=withContext(Dispatchers.IO) { store.list() }; mutable.value=mutable.value.copy(names=names)
                    }
                    NoteAction.New -> Unit
                }
            } catch(e: Exception) { failure(e) }
            finally { mutable.value=mutable.value.copy(busy=false) }
        }
    }
    fun save() {
        val s=mutable.value
        if(s.busy || s.pending!=null) return
        mutable.value=s.copy(busy=true)
        viewModelScope.launch {
            try {
                val note=withContext(Dispatchers.IO) { store.save(s.name,s.text,s.revision) }
                mutable.value=mutable.value.copy(name=note.name,text=note.text,savedText=note.text,revision=note.revision,links=null,
                    names=(s.names+note.name).distinct().sorted(),status="Saved locally with exact readback. Not a backup or a physical durability guarantee.")
            } catch(e: Exception) { failure(e) }
            finally { mutable.value=mutable.value.copy(busy=false) }
        }
    }
    private fun failure(e: Exception) {
        if(e is CancellationException) throw e
        mutable.value=mutable.value.copy(status="Operation not confirmed: ${e.message?.take(240) ?: e.javaClass.simpleName}. Draft retained; refresh/reopen to reconcile.")
    }
}

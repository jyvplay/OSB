package dev.fusion.editor

import android.app.Application
import android.net.Uri
import android.util.AtomicFile
import dev.fusion.storage.CheckedAtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.fusion.documents.EditorSession
import dev.fusion.documents.PendingCopy
import dev.fusion.documents.CopyProtocol
import dev.fusion.documents.CopyStage
import android.provider.DocumentsContract
import dev.fusion.documents.SessionCodec
import dev.fusion.documents.TextBytes
import dev.fusion.documents.TextDocument
import dev.fusion.documents.TextEncoding
import dev.fusion.documents.TextEncodingCodec
import dev.fusion.documents.VerifiedCopy
import dev.fusion.documents.EditBuffer
import dev.fusion.documents.TextSelection
import dev.fusion.documents.LiteralQuery
import dev.fusion.documents.LiteralSearch
import dev.fusion.documents.TextReplacement
import dev.fusion.documents.ReplacementPlan
import dev.fusion.documents.LineDiff
import dev.fusion.documents.LineComparison
import dev.fusion.documents.TextNavigation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** PR-01 / FM-18 / FF-15. Activity-scoped so window hiding and rotation do not lose edits. */
class EditorViewModel(application: Application) : AndroidViewModel(application) {
    data class State(
        val session: EditorSession = EditorSession(),
        val loading: Boolean = true,
        val busy: Boolean = false,
        val recoveryBlocked: Boolean = false,
        val status: String = "Loading local recovery…",
        val buffers: Map<String, EditBuffer> = emptyMap(),
        val fields: Map<String, TextFieldValue> = emptyMap(),
        val toolsBusy: Boolean = false,
        val replacement: ReplacementPlan? = null,
        val comparison: LineComparison? = null,
    )

    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val file = AtomicFile(File(application.noBackupFilesDir, "editor-session-v1.bin"))
    private val disk = Mutex()
    private val checkpoints = Channel<Unit>(Channel.CONFLATED)
    // At most one result being processed plus one queued result; duplicate callbacks cannot grow an unbounded coroutine backlog.
    private val copyResults = Channel<CopyPickerResult?>(1)
    private var toolTicket = Any()
    private var runningToolTicket = toolTicket
    private val resolver get() = getApplication<Application>().contentResolver

    init {
        viewModelScope.launch { for(result in copyResults) handleCopyResult(result) }
        viewModelScope.launch {
            try {
                val restored = withContext(Dispatchers.IO) {
                    if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
                        CopyProtocol.recover(file.openRead().use(SessionCodec::read))
                    } else EditorSession()
                }
                mutable.value = State(session = restored, loading = false,
                    buffers = restored.documents.associate { it.id to EditBuffer.create(it.id,it.content) },
                    fields = restored.documents.associate { it.id to TextFieldValue(it.content) }, status = when {
                    restored.pendingCopy != null -> "Interrupted or legacy copy needs review. No destination will be replayed. Inspect any partial file, then retry to a new destination or abandon."
                    restored.documents.isNotEmpty() -> "Recovered local session. Source documents have not been changed."
                    else -> "Local recovery is separate from saving a document. Open bounded text or create a document."
                })
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = State(loading = false, recoveryBlocked = true,
                    status = "Recovery could not be read (${e.message}). Snapshot retained, not overwritten. Export it from Android Studio Device Explorer: no_backup/editor-session-v1.bin.")
            }
            // Coalesce without cancelling an in-flight checkpoint or starving on continuous typing.
            for (ignored in checkpoints) { delay(500); flush() }
        }
    }

    private fun ready() = !state.value.loading && !state.value.recoveryBlocked
    private fun change(session: EditorSession, buffer: EditBuffer? = null, field: TextFieldValue? = null, message: String? = null) {
        val old = state.value
        val buffers = session.documents.associate { doc ->
            doc.id to (buffer?.takeIf { it.documentId == doc.id && it.content == doc.content }
                ?: old.buffers[doc.id]?.takeIf { it.content == doc.content }
                ?: EditBuffer.create(doc.id,doc.content))
        }
        val fields = session.documents.associate { doc ->
            doc.id to (field?.takeIf { buffer?.documentId == doc.id && it.text == doc.content }
                ?: old.fields[doc.id]?.takeIf { it.text == doc.content && buffer?.documentId != doc.id }
                ?: TextFieldValue(doc.content,TextRange(buffers.getValue(doc.id).selection.anchor,buffers.getValue(doc.id).selection.caret)))
        }
        mutable.value = old.copy(session = session, buffers = buffers, fields = fields,
            comparison = null,
            replacement = old.replacement?.takeIf { plan -> session.documents.any { it.id == plan.documentId } },
            status = message ?: old.status)
        if (old.session != session) checkpoints.trySend(Unit)
    }
    private fun applyBuffer(buffer: EditBuffer, field: TextFieldValue? = null, message: String? = null) {
        val session = state.value.session
        if (session.documents.none { it.id == buffer.documentId }) return
        change(session.copy(documents = session.documents.map { if(it.id == buffer.documentId) it.copy(content=buffer.content) else it }),buffer,field,message)
    }

    /** Coalesced local AtomicFile writes; a cancelled coroutine never interrupts a file commit. */
    suspend fun flush(): Boolean {
        if (!ready()) return false
        return try {
            disk.withLock {
                val snapshot = state.value.session
                withContext(Dispatchers.IO) {
                    CheckedAtomicFile.write(file, snapshot, SessionCodec::write, SessionCodec::read)
                }
            }
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mutable.value = state.value.copy(status = "Local recovery write failed: ${e.message}. Keep this window open and save a copy.")
            false
        }
    }

    fun newDocument() {
        if (!ready() || state.value.busy) return
        addDocument("Untitled.txt", null, "")
    }

    fun open(uri: Uri, suppliedName: String? = null, encoding: TextEncoding? = null) {
        if (!ready() || state.value.busy) return
        // Reopening a source focuses its draft; it must never replace unsaved text.
        state.value.session.documents.firstOrNull { it.sourceUri == uri.toString() }?.let {
            select(it.id); return
        }
        if(state.value.session.documents.size>=SessionCodec.MAX_DOCUMENTS) {
            mutable.value=state.value.copy(status="Eight-document limit. Close a tab before opening another.");return
        }
        mutable.value = state.value.copy(busy = true, status = "Reading bounded text document…")
        viewModelScope.launch {
            try {
                val (name, decoded) = withContext(Dispatchers.IO) {
                    val name = suppliedName ?: resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "Document.txt"
                    val bytes = requireNotNull(resolver.openInputStream(uri)) { "Provider did not open the document" }.use(TextBytes::read)
                    name to TextEncodingCodec.decode(bytes,encoding)
                }
                addDocument(name, uri.toString(), decoded.content,decoded.encoding)
                mutable.value = state.value.copy(status = "Opened ${decoded.encoding.label}; Save a Copy preserves this encoding/BOM. Source unchanged." +
                    if(name.length>512) " Display label shortened without changing the source filename." else "")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = state.value.copy(status = "Open failed: ${e.message}. No draft was replaced.")
            } finally { mutable.value = state.value.copy(busy = false) }
        }
    }

    private fun addDocument(name: String, source: String?, content: String, encoding: TextEncoding = TextEncoding.UTF8) {
        val session = state.value.session
        if (session.documents.size >= SessionCodec.MAX_DOCUMENTS) {
            mutable.value = state.value.copy(status = "Eight-document limit. Close a tab before opening another."); return
        }
        // A bounded display label must not split a surrogate pair and poison all future checkpoints.
        val label = name.substring(0,TextSelection(minOf(512,name.length)).inText(name).anchor)
        TextBytes.encodedSize(label)
        val doc = TextDocument(UUID.randomUUID().toString(), label, source, content, TextBytes.digest(content),encoding)
        change(session.copy(documents = session.documents + doc, selectedId = doc.id))
    }

    fun select(id: String) {
        if (ready() && state.value.session.documents.any { it.id == id }) change(state.value.session.copy(selectedId = id))
    }

    // [U] verify on API 34/36/37: actual IME composing updates, focus changes and recreation.
    /** Keep IME composition from the incoming value; selection normalization never accepts broken UTF-16. */
    fun edit(id: String, value: TextFieldValue) {
        if (!ready()) return
        val current = state.value.buffers[id] ?: return
        try {
            val updated = current.edit(value.text,TextSelection(value.selection.start,value.selection.end))
            val field = value.copy(selection=TextRange(updated.selection.anchor,updated.selection.caret))
            applyBuffer(updated,field,if(updated.historyTrimmed && !current.historyTrimmed)
                "Undo history reached its 100-step / 4,194,304 UTF-16-unit budget; oldest steps were discarded. Current edit remains undoable." else null)
        } catch (e: IllegalArgumentException) {
            mutable.value = state.value.copy(status = "Edit rejected: ${e.message}. Draft unchanged.")
        }
    }
    fun undo(redo: Boolean = false) {
        if (!ready() || state.value.busy || state.value.toolsBusy) return
        val buffer = state.value.buffers[state.value.session.selectedId] ?: return
        val next = if(redo) buffer.redo() else buffer.undo()
        applyBuffer(next,message=if(next === buffer) "No further history in this direction." else
            "${if(redo) "Redid" else "Undid"} one draft edit. Source and any pending Save a Copy payload are unchanged.")
    }
    private fun sameRevision(buffer: EditBuffer): Boolean {
        val current = state.value.buffers[buffer.documentId]
        return runningToolTicket === toolTicket && state.value.session.selectedId == buffer.documentId && current != null && current.revision == buffer.revision && current.content == buffer.content
    }
    fun find(query: LiteralQuery, backwards: Boolean = false) {
        runTool { buffer ->
            val matches = withContext(Dispatchers.Default) { LiteralSearch.matches(buffer.content,query) }
            if (!sameRevision(buffer) || state.value.buffers[buffer.documentId]?.selection != buffer.selection) {
                mutable.value = state.value.copy(status="Search canceled or text, tab or selection changed. Search again.")
            } else {
                val target = LiteralSearch.next(matches,buffer.selection,backwards)
                if(target != null) applyBuffer(buffer.select(target),message="Found ${matches.size} non-overlapping matches in this revision; navigation wraps. Source unchanged.")
                else mutable.value = state.value.copy(status="No literal matches. No normalization, regex or expanding case folds are used.")
            }
        }
    }
    fun planReplace(query: LiteralQuery, replacement: String, onlySelection: Boolean) {
        runTool { buffer ->
            val plan = withContext(Dispatchers.Default) { TextReplacement.plan(buffer,query,replacement,onlySelection) }
            if(sameRevision(buffer)) mutable.value = state.value.copy(replacement=plan)
            else mutable.value = state.value.copy(status="Replacement preview abandoned: draft changed. Review again.")
        }
    }
    fun confirmReplace() {
        if(!ready() || state.value.toolsBusy) return
        val plan = state.value.replacement ?: return
        mutable.value = state.value.copy(replacement=null,comparison=null)
        val buffer = state.value.buffers[plan.documentId] ?: return
        try {
            require(state.value.session.selectedId == plan.documentId) { "Selected tab changed; review again" }
            val updated = plan.apply(buffer)
            applyBuffer(updated,message=if(updated.content == buffer.content) "Replacement made no text change; no undo step added."
                else "Replaced ${plan.count} literal matches in one undoable draft edit. No source file was written.")
        } catch(e: IllegalArgumentException) { mutable.value = state.value.copy(status="Replacement refused: ${e.message}") }
    }
    fun compareSource() {
        runTool { buffer ->
            val document=state.value.session.documents.single { it.id==buffer.documentId }
            val source=document.sourceUri ?: throw IllegalArgumentException("This draft has no source file")
            val original=withContext(Dispatchers.IO) {
                val bytes=requireNotNull(resolver.openInputStream(Uri.parse(source))).use(TextBytes::read)
                TextEncodingCodec.decode(bytes,document.encoding).content
            }
            val comparison=withContext(Dispatchers.Default) { LineDiff.compare(original,buffer.content) }
            if(sameRevision(buffer)) mutable.value=state.value.copy(comparison=comparison,
                status="Compared with source bytes read now, not a historical saved version. Source was not written.")
            else mutable.value=state.value.copy(status="Comparison discarded: selected draft/revision changed.")
        }
    }
    fun cancelTools() {
        toolTicket = Any()
        mutable.value = state.value.copy(replacement=null,comparison=null)
    }
    fun goTo(line: Int, column: Int) {
        runTool { buffer ->
            val offset = withContext(Dispatchers.Default) { TextNavigation.offset(buffer.content,line,column) }
            if(sameRevision(buffer)) applyBuffer(buffer.select(TextSelection(offset)),message="Selected line $line, code-point column $column. CRLF counts as one line break.")
            else mutable.value = state.value.copy(status="Navigation abandoned: draft changed. Try again.")
        }
    }
    private fun runTool(operation: suspend (EditBuffer)->Unit) {
        if(!ready() || state.value.busy || state.value.toolsBusy) return
        val buffer = state.value.buffers[state.value.session.selectedId] ?: return
        val ticket = Any().also { toolTicket = it; runningToolTicket = it }
        mutable.value = state.value.copy(toolsBusy=true,replacement=null,comparison=null)
        viewModelScope.launch {
            try { operation(buffer) }
            catch(e: Exception) {
                if(e is CancellationException) throw e
                mutable.value = state.value.copy(status="Editor tool refused: ${e.message}. No replacement applied.")
            } finally {
                mutable.value = state.value.copy(toolsBusy=false,
                    replacement=if(ticket === toolTicket) state.value.replacement else null,
                    comparison=if(ticket === toolTicket) state.value.comparison else null)
            }
        }
    }

    /** UI must obtain explicit discard confirmation if dirty. */
    fun closeDocument(id: String, discard: Boolean = false) {
        val s = state.value.session
        val doc = s.documents.find { it.id == id } ?: return
        if (!ready() || state.value.busy || s.pendingCopy?.documentId == id || (doc.dirty && !discard)) return
        val docs = s.documents.filterNot { it.id == id }
        change(s.copy(documents = docs, selectedId = if (s.selectedId == id) docs.lastOrNull()?.id else s.selectedId))
    }

    fun prepareCopy(documentId: String, onPicker: (CopyPickerRequest) -> Unit) {
        if (!ready() || state.value.busy || state.value.session.pendingCopy != null) return
        if(state.value.session.selectedId!=documentId) {
            mutable.value=state.value.copy(status="Selected tab changed; request Save a Copy again."); return
        }
        launchCopy(onPicker) { CopyProtocol.start(it,documentId,UUID.randomUUID().toString()) }
    }

    fun retryCopy(expectedId: String?, onPicker: (CopyPickerRequest) -> Unit) {
        if(!ready() || state.value.busy) return
        launchCopy(onPicker) { CopyProtocol.retry(it,expectedId,UUID.randomUUID().toString()) }
    }

    private fun launchCopy(onPicker: (CopyPickerRequest)->Unit, transition: (EditorSession)->EditorSession) {
        mutable.value=state.value.copy(busy=true)
        viewModelScope.launch {
            var id: String?=null
            try {
                val current=state.value.session
                val next=transition(current)
                if(next===current) return@launch
                change(next)
                val pending=next.pendingCopy ?: return@launch
                id=requireNotNull(pending.requestId)
                if(!checkpointCopy(id,CopyStage.AWAITING_PICKER)) {
                    change(CopyProtocol.uncertain(state.value.session,id))
                    mutable.value=state.value.copy(status="Copy request checkpoint unconfirmed. No picker launched; frozen payload retained.")
                    return@launch
                }
                val base=pending.name.substringBeforeLast('.').ifBlank { "document" }
                val prefix=base.substring(0,base.offsetByCodePoints(0,minOf(80,base.codePointCount(0,base.length))))
                    .map { if(it=='/' || it=='\\' || it.isISOControl()) '_' else it }.joinToString("")
                onPicker(CopyPickerRequest(id,"$prefix-copy.txt"))
                mutable.value=state.value.copy(status="Copy attempt launched. Only its matching result can authorize the frozen bytes.")
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                id?.let { change(CopyProtocol.uncertain(state.value.session,it)) }
                mutable.value=state.value.copy(status="Picker launch unconfirmed (${e.javaClass.simpleName}); draft and frozen payload retained. Retry requires a new attempt.")
            } finally { mutable.value=state.value.copy(busy=false) }
        }
    }

    /** Expected identity comes from the rendered control, not whatever request is current later. */
    fun cancelCopy(expectedId: String?) {
        if(!ready() || state.value.busy) return
        val next=CopyProtocol.abandon(state.value.session,expectedId)
        if(next===state.value.session) return
        change(next)
        mutable.value=state.value.copy(status="Attempt abandoned in memory. Draft retained; no destination was deleted. Local checkpoint is pending.")
    }

    /** Disk barrier specifically verifies this request/stage, not an unrelated concurrent checkpoint. */
    private suspend fun checkpointCopy(id: String, stage: CopyStage): Boolean {
        return try {
            disk.withLock {
                val snapshot=state.value.session
                val pending=snapshot.pendingCopy
                require(pending != null && pending.requestId==id && pending.stage==stage)
                withContext(Dispatchers.IO) { CheckedAtomicFile.write(file,snapshot,SessionCodec::write,SessionCodec::read) }
                state.value.session.pendingCopy?.let { it.requestId==id && it.stage==stage } == true
            }
        } catch(e: Exception) {
            if(e is CancellationException) throw e
            false
        }
    }

    fun completeCopy(result: CopyPickerResult?) {
        if(!copyResults.trySend(result).isSuccess)
            mutable.value=state.value.copy(status="Extra picker result refused: result queue is full. No write is authorized by that result; inspect pending state and retry explicitly if needed.")
    }

    private suspend fun handleCopyResult(result: CopyPickerResult?) {
        kotlinx.coroutines.coroutineScope {
            // Registry delivery may precede asynchronous recovery. Never guess from the current tab.
            while(state.value.loading) delay(10)
            if(!ready()) return@coroutineScope
            if(result==null) {
                mutable.value=state.value.copy(status="Unattributed picker result ignored. No destination opened; any pending payload is retained.")
                return@coroutineScope
            }
            // Serialize with launch/open, but re-check identity after waiting. A late callback is not dropped onto a new job.
            while(state.value.busy) delay(10)
            val current=state.value.session
            val pending=current.pendingCopy
            if(pending==null || pending.requestId!=result.requestId || pending.stage!=CopyStage.AWAITING_PICKER) {
                mutable.value=state.value.copy(status="Stale or duplicate picker result ignored. No destination opened.")
                return@coroutineScope
            }
            mutable.value=state.value.copy(busy=true)
            try {
                if(result.outcome==CopyPickerOutcome.FAILED) {
                    change(CopyProtocol.uncertain(current,result.requestId))
                    mutable.value=state.value.copy(status="Picker failed. Frozen payload retained; retry with a fresh attempt.")
                    flush(); return@coroutineScope
                }
                val destination=if(result.outcome==CopyPickerOutcome.SELECTED) requireNotNull(result.uri).toString() else null
                val received=CopyProtocol.receive(current,result.requestId,destination)
                change(received)
                if(received.pendingCopy==null) {
                    mutable.value=state.value.copy(status="Matching picker canceled. Draft retained; no copy reported.")
                    flush(); return@coroutineScope
                }
                val reserved=CopyProtocol.reserve(received,result.requestId)
                check(reserved!==received && reserved.pendingCopy?.stage==CopyStage.WRITE_RESERVED)
                change(reserved)
                if(!checkpointCopy(result.requestId,CopyStage.WRITE_RESERVED)) {
                    change(CopyProtocol.uncertain(state.value.session,result.requestId))
                    mutable.value=state.value.copy(status="Write reservation checkpoint unconfirmed. Destination was not opened for writing. Retry only to a new destination.")
                    return@coroutineScope
                }
                val frozen=requireNotNull(reserved.pendingCopy)
                val uri=Uri.parse(requireNotNull(frozen.destinationUri))
                val forbidden=(reserved.documents.mapNotNull { it.sourceUri } + listOfNotNull(frozen.sourceUri) +
                    reserved.copyReceipts.mapNotNull { it.destinationUri }).distinct().map(Uri::parse)
                mutable.value=state.value.copy(status="Reserved attempt: checking aliases, writing frozen bytes and verifying readback…")
                val evidence=withContext(Dispatchers.IO) {
                    rejectKnownAliases(uri,forbidden)
                    // ACTION_CREATE_DOCUMENT may return an existing item. Metadata size is not trusted.
                    // This read observation is not an exclusive lease; concurrent/provider alias risks remain.
                    VerifiedCopy.requireEmpty { requireNotNull(resolver.openInputStream(uri)) }
                    VerifiedCopy.write(TextEncodingCodec.encode(frozen.content,frozen.encoding),
                        output={ requireNotNull(resolver.openOutputStream(uri,"wt")) },
                        reopen={ requireNotNull(resolver.openInputStream(uri)) },
                        binding=VerifiedCopy.Binding(result.requestId,requireNotNull(frozen.destinationUri)))
                }
                change(CopyProtocol.verified(state.value.session,result.requestId,evidence))
                val recorded=flush()
                mutable.value=state.value.copy(status=if(recorded)
                    "Copy bytes read back and receipt checkpoint verified. Later edits remain unsaved. Provider alias behavior, cloud sync and physical durability are not verified."
                    else "Copy bytes matched readback, but receipt checkpoint is unconfirmed. Inspect destination; recovery may require review. Do not retry over it.")
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                change(CopyProtocol.uncertain(state.value.session,result.requestId))
                mutable.value=state.value.copy(status=if(e is VerifiedCopy.DestinationNotEmptyException)
                    "Refused a nonempty destination before opening output. Existing data was not deleted. Choose a new file; frozen payload retained."
                    else "Copy unverified (${e.javaClass.simpleName}). Draft/frozen payload retained. A partial destination may exist; inspect it before retrying to a new file. Do not treat uncertain data as disposable.")
                flush()
            } finally { mutable.value=state.value.copy(busy=false) }
        }
    }

    private fun rejectKnownAliases(target: Uri, forbidden: List<Uri>) {
        require(target.scheme?.lowercase(java.util.Locale.ROOT)=="content" && !target.authority.isNullOrBlank())
        fun normalized(uri: Uri)=uri.normalizeScheme().buildUpon().clearQuery().fragment(null).build()
        fun same(a: Uri,b: Uri): Boolean {
            if(normalized(a)==normalized(b)) return true // Conservative: query differences do not prove distinct storage.
            // [U] verify on API 34/36/37 and providers: document IDs are scoped by authority, not tree-grant spelling.
            return a.authority==b.authority && DocumentsContract.isDocumentUri(getApplication<Application>(),a) &&
                DocumentsContract.isDocumentUri(getApplication<Application>(),b) && DocumentsContract.getDocumentId(a)==DocumentsContract.getDocumentId(b)
        }
        // Canonicalization is additional evidence, not proof against arbitrary provider aliases or TOCTOU.
        val canonicalTarget=runCatching { resolver.canonicalize(target) }.getOrNull() ?: target
        forbidden.forEach { original ->
            require(!same(target,original)) { "Destination aliases a source or recent copy" }
            val canonicalOriginal=runCatching { resolver.canonicalize(original) }.getOrNull() ?: original
            require(!same(canonicalTarget,canonicalOriginal)) { "Canonical destination aliases a source or recent copy" }
        }
    }
}

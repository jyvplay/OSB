// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.fusion.documents.CopyProtocol
import dev.fusion.documents.FrozenExport
import dev.fusion.documents.TextDocument
import dev.fusion.documents.VerifiedCopy
import dev.fusion.editor.CopyDestinationContract
import dev.fusion.editor.CopyPickerOutcome
import dev.fusion.editor.CopyPickerRequest
import dev.fusion.editor.CopyPickerResult
import dev.fusion.ooxml.OfficeSheetDraft
import dev.fusion.ooxml.PlainWriterDocx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/** Explicit, in-memory Save-a-Copy attempts. A process restart never replays provider I/O. */
class OfficeExportViewModel(application: Application) : AndroidViewModel(application) {
    data class State(val busy: Boolean = false, val status: String = "")
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var attempt: FrozenExport? = null
    private val recentDestinations = ArrayList<String>()
    private val resolver get() = getApplication<Application>().contentResolver

    fun writer(doc: TextDocument, forbidden: List<String>, openPicker: (CopyPickerRequest) -> Unit) {
        val base = doc.name.substringBeforeLast('.', doc.name).map {
            if (it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_') it else '_'
        }.joinToString("").take(64).trim('_').ifEmpty { "document" }
        prepare("$base-copy.docx", CopyDestinationContract.DOCX_MIME, forbidden, openPicker) {
            PlainWriterDocx.write(doc.content)
        }
    }

    fun sheet(draft: OfficeSheetDraft.Draft, forbidden: List<String>, openPicker: (CopyPickerRequest) -> Unit) {
        prepare("sheet-copy.xlsx", CopyDestinationContract.XLSX_MIME, forbidden, openPicker) {
            OfficeSheetDraft.xlsx(draft)
        }
    }

    private fun prepare(name: String, mime: String, forbidden: List<String>, openPicker: (CopyPickerRequest) -> Unit, encode: () -> ByteArray) {
        if (mutable.value.busy) return
        // Capture source identities before dispatch; callers cannot mutate the list while encoding.
        val sources = forbidden.toList()
        mutable.value = State(true, "Preparing a frozen export copy locally…")
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) { encode() }
                val id = UUID.randomUUID().toString() + "-1"
                attempt = FrozenExport(id, bytes, sources)
                mutable.value = State(true, "Choose a new destination. This frozen copy does not include later edits.")
                openPicker(CopyPickerRequest(id, name, mime))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                attempt = null
                mutable.value = State(false, "Export preparation or picker launch failed (${e.message ?: e.javaClass.simpleName}). The draft is unchanged.")
            }
        }
    }

    /** Current editor sources are rechecked too: another source might have been opened while the picker was visible. */
    fun complete(result: CopyPickerResult?, currentSources: List<String>, sourcesKnown: Boolean = true) {
        val pending = attempt ?: return // No attempt survives process death; never resume a write by guessing.
        if (result == null) {
            pending.cancel(pending.requestId)
            attempt = null
            mutable.value = State(false, "Unattributed picker result ignored. No output opened; retry explicitly to a new destination.")
            return
        }
        if (result.requestId != pending.requestId) return // stale results cannot consume a newer attempt
        if (result.outcome != CopyPickerOutcome.SELECTED || result.uri == null) {
            pending.cancel(result.requestId)
            attempt = null
            mutable.value = State(false, "Export canceled or picker failed. The draft remains unchanged.")
            return
        }
        if (!sourcesKnown) {
            pending.cancel(result.requestId)
            attempt = null
            mutable.value = State(false, "Editor source identities are unavailable or changing; no output was opened. Retry only after recovery and any editor operation finish.")
            return
        }
        val bytes = pending.claim(result.requestId) ?: return
        attempt = null
        val uri = result.uri
        val forbidden = (pending.forbiddenUris + currentSources + recentDestinations).distinct()
        mutable.value = State(true, "Checking destination, writing the frozen copy and verifying readback…")
        viewModelScope.launch {
            try {
                require(CopyProtocol.validDestination(uri.toString())) { "Destination is not a valid content URI" }
                // Record even an uncertain destination for this process before I/O.
                recentDestinations.add(uri.toString())
                if (recentDestinations.size > 32) recentDestinations.removeAt(0)
                withContext(Dispatchers.IO) {
                    rejectKnownAliases(uri, forbidden.map(Uri::parse))
                    VerifiedCopy.requireEmpty { requireNotNull(resolver.openInputStream(uri)) { "Cannot read destination" } }
                    VerifiedCopy.write(bytes,
                        output = { requireNotNull(resolver.openOutputStream(uri, "wt")) { "Cannot write destination" } },
                        reopen = { requireNotNull(resolver.openInputStream(uri)) { "Cannot reopen destination" } },
                        binding = VerifiedCopy.Binding(result.requestId, uri.toString()))
                }
                mutable.value = State(false, "Copy matched byte-for-byte readback. The draft is unchanged; provider atomicity, sync and power-loss durability are not established.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = State(false, if (e is VerifiedCopy.DestinationNotEmptyException)
                    "Destination was nonempty; output was not opened. Draft unchanged; choose a new file."
                else "Export unverified (${e.javaClass.simpleName}). Draft unchanged; a partial destination may exist. Inspect it and retry only to a new file.")
            }
        }
    }

    private fun rejectKnownAliases(target: Uri, forbidden: List<Uri>) {
        require(target.scheme?.lowercase(Locale.ROOT) == "content" && !target.authority.isNullOrBlank())
        fun normalized(uri: Uri) = uri.normalizeScheme().buildUpon().clearQuery().fragment(null).build()
        fun same(a: Uri, b: Uri): Boolean {
            if (normalized(a) == normalized(b)) return true
            return a.authority == b.authority && DocumentsContract.isDocumentUri(getApplication<Application>(), a) &&
                DocumentsContract.isDocumentUri(getApplication<Application>(), b) && DocumentsContract.getDocumentId(a) == DocumentsContract.getDocumentId(b)
        }
        val canonicalTarget = runCatching { resolver.canonicalize(target) }.getOrNull() ?: target
        for (original in forbidden) {
            require(!same(target, original)) { "Destination aliases an open source or prior copy" }
            val canonicalOriginal = runCatching { resolver.canonicalize(original) }.getOrNull() ?: original
            require(!same(canonicalTarget, canonicalOriginal)) { "Canonical destination aliases an open source or prior copy" }
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.fusion.ooxml.OfficeSheetCodec
import dev.fusion.ooxml.OfficeSheetDraft
import dev.fusion.ooxml.OfficeSheetTextGrid
import dev.fusion.storage.CheckedAtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** One private single-sheet draft. Checkpoints are not a backup or an external save. */
class SheetViewModel(application: Application) : AndroidViewModel(application) {
    data class State(
        val data: OfficeSheetDraft.Calculated = OfficeSheetDraft.calculate(OfficeSheetDraft.empty()),
        val loading: Boolean = true,
        val busy: Boolean = false,
        val recoveryBlocked: Boolean = false,
        val status: String = "Loading local sheet recovery…",
        val checkpoint: String = "",
        val undoDepth: Int = 0,
        val redoDepth: Int = 0,
        val selected: String = "A1",
        val inputKind: OfficeSheetDraft.Kind = OfficeSheetDraft.Kind.TEXT,
        val input: String = "",
        val textGridPreview: OfficeSheetTextGrid.Preview? = null,
        val textGridError: String = "",
    ) {
        val unapplied: Boolean get() {
            val cell = data.draft.cells[selected]
            return input != (cell?.input ?: "") || inputKind != (cell?.kind ?: OfficeSheetDraft.Kind.TEXT)
        }
    }

    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val file = AtomicFile(File(application.noBackupFilesDir, "sheet-draft-v1.bin"))
    private val disk = Mutex()
    private val checkpoints = Channel<Unit>(Channel.CONFLATED)
    private val undo = ArrayDeque<Map<String, OfficeSheetDraft.Cell>>()
    private val redo = ArrayDeque<Map<String, OfficeSheetDraft.Cell>>()

    init {
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val base = file.baseFile
                    if (base.exists() || File(base.path + ".bak").exists() || File(base.path + ".new").exists()) {
                        OfficeSheetDraft.calculate(file.openRead().use(OfficeSheetCodec::read))
                    } else OfficeSheetDraft.calculate(OfficeSheetDraft.empty())
                }
                val selected = data.draft.cells["A1"]
                mutable.value = State(data = data, loading = false, inputKind = selected?.kind ?: OfficeSheetDraft.Kind.TEXT,
                    input = selected?.input ?: "",
                    status = if (data.draft.cells.isEmpty()) "Start with a blank local sheet. Apply each cell before exporting." else "Recovered a local draft. This is not an external save or a backup.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = State(loading = false, recoveryBlocked = true,
                    status = "Sheet recovery could not be read (${e.javaClass.simpleName}). Original bytes retained; no new checkpoint will overwrite them.")
            }
            for (ignored in checkpoints) { delay(500); flush() }
        }
    }

    private fun ready(): Boolean = !state.value.loading && !state.value.busy && !state.value.recoveryBlocked && state.value.textGridPreview == null

    fun updateInput(input: String) {
        if (ready() && input.length <= OfficeSheetDraft.MAX_CELL_BYTES) mutable.value = state.value.copy(input = input)
    }

    fun updateKind(kind: OfficeSheetDraft.Kind) {
        if (ready()) mutable.value = state.value.copy(inputKind = kind)
    }

    /** Switching away from an uncommitted input requires an explicit user decision. */
    fun select(raw: String, discardInput: Boolean = false) {
        if (!ready() || (state.value.unapplied && !discardInput)) return
        val ref = OfficeSheetDraft.address(raw)
        val cell = state.value.data.draft.cells[ref]
        mutable.value = state.value.copy(selected = ref, inputKind = cell?.kind ?: OfficeSheetDraft.Kind.TEXT, input = cell?.input ?: "")
    }

    fun apply(ref: String, kind: OfficeSheetDraft.Kind, input: String) {
        if (ref != state.value.selected || kind != state.value.inputKind || input != state.value.input) return
        transition("Applied $ref") { before -> OfficeSheetDraft.replace(before, ref, OfficeSheetDraft.Cell(kind, input)) }
    }

    fun clear(ref: String) {
        if (ref != state.value.selected || state.value.unapplied) return
        transition("Cleared $ref") { before -> OfficeSheetDraft.replace(before, ref, null) }
    }

    private fun transition(message: String, operation: (OfficeSheetDraft.Draft) -> OfficeSheetDraft.Calculated) {
        if (!ready()) return
        val before = state.value.data.draft
        mutable.value = state.value.copy(busy = true, status = "Checking cell and recalculating…")
        viewModelScope.launch {
            try {
                val after = withContext(Dispatchers.Default) { operation(before) }
                if (after.draft !== before) {
                    push(undo, before.cells)
                    redo.clear()
                    val selectedCell = after.draft.cells[state.value.selected]
                    mutable.value = state.value.copy(data = after, busy = false, status = "$message. Local recovery is pending; export is separate.",
                        inputKind = selectedCell?.kind ?: OfficeSheetDraft.Kind.TEXT, input = selectedCell?.input ?: "",
                        checkpoint = "New changes have not yet been checkpointed.", undoDepth = undo.size, redoDepth = 0)
                    checkpoints.trySend(Unit)
                } else mutable.value = state.value.copy(busy = false, status = "Cell unchanged.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = state.value.copy(busy = false, status = "Edit refused: ${e.message ?: e.javaClass.simpleName}. Previous sheet retained.")
            }
        }
    }

    /** Parse and recalculate off the UI thread. The review holds no raw input string. */
    fun previewTextGrid(raw: String) {
        if (!ready() || state.value.unapplied) return
        val before = state.value.data.draft
        val anchor = state.value.selected
        mutable.value = state.value.copy(busy = true, textGridError = "", status = "Checking text grid…")
        viewModelScope.launch {
            try {
                val plan = withContext(Dispatchers.Default) { OfficeSheetTextGrid.prepare(before, anchor, raw) }
                val current = state.value
                if (plan.isCurrent(current.data.draft, current.selected) && !current.unapplied) {
                    mutable.value = current.copy(busy = false, textGridPreview = plan, status = "Review the text-grid changes before applying.")
                } else {
                    mutable.value = current.copy(busy = false, textGridError = "The selected cell or draft changed; preview again.")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = state.value.copy(busy = false,
                    textGridError = "Text grid refused: ${e.message ?: e.javaClass.simpleName}. Sheet unchanged.",
                    status = "Text grid was not applied.")
            }
        }
    }

    /** Confirmation is one revision and one undo step; a stale preview is never applied. */
    fun acceptTextGrid() {
        val current = state.value
        val plan = current.textGridPreview ?: return
        if (current.loading || current.busy || current.recoveryBlocked || current.unapplied ||
            !plan.isCurrent(current.data.draft, current.selected)) {
            mutable.value = current.copy(textGridPreview = null, status = "Text-grid preview became stale; nothing applied.")
            return
        }
        if (plan.changed == 0) {
            mutable.value = current.copy(textGridPreview = null, status = "Text grid matches the existing cells; nothing changed.")
            return
        }
        push(undo, plan.original.cells)
        redo.clear()
        val selectedCell = plan.calculated.draft.cells[current.selected]
        mutable.value = current.copy(data = plan.calculated, textGridPreview = null,
            inputKind = selectedCell?.kind ?: OfficeSheetDraft.Kind.TEXT, input = selectedCell?.input ?: "",
            status = "Applied ${plan.changed} text-grid changes. Local recovery is pending; export is separate.",
            checkpoint = "New changes have not yet been checkpointed.", undoDepth = undo.size, redoDepth = 0)
        checkpoints.trySend(Unit)
    }

    fun cancelTextGridPreview() {
        if (state.value.textGridPreview != null) mutable.value = state.value.copy(textGridPreview = null, status = "Text-grid preview cancelled; sheet unchanged.")
    }

    fun clearTextGridError() {
        if (ready()) mutable.value = state.value.copy(textGridError = "")
    }

    fun undo() = history(undo, redo, "Undo")
    fun redo() = history(redo, undo, "Redo")

    private fun history(from: ArrayDeque<Map<String, OfficeSheetDraft.Cell>>, to: ArrayDeque<Map<String, OfficeSheetDraft.Cell>>, label: String) {
        if (!ready() || state.value.unapplied || from.isEmpty()) return
        val before = state.value.data.draft
        val target = from.last()
        mutable.value = state.value.copy(busy = true, status = "$label: recalculating…")
        viewModelScope.launch {
            try {
                require(before.revision < Long.MAX_VALUE) { "Sheet revision exhausted" }
                val after = withContext(Dispatchers.Default) { OfficeSheetDraft.restore(before.revision + 1, target) }
                from.removeLast()
                push(to, before.cells)
                val selectedCell = after.draft.cells[state.value.selected]
                mutable.value = state.value.copy(data = after, busy = false, status = "$label applied. Undo history is not restored after process death.",
                    inputKind = selectedCell?.kind ?: OfficeSheetDraft.Kind.TEXT, input = selectedCell?.input ?: "",
                    checkpoint = "New changes have not yet been checkpointed.", undoDepth = undo.size, redoDepth = redo.size)
                checkpoints.trySend(Unit)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = state.value.copy(busy = false, status = "$label refused: ${e.message ?: e.javaClass.simpleName}. Sheet unchanged.")
            }
        }
    }

    private fun push(stack: ArrayDeque<Map<String, OfficeSheetDraft.Cell>>, value: Map<String, OfficeSheetDraft.Cell>) {
        if (stack.size == 16) stack.removeFirst()
        stack.addLast(value)
    }

    suspend fun flush(): Boolean {
        if (state.value.loading || state.value.recoveryBlocked) return false
        return try {
            disk.withLock {
                val snapshot = state.value.data.draft
                withContext(Dispatchers.IO) { CheckedAtomicFile.write(file, snapshot, OfficeSheetCodec::write, OfficeSheetCodec::read) }
                mutable.value = state.value.copy(checkpoint = if (state.value.data.draft == snapshot)
                    "Local recovery checkpoint read back. It is not a backup or external save."
                else "An older checkpoint was read back; newer changes are still pending.")
            }
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mutable.value = state.value.copy(checkpoint = "Local checkpoint failed (${e.javaClass.simpleName}); keep the app open and export a copy.")
            false
        }
    }
}

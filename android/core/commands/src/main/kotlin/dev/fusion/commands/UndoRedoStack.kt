// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Clean-room bounded Undo/Redo history stack with typing-burst coalescing (`UndoRedoStack`).
 */
class UndoRedoStack<S>(
    initialState: S,
    val maxEntries: Int = 100,
    val coalesceWindowMs: Long = 500L,
) {
    companion object {
        const val SCOPE =
            "Bounded Undo/Redo state history with typing-burst coalescing window and branch truncation on new edit; persistent disk snapshot out of scope."
    }

    private data class Frame<S>(val state: S, val coalesceKey: String?, val timestampMs: Long)

    private val undoFrames = ArrayDeque<Frame<S>>()
    private val redoFrames = ArrayDeque<Frame<S>>()

    var currentState: S = initialState
        private set
    private var lastKey: String? = null
    private var lastTimestampMs: Long = Long.MIN_VALUE / 2

    val canUndo: Boolean get() = undoFrames.isNotEmpty()
    val canRedo: Boolean get() = redoFrames.isNotEmpty()
    val undoDepth: Int get() = undoFrames.size

    fun pushState(nextState: S, nowMs: Long, coalesceKey: String? = null) {
        redoFrames.clear()
        val shouldCoalesce = coalesceKey != null &&
            coalesceKey == lastKey &&
            undoFrames.isNotEmpty() &&
            (nowMs - lastTimestampMs) in 0..coalesceWindowMs
        if (!shouldCoalesce) {
            undoFrames.addLast(Frame(currentState, coalesceKey, nowMs))
            while (undoFrames.size > maxEntries) undoFrames.removeFirst()
        }
        currentState = nextState
        lastKey = coalesceKey
        lastTimestampMs = nowMs
    }

    fun undo(): S? {
        if (undoFrames.isEmpty()) return null
        val prev = undoFrames.removeLast()
        redoFrames.addLast(Frame(currentState, null, 0L))
        currentState = prev.state
        lastKey = null
        return currentState
    }

    fun redo(): S? {
        if (redoFrames.isEmpty()) return null
        val next = redoFrames.removeLast()
        undoFrames.addLast(Frame(currentState, null, 0L))
        currentState = next.state
        lastKey = null
        return currentState
    }

    data class UndoReceipt(
        val id: String,
        val message: String,
        val undoAvailable: Boolean,
        val undoCommandId: String?,
        val createdAtMs: Long,
    )

    /** Self-contained undo receipt builder (standalone-compilable in R47+ without external deck imports). */
    fun createUndoReceipt(id: String, summaryMessage: String, nowMs: Long): UndoReceipt {
        require(id.isNotBlank() && summaryMessage.isNotBlank()) { "UndoReceipt id and message must be non-blank" }
        return UndoReceipt(
            id = id,
            message = summaryMessage,
            undoAvailable = canUndo,
            undoCommandId = if (canUndo) "undo:$id" else null,
            createdAtMs = nowMs,
        )
    }
}

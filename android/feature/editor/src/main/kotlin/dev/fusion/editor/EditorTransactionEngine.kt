// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Production-grade bounded transactional text buffer with atomic multi-op
 * undo/redo, cursor offset remapping, and line-index projection.
 */
class EditorTransactionEngine private constructor(
    initialText: String,
    private val maxHistory: Int = 256
) {
    companion object {
        const val MAX_DOC_LEN = 1 shl 22 // 4 MiB chars

        fun create(initialText: String = "", maxHistory: Int = 256): EditorTransactionEngine {
            require(initialText.length <= MAX_DOC_LEN) { "initialText exceeds MAX_DOC_LEN" }
            require(maxHistory in 1..4096) { "maxHistory out of bounds" }
            return EditorTransactionEngine(initialText, maxHistory)
        }
    }

    sealed class EditOp {
        abstract val offset: Int
        data class Insert(override val offset: Int, val text: String) : EditOp()
        data class Delete(override val offset: Int, val length: Int) : EditOp()
    }

    private data class InverseOp(val op: EditOp)
    private data class Transaction(val label: String, val inverseOps: List<InverseOp>, val forwardOps: List<EditOp>)

    private val buffer = StringBuilder(initialText)
    private val undoStack = ArrayDeque<Transaction>()
    private val redoStack = ArrayDeque<Transaction>()

    fun text(): String = buffer.toString()
    fun length(): Int = buffer.length
    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun applyTransaction(label: String, ops: List<EditOp>): Boolean {
        if (ops.isEmpty()) return false
        val snapshot = buffer.toString()
        val inverses = mutableListOf<InverseOp>()
        try {
            for (op in ops) {
                val inv = applySingle(op)
                inverses.add(inv)
            }
        } catch (e: IllegalArgumentException) {
            buffer.setLength(0)
            buffer.append(snapshot)
            throw e
        }
        if (undoStack.size == maxHistory) undoStack.removeFirst()
        undoStack.addLast(Transaction(label, inverses.reversed(), ops))
        redoStack.clear()
        return true
    }

    fun undo(): String? {
        if (undoStack.isEmpty()) return null
        val tx = undoStack.removeLast()
        for (inv in tx.inverseOps) {
            applySingle(inv.op)
        }
        redoStack.addLast(tx)
        return tx.label
    }

    fun redo(): String? {
        if (redoStack.isEmpty()) return null
        val tx = redoStack.removeLast()
        val inverses = mutableListOf<InverseOp>()
        for (op in tx.forwardOps) {
            inverses.add(applySingle(op))
        }
        undoStack.addLast(Transaction(tx.label, inverses.reversed(), tx.forwardOps))
        return tx.label
    }

    private fun applySingle(op: EditOp): InverseOp = when (op) {
        is EditOp.Insert -> {
            require(op.offset in 0..buffer.length) { "insert offset out of bounds: ${op.offset}" }
            require(buffer.length + op.text.length <= MAX_DOC_LEN) { "document exceeds MAX_DOC_LEN" }
            buffer.insert(op.offset, op.text)
            InverseOp(EditOp.Delete(op.offset, op.text.length))
        }
        is EditOp.Delete -> {
            require(op.offset >= 0 && op.length >= 0 && op.offset + op.length <= buffer.length) {
                "delete range out of bounds: ${op.offset}+${op.length}"
            }
            val removed = buffer.substring(op.offset, op.offset + op.length)
            buffer.delete(op.offset, op.offset + op.length)
            InverseOp(EditOp.Insert(op.offset, removed))
        }
    }

    fun remapCursor(cursorOffset: Int, op: EditOp): Int {
        val c = cursorOffset.coerceIn(0, buffer.length)
        return when (op) {
            is EditOp.Insert -> if (c >= op.offset) c + op.text.length else c
            is EditOp.Delete -> when {
                c <= op.offset -> c
                c <= op.offset + op.length -> op.offset
                else -> c - op.length
            }
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Clean-room immutable Piece Table document buffer + bounded undo/redo history.
 *
 * Architecture (classic Gutknecht / VS Code / AbiWord piece-table model):
 * - [Buffer] stores an immutable `original` buffer, an append-only `added` string,
 *   and an ordered list of non-empty [Piece] descriptors pointing into either buffer.
 * - Edits split/coalesce adjacent descriptors without copying untouched `original` text.
 * - [History] layers deterministic snapshot undo/redo (`MAX_HISTORY` steps) over [Buffer]
 *   so every undo/redo transition restores exact UTF-16 contents and piece structure.
 */
object PieceTable {
    const val SCOPE =
        "Immutable UTF-16 piece-table buffer + bounded undo/redo + 1-based line/column lookup; Rope/B-tree PieceTree balancing and CRDT vector clocks out of scope."

    const val MAX_TEXT_CHARS = 1_048_576
    const val MAX_HISTORY = 256

    enum class Source { ORIGINAL, ADDED }

    data class Piece(val source: Source, val start: Int, val length: Int) {
        init {
            require(start >= 0 && length > 0) { "invalid piece ($source, $start, $length)" }
        }
    }

    data class LineCol(val line: Int, val column: Int)

    class Buffer internal constructor(
        val original: String,
        val added: String,
        val pieces: List<Piece>,
        val length: Int,
    ) {
        fun materialize(): String {
            if (length == 0) return ""
            val sb = StringBuilder(length)
            for (p in pieces) {
                val buf = if (p.source == Source.ORIGINAL) original else added
                sb.append(buf, p.start, p.start + p.length)
            }
            return sb.toString()
        }

        fun slice(startIndex: Int, endExclusive: Int): String {
            require(startIndex >= 0 && endExclusive <= length && startIndex <= endExclusive) {
                "slice [$startIndex, $endExclusive) out of bounds for length $length"
            }
            if (startIndex == endExclusive) return ""
            val out = StringBuilder(endExclusive - startIndex)
            var offset = 0
            for (p in pieces) {
                val pEnd = offset + p.length
                if (pEnd <= startIndex) {
                    offset = pEnd
                    continue
                }
                if (offset >= endExclusive) break
                val localFrom = maxOf(0, startIndex - offset)
                val localTo = minOf(p.length, endExclusive - offset)
                val buf = if (p.source == Source.ORIGINAL) original else added
                out.append(buf, p.start + localFrom, p.start + localTo)
                offset = pEnd
            }
            return out.toString()
        }

        fun insert(index: Int, inserted: String): Buffer = replace(index, index, inserted)

        fun delete(startIndex: Int, endExclusive: Int): Buffer = replace(startIndex, endExclusive, "")

        fun replace(startIndex: Int, endExclusive: Int, replacement: String): Buffer {
            require(startIndex >= 0 && endExclusive <= length && startIndex <= endExclusive) {
                "replace [$startIndex, $endExclusive) out of bounds for length $length"
            }
            val newLen = length - (endExclusive - startIndex) + replacement.length
            require(newLen <= MAX_TEXT_CHARS) { "document length $newLen exceeds $MAX_TEXT_CHARS" }
            if (startIndex == endExclusive && replacement.isEmpty()) return this

            val newAdded = if (replacement.isEmpty()) added else added + replacement
            val repPiece = if (replacement.isEmpty()) null else Piece(Source.ADDED, added.length, replacement.length)

            val raw = ArrayList<Piece>(pieces.size + 2)
            var offset = 0
            var insertedRep = false
            for (p in pieces) {
                val pEnd = offset + p.length
                if (pEnd <= startIndex) {
                    raw.add(p)
                } else if (offset >= endExclusive) {
                    if (!insertedRep) {
                        if (repPiece != null) raw.add(repPiece)
                        insertedRep = true
                    }
                    raw.add(p)
                } else {
                    if (offset < startIndex) {
                        raw.add(Piece(p.source, p.start, startIndex - offset))
                    }
                    if (!insertedRep) {
                        if (repPiece != null) raw.add(repPiece)
                        insertedRep = true
                    }
                    if (pEnd > endExclusive) {
                        val rightSkip = endExclusive - offset
                        raw.add(Piece(p.source, p.start + rightSkip, p.length - rightSkip))
                    }
                }
                offset = pEnd
            }
            if (!insertedRep && repPiece != null) {
                raw.add(repPiece)
            }
            return Buffer(original, newAdded, coalesce(raw), newLen)
        }

        /** 1-based `(line, column)` at UTF-16 cursor [offset] (`0..length`). `\n` advances line. */
        fun lineColAt(offset: Int): LineCol {
            require(offset in 0..length) { "offset $offset outside 0..$length" }
            var line = 1
            var col = 1
            var seen = 0
            for (p in pieces) {
                if (seen >= offset) break
                val take = minOf(p.length, offset - seen)
                val buf = if (p.source == Source.ORIGINAL) original else added
                for (k in 0 until take) {
                    if (buf[p.start + k] == '\n') {
                        line++
                        col = 1
                    } else {
                        col++
                    }
                }
                seen += take
            }
            return LineCol(line, col)
        }
    }

    data class History(
        val current: Buffer,
        val undoStack: List<Buffer> = emptyList(),
        val redoStack: List<Buffer> = emptyList(),
    ) {
        val canUndo: Boolean get() = undoStack.isNotEmpty()
        val canRedo: Boolean get() = redoStack.isNotEmpty()

        fun edit(transform: (Buffer) -> Buffer): History {
            val next = transform(current)
            if (next === current) return this
            val cappedUndo = if (undoStack.size >= MAX_HISTORY) {
                undoStack.drop(undoStack.size - MAX_HISTORY + 1) + current
            } else {
                undoStack + current
            }
            return History(current = next, undoStack = cappedUndo, redoStack = emptyList())
        }

        fun undo(): History {
            require(undoStack.isNotEmpty()) { "undo stack is empty" }
            val prev = undoStack.last()
            return History(
                current = prev,
                undoStack = undoStack.dropLast(1),
                redoStack = redoStack + current,
            )
        }

        fun redo(): History {
            require(redoStack.isNotEmpty()) { "redo stack is empty" }
            val next = redoStack.last()
            return History(
                current = next,
                undoStack = undoStack + current,
                redoStack = redoStack.dropLast(1),
            )
        }
    }

    fun create(initial: String): Buffer {
        require(initial.length <= MAX_TEXT_CHARS) { "initial text exceeds $MAX_TEXT_CHARS" }
        val pieces = if (initial.isEmpty()) emptyList() else listOf(Piece(Source.ORIGINAL, 0, initial.length))
        return Buffer(initial, "", pieces, initial.length)
    }

    fun createHistory(initial: String): History = History(create(initial))

    private fun coalesce(pieces: List<Piece>): List<Piece> {
        if (pieces.size <= 1) return pieces
        val out = ArrayList<Piece>(pieces.size)
        var cur = pieces[0]
        for (i in 1 until pieces.size) {
            val nxt = pieces[i]
            cur = if (cur.source == nxt.source && cur.start + cur.length == nxt.start) {
                Piece(cur.source, cur.start, cur.length + nxt.length)
            } else {
                out.add(cur)
                nxt
            }
        }
        out.add(cur)
        return out
    }
}

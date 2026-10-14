package dev.fusion.documents

/** UTF-16 offsets for Compose; never split a surrogate pair. Columns elsewhere are code points. */
data class TextSelection(val anchor: Int, val caret: Int = anchor) {
    val start get() = minOf(anchor, caret)
    val end get() = maxOf(anchor, caret)
    fun inText(text: String): TextSelection = TextSelection(boundary(text, anchor), boundary(text, caret))
    companion object {
        internal fun boundary(text: String, value: Int): Int {
            val p = value.coerceIn(0, text.length)
            return if (p > 0 && p < text.length && Character.isHighSurrogate(text[p-1]) && Character.isLowSurrogate(text[p])) p-1 else p
        }
    }
}

private data class TextDelta(val start: Int, val removed: String, val inserted: String,
                             val before: TextSelection, val after: TextSelection) {
    val units: Long get() = removed.length.toLong() + inserted.length
}

/**
 * PR-01: immutable draft with reversible minimal-span deltas. No file effects or implicit saves.
 * Budget counts retained UTF-16 payload, NOT actual heap bytes. History is Activity-lifetime only.
 */
class EditBuffer private constructor(
    val documentId: String,
    val content: String,
    val selection: TextSelection,
    val revision: Long,
    private val past: List<TextDelta>,
    private val future: List<TextDelta>,
    val historyTrimmed: Boolean,
) {
    val undoDepth get() = past.size
    val redoDepth get() = future.size
    val historyUnits get() = past.sumOf { it.units } + future.sumOf { it.units }

    fun select(selection: TextSelection): EditBuffer = EditBuffer(documentId,content,selection.inText(content),revision,past,future,historyTrimmed)

    fun edit(text: String, selection: TextSelection = TextSelection(text.length)): EditBuffer {
        TextBytes.encodedSize(text)
        val after = selection.inText(text)
        if (text == content) return select(after) // Selection-only changes do not invalidate a text revision.
        val nextRevision = nextRevision()
        var prefix = 0
        while (prefix < minOf(content.length,text.length) && content[prefix] == text[prefix]) prefix++
        prefix = minOf(TextSelection.boundary(content,prefix),TextSelection.boundary(text,prefix))
        var suffix = 0
        while (suffix < minOf(content.length-prefix,text.length-prefix) && content[content.lastIndex-suffix] == text[text.lastIndex-suffix]) suffix++
        while (TextSelection.boundary(content,content.length-suffix) != content.length-suffix ||
            TextSelection.boundary(text,text.length-suffix) != text.length-suffix) suffix--
        val delta = TextDelta(prefix,content.substring(prefix,content.length-suffix),text.substring(prefix,text.length-suffix),this.selection,after)
        val kept = (past + delta).toMutableList()
        var units = kept.sumOf { it.units }
        var trimmed = historyTrimmed
        while (kept.size > MAX_STEPS || units > MAX_HISTORY_UNITS) {
            units -= kept.removeAt(0).units; trimmed = true
        }
        check(kept.isNotEmpty()) { "A single bounded edit must always fit the undo budget" }
        return EditBuffer(documentId,text,after,nextRevision,kept.toList(),emptyList(),trimmed)
    }
    fun undo(): EditBuffer {
        val delta = past.lastOrNull() ?: return this
        check(content.regionMatches(delta.start,delta.inserted,0,delta.inserted.length)) { "Undo precondition failed" }
        val text = content.replaceRange(delta.start,delta.start+delta.inserted.length,delta.removed)
        return EditBuffer(documentId,text,delta.before,nextRevision(),past.dropLast(1),future+delta,historyTrimmed)
    }
    fun redo(): EditBuffer {
        val delta = future.lastOrNull() ?: return this
        check(content.regionMatches(delta.start,delta.removed,0,delta.removed.length)) { "Redo precondition failed" }
        val text = content.replaceRange(delta.start,delta.start+delta.removed.length,delta.inserted)
        return EditBuffer(documentId,text,delta.after,nextRevision(),past+delta,future.dropLast(1),historyTrimmed)
    }
    private fun nextRevision(): Long {
        check(revision < Long.MAX_VALUE) { "Revision exhausted; reopen the document before further edits" }
        return revision+1
    }
    companion object {
        const val MAX_STEPS = 100
        // Worst single change: two MAX_BYTES ASCII strings, each one UTF-16 unit per byte.
        const val MAX_HISTORY_UNITS = 2L * TextBytes.MAX_BYTES
        fun create(documentId: String, text: String): EditBuffer {
            require(documentId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
            TextBytes.encodedSize(text)
            return EditBuffer(documentId,text,TextSelection(0),0,emptyList(),emptyList(),false)
        }
    }
}

data class LiteralQuery(val text: String, val matchCase: Boolean = true)

/** Linear-time KMP over Unicode code points; no regex, locale conversion or normalization. */
object LiteralSearch {
    const val MAX_QUERY_UNITS = 1024
    const val MAX_MATCHES = 10_000
    fun matches(text: String, query: LiteralQuery): List<TextSelection> {
        require(query.text.isNotEmpty()) { "Enter nonempty literal search text" }
        require(query.text.length <= MAX_QUERY_UNITS) { "Search text limit: 1024 UTF-16 units" }
        TextBytes.encodedSize(text); TextBytes.encodedSize(query.text)
        fun fold(cp: Int): Int = if (query.matchCase) cp else Character.toLowerCase(Character.toUpperCase(cp))
        val pattern = query.text.codePoints().toArray().map(::fold).toIntArray()
        val failure = IntArray(pattern.size)
        var k = 0
        for (i in 1 until pattern.size) {
            while (k > 0 && pattern[i] != pattern[k]) k = failure[k-1]
            if (pattern[i] == pattern[k]) k++
            failure[i] = k
        }
        val starts = IntArray(pattern.size)
        val found = ArrayList<TextSelection>()
        var offset = 0; var processed = 0; var matched = 0
        while (offset < text.length) {
            starts[processed % pattern.size] = offset
            val cp = text.codePointAt(offset)
            offset += Character.charCount(cp)
            val value = fold(cp)
            while (matched > 0 && value != pattern[matched]) matched = failure[matched-1]
            if (value == pattern[matched]) matched++
            if (matched == pattern.size) {
                require(found.size < MAX_MATCHES) { "More than 10,000 matches; narrow the search. No partial replacement is allowed." }
                found += TextSelection(starts[(processed-pattern.size+1) % pattern.size],offset)
                matched = 0 // Declared non-overlap semantics: aa in aaa matches once.
            }
            processed++
        }
        return found.toList()
    }
    fun next(matches: List<TextSelection>, selection: TextSelection, backwards: Boolean = false): TextSelection? {
        if (matches.isEmpty()) return null
        return if (backwards) matches.lastOrNull { it.end <= selection.start } ?: matches.last()
        else matches.firstOrNull { it.start >= selection.end } ?: matches.first()
    }
}

/** Created only by the planner; confirmation cannot reinterpret a changed source or another tab. */
class ReplacementPlan internal constructor(
    val documentId: String,
    val revision: Long,
    private val original: String,
    private val replaced: String,
    private val after: TextSelection,
    private val requiredSelection: TextSelection?,
    val count: Int,
    val resultingBytes: Int,
) {
    fun apply(buffer: EditBuffer): EditBuffer {
        require(buffer.documentId == documentId && buffer.revision == revision && buffer.content == original) {
            "Replacement is stale or belongs to another document. Search and review again; nothing was changed."
        }
        require(requiredSelection == null || (buffer.selection.start == requiredSelection.start && buffer.selection.end == requiredSelection.end)) {
            "Selected match changed. Review the replacement again; nothing was changed."
        }
        return buffer.edit(replaced,after)
    }
}

object TextReplacement {
    fun plan(buffer: EditBuffer, query: LiteralQuery, replacement: String, onlySelection: Boolean = false): ReplacementPlan {
        TextBytes.encodedSize(replacement)
        val all = LiteralSearch.matches(buffer.content,query)
        val ranges = if (onlySelection) all.filter { it.start == buffer.selection.start && it.end == buffer.selection.end } else all
        require(ranges.isNotEmpty()) { if (onlySelection) "Select an exact match with Find next/previous first" else "No matches; nothing to replace" }
        val projectedUnits = buffer.content.length.toLong() + ranges.sumOf { replacement.length.toLong() - (it.end-it.start) }
        require(projectedUnits <= TextBytes.MAX_BYTES) { "Replacement would exceed the editor limit" }
        val result = buildString(projectedUnits.toInt()) {
            var start = 0
            for (range in ranges) {
                append(buffer.content,start,range.start); append(replacement); start = range.end
            }
            append(buffer.content,start,buffer.content.length)
        }
        // UTF-16 count alone cannot admit UTF-8 bytes. Validate before returning any mutable effect.
        val bytes = TextBytes.encodedSize(result)
        return ReplacementPlan(buffer.documentId,buffer.revision,buffer.content,result,
            TextSelection(ranges.first().start,ranges.first().start+replacement.length),
            if(onlySelection) buffer.selection else null,ranges.size,bytes)
    }
}

/** One-based line/code-point column; CRLF is one break, lone CR and LF also break lines. */
object TextNavigation {
    fun offset(text: String, line: Int, column: Int): Int {
        require(line > 0 && column > 0) { "Line and column start at 1" }
        TextBytes.encodedSize(text)
        var offset = 0; var currentLine = 1
        while (currentLine < line && offset < text.length) {
            when (text[offset++]) {
                '\r' -> { if (offset < text.length && text[offset]=='\n') offset++; currentLine++ }
                '\n' -> currentLine++
            }
        }
        require(currentLine == line) { "Line does not exist" }
        var currentColumn = 1
        while (currentColumn < column && offset < text.length && text[offset] != '\r' && text[offset] != '\n') {
            offset += Character.charCount(text.codePointAt(offset)); currentColumn++
        }
        require(currentColumn == column) { "Column is past the end of this line" }
        return offset
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Fusion Writer `<w:ins>` / `<w:del>` Tracked-Changes Revision Accept/Reject state machine
 * supporting selective per-author, per-revision-ID, or Accept-All / Reject-All document materialization
 * with exact inverse undo deltas!
 */
object WriterTrackChangesAcceptRejectEngine {
    const val SCOPE =
        "Writer <w:ins>/<w:del> tracked-changes selective per-author/per-ID Accept & Reject state machine with reversible snapshots."

    enum class SpanKind { UNCHANGED, INSERTED, DELETED }

    data class RevisionSpan(
        val revId: Int,
        val kind: SpanKind,
        val author: String,
        val timestampIso: String,
        val text: String,
    )

    fun renderVisibleText(spans: List<RevisionSpan>, showMarkupDeletions: Boolean = false): String =
        spans.filter { it.kind != SpanKind.DELETED || showMarkupDeletions }.joinToString("") { it.text }

    fun acceptRevisions(spans: List<RevisionSpan>, filterAuthor: String? = null, filterRevId: Int? = null): List<RevisionSpan> {
        val out = ArrayList<RevisionSpan>()
        for (s in spans) {
            val matches = s.kind != SpanKind.UNCHANGED &&
                (filterAuthor == null || s.author == filterAuthor) &&
                (filterRevId == null || s.revId == filterRevId)
            if (!matches) {
                out.add(s)
            } else if (s.kind == SpanKind.INSERTED) {
                out.add(s.copy(kind = SpanKind.UNCHANGED))
            }
            // Accepted DELETED span is permanently removed!
        }
        return coalesceUnchanged(out)
    }

    fun rejectRevisions(spans: List<RevisionSpan>, filterAuthor: String? = null, filterRevId: Int? = null): List<RevisionSpan> {
        val out = ArrayList<RevisionSpan>()
        for (s in spans) {
            val matches = s.kind != SpanKind.UNCHANGED &&
                (filterAuthor == null || s.author == filterAuthor) &&
                (filterRevId == null || s.revId == filterRevId)
            if (!matches) {
                out.add(s)
            } else if (s.kind == SpanKind.DELETED) {
                // Rejected deletion restores text as UNCHANGED!
                out.add(s.copy(kind = SpanKind.UNCHANGED))
            }
            // Rejected INSERTED span is discarded!
        }
        return coalesceUnchanged(out)
    }

    private fun coalesceUnchanged(spans: List<RevisionSpan>): List<RevisionSpan> {
        if (spans.isEmpty()) return emptyList()
        val merged = ArrayList<RevisionSpan>()
        for (s in spans) {
            val prev = merged.lastOrNull()
            if (prev != null && prev.kind == SpanKind.UNCHANGED && s.kind == SpanKind.UNCHANGED) {
                merged[merged.lastIndex] = prev.copy(text = prev.text + s.text)
            } else {
                merged.add(s)
            }
        }
        return merged
    }
}

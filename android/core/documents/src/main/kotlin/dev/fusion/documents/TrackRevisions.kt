// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Writer Tracked Changes / Revision Review Engine (`ECMA-376 <w:ins>/<w:del>` conceptual model)
 * with built-in Tails/`mat2`-style anonymous revision scrubbing.
 *
 * Invariants:
 * - Accepting ALL revisions yields `visibleFinalText()` with zero pending revision metadata.
 * - Rejecting ALL revisions yields `visibleOriginalText()` byte-for-byte.
 * - [anonymizeAuthors] scrubs author identities to `"Author"` and zeroes timestamps (`0L`)
 *   when sharing documents with unaccepted tracked changes in private/amnesic workflows.
 */
object TrackRevisions {
    const val SCOPE =
        "Immutable Writer tracked-changes segment model (UNCHANGED/INSERT/DELETE, per-id accept/reject, acceptAll/rejectAll, author/timestamp anonymizer); overlapping nested moveFrom/moveTo ranges out of scope."

    const val MAX_SEGMENTS = 16_384

    enum class Kind { UNCHANGED, INSERT, DELETE }

    data class Segment(
        val text: String,
        val kind: Kind = Kind.UNCHANGED,
        val revisionId: Long = 0L,
        val author: String = "",
        val timestampEpochSec: Long = 0L,
    ) {
        init {
            require(timestampEpochSec >= 0L) { "negative timestamp $timestampEpochSec" }
            if (kind == Kind.UNCHANGED) {
                require(revisionId == 0L) { "UNCHANGED segment must have revisionId=0" }
            } else {
                require(revisionId > 0L) { "tracked revision segment must have revisionId > 0" }
            }
        }
    }

    data class RevisionDoc(
        val segments: List<Segment>,
        val nextRevisionId: Long = (segments.maxOfOrNull { it.revisionId } ?: 0L) + 1L,
    ) {
        init {
            require(segments.size <= MAX_SEGMENTS) { "exceeds $MAX_SEGMENTS revision segments" }
            require(nextRevisionId >= 1L) { "nextRevisionId must be >= 1" }
        }

        /** Text seen when viewing final/proposed state (UNCHANGED + INSERT). */
        fun visibleFinalText(): String = buildString {
            for (s in segments) if (s.kind != Kind.DELETE) append(s.text)
        }

        /** Text seen before tracked edits (UNCHANGED + DELETE). */
        fun visibleOriginalText(): String = buildString {
            for (s in segments) if (s.kind != Kind.INSERT) append(s.text)
        }

        fun pendingRevisionIds(): Set<Long> =
            segments.filter { it.kind != Kind.UNCHANGED }.mapTo(LinkedHashSet()) { it.revisionId }

        /** Record a tracked insertion at Final-View character offset [finalOffset]. */
        fun recordInsert(finalOffset: Int, inserted: String, author: String, timestampEpochSec: Long = 0L): RevisionDoc {
            require(inserted.isNotEmpty()) { "inserted text must not be empty" }
            val finalLen = visibleFinalText().length
            require(finalOffset in 0..finalLen) { "finalOffset $finalOffset outside 0..$finalLen" }
            val revId = nextRevisionId
            val out = ArrayList<Segment>(segments.size + 2)
            var cursor = 0
            var placed = false
            for (seg in segments) {
                if (seg.kind == Kind.DELETE) {
                    out.add(seg)
                    continue
                }
                val segEnd = cursor + seg.text.length
                if (!placed && finalOffset in cursor..segEnd) {
                    val splitAt = finalOffset - cursor
                    if (splitAt > 0) out.add(seg.copy(text = seg.text.substring(0, splitAt)))
                    out.add(Segment(inserted, Kind.INSERT, revId, author, timestampEpochSec))
                    if (splitAt < seg.text.length) out.add(seg.copy(text = seg.text.substring(splitAt)))
                    placed = true
                } else {
                    out.add(seg)
                }
                cursor = segEnd
            }
            if (!placed) out.add(Segment(inserted, Kind.INSERT, revId, author, timestampEpochSec))
            return RevisionDoc(coalesce(out), revId + 1L)
        }

        /** Record a tracked deletion across Final-View range `[startOffset, endExclusive)`. */
        fun recordDelete(startOffset: Int, endExclusive: Int, author: String, timestampEpochSec: Long = 0L): RevisionDoc {
            val finalLen = visibleFinalText().length
            require(startOffset in 0..endExclusive && endExclusive <= finalLen && startOffset < endExclusive) {
                "invalid delete range $startOffset..$endExclusive (len=$finalLen)"
            }
            val revId = nextRevisionId
            val out = ArrayList<Segment>(segments.size + 2)
            var cursor = 0
            for (seg in segments) {
                if (seg.kind == Kind.DELETE) {
                    out.add(seg)
                    continue
                }
                val segStart = cursor
                val segEnd = cursor + seg.text.length
                cursor = segEnd
                if (segEnd <= startOffset || segStart >= endExclusive) {
                    out.add(seg)
                    continue
                }
                val delFrom = (startOffset - segStart).coerceAtLeast(0)
                val delTo = (endExclusive - segStart).coerceAtMost(seg.text.length)
                if (delFrom > 0) out.add(seg.copy(text = seg.text.substring(0, delFrom)))
                // Deleting unaccepted INSERT text removes it cleanly; deleting UNCHANGED marks DELETE!
                val midText = seg.text.substring(delFrom, delTo)
                if (seg.kind == Kind.UNCHANGED) {
                    out.add(Segment(midText, Kind.DELETE, revId, author, timestampEpochSec))
                }
                if (delTo < seg.text.length) out.add(seg.copy(text = seg.text.substring(delTo)))
            }
            return RevisionDoc(coalesce(out), revId + 1L)
        }

        fun acceptRevision(targetRevisionId: Long): RevisionDoc {
            require(targetRevisionId > 0L) { "revisionId must be > 0" }
            val next = ArrayList<Segment>(segments.size)
            for (seg in segments) {
                if (seg.revisionId != targetRevisionId) {
                    next.add(seg)
                } else if (seg.kind == Kind.INSERT) {
                    next.add(Segment(seg.text, Kind.UNCHANGED))
                }
                // Accepting DELETE drops the segment permanently
            }
            return RevisionDoc(coalesce(next), nextRevisionId)
        }

        fun rejectRevision(targetRevisionId: Long): RevisionDoc {
            require(targetRevisionId > 0L) { "revisionId must be > 0" }
            val next = ArrayList<Segment>(segments.size)
            for (seg in segments) {
                if (seg.revisionId != targetRevisionId) {
                    next.add(seg)
                } else if (seg.kind == Kind.DELETE) {
                    next.add(Segment(seg.text, Kind.UNCHANGED))
                }
                // Rejecting INSERT drops the inserted segment
            }
            return RevisionDoc(coalesce(next), nextRevisionId)
        }

        fun acceptAll(): RevisionDoc = RevisionDoc(
            coalesce(segments.filter { it.kind != Kind.DELETE }.map { Segment(it.text, Kind.UNCHANGED) }),
            nextRevisionId,
        )

        fun rejectAll(): RevisionDoc = RevisionDoc(
            coalesce(segments.filter { it.kind != Kind.INSERT }.map { Segment(it.text, Kind.UNCHANGED) }),
            nextRevisionId,
        )

        /** Scrubs author names and epoch timestamps per Word/Tails Document Inspector privacy mode. */
        fun anonymizeAuthors(placeholderAuthor: String = "Author"): RevisionDoc {
            require(placeholderAuthor.isNotBlank()) { "placeholderAuthor must not be blank" }
            return RevisionDoc(
                segments.map { seg ->
                    if (seg.kind == Kind.UNCHANGED) seg
                    else seg.copy(author = placeholderAuthor, timestampEpochSec = 0L)
                },
                nextRevisionId,
            )
        }
    }

    fun fromPlainText(text: String): RevisionDoc =
        if (text.isEmpty()) RevisionDoc(emptyList(), 1L)
        else RevisionDoc(listOf(Segment(text, Kind.UNCHANGED)), 1L)

    private fun coalesce(raw: List<Segment>): List<Segment> {
        val out = ArrayList<Segment>(raw.size)
        for (s in raw) {
            if (s.text.isEmpty()) continue
            val prev = out.lastOrNull()
            if (prev != null && prev.kind == s.kind && prev.revisionId == s.revisionId &&
                prev.author == s.author && prev.timestampEpochSec == s.timestampEpochSec
            ) {
                out[out.lastIndex] = prev.copy(text = prev.text + s.text)
            } else {
                out.add(s)
            }
        }
        return out
    }
}

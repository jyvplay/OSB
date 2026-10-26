package dev.fusion.find

/**
 * Half-open match-span algebra for search highlighting: merge
 * overlapping or touching [start, end) ranges into the canonical
 * minimal sorted disjoint set, plus coverage and inversion.
 *
 * Touching spans merge ([1,3) + [3,5) = [1,5)) because adjacent
 * highlights must render as one — a seam between them is a visual
 * artifact, not information.
 *
 * Executed properties: merging is idempotent; inversion within bounds
 * twice returns the merged original; covered + uncovered lengths sum
 * to the bounds length.
 *
 * Declared narrowings: Int offsets in whatever unit the caller indexes
 * by (char, byte — the kernel does not care, and mixing units is the
 * caller's bug); empty spans (start == end) are refused rather than
 * silently dropped.
 */
object SpanMerge {
    const val SCOPE = "Half-open span algebra; touching spans merge; empty spans refuse; units are the caller's."

    data class Span(val start: Int, val end: Int) {
        init { require(start < end) { "empty or inverted span [$start,$end)" } }
        val length: Int get() = end - start
    }

    fun merge(spans: List<Span>): List<Span> {
        if (spans.isEmpty()) return emptyList()
        val sorted = spans.sortedWith(compareBy({ it.start }, { it.end }))
        val out = ArrayList<Span>()
        var cur = sorted[0]
        for (s in sorted.drop(1)) {
            cur = if (s.start <= cur.end) {
                if (s.end > cur.end) Span(cur.start, s.end) else cur
            } else {
                out.add(cur); s
            }
        }
        out.add(cur)
        return out
    }

    fun covered(spans: List<Span>): Int = merge(spans).sumOf { it.length }

    /** Gaps inside [0, bound) not covered by the spans. */
    fun invert(spans: List<Span>, bound: Int): List<Span> {
        require(bound >= 0) { "negative bound" }
        val merged = merge(spans)
        merged.forEach { require(it.start >= 0 && it.end <= bound) { "span $it outside [0,$bound)" } }
        val out = ArrayList<Span>()
        var pos = 0
        for (s in merged) {
            if (s.start > pos) out.add(Span(pos, s.start))
            pos = s.end
        }
        if (pos < bound) out.add(Span(pos, bound))
        return out
    }

    fun contains(spans: List<Span>, offset: Int): Boolean =
        merge(spans).any { offset >= it.start && offset < it.end }
}

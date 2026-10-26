package dev.fusion.find

/**
 * Result-snippet window selection: given match spans and a window
 * width W, choose the window [start, start+W) covering the MOST
 * matched characters; ties resolve to the smallest start (stable
 * snippets across runs — the property that matters for caching).
 *
 * Candidate starts are span starts and (span ends - W) clamped to 0 —
 * an optimal window always has a span boundary at one of its own
 * edges, so the candidate set is sufficient (and the sweep asserts a
 * brute-force agreement on small cases).
 *
 * Declared narrowings: works on [SpanMerge]-merged spans; character
 * units are the caller's (same contract as SpanMerge).
 */
object SnippetWindow {
    const val SCOPE = "Max-coverage window with smallest-start ties; candidate-edge optimality used and tested."

    data class Window(val start: Int, val covered: Int)

    fun coveredIn(spans: List<SpanMerge.Span>, start: Int, width: Int): Int {
        var total = 0
        for (s in spans) {
            val lo = maxOf(s.start, start)
            val hi = minOf(s.end, start + width)
            if (hi > lo) total += hi - lo
        }
        return total
    }

    fun best(rawSpans: List<SpanMerge.Span>, width: Int, textLength: Int): Window {
        require(width > 0) { "window width $width" }
        require(textLength >= 0) { "negative text length" }
        val spans = SpanMerge.merge(rawSpans)
        spans.forEach { require(it.end <= textLength) { "span $it outside the text" } }
        if (spans.isEmpty()) return Window(0, 0)
        val maxStart = maxOf(0, textLength - width)
        val candidates = sortedSetOf(0)
        for (s in spans) {
            candidates.add(s.start.coerceIn(0, maxStart))
            candidates.add((s.end - width).coerceIn(0, maxStart))
        }
        var best = Window(0, -1)
        for (c in candidates) {
            val cov = coveredIn(spans, c, width)
            if (cov > best.covered) best = Window(c, cov)
        }
        return best
    }
}

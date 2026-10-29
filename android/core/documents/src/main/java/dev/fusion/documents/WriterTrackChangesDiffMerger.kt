// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Clean-room Writer Word-Level LCS Track-Changes Revision Engine (`<w:ins>` / `<w:del>`) with
 * Selective Accept / Reject Merge Algebra.
 */
object WriterTrackChangesDiffMerger {
    const val SCOPE =
        "Pure-JVM word-level LCS Track Changes (<w:ins>/<w:del>) revision generator and selective accept/reject document merger."

    enum class ChangeKind { EQUAL, INSERT, DELETE }

    data class RevisionRun(val id: Int, val kind: ChangeKind, val text: String, val author: String)

    fun diffWords(original: String, revised: String, author: String = "FusionUser"): List<RevisionRun> {
        val a = tokenize(original)
        val b = tokenize(revised)
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                dp[i][j] = if (a[i] == b[j]) 1 + dp[i + 1][j + 1] else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val raw = ArrayList<Pair<ChangeKind, String>>()
        var i = 0; var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { raw.add(ChangeKind.EQUAL to a[i]); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { raw.add(ChangeKind.DELETE to a[i]); i++ }
                else -> { raw.add(ChangeKind.INSERT to b[j]); j++ }
            }
        }
        while (i < a.size) raw.add(ChangeKind.DELETE to a[i++])
        while (j < b.size) raw.add(ChangeKind.INSERT to b[j++])

        // Coalesce adjacent identical ChangeKind runs
        val merged = ArrayList<RevisionRun>()
        var nextId = 1
        for ((k, tok) in raw) {
            val last = merged.lastOrNull()
            if (last != null && last.kind == k) {
                merged[merged.lastIndex] = last.copy(text = "${last.text} $tok")
            } else {
                merged.add(RevisionRun(if (k == ChangeKind.EQUAL) 0 else nextId++, k, tok, author))
            }
        }
        return merged
    }

    fun applyDecisions(runs: List<RevisionRun>, acceptedIds: Set<Int>, rejectedIds: Set<Int>): String {
        require((acceptedIds intersect rejectedIds).isEmpty()) { "contradictory accept & reject on same revision id" }
        val kept = ArrayList<String>()
        for (r in runs) {
            when (r.kind) {
                ChangeKind.EQUAL -> kept.add(r.text)
                ChangeKind.INSERT -> if (r.id in acceptedIds || r.id !in rejectedIds) kept.add(r.text)
                ChangeKind.DELETE -> if (r.id in rejectedIds) kept.add(r.text)
            }
        }
        return kept.joinToString(" ").trim()
    }

    fun toWordprocessingMl(runs: List<RevisionRun>, isoTimestamp: String = "2026-10-27T12:00:00Z"): String =
        runs.joinToString("") { r ->
            val esc = r.text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            when (r.kind) {
                ChangeKind.EQUAL -> "<w:r><w:t xml:space=\"preserve\">$esc </w:t></w:r>"
                ChangeKind.INSERT -> "<w:ins w:id=\"${r.id}\" w:author=\"${r.author}\" w:date=\"$isoTimestamp\"><w:r><w:t>$esc </w:t></w:r></w:ins>"
                ChangeKind.DELETE -> "<w:del w:id=\"${r.id}\" w:author=\"${r.author}\" w:date=\"$isoTimestamp\"><w:r><w:delText>$esc </w:delText></w:r></w:del>"
            }
        }

    private fun tokenize(s: String): List<String> = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
}

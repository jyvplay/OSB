// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

import java.util.Locale
import kotlin.math.abs

/**
 * Clean-room bidirectional Zettelkasten wiki-link & PageRank graph engine (`WikiLinkGraph`).
 *
 * Powers `:feature:notes` backlink panels, broken-link diagnostics, tag indexing, and hub discovery:
 * - Parses `[[Target]]`, `[[Target|Display Alias]]`, `[[Target#Section]]`, and `#hierarchical/tag` tokens
 *   while ignoring fenced code blocks (` ``` `) and inline code spans (`` `...` ``).
 * - Normalizes slugs case-insensitively (`slugify("Project Roadmap")` -> `"project-roadmap"`).
 * - Computes exact incoming backlinks, unresolved dangling links, orphan notes, and damped iterative
 *   PageRank (`damping = 0.85`, handling sink/dangling nodes so scores sum to `1.0`).
 */
object WikiLinkGraph {
    const val SCOPE =
        "Bidirectional [[WikiLink]] + #tag knowledge-graph indexer (fenced-code-aware parser, backlinks, broken links, orphans, damped PageRank); semantic vector embeddings out of scope."

    data class NoteInput(val id: String, val title: String, val markdown: String)

    data class OutgoingLink(
        val sourceSlug: String,
        val targetSlug: String,
        val section: String?,
        val alias: String?,
    )

    data class GraphIndex(
        val slugs: Set<String>,
        val outgoing: Map<String, List<OutgoingLink>>,
        val backlinks: Map<String, Set<String>>,
        val brokenLinks: List<OutgoingLink>,
        val tagsByNote: Map<String, Set<String>>,
        val orphanSlugs: Set<String>,
        val pageRank: Map<String, Double>,
    )

    private val WIKI_RE = Regex("""\[\[([^\[\]\n]+)]]""")
    private val TAG_RE = Regex("""(?<=^|\s)#([A-Za-z][A-Za-z0-9_/-]{0,63})""")

    fun slugify(raw: String): String =
        raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')

    fun build(notes: List<NoteInput>, damping: Double = 0.85, maxIterations: Int = 60): GraphIndex {
        require(damping in 0.1..0.99) { "damping must be in 0.1..0.99: $damping" }
        val slugToInput = linkedMapOf<String, NoteInput>()
        val titleSlugToCanonical = linkedMapOf<String, String>()
        for (n in notes) {
            val s = slugify(n.id.ifBlank { n.title })
            require(s.isNotEmpty()) { "Note slug must not be empty" }
            slugToInput[s] = n
            titleSlugToCanonical[s] = s
            val ts = slugify(n.title)
            if (ts.isNotEmpty()) titleSlugToCanonical.putIfAbsent(ts, s)
        }

        val allSlugs = slugToInput.keys.toSet()
        val outgoing = linkedMapOf<String, List<OutgoingLink>>()
        val backlinks = allSlugs.associateWith { linkedSetOf<String>() }
        val broken = mutableListOf<OutgoingLink>()
        val tagsByNote = linkedMapOf<String, Set<String>>()

        for ((slug, note) in slugToInput) {
            val stripped = stripCodeRegions(note.markdown)
            val links = mutableListOf<OutgoingLink>()
            for (m in WIKI_RE.findAll(stripped)) {
                val body = m.groupValues[1].trim()
                val pipeIdx = body.indexOf('|')
                val targetAndSec = if (pipeIdx >= 0) body.substring(0, pipeIdx).trim() else body
                val alias = if (pipeIdx >= 0) body.substring(pipeIdx + 1).trim().ifEmpty { null } else null
                val hashIdx = targetAndSec.indexOf('#')
                val rawTarget = if (hashIdx >= 0) targetAndSec.substring(0, hashIdx).trim() else targetAndSec
                val section = if (hashIdx >= 0) targetAndSec.substring(hashIdx + 1).trim().ifEmpty { null } else null
                val targetCandidate = slugify(rawTarget)
                if (targetCandidate.isEmpty()) continue
                val resolved = titleSlugToCanonical[targetCandidate] ?: targetCandidate
                val link = OutgoingLink(slug, resolved, section, alias)
                links.add(link)
                if (resolved in allSlugs) {
                    if (resolved != slug) backlinks.getValue(resolved).add(slug)
                } else {
                    broken.add(link)
                }
            }
            outgoing[slug] = links
            tagsByNote[slug] = TAG_RE.findAll(stripped).map { it.groupValues[1].lowercase(Locale.ROOT) }.toSet()
        }

        val orphans = allSlugs.filterTo(linkedSetOf()) { s ->
            val outInternal = outgoing[s].orEmpty().count { it.targetSlug in allSlugs && it.targetSlug != s }
            val inInternal = backlinks[s].orEmpty().size
            outInternal == 0 && inInternal == 0
        }

        val pageRank = computePageRank(allSlugs.toList(), outgoing, damping, maxIterations)
        return GraphIndex(
            slugs = allSlugs,
            outgoing = outgoing,
            backlinks = backlinks,
            brokenLinks = broken,
            tagsByNote = tagsByNote,
            orphanSlugs = orphans,
            pageRank = pageRank,
        )
    }

    private fun computePageRank(
        nodes: List<String>,
        outgoing: Map<String, List<OutgoingLink>>,
        damping: Double,
        maxIter: Int,
    ): Map<String, Double> {
        val n = nodes.size
        if (n == 0) return emptyMap()
        val nodeSet = nodes.toSet()
        val idxOf = nodes.withIndex().associate { it.value to it.index }
        val adj = Array(n) { i ->
            outgoing[nodes[i]].orEmpty()
                .map { it.targetSlug }
                .filter { it in nodeSet && it != nodes[i] }
                .distinct()
                .map { idxOf.getValue(it) }
                .toIntArray()
        }
        var rank = DoubleArray(n) { 1.0 / n }
        val base = (1.0 - damping) / n
        for (iter in 0 until maxIter) {
            val next = DoubleArray(n) { base }
            var sinkMass = 0.0
            for (i in 0 until n) {
                val targets = adj[i]
                if (targets.isEmpty()) {
                    sinkMass += rank[i]
                } else {
                    val share = damping * rank[i] / targets.size
                    for (t in targets) next[t] += share
                }
            }
            val sinkShare = damping * sinkMass / n
            var delta = 0.0
            for (i in 0 until n) {
                next[i] += sinkShare
                delta += abs(next[i] - rank[i])
            }
            rank = next
            if (delta < 1e-11) break
        }
        return nodes.withIndex().associate { (i, slug) -> slug to rank[i] }
    }

    private fun stripCodeRegions(md: String): String {
        val withoutTriple = md.replace(Regex("```[\\s\\S]*?```"), " ")
        val out = StringBuilder()
        var inFence = false
        for (line in withoutTriple.split('\n')) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("~~~")) {
                inFence = !inFence
                continue
            }
            if (!inFence) {
                out.append(line.replace(Regex("`[^`]*`"), " ")).append('\n')
            }
        }
        return out.toString()
    }
}

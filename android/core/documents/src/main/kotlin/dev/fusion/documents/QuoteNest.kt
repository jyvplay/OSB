package dev.fusion.documents

/**
 * Plain-text email quote nesting (`>`-prefixed lines) for the writer's
 * paste/reflow path: depth of a line = count of leading `>` markers,
 * each optionally followed by one space; [stripLevel] removes exactly
 * one level; [depthRuns] groups consecutive lines by depth for
 * rendering.
 *
 * Declared narrowings: the `>` convention only (no `|` or locale
 * quote prefixes); content that legitimately starts with `>` (code,
 * markdown blockquote source) is indistinguishable BY DESIGN — the
 * caller decides via context, and this KDoc says so instead of
 * pretending a heuristic.
 */
object QuoteNest {
    const val SCOPE = "'>' quote depth parse/strip/runs; '>' content ambiguity is declared, not heuristically guessed."

    fun depth(line: String): Int {
        var d = 0; var i = 0
        while (i < line.length && line[i] == '>') {
            d++; i++
            if (i < line.length && line[i] == ' ') i++
        }
        return d
    }

    fun body(line: String): String {
        var i = 0
        var d = depth(line)
        while (d > 0 && i < line.length && line[i] == '>') {
            i++
            if (i < line.length && line[i] == ' ') i++
            d--
        }
        return line.substring(i)
    }

    fun stripLevel(line: String): String {
        require(depth(line) >= 1) { "line carries no quote marker" }
        var i = 1
        if (i < line.length && line[i] == ' ') i++
        return line.substring(i)
    }

    fun addLevel(line: String): String = "> " + line

    data class RunD(val depth: Int, val lines: List<String>)

    fun depthRuns(lines: List<String>): List<RunD> {
        val out = ArrayList<RunD>()
        var curDepth = -1
        var cur = ArrayList<String>()
        for (l in lines) {
            val d = depth(l)
            if (d != curDepth) {
                if (cur.isNotEmpty()) out.add(RunD(curDepth, cur))
                curDepth = d; cur = ArrayList()
            }
            cur.add(body(l))
        }
        if (cur.isNotEmpty()) out.add(RunD(curDepth, cur))
        return out
    }
}

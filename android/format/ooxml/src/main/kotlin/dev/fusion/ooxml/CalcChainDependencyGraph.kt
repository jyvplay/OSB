package dev.fusion.ooxml

/**
 * Clean-room spreadsheet dependency graph analyzer: Tarjan strongly-connected-components (SCC)
 * circular-reference isolator, parallel wave-level topological scheduler, and ECMA-376
 * `xl/calcChain.xml` serializer/parser (Apache-2.0).
 */
object CalcChainDependencyGraph {
    const val SCOPE =
        "Tarjan SCC circular-reference isolator + topological parallel wave scheduler + ECMA-376 xl/calcChain.xml codec"

    data class CalcChainEntry(val sheetId: Int, val cellA1: String, val childChain: Boolean = false)

    data class GraphSchedule(
        val waves: List<List<String>>,
        val topologicalOrder: List<String>,
        val circularComponents: List<List<String>>,
    ) {
        val hasCycles: Boolean get() = circularComponents.isNotEmpty()
    }

    /**
     * Analyzes [precedentsByCell] (`cell -> set of precedent cells it reads`) using Tarjan's
     * O(V + E) SCC algorithm and partitions acyclic cells into deterministic parallel waves
     * where every cell in `waves[k]` depends only on literals or `waves[0..k-1]`.
     */
    fun buildSchedule(precedentsByCell: Map<String, Set<String>>): GraphSchedule {
        val allNodes = sortedSetOf<String>()
        val normPrec = LinkedHashMap<String, Set<String>>()
        for ((rawCell, rawDeps) in precedentsByCell) {
            val c = normalizeKey(rawCell)
            val ds = rawDeps.map(::normalizeKey).toSortedSet()
            allNodes.add(c)
            allNodes.addAll(ds)
            normPrec[c] = ds
        }
        for (n in allNodes) normPrec.putIfAbsent(n, emptySet())

        // Tarjan SCC on directed edge u -> v ("u must compute before v", i.e. u is precedent of v)
        val dependents = LinkedHashMap<String, MutableList<String>>()
        for (n in allNodes) dependents[n] = mutableListOf()
        for ((cell, precs) in normPrec) {
            for (p in precs) dependents.getValue(p).add(cell)
        }
        for (list in dependents.values) list.sort()

        var index = 0
        val indices = HashMap<String, Int>()
        val lowlink = HashMap<String, Int>()
        val onStack = HashSet<String>()
        val stack = ArrayDeque<String>()
        val cycles = mutableListOf<List<String>>()

        fun strongConnect(v: String) {
            indices[v] = index
            lowlink[v] = index
            index++
            stack.addLast(v)
            onStack.add(v)
            for (w in dependents[v].orEmpty()) {
                if (w !in indices) {
                    strongConnect(w)
                    lowlink[v] = minOf(lowlink.getValue(v), lowlink.getValue(w))
                } else if (w in onStack) {
                    lowlink[v] = minOf(lowlink.getValue(v), indices.getValue(w))
                }
            }
            if (lowlink.getValue(v) == indices.getValue(v)) {
                val comp = mutableListOf<String>()
                while (true) {
                    val w = stack.removeLast()
                    onStack.remove(w)
                    comp.add(w)
                    if (w == v) break
                }
                comp.sort()
                val selfLoop = comp.size == 1 && comp[0] in normPrec[comp[0]].orEmpty()
                if (comp.size > 1 || selfLoop) cycles.add(comp)
            }
        }
        for (n in allNodes) if (n !in indices) strongConnect(n)
        cycles.sortBy { it.first() }

        val cyclicSet = cycles.flatten().toHashSet()
        // Downstream taint: any cell depending on a cycle cannot be scheduled cleanly either
        val tainted = LinkedHashSet(cyclicSet)
        val q = ArrayDeque(cyclicSet)
        while (q.isNotEmpty()) {
            val cur = q.removeFirst()
            for (d in dependents[cur].orEmpty()) if (tainted.add(d)) q.addLast(d)
        }

        val acyclicNodes = allNodes.filter { it !in tainted }
        val inDegree = HashMap<String, Int>()
        for (u in acyclicNodes) {
            inDegree[u] = normPrec.getValue(u).count { it in acyclicNodes }
        }
        val waves = mutableListOf<List<String>>()
        val topo = mutableListOf<String>()
        var frontier = acyclicNodes.filter { inDegree.getValue(it) == 0 }.sorted()
        while (frontier.isNotEmpty()) {
            // Only emit formula-bearing or referenced nodes in deterministic order
            waves.add(frontier)
            topo.addAll(frontier)
            val next = sortedSetOf<String>()
            for (u in frontier) {
                for (w in dependents[u].orEmpty()) {
                    if (w in inDegree) {
                        val deg = inDegree.getValue(w) - 1
                        inDegree[w] = deg
                        if (deg == 0) next.add(w)
                    }
                }
            }
            frontier = next.toList()
        }
        return GraphSchedule(waves = waves, topologicalOrder = topo, circularComponents = cycles)
    }

    fun toCalcChainXml(entries: List<CalcChainEntry>): String = buildString {
        require(entries.isNotEmpty()) { "calcChain requires >= 1 formula cell" }
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<calcChain xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        var prevSheet: Int? = null
        for (e in entries) {
            append("""<c r="${SheetRange.parseCell(e.cellA1).toA1()}"""")
            if (prevSheet != e.sheetId) {
                append(""" i="${e.sheetId}"""")
                prevSheet = e.sheetId
            }
            if (e.childChain) append(""" l="1"""")
            append("/>")
        }
        append("</calcChain>")
    }

    fun parseCalcChainXml(xml: String): List<CalcChainEntry> {
        val root = BoundedXml.parse(xml.toByteArray(Charsets.UTF_8))
        require(root.name.local == "calcChain") { "Expected <calcChain>, found <${root.name.local}>" }
        var curSheet = 1
        return root.children.filter { it.name.local == "c" }.map { node ->
            node.attr("i")?.toIntOrNull()?.let { curSheet = it }
            CalcChainEntry(
                sheetId = curSheet,
                cellA1 = SheetRange.parseCell(node.attr("r") ?: throw IllegalArgumentException("Missing r attr")).toA1(),
                childChain = node.attr("l") == "1",
            )
        }
    }

    private fun normalizeKey(raw: String): String {
        val bang = raw.lastIndexOf('!')
        return if (bang >= 0) {
            val sh = raw.substring(0, bang).trim().removeSurrounding("'")
            "$sh!${SheetRange.parseCell(raw.substring(bang + 1)).toA1()}"
        } else {
            SheetRange.parseCell(raw).toA1()
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Multi-sheet ECMA-376 `.xlsx` workbook evaluator & zero-epoch archive reader/writer (`1..16` sheets).
 *
 * Extends [FormulaEval] + [FormulaXlsx] from single-sheet workbooks to cross-sheet DAGs:
 * - Supports cross-sheet cell & rectangular range references (`Sheet2!B3`, `'Q1 Sales'!A1:B4`)
 *   rewritten and evaluated in a single global topological sort across `(sheet, A1)` nodes.
 * - Detects cross-sheet circular dependencies (`Sheet1!A1 = Sheet2!A1 + 1`, `Sheet2!A1 = Sheet1!A1`)
 *   and refuses with an explicit witness cycle path without recursion overflow.
 * - Writes and reads standards-compliant multi-sheet `.xlsx` packages (`xl/workbook.xml`,
 *   `xl/_rels/workbook.xml.rels`, `xl/worksheets/sheet1.xml .. sheetN.xml`) with both `<f>` and
 *   cached `<v>` values so `openpyxl` opens every sheet in both `data_only=False` and `data_only=True`.
 */
object MultiSheetBook {
    const val SCOPE =
        "Multi-sheet ECMA-376 .xlsx DAG evaluator and zero-epoch OPC reader/writer (1..16 sheets, cross-sheet Sheet!A1 & 'Quoted Sheet'!A1:B4 refs, evaluate3d/write3d 3D sheet spans Sheet1:Sheet4!A1:B4 with reversed-order normalization and #REF! on missing endpoint sheets, cross-sheet cycle witness refusal); external [Book2.xlsx] links refused."

    const val MAX_SHEETS = 16
    const val MAX_TOTAL_CELLS = 32_768
    const val MAX_ZIP_BYTES = 16 * 1024 * 1024
    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_DOC_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"

    data class SheetDef(
        val name: String,
        val cells: Map<String, FormulaEval.CellInput>,
    )

    data class EvaluatedBook(
        val sheetOrder: List<String>,
        val values: Map<String, Map<String, FormulaEval.CellValue>>,
        val cycleNodes: Set<String>,
    ) {
        fun cell(sheet: String, a1: String): FormulaEval.CellValue =
            values[sheet]?.get(FormulaEval.canonicalCellRef(a1)) ?: FormulaEval.CellValue.Empty
    }

    private val SPAN_3D_RE = Regex(
        """(?:'((?:[^']|'')+)'|([A-Za-z_][A-Za-z0-9_.]*))\s*:\s*(?:'((?:[^']|'')+)'|([A-Za-z_][A-Za-z0-9_.]*))!(\$?[A-Za-z]{1,3}\$?[1-9][0-9]*)(?::(\$?[A-Za-z]{1,3}\$?[1-9][0-9]*))?""",
    )

    private val CROSS_RANGE_RE = Regex(
        """(?:'((?:[^']|'')+)'|([A-Za-z_][A-Za-z0-9_.]*))!(\$?[A-Za-z]{1,3}\$?[1-9][0-9]*)(?::(\$?[A-Za-z]{1,3}\$?[1-9][0-9]*))?""",
    )

    fun evaluate(sheets: List<SheetDef>): EvaluatedBook = evaluateInternal(sheets, allow3dSpans = false)

    /** Evaluates multi-sheet workbooks with full Excel 3D cross-sheet span expansion (`SUM(Q1:Q4!B2:C5)`). */
    fun evaluate3d(sheets: List<SheetDef>): EvaluatedBook = evaluateInternal(sheets, allow3dSpans = true)

    /** Writes a standards-compliant multi-sheet `.xlsx` workbook with cached 3D cross-sheet span values. */
    fun write3d(sheets: List<SheetDef>): ByteArray = writeInternal(sheets, allow3dSpans = true)

    private fun expand3dSpansInBody(
        body: String,
        orderedNames: List<String>,
        indexByLower: Map<String, Int>,
    ): String = rewriteOutsideStrings(body) { seg ->
        SPAN_3D_RE.replace(seg) { m ->
            val s1 = m.groups[1]?.value?.replace("''", "'") ?: m.groups[2]!!.value
            val s2 = m.groups[3]?.value?.replace("''", "'") ?: m.groups[4]!!.value
            val c1 = m.groups[5]!!.value
            val c2 = m.groups[6]?.value
            val refSuffix = if (c2 != null) "$c1:$c2" else c1
            val i1 = indexByLower[s1.lowercase()]
            val i2 = indexByLower[s2.lowercase()]
            if (i1 == null || i2 == null) {
                "__MISSING_3D_SHEET__!$c1"
            } else {
                val lo = minOf(i1, i2)
                val hi = maxOf(i1, i2)
                (lo..hi).joinToString(",") { idx ->
                    val nm = orderedNames[idx]
                    val quoted = if (nm.any { !it.isLetterOrDigit() && it != '_' && it != '.' }) {
                        "'${nm.replace("'", "''")}'"
                    } else nm
                    "$quoted!$refSuffix"
                }
            }
        }
    }

    private fun evaluateInternal(sheets: List<SheetDef>, allow3dSpans: Boolean): EvaluatedBook {
        require(sheets.size in 1..MAX_SHEETS) { "sheet count ${sheets.size} outside 1..$MAX_SHEETS" }
        val seenLower = HashSet<String>()
        val canonicalByLower = LinkedHashMap<String, String>()
        val indexByLower = LinkedHashMap<String, Int>()
        val orderedNames = sheets.map { it.name }
        var totalCells = 0
        val rawNormSheets = LinkedHashMap<String, Map<String, FormulaEval.CellInput>>()

        for ((idx, s) in sheets.withIndex()) {
            validateSheetName(s.name)
            val low = s.name.lowercase()
            require(seenLower.add(low)) { "duplicate sheet name '${s.name}' (case-insensitive)" }
            canonicalByLower[low] = s.name
            indexByLower[low] = idx
            totalCells += s.cells.size
            require(totalCells <= MAX_TOTAL_CELLS) { "workbook exceeds $MAX_TOTAL_CELLS cells" }
            val cmap = LinkedHashMap<String, FormulaEval.CellInput>(s.cells.size)
            for ((k, v) in s.cells) {
                cmap[FormulaEval.canonicalCellRef(k)] = v
            }
            rawNormSheets[s.name] = cmap
        }

        // Reject external [Book.xlsx]Sheet1!A1 always; reject 3D spans in strict legacy evaluate(), expand in evaluate3d()!
        val normSheets = LinkedHashMap<String, Map<String, FormulaEval.CellInput>>()
        for ((sName, cmap) in rawNormSheets) {
            val expandedMap = LinkedHashMap<String, FormulaEval.CellInput>(cmap.size)
            for ((a1, inp) in cmap) {
                if (inp is FormulaEval.CellInput.Formula) {
                    val stripped = stripStringLiterals(inp.body)
                    require(!stripped.contains('[') && !stripped.contains(']')) {
                        "external workbook reference refused in $sName!$a1"
                    }
                    if (!allow3dSpans) {
                        require(!Regex("""[A-Za-z0-9_']+\s*:\s*[A-Za-z0-9_']+!""").containsMatchIn(stripped)) {
                            "3D multi-sheet span reference refused in $sName!$a1"
                        }
                        expandedMap[a1] = inp
                    } else {
                        val expandedBody = expand3dSpansInBody(inp.body, orderedNames, indexByLower)
                        expandedMap[a1] = FormulaEval.CellInput.Formula("=$expandedBody")
                    }
                } else {
                    expandedMap[a1] = inp
                }
            }
            normSheets[sName] = expandedMap
        }

        // Build global dependency graph over node keys "SheetName!A1"
        val allNodes = LinkedHashSet<String>()
        val deps = LinkedHashMap<String, Set<String>>()
        for ((sName, cmap) in normSheets) {
            for ((a1, inp) in cmap) {
                val key = "$sName!$a1"
                allNodes.add(key)
                if (inp is FormulaEval.CellInput.Formula) {
                    deps[key] = extractDependencies(sName, inp.body, canonicalByLower)
                } else {
                    deps[key] = emptySet()
                }
            }
        }
        for (dset in deps.values) allNodes.addAll(dset)

        // 3-color DFS topological sort + cycle participant marking
        val state = HashMap<String, Int>() // 0=unvisited, 1=visiting, 2=done
        val order = ArrayList<String>(allNodes.size)
        val inCycle = LinkedHashSet<String>()
        val stack = ArrayList<String>()

        fun dfs(u: String) {
            state[u] = 1
            stack.add(u)
            for (v in deps[u].orEmpty()) {
                when (state[v] ?: 0) {
                    0 -> dfs(v)
                    1 -> {
                        val idx = stack.indexOf(v)
                        val witness = if (idx >= 0) (stack.subList(idx, stack.size) + v).joinToString(" -> ") else "$u -> $v"
                        throw IllegalArgumentException("cross-sheet circular dependency: $witness")
                    }
                }
            }
            stack.removeAt(stack.lastIndex)
            state[u] = 2
            order.add(u)
        }

        for (n in allNodes) {
            if ((state[n] ?: 0) == 0) dfs(n)
        }

        val globalValues = LinkedHashMap<String, FormulaEval.CellValue>()
        for (node in order) {
            val bang = node.lastIndexOf('!')
            val sName = node.substring(0, bang)
            val a1 = node.substring(bang + 1)
            if (sName.lowercase() !in canonicalByLower) {
                globalValues[node] = FormulaEval.CellValue.Err(CellErr.Err.REF)
                continue
            }
            val inp = normSheets[sName]?.get(a1) ?: FormulaEval.CellInput.Blank
            globalValues[node] = when (inp) {
                FormulaEval.CellInput.Blank -> FormulaEval.CellValue.Empty
                is FormulaEval.CellInput.NumberLiteral -> FormulaEval.CellValue.Num(inp.value)
                is FormulaEval.CellInput.TextLiteral -> FormulaEval.CellValue.Text(inp.value)
                is FormulaEval.CellInput.BoolLiteral -> FormulaEval.CellValue.Bool(inp.value)
                is FormulaEval.CellInput.ErrorLiteral -> FormulaEval.CellValue.Err(inp.error)
                is FormulaEval.CellInput.Formula -> evalFormulaInContext(sName, inp.body, canonicalByLower, globalValues)
            }
        }

        val perSheet = LinkedHashMap<String, Map<String, FormulaEval.CellValue>>()
        for ((sName, cmap) in normSheets) {
            val sv = LinkedHashMap<String, FormulaEval.CellValue>()
            for (a1 in cmap.keys) {
                val v = globalValues["$sName!$a1"] ?: FormulaEval.CellValue.Empty
                if (v != FormulaEval.CellValue.Empty) sv[a1] = v
            }
            perSheet[sName] = sv
        }
        return EvaluatedBook(normSheets.keys.toList(), perSheet, inCycle)
    }

    fun write(sheets: List<SheetDef>): ByteArray = writeInternal(sheets, allow3dSpans = false)

    private fun writeInternal(sheets: List<SheetDef>, allow3dSpans: Boolean): ByteArray {
        val evaluated = evaluateInternal(sheets, allow3dSpans)
        val parts = LinkedHashMap<String, String>()
        parts["[Content_Types].xml"] = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            append("""<Default Extension="xml" ContentType="application/xml"/>""")
            append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
            for (i in sheets.indices) {
                append("""<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            }
            append("</Types>")
        }
        parts["_rels/.rels"] =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<Relationships xmlns="$PKG_REL_NS">""" +
                """<Relationship Id="rId1" Type="$REL_DOC_NS/officeDocument" Target="xl/workbook.xml"/>""" +
                """</Relationships>"""
        parts["xl/workbook.xml"] = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<workbook xmlns="$MAIN_NS" xmlns:r="$REL_DOC_NS"><sheets>""")
            for ((i, s) in sheets.withIndex()) {
                append("""<sheet name="${escapeXml(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
            }
            append("</sheets></workbook>")
        }
        parts["xl/_rels/workbook.xml.rels"] = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="$PKG_REL_NS">""")
            for (i in sheets.indices) {
                append("""<Relationship Id="rId${i + 1}" Type="$REL_DOC_NS/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""")
            }
            append("</Relationships>")
        }
        for ((i, s) in sheets.withIndex()) {
            val canonCells = LinkedHashMap<String, FormulaEval.CellInput>()
            for ((k, v) in s.cells) canonCells[FormulaEval.canonicalCellRef(k)] = v
            val vals = evaluated.values[s.name].orEmpty()
            parts["xl/worksheets/sheet${i + 1}.xml"] = buildSheetXml(canonCells, vals)
        }
        return storedZip(parts)
    }

    fun read(xlsxBytes: ByteArray): List<SheetDef> {
        require(xlsxBytes.size <= MAX_ZIP_BYTES) { "xlsx archive exceeds $MAX_ZIP_BYTES bytes" }
        val parts = readZip(xlsxBytes)
        require(parts.keys.none { it.endsWith("vbaProject.bin", ignoreCase = true) }) {
            "VBA macro part (vbaProject.bin) refused"
        }
        val wbXml = BoundedXml.parse(parts["xl/workbook.xml"] ?: throw IllegalArgumentException("missing xl/workbook.xml"))
        val relsXml = BoundedXml.parse(
            parts["xl/_rels/workbook.xml.rels"] ?: throw IllegalArgumentException("missing xl/_rels/workbook.xml.rels"),
        )
        val relTargets = HashMap<String, String>()
        for (rel in relsXml.children.filter { it.name.local == "Relationship" }) {
            val id = rel.attr("Id") ?: continue
            val target = rel.attr("Target") ?: continue
            relTargets[id] = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
        }
        val sharedStrings = parts["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheetsEl = wbXml.children.firstOrNull { it.name.local == "sheets" }
            ?: throw IllegalArgumentException("missing <sheets> in workbook.xml")
        val sheetEls = sheetsEl.children.filter { it.name.local == "sheet" }
        require(sheetEls.size in 1..MAX_SHEETS) { "sheet count ${sheetEls.size} outside 1..$MAX_SHEETS" }

        return sheetEls.mapIndexed { idx, el ->
            val name = el.attr("name") ?: throw IllegalArgumentException("sheet missing name")
            val rId = el.attr("id", REL_DOC_NS) ?: el.attr("id") ?: "rId${idx + 1}"
            val target = relTargets[rId] ?: "xl/worksheets/sheet${idx + 1}.xml"
            val sheetBytes = parts[target] ?: throw IllegalArgumentException("missing worksheet part $target")
            SheetDef(name, parseSheetInputs(sheetBytes, sharedStrings))
        }
    }

    private fun evalFormulaInContext(
        currentSheet: String,
        body: String,
        canonicalByLower: Map<String, String>,
        globalValues: Map<String, FormulaEval.CellValue>,
    ): FormulaEval.CellValue {
        // Project current sheet + referenced cross-sheet cells into a temporary local sheet environment.
        // Any cross-sheet reference `Other!A1` or `'Other'!A1:B2` is mapped into synthetic free columns Starting at ZZ/AAA?
        // Even cleaner & exact: allocate synthetic local cell coordinates on unused high rows (row 900_000+) for each cross-sheet range/cell!
        val localInputs = LinkedHashMap<String, FormulaEval.CellInput>()
        for ((nodeKey, cv) in globalValues) {
            val bang = nodeKey.lastIndexOf('!')
            if (nodeKey.substring(0, bang) == currentSheet) {
                val a1 = nodeKey.substring(bang + 1)
                localInputs[a1] = cellValueToLiteral(cv)
            }
        }
        var nextSynthRow = 900_001
        val rewritten = rewriteOutsideStrings(body) { seg ->
            CROSS_RANGE_RE.replace(seg) { m ->
                val rawSheet = m.groups[1]?.value?.replace("''", "'") ?: m.groups[2]!!.value
                val targetSheet = canonicalByLower[rawSheet.lowercase()]
                if (targetSheet == null) {
                    val errCell = "ZZ$nextSynthRow"
                    nextSynthRow++
                    localInputs[errCell] = FormulaEval.CellInput.ErrorLiteral(CellErr.Err.REF)
                    errCell
                } else {
                    val startA1 = FormulaEval.canonicalCellRef(m.groups[3]!!.value)
                    val endRaw = m.groups[4]?.value
                    if (endRaw == null) {
                        val syn = "ZZ$nextSynthRow"
                        nextSynthRow++
                        val v = globalValues["$targetSheet!$startA1"] ?: FormulaEval.CellValue.Empty
                        localInputs[syn] = cellValueToLiteral(v)
                        syn
                    } else {
                        val range = SheetRange.parse("$startA1:${FormulaEval.canonicalCellRef(endRaw)}")
                        val baseRow = nextSynthRow
                        nextSynthRow += range.height + 1
                        for (dr in 0 until range.height) {
                            for (dc in 0 until range.width) {
                                val srcA1 = SheetRange.Cell(range.top.col + dc, range.top.row + dr).toA1()
                                val dstA1 = SheetRange.Cell(1 + dc, baseRow + dr).toA1()
                                val v = globalValues["$targetSheet!$srcA1"] ?: FormulaEval.CellValue.Empty
                                localInputs[dstA1] = cellValueToLiteral(v)
                            }
                        }
                        val tl = SheetRange.Cell(1, baseRow).toA1()
                        val br = SheetRange.Cell(range.width, baseRow + range.height - 1).toA1()
                        "$tl:$br"
                    }
                }
            }
        }
        val probeCell = "ZZ900000"
        localInputs[probeCell] = FormulaEval.CellInput.Formula("=$rewritten")
        return FormulaEval.evaluateSheet(localInputs).values[probeCell] ?: FormulaEval.CellValue.Empty
    }

    private fun cellValueToLiteral(cv: FormulaEval.CellValue): FormulaEval.CellInput = when (cv) {
        FormulaEval.CellValue.Empty -> FormulaEval.CellInput.Blank
        is FormulaEval.CellValue.Num -> FormulaEval.CellInput.NumberLiteral(cv.value)
        is FormulaEval.CellValue.Text -> FormulaEval.CellInput.TextLiteral(cv.value)
        is FormulaEval.CellValue.Bool -> FormulaEval.CellInput.BoolLiteral(cv.value)
        is FormulaEval.CellValue.Err -> FormulaEval.CellInput.ErrorLiteral(cv.error)
    }

    private fun extractDependencies(
        currentSheet: String,
        body: String,
        canonicalByLower: Map<String, String>,
    ): Set<String> {
        val out = LinkedHashSet<String>()
        val stripped = stripStringLiterals(body)
        val consumedRanges = ArrayList<IntRange>()
        for (m in CROSS_RANGE_RE.findAll(stripped)) {
            consumedRanges.add(m.range)
            val rawSheet = m.groups[1]?.value?.replace("''", "'") ?: m.groups[2]!!.value
            val targetSheet = canonicalByLower[rawSheet.lowercase()] ?: rawSheet
            val c1 = FormulaEval.canonicalCellRef(m.groups[3]!!.value)
            val c2 = m.groups[4]?.value?.let(FormulaEval::canonicalCellRef)
            if (c2 == null) {
                out.add("$targetSheet!$c1")
            } else {
                val r = SheetRange.parse("$c1:$c2")
                require(r.cells <= FormulaEval.MAX_RANGE_CELLS) { "cross-sheet range exceeds cap" }
                for (row in r.top.row..r.bottom.row) {
                    for (col in r.top.col..r.bottom.col) {
                        out.add("$targetSheet!${SheetRange.Cell(col, row).toA1()}")
                    }
                }
            }
        }
        // Mask out cross-sheet substrings and collect same-sheet references
        val mask = StringBuilder(stripped)
        for (rg in consumedRanges) {
            for (idx in rg) mask.setCharAt(idx, ' ')
        }
        val localRangeRe = Regex("""(\$?[A-Za-z]{1,3}\$?[1-9][0-9]*)(?::(\$?[A-Za-z]{1,3}\$?[1-9][0-9]*))?(?![A-Za-z0-9_(!])""")
        for (m in localRangeRe.findAll(mask.toString())) {
            val tok1 = m.groups[1]!!.value.uppercase()
            if (tok1 == "TRUE" || tok1 == "FALSE") continue
            val c1 = runCatching { FormulaEval.canonicalCellRef(tok1) }.getOrNull() ?: continue
            val tok2 = m.groups[2]?.value
            if (tok2 == null) {
                out.add("$currentSheet!$c1")
            } else {
                val c2 = FormulaEval.canonicalCellRef(tok2)
                val r = SheetRange.parse("$c1:$c2")
                require(r.cells <= FormulaEval.MAX_RANGE_CELLS) { "range exceeds cap" }
                for (row in r.top.row..r.bottom.row) {
                    for (col in r.top.col..r.bottom.col) {
                        out.add("$currentSheet!${SheetRange.Cell(col, row).toA1()}")
                    }
                }
            }
        }
        return out
    }

    private fun stripStringLiterals(s: String): String = rewriteOutsideStrings(s) { it }
        .let { _ ->
            val sb = StringBuilder(s.length)
            var inStr = false
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '"') {
                    if (inStr && i + 1 < s.length && s[i + 1] == '"') {
                        sb.append("  "); i += 2; continue
                    }
                    inStr = !inStr
                    sb.append(' ')
                } else {
                    sb.append(if (inStr) ' ' else c)
                }
                i++
            }
            sb.toString()
        }

    private fun rewriteOutsideStrings(s: String, fn: (String) -> String): String {
        val sb = StringBuilder()
        var i = 0
        var segStart = 0
        var inStr = false
        while (i < s.length) {
            if (s[i] == '"') {
                if (!inStr) {
                    sb.append(fn(s.substring(segStart, i)))
                    segStart = i
                    inStr = true
                } else if (i + 1 < s.length && s[i + 1] == '"') {
                    i += 2; continue
                } else {
                    sb.append(s.substring(segStart, i + 1))
                    segStart = i + 1
                    inStr = false
                }
            }
            i++
        }
        if (segStart < s.length) {
            sb.append(if (inStr) s.substring(segStart) else fn(s.substring(segStart)))
        }
        return sb.toString()
    }

    private fun validateSheetName(name: String) {
        require(name.isNotBlank() && name.length <= 31) { "invalid sheet name '$name' (1..31 chars required)" }
        require(name.none { it in charArrayOf(':', '\\', '/', '?', '*', '[', ']') }) {
            "sheet name '$name' contains forbidden ECMA-376 character"
        }
    }

    private fun buildSheetXml(
        inputs: Map<String, FormulaEval.CellInput>,
        values: Map<String, FormulaEval.CellValue>,
    ): String {
        val byRow = LinkedHashMap<Int, MutableList<Pair<SheetRange.Cell, String>>>()
        for (a1 in inputs.keys) {
            val c = SheetRange.parseCell(a1)
            byRow.getOrPut(c.row) { ArrayList() }.add(c to a1)
        }
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="$MAIN_NS"><sheetData>""")
            for (r in byRow.keys.sorted()) {
                append("""<row r="$r">""")
                for ((_, a1) in byRow[r]!!.sortedBy { it.first.col }) {
                    val inp = inputs[a1]!!
                    val v = values[a1] ?: FormulaEval.CellValue.Empty
                    if (inp is FormulaEval.CellInput.Formula) {
                        val fEsc = escapeXml(inp.body)
                        when (v) {
                            FormulaEval.CellValue.Empty -> append("""<c r="$a1"><f>$fEsc</f></c>""")
                            is FormulaEval.CellValue.Num ->
                                append("""<c r="$a1"><f>$fEsc</f><v>${FormulaEval.formatCanonicalNumber(v.value)}</v></c>""")
                            is FormulaEval.CellValue.Bool ->
                                append("""<c r="$a1" t="b"><f>$fEsc</f><v>${if (v.value) 1 else 0}</v></c>""")
                            is FormulaEval.CellValue.Err ->
                                append("""<c r="$a1" t="e"><f>$fEsc</f><v>${escapeXml(v.error.literal)}</v></c>""")
                            is FormulaEval.CellValue.Text ->
                                append("""<c r="$a1" t="str"><f>$fEsc</f><v xml:space="preserve">${escapeXml(v.value)}</v></c>""")
                        }
                    } else {
                        when (inp) {
                            FormulaEval.CellInput.Blank -> {}
                            is FormulaEval.CellInput.NumberLiteral ->
                                append("""<c r="$a1"><v>${FormulaEval.formatCanonicalNumber(inp.value)}</v></c>""")
                            is FormulaEval.CellInput.BoolLiteral ->
                                append("""<c r="$a1" t="b"><v>${if (inp.value) 1 else 0}</v></c>""")
                            is FormulaEval.CellInput.ErrorLiteral ->
                                append("""<c r="$a1" t="e"><v>${escapeXml(inp.error.literal)}</v></c>""")
                            is FormulaEval.CellInput.TextLiteral ->
                                append("""<c r="$a1" t="inlineStr"><is><t xml:space="preserve">${escapeXml(inp.value)}</t></is></c>""")
                            is FormulaEval.CellInput.Formula -> {}
                        }
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val root = BoundedXml.parse(xmlBytes)
        return root.children.filter { it.name.local == "si" }.map { si ->
            val direct = si.children.firstOrNull { it.name.local == "t" }
            if (direct != null) direct.text
            else si.children.filter { it.name.local == "r" }
                .joinToString("") { r -> r.children.firstOrNull { it.name.local == "t" }?.text.orEmpty() }
        }
    }

    private fun parseSheetInputs(sheetBytes: ByteArray, shared: List<String>): Map<String, FormulaEval.CellInput> {
        val root = BoundedXml.parse(sheetBytes)
        val data = root.children.firstOrNull { it.name.local == "sheetData" } ?: return emptyMap()
        val out = LinkedHashMap<String, FormulaEval.CellInput>()
        for (row in data.children.filter { it.name.local == "row" }) {
            for (c in row.children.filter { it.name.local == "c" }) {
                val ref = FormulaEval.canonicalCellRef(c.attr("r") ?: continue)
                val t = c.attr("t").orEmpty()
                val fEl = c.children.firstOrNull { it.name.local == "f" }
                val vText = c.children.firstOrNull { it.name.local == "v" }?.text
                if (fEl != null) {
                    out[ref] = FormulaEval.CellInput.Formula("=" + fEl.text)
                } else {
                    out[ref] = when (t) {
                        "inlineStr" -> {
                            val isEl = c.children.firstOrNull { it.name.local == "is" }
                            val txt = isEl?.children?.firstOrNull { it.name.local == "t" }?.text.orEmpty()
                            FormulaEval.CellInput.TextLiteral(txt)
                        }
                        "s" -> {
                            val idx = vText?.trim()?.toIntOrNull() ?: throw IllegalArgumentException("bad sharedString index")
                            require(idx in shared.indices) { "sharedString index $idx out of bounds" }
                            FormulaEval.CellInput.TextLiteral(shared[idx])
                        }
                        "str" -> FormulaEval.CellInput.TextLiteral(vText.orEmpty())
                        "b" -> FormulaEval.CellInput.BoolLiteral(vText?.trim() == "1" || vText.equals("true", true))
                        "e" -> FormulaEval.CellInput.ErrorLiteral(
                            CellErr.parse(vText.orEmpty()),
                        )
                        else -> if (vText == null || vText.isEmpty()) FormulaEval.CellInput.Blank
                        else FormulaEval.CellInput.NumberLiteral(vText.trim().toDouble())
                    }
                }
            }
        }
        return out
    }

    private fun escapeXml(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        var expanded = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            val buf = ByteArray(8192)
            var e: ZipEntry? = zis.nextEntry
            var count = 0
            while (e != null) {
                count++
                require(count <= 128) { "too many zip entries" }
                val name = e.name
                require(!name.contains("..") && !name.startsWith("/")) { "unsafe zip entry $name" }
                if (!e.isDirectory) {
                    val baos = ByteArrayOutputStream()
                    while (true) {
                        val n = zis.read(buf)
                        if (n < 0) break
                        expanded += n
                        require(expanded <= MAX_ZIP_BYTES) { "zip expansion cap exceeded" }
                        baos.write(buf, 0, n)
                    }
                    out[name] = baos.toByteArray()
                }
                e = zis.nextEntry
            }
        }
        return out
    }

    private fun storedZip(parts: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        val cd = ByteArrayOutputStream()
        var offset = 0
        var count = 0
        for ((name, content) in parts) {
            val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
            val data = content.toByteArray(StandardCharsets.UTF_8)
            val crc = CRC32().apply { update(data) }.value.toInt()
            // Local file header (zero-epoch DOS date=0x0021 => 1980-01-01 00:00:00)
            writeLe32(out, 0x04034b50)
            writeLe16(out, 20); writeLe16(out, 0); writeLe16(out, 0)
            writeLe16(out, 0); writeLe16(out, 0x0021)
            writeLe32(out, crc); writeLe32(out, data.size); writeLe32(out, data.size)
            writeLe16(out, nameBytes.size); writeLe16(out, 0)
            out.write(nameBytes); out.write(data)

            writeLe32(cd, 0x02014b50)
            writeLe16(cd, 20); writeLe16(cd, 20); writeLe16(cd, 0); writeLe16(cd, 0)
            writeLe16(cd, 0); writeLe16(cd, 0x0021)
            writeLe32(cd, crc); writeLe32(cd, data.size); writeLe32(cd, data.size)
            writeLe16(cd, nameBytes.size); writeLe16(cd, 0); writeLe16(cd, 0)
            writeLe16(cd, 0); writeLe16(cd, 0); writeLe32(cd, 0); writeLe32(cd, offset)
            cd.write(nameBytes)

            offset += 30 + nameBytes.size + data.size
            count++
        }
        val cdBytes = cd.toByteArray()
        out.write(cdBytes)
        writeLe32(out, 0x06054b50)
        writeLe16(out, 0); writeLe16(out, 0)
        writeLe16(out, count); writeLe16(out, count)
        writeLe32(out, cdBytes.size); writeLe32(out, offset)
        writeLe16(out, 0)
        return out.toByteArray()
    }

    private fun writeLe16(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
    }

    private fun writeLe32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
    }
}

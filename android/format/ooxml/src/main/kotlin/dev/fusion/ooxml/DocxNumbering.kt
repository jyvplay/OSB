// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.nio.charset.StandardCharsets

/**
 * Clean-room ECMA-376 Part 1 §17.9 WordprocessingML `word/numbering.xml` parser and
 * 9-level (`ilvl` `0..8`) hierarchical list-counter engine over [BoundedXml].
 *
 * Supported formats (`ST_NumberFormat`):
 * - `decimal` (`1, 2, 3`), `decimalZero` (`01..09, 10`),
 * - `lowerLetter` (`a..z, aa..zz`), `upperLetter` (`A..Z, AA..ZZ`),
 * - `lowerRoman` (`i..mmmcmxcix`), `upperRoman` (`I..MMMCMXCIX`),
 * - `bullet`, `none`.
 *
 * Supported rules:
 * - Multi-level `%1.%2.%3.` `w:lvlText` placeholder substitution,
 * - `w:start` initial counter value (`>= 0`),
 * - `w:lvlRestart` (`0` = never restart on shallower step; `k >= 1` = restart when `ilvl <= k - 1` steps),
 * - `w:num` -> `w:abstractNumId` mapping with per-instance `w:lvlOverride` / `w:startOverride`.
 */
object DocxNumbering {
    const val SCOPE =
        "ECMA-376 §17.9 9-level numbering.xml parser + counter stepper (decimal/decimalZero/letter/roman/bullet, lvlText %1..%9, lvlRestart, startOverride); legacy lvlLegacy and picture bullets out of scope."

    const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    const val MAX_LEVEL = 8
    const val MAX_ROMAN = 3999

    enum class NumFormat(val wire: String) {
        DECIMAL("decimal"),
        DECIMAL_ZERO("decimalZero"),
        LOWER_LETTER("lowerLetter"),
        UPPER_LETTER("upperLetter"),
        LOWER_ROMAN("lowerRoman"),
        UPPER_ROMAN("upperRoman"),
        BULLET("bullet"),
        NONE("none");

        companion object {
            fun fromWire(raw: String): NumFormat =
                entries.firstOrNull { it.wire == raw }
                    ?: throw IllegalArgumentException("unsupported w:numFmt '$raw'")
        }
    }

    data class LevelSpec(
        val ilvl: Int,
        val start: Int = 1,
        val numFmt: NumFormat = NumFormat.DECIMAL,
        val lvlText: String = "%${ilvl + 1}.",
        val lvlRestart: Int = ilvl,
    ) {
        init {
            require(ilvl in 0..MAX_LEVEL) { "ilvl $ilvl outside 0..$MAX_LEVEL" }
            require(start >= 0) { "start $start must be >= 0" }
            require(lvlRestart in 0..(MAX_LEVEL + 1)) { "lvlRestart $lvlRestart out of bounds" }
        }
    }

    data class AbstractNum(val abstractNumId: Int, val levels: Map<Int, LevelSpec>)

    data class NumOverride(val ilvl: Int, val startOverride: Int?, val levelReplacement: LevelSpec?)

    data class NumInstance(
        val numId: Int,
        val abstractNumId: Int,
        val overrides: Map<Int, NumOverride> = emptyMap(),
    )

    data class Definitions(
        val abstractNums: Map<Int, AbstractNum>,
        val nums: Map<Int, NumInstance>,
    ) {
        fun resolveLevel(numId: Int, ilvl: Int): LevelSpec {
            require(ilvl in 0..MAX_LEVEL) { "ilvl $ilvl outside 0..$MAX_LEVEL" }
            val inst = nums[numId] ?: throw IllegalArgumentException("unknown numId $numId")
            val abs = abstractNums[inst.abstractNumId]
                ?: throw IllegalArgumentException("numId $numId references missing abstractNumId ${inst.abstractNumId}")
            val ovr = inst.overrides[ilvl]
            val base = ovr?.levelReplacement ?: abs.levels[ilvl] ?: LevelSpec(ilvl = ilvl)
            return if (ovr?.startOverride != null) base.copy(start = ovr.startOverride) else base
        }
    }

    data class ParagraphRef(val numId: Int?, val ilvl: Int = 0, val text: String)

    data class RenderedItem(val marker: String, val numId: Int?, val ilvl: Int, val text: String)

    fun parseXml(xml: String): Definitions = parseXmlBytes(xml.toByteArray(StandardCharsets.UTF_8))

    fun parseXmlBytes(xmlBytes: ByteArray): Definitions {
        val root = BoundedXml.parse(xmlBytes)
        require(root.name.local == "numbering") { "expected <w:numbering>, got <${root.name.local}>" }

        val abstractNums = LinkedHashMap<Int, AbstractNum>()
        val nums = LinkedHashMap<Int, NumInstance>()

        for (child in root.children) {
            when (child.name.local) {
                "abstractNum" -> {
                    val id = wAttr(child, "abstractNumId")?.toIntOrNull()
                        ?: throw IllegalArgumentException("w:abstractNum missing abstractNumId")
                    require(id !in abstractNums) { "duplicate abstractNumId $id" }
                    val levels = LinkedHashMap<Int, LevelSpec>()
                    for (lvlNode in child.children) {
                        if (lvlNode.name.local == "lvl") {
                            val spec = parseLvlNode(lvlNode)
                            require(spec.ilvl !in levels) { "duplicate ilvl ${spec.ilvl} in abstractNum $id" }
                            levels[spec.ilvl] = spec
                        }
                    }
                    abstractNums[id] = AbstractNum(id, levels)
                }
                "num" -> {
                    val id = wAttr(child, "numId")?.toIntOrNull()
                        ?: throw IllegalArgumentException("w:num missing numId")
                    require(id > 0 && id !in nums) { "invalid or duplicate numId $id" }
                    var absRef: Int? = null
                    val overrides = LinkedHashMap<Int, NumOverride>()
                    for (sub in child.children) {
                        when (sub.name.local) {
                            "abstractNumId" -> {
                                absRef = wAttr(sub, "val")?.toIntOrNull()
                                    ?: throw IllegalArgumentException("w:abstractNumId missing w:val")
                            }
                            "lvlOverride" -> {
                                val ilvl = wAttr(sub, "ilvl")?.toIntOrNull()
                                    ?: throw IllegalArgumentException("w:lvlOverride missing w:ilvl")
                                require(ilvl in 0..MAX_LEVEL) { "override ilvl $ilvl out of range" }
                                var startOvr: Int? = null
                                var repLvl: LevelSpec? = null
                                for (ovrChild in sub.children) {
                                    when (ovrChild.name.local) {
                                        "startOverride" -> {
                                            startOvr = wAttr(ovrChild, "val")?.toIntOrNull()
                                                ?: throw IllegalArgumentException("w:startOverride missing w:val")
                                        }
                                        "lvl" -> repLvl = parseLvlNode(ovrChild)
                                    }
                                }
                                overrides[ilvl] = NumOverride(ilvl, startOvr, repLvl)
                            }
                        }
                    }
                    val resolvedAbs = absRef ?: throw IllegalArgumentException("w:num $id missing w:abstractNumId")
                    nums[id] = NumInstance(id, resolvedAbs, overrides)
                }
            }
        }
        return Definitions(abstractNums, nums)
    }

    private fun parseLvlNode(node: BoundedXml.Node): LevelSpec {
        val ilvl = wAttr(node, "ilvl")?.toIntOrNull()
            ?: throw IllegalArgumentException("w:lvl missing w:ilvl")
        var start = 1
        var fmt = NumFormat.DECIMAL
        var lvlText: String? = null
        var lvlRestart: Int? = null
        for (c in node.children) {
            when (c.name.local) {
                "start" -> start = wAttr(c, "val")?.toIntOrNull()
                    ?: throw IllegalArgumentException("w:start missing w:val")
                "numFmt" -> fmt = NumFormat.fromWire(
                    wAttr(c, "val") ?: throw IllegalArgumentException("w:numFmt missing w:val"),
                )
                "lvlText" -> lvlText = wAttr(c, "val") ?: ""
                "lvlRestart" -> lvlRestart = wAttr(c, "val")?.toIntOrNull()
                    ?: throw IllegalArgumentException("w:lvlRestart missing w:val")
            }
        }
        return LevelSpec(
            ilvl = ilvl,
            start = start,
            numFmt = fmt,
            lvlText = lvlText ?: "%${ilvl + 1}.",
            lvlRestart = lvlRestart ?: ilvl,
        )
    }

    private fun wAttr(node: BoundedXml.Node, local: String): String? =
        node.attr(local, WORD_NS) ?: node.attr(local, "")

    fun renderSequence(defs: Definitions, paragraphs: List<ParagraphRef>): List<RenderedItem> {
        val stateByNum = HashMap<Int, IntArray>()
        val initializedByNum = HashMap<Int, BooleanArray>()
        val out = ArrayList<RenderedItem>(paragraphs.size)

        for (p in paragraphs) {
            val numId = p.numId
            if (numId == null || numId == 0) {
                out.add(RenderedItem("", null, 0, p.text))
                continue
            }
            require(p.ilvl in 0..MAX_LEVEL) { "ilvl ${p.ilvl} outside 0..$MAX_LEVEL" }
            val counters = stateByNum.getOrPut(numId) { IntArray(MAX_LEVEL + 1) }
            val init = initializedByNum.getOrPut(numId) { BooleanArray(MAX_LEVEL + 1) }
            val spec = defs.resolveLevel(numId, p.ilvl)

            if (!init[p.ilvl]) {
                for (shallow in 0 until p.ilvl) {
                    if (!init[shallow]) {
                        counters[shallow] = defs.resolveLevel(numId, shallow).start
                        init[shallow] = true
                    }
                }
                counters[p.ilvl] = spec.start
                init[p.ilvl] = true
            } else {
                counters[p.ilvl] += 1
            }

            for (deeper in (p.ilvl + 1)..MAX_LEVEL) {
                val deeperSpec = defs.resolveLevel(numId, deeper)
                if (deeperSpec.lvlRestart >= p.ilvl + 1) {
                    init[deeper] = false
                    counters[deeper] = 0
                }
            }

            val marker = formatLevelMarker(defs, numId, p.ilvl, counters)
            out.add(RenderedItem(marker, numId, p.ilvl, p.text))
        }
        return out
    }

    fun formatCounter(value: Int, fmt: NumFormat): String = when (fmt) {
        NumFormat.NONE -> ""
        NumFormat.BULLET -> "\u2022"
        NumFormat.DECIMAL -> value.toString()
        NumFormat.DECIMAL_ZERO -> if (value in 0..9) "0$value" else value.toString()
        NumFormat.LOWER_LETTER -> formatAlpha(value, lower = true)
        NumFormat.UPPER_LETTER -> formatAlpha(value, lower = false)
        NumFormat.LOWER_ROMAN -> formatRoman(value).lowercase()
        NumFormat.UPPER_ROMAN -> formatRoman(value)
    }

    private fun formatLevelMarker(defs: Definitions, numId: Int, ilvl: Int, counters: IntArray): String {
        val spec = defs.resolveLevel(numId, ilvl)
        if (spec.numFmt == NumFormat.NONE) return ""
        if (spec.numFmt == NumFormat.BULLET) {
            return if ('%' !in spec.lvlText && spec.lvlText.isNotEmpty()) spec.lvlText else "\u2022"
        }
        val sb = StringBuilder(spec.lvlText.length + 8)
        var i = 0
        while (i < spec.lvlText.length) {
            if (spec.lvlText[i] == '%' && i + 1 < spec.lvlText.length && spec.lvlText[i + 1] in '1'..'9') {
                val refLevel = spec.lvlText[i + 1] - '1'
                val refFmt = defs.resolveLevel(numId, refLevel).numFmt
                sb.append(formatCounter(counters[refLevel], refFmt))
                i += 2
            } else {
                sb.append(spec.lvlText[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun formatAlpha(value: Int, lower: Boolean): String {
        require(value >= 1) { "letter numbering requires counter >= 1, got $value" }
        val idx = (value - 1) % 26
        val repeat = (value - 1) / 26 + 1
        val ch = (if (lower) 'a' else 'A') + idx
        return buildString(repeat) { repeat(repeat) { append(ch) } }
    }

    private fun formatRoman(value: Int): String {
        require(value in 1..MAX_ROMAN) { "roman numbering requires 1..$MAX_ROMAN, got $value" }
        val table = arrayOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
            100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
        )
        var rem = value
        val sb = StringBuilder()
        for ((unit, sym) in table) {
            while (rem >= unit) {
                sb.append(sym)
                rem -= unit
            }
        }
        return sb.toString()
    }
}

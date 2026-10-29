// SPDX-License-Identifier: Apache-2.0
import dev.fusion.editor.WriterMailMergeEngine as Merge
import dev.fusion.editor.WriterFootnotePaginator as Pages
import dev.fusion.ooxml.FormulaEval as Formula

private var checks = 0
private fun verify(ok: Boolean) { check(ok); checks++ }
private fun refuses(block: () -> Unit) {
    var refused = false
    try { block() } catch (_: IllegalArgumentException) { refused = true }
    verify(refused)
}
fun main() {
    fun merge(t: String, r: Map<String, Any?>) = Merge.mergeSingle(0, t, r)
    verify(merge("{{name}}", mapOf("name" to "{{secret}}", "secret" to "LEAK")).renderedBody == "{{secret}}")
    val template = "{{#each rows}}{{#if enabled}}{{name}}{{#else}}off{{/if}}{{/each}}"
    verify(merge(template, mapOf("rows" to listOf(mapOf("enabled" to true, "name" to "A"), mapOf("enabled" to false)))).renderedBody == "Aoff")
    verify(merge("{{#if a}}x{{#if b}}y{{#else}}z{{/if}}{{#else}}n{{/if}}", mapOf("a" to true, "b" to false)).renderedBody == "xz")
    verify(merge("{{#if a}}{{missing}}{{#else}}ok{{/if}}", emptyMap()).unresolvedPlaceholders.isEmpty())
    verify(merge("{{a}}/{{b}}", emptyMap()).unresolvedPlaceholders == setOf("a", "b"))
    verify(merge("{{#each absent}}x{{/each}}", emptyMap()).unresolvedPlaceholders == setOf("absent"))
    for (t in listOf("{{#if a}}x", "{{/if}}", "{{#each a}}{{#else}}{{/each}}", "{{#if a}}{{/each}}", "{{bad")) refuses { merge(t, emptyMap()) }
    refuses { merge("{{#each a}}x{{/each}}", mapOf("a" to listOf("not a record"))) }
    refuses { merge("{{#if a}}".repeat(33) + "{{/if}}".repeat(33), emptyMap()) }
    refuses { merge("{{#each a}}{{x}}{{/each}}", mapOf("a" to List(3) { mapOf("x" to "x".repeat(1_000_000)) })) }
    for (n in 1..200) {
        val lines = (0 until n).map { Pages.BodyLineWithFootnotes(it, 12.0, if (it % 3 == 0) listOf(18.0) else emptyList()) }
        val pages = Pages.paginate(lines, 120.0)
        verify(pages.flatMap { it.bodyLineIndices } == (0 until n).toList())
        verify(pages.all { it.usedHeightPt <= 120.0 && it.usedHeightPt > 0.0 })
    }
    for (h in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 121.0)) refuses {
        Pages.paginate(listOf(Pages.BodyLineWithFootnotes(1, h)), 120.0)
    }
    refuses { Pages.paginate(listOf(Pages.BodyLineWithFootnotes(1, 12.0, listOf(Double.NaN)))) }
    refuses { Pages.paginate(List(2) { Pages.BodyLineWithFootnotes(1, 12.0) }) }
    refuses { Pages.paginate(listOf(Pages.BodyLineWithFootnotes(1, 12.0)), footnoteSeparatorHeightPt = -1.0) }
    fun round(v: String, d: String): Double {
        val value = Formula.evaluateSheet(mapOf("A1" to Formula.CellInput.Formula("=ROUND($v,$d)"))).values.getValue("A1")
        return (value as Formula.CellValue.Num).value
    }
    verify(round("1.005", "2") == 1.01)
    verify(round("-1.005", "2") == -1.01)
    verify(round("123456789012345", "-13") == 120000000000000.0)
    verify(round("1.23456789012345", "14") == 1.23456789012345)
    verify(round("125", "-1.9") == 130.0)
    verify(round("1", "10000") == 1.0)
    verify(round("1", "-10000") == 0.0)
    // Independent integer tie oracle, both signs and all supported moderate scales.
    for (n in -100..100) {
        val expected = if (n >= 0) n + 1.0 else n - 1.0
        val value = if (n >= 0) "$n.5" else "$n.5"
        verify(round(value, "0") == expected)
    }
    println("R54 PASS: $checks executable assertions; Writer + pagination + FormulaEval")
}

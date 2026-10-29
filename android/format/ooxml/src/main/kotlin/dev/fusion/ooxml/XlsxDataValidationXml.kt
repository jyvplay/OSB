// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Clean-room ECMA-376 §18.3.1.32 **`<dataValidations>` Worksheet Rule Builder & Cell Validator**.
 *
 * Supports dropdown list (`type="list"`), integer/decimal range (`between`, `greaterThanOrEqual`),
 * and text-length (`textLength`) validation rules, both evaluating user input at edit time AND
 * serializing standards-compliant `<dataValidations count="N">...</dataValidations>` XML fragments.
 */
object XlsxDataValidationXml {
    const val SCOPE =
        "Clean-room ECMA-376 <dataValidations> rule evaluator & XML serializer (dropdown list, whole/decimal bounds, textLength)."

    sealed interface Rule {
        val sqref: String
        val allowBlank: Boolean

        data class DropdownList(
            override val sqref: String,
            val options: List<String>,
            override val allowBlank: Boolean = true,
        ) : Rule {
            init {
                require(options.isNotEmpty() && options.all { ',' !in it && '"' !in it }) {
                    "dropdown options must be non-empty and free of commas/quotes"
                }
            }
        }

        data class NumericBetween(
            override val sqref: String,
            val min: Double,
            val max: Double,
            val wholeOnly: Boolean = false,
            override val allowBlank: Boolean = true,
        ) : Rule {
            init {
                require(min.isFinite() && max.isFinite() && min <= max) { "invalid numeric bounds [$min, $max]" }
            }
        }

        data class MaxTextLength(
            override val sqref: String,
            val maxChars: Int,
            override val allowBlank: Boolean = true,
        ) : Rule {
            init {
                require(maxChars in 0..32_767) { "maxChars outside 0..32767" }
            }
        }
    }

    fun validateCellInput(rule: Rule, rawInput: String): Boolean {
        if (rawInput.isEmpty()) return rule.allowBlank
        return when (rule) {
            is Rule.DropdownList -> rawInput in rule.options
            is Rule.NumericBetween -> {
                val v = rawInput.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: return false
                if (rule.wholeOnly && v != v.toLong().toDouble()) return false
                v in rule.min..rule.max
            }
            is Rule.MaxTextLength -> rawInput.length <= rule.maxChars
        }
    }

    fun toWorksheetXml(rules: List<Rule>): String {
        if (rules.isEmpty()) return ""
        return buildString {
            append("""<dataValidations count="${rules.size}">""")
            for (r in rules) {
                val range = SheetRange.parse(r.sqref).toA1()
                val ab = if (r.allowBlank) "1" else "0"
                when (r) {
                    is Rule.DropdownList -> {
                        val csv = escXml("\"" + r.options.joinToString(",") + "\"")
                        append("""<dataValidation type="list" allowBlank="$ab" showDropDown="0" sqref="$range"><formula1>$csv</formula1></dataValidation>""")
                    }
                    is Rule.NumericBetween -> {
                        val t = if (r.wholeOnly) "whole" else "decimal"
                        append("""<dataValidation type="$t" operator="between" allowBlank="$ab" sqref="$range"><formula1>${FormulaEval.formatCanonicalNumber(r.min)}</formula1><formula2>${FormulaEval.formatCanonicalNumber(r.max)}</formula2></dataValidation>""")
                    }
                    is Rule.MaxTextLength -> {
                        append("""<dataValidation type="textLength" operator="lessThanOrEqual" allowBlank="$ab" sqref="$range"><formula1>${r.maxChars}</formula1></dataValidation>""")
                    }
                }
            }
            append("""</dataValidations>""")
        }
    }

    private fun escXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * Bounded settings validator. Validates settings values against a catalog
 * of rules: required keys, allowed values (enum), minimum/maximum numeric
 * bounds, and pattern matches. Returns a validation report listing every
 * failed rule — all rules are evaluated, not short-circuited.
 *
 * Declared narrowings: at most [MAX_RULES] rules; at most
 * [MAX_ALLOWED_VALUES] allowed values per rule; each rule key and pattern
 * at most [MAX_KEY_LEN] and [MAX_PATTERN_LEN] chars respectively; values
 * at most [MAX_VALUE_LEN] chars; numeric bounds parsed as Decimal —
 * non-numeric values fail numeric rules.
 */
class SettingValidator private constructor(
    private val rules: LinkedHashMap<String, ValidationRule>
) {
    companion object {
        const val MAX_RULES = 256
        const val MAX_ALLOWED_VALUES = 64
        const val MAX_KEY_LEN = 256
        const val MAX_PATTERN_LEN = 128
        const val MAX_VALUE_LEN = 1024
        private val KEY_RE = Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*")

        data class ValidationRule(
            val required: Boolean,
            val allowedValues: Set<String>?,
            val min: Double?,
            val max: Double?,
            val pattern: String?
        )

        data class Violation(
            val key: String,
            val rule: String,
            val message: String
        )

        data class Report(
            val valid: Boolean,
            val violations: List<Violation>
        )

        fun empty(): SettingValidator = SettingValidator(LinkedHashMap())
    }

    fun addRule(
        key: String,
        required: Boolean = false,
        allowedValues: Set<String>? = null,
        min: Double? = null,
        max: Double? = null,
        pattern: String? = null
    ) {
        require(KEY_RE.matches(key)) { "invalid settings key: $key" }
        require(key.length <= MAX_KEY_LEN) { "key exceeds $MAX_KEY_LEN chars" }
        require(rules.size < MAX_RULES || rules.containsKey(key)) { "too many rules: $MAX_RULES" }
        require(allowedValues == null || allowedValues.size <= MAX_ALLOWED_VALUES) {
            "too many allowed values"
        }
        require(pattern == null || pattern.length <= MAX_PATTERN_LEN) {
            "pattern exceeds $MAX_PATTERN_LEN chars"
        }
        rules[key] = ValidationRule(required, allowedValues, min, max, pattern)
    }

    fun validate(values: Map<String, String>): Report {
        val violations = mutableListOf<Violation>()
        for ((key, rule) in rules) {
            val value = values[key]
            if (value == null) {
                if (rule.required) {
                    violations.add(Violation(key, "required", "required setting missing"))
                }
                continue
            }
            require(value.length <= MAX_VALUE_LEN) { "value for $key exceeds $MAX_VALUE_LEN chars" }
            if (rule.allowedValues != null && !rule.allowedValues!!.contains(value)) {
                violations.add(Violation(key, "allowedValues", "value not in allowed set"))
            }
            if (rule.min != null || rule.max != null) {
                val num = value.toDoubleOrNull()
                if (num == null) {
                    violations.add(Violation(key, "numeric", "value is not numeric"))
                } else {
                    if (rule.min != null && num < rule.min!!) {
                        violations.add(Violation(key, "min", "value below minimum"))
                    }
                    if (rule.max != null && num > rule.max!!) {
                        violations.add(Violation(key, "max", "value above maximum"))
                    }
                }
            }
            if (rule.pattern != null) {
                if (!rule.pattern!!.toRegex().matches(value)) {
                    violations.add(Violation(key, "pattern", "value does not match pattern"))
                }
            }
        }
        return Report(violations.isEmpty(), violations.toList())
    }

    fun ruleCount(): Int = rules.size
    fun removeRule(key: String): Boolean = rules.remove(key) != null
}

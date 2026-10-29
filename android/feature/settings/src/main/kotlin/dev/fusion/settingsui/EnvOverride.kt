// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settingsui

/**
 * Clean-room strict typed `KEY=VALUE` configuration override resolver with per-key provenance.
 *
 * Designed for reproducible headless/developer overrides over default settings schemas:
 * - Refuses unknown keys fail-closed (typos never silently no-op).
 * - Refuses duplicate override keys in the same override layer.
 * - Refuses YAML 1.1 "Norway problem" ambiguous boolean spellings (`yes`, `no`, `on`, `off`,
 *   `1`, `0`, `y`, `n`); ONLY lowercase/case-folded `true` or `false` is admitted for [Spec.Bool].
 * - Enforces bounded integer and millisecond ranges plus closed enum sets.
 */
object EnvOverride {
    const val SCOPE =
        "Typed KEY=VALUE schema override resolver (Bool refusing yes/no/on/off, bounded Int, bounded DurationMs, closed Enum) with DEFAULT/OVERRIDE provenance; variable interpolation (\${VAR}) out of scope."

    const val MAX_ENTRIES = 512

    enum class Source { DEFAULT, OVERRIDE }

    sealed interface Spec {
        val key: String

        data class Bool(override val key: String, val default: Boolean) : Spec
        data class IntRangeSpec(override val key: String, val default: Int, val min: Int, val max: Int) : Spec {
            init {
                require(min <= max && default in min..max) { "invalid IntRangeSpec for '$key'" }
            }
        }
        data class DurationMs(override val key: String, val default: Long, val minMs: Long, val maxMs: Long) : Spec {
            init {
                require(minMs >= 0L && minMs <= maxMs && default in minMs..maxMs) {
                    "invalid DurationMs spec for '$key'"
                }
            }
        }
        data class EnumSpec(override val key: String, val default: String, val allowed: Set<String>) : Spec {
            init {
                require(allowed.isNotEmpty() && default in allowed) { "invalid EnumSpec for '$key'" }
            }
        }
    }

    sealed interface Value {
        data class B(val v: Boolean) : Value
        data class I(val v: Int) : Value
        data class Ms(val v: Long) : Value
        data class E(val v: String) : Value
    }

    data class ResolvedEntry(val key: String, val value: Value, val source: Source)

    fun parseLines(rawText: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((lineNo, rawLine) in rawText.lines().withIndex()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            require(out.size < MAX_ENTRIES) { "override entry count exceeds $MAX_ENTRIES" }
            val eq = trimmed.indexOf('=')
            require(eq > 0) { "missing '=' on line ${lineNo + 1}: '$trimmed'" }
            val key = trimmed.substring(0, eq).trim()
            val value = unquoteOptional(trimmed.substring(eq + 1).trim())
            require(key.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,127}"))) {
                "invalid override key '$key' on line ${lineNo + 1}"
            }
            require(!value.contains("\${")) { "variable interpolation refused in '$key'" }
            require(key !in out) { "duplicate override key '$key' on line ${lineNo + 1}" }
            out[key] = value
        }
        return out
    }

    fun resolve(schema: List<Spec>, rawOverrides: Map<String, String>): Map<String, ResolvedEntry> {
        val byKey = LinkedHashMap<String, Spec>(schema.size)
        for (s in schema) {
            require(s.key !in byKey) { "duplicate schema key '${s.key}'" }
            byKey[s.key] = s
        }
        for (k in rawOverrides.keys) {
            require(k in byKey) { "unknown override key '$k'" }
        }

        val resolved = LinkedHashMap<String, ResolvedEntry>(schema.size)
        for (spec in schema) {
            val raw = rawOverrides[spec.key]
            if (raw == null) {
                resolved[spec.key] = ResolvedEntry(spec.key, defaultOf(spec), Source.DEFAULT)
            } else {
                resolved[spec.key] = ResolvedEntry(spec.key, coerce(spec, raw), Source.OVERRIDE)
            }
        }
        return resolved
    }

    private fun defaultOf(spec: Spec): Value = when (spec) {
        is Spec.Bool -> Value.B(spec.default)
        is Spec.IntRangeSpec -> Value.I(spec.default)
        is Spec.DurationMs -> Value.Ms(spec.default)
        is Spec.EnumSpec -> Value.E(spec.default)
    }

    private fun coerce(spec: Spec, raw: String): Value = when (spec) {
        is Spec.Bool -> when (raw.lowercase()) {
            "true" -> Value.B(true)
            "false" -> Value.B(false)
            "yes", "no", "on", "off", "1", "0", "y", "n" ->
                throw IllegalArgumentException("ambiguous boolean '$raw' for '${spec.key}' refused; use true or false")
            else -> throw IllegalArgumentException("invalid boolean '$raw' for '${spec.key}'")
        }
        is Spec.IntRangeSpec -> {
            val n = raw.toIntOrNull()
                ?: throw IllegalArgumentException("invalid integer '$raw' for '${spec.key}'")
            require(n in spec.min..spec.max) {
                "integer $n for '${spec.key}' outside ${spec.min}..${spec.max}"
            }
            Value.I(n)
        }
        is Spec.DurationMs -> {
            val ms = parseDurationMillis(spec.key, raw)
            require(ms in spec.minMs..spec.maxMs) {
                "duration ${ms}ms for '${spec.key}' outside ${spec.minMs}..${spec.maxMs}ms"
            }
            Value.Ms(ms)
        }
        is Spec.EnumSpec -> {
            require(raw in spec.allowed) {
                "value '$raw' for '${spec.key}' not in allowed ${spec.allowed}"
            }
            Value.E(raw)
        }
    }

    private fun parseDurationMillis(key: String, raw: String): Long {
        val lower = raw.lowercase()
        val (numPart, mult) = when {
            lower.endsWith("ms") -> lower.removeSuffix("ms") to 1L
            lower.endsWith("s") -> lower.removeSuffix("s") to 1_000L
            lower.endsWith("m") -> lower.removeSuffix("m") to 60_000L
            else -> lower to 1L
        }
        val base = numPart.toLongOrNull()
            ?: throw IllegalArgumentException("invalid duration '$raw' for '$key'")
        require(base >= 0L && base <= Long.MAX_VALUE / mult) { "duration overflow in '$key'" }
        return base * mult
    }

    private fun unquoteOptional(s: String): String =
        if (s.length >= 2 && ((s.startsWith('"') && s.endsWith('"')) || (s.startsWith('\'') && s.endsWith('\'')))) {
            s.substring(1, s.length - 1)
        } else {
            s
        }
}

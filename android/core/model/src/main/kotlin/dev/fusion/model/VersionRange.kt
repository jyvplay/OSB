// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * R176 repairs the R43 public range API and removes a colliding SemVer placeholder.
 * Uses the real SemVer parser/precedence comparator. Single explicit operator:
 * ^, ~, >=, <=, ==, !=, >, <. No unions, whitespace, bare versions or wildcards.
 * Prerelease candidates never satisfy these release-only ranges; prerelease
 * bounds refuse. Build metadata does not affect precedence. Range <=64 chars.
 * Caret bumps the first nonzero component (0.0.x bumps patch); tilde bumps minor.
 * An unrepresentable exclusive upper bound refuses, never wraps or saturates.
 */
object VersionRange {
    const val SCOPE = "Bounded explicit single-operator release-version ranges; no unions or prerelease admission."
    const val MAX_RANGE_LEN = 64
    private val EXPRESSION = Regex("(>=|<=|==|!=|>|<|\\^|~)(.+)")

    class Range internal constructor(
        private val operator: String,
        val lower: SemVer,
        val upperExclusive: SemVer?,
    ) {
        fun satisfiedBy(other: SemVer): Boolean {
            if (other.prerelease.isNotEmpty()) return false
            val comparison = other.compareTo(lower)
            return when (operator) {
                "^", "~" -> comparison >= 0 && other < requireNotNull(upperExclusive)
                ">=" -> comparison >= 0
                "<=" -> comparison <= 0
                "==" -> comparison == 0
                "!=" -> comparison != 0
                ">" -> comparison > 0
                "<" -> comparison < 0
                else -> error("unreachable operator")
            }
        }
        fun range(): String = operator + lower.toString()
    }

    fun parse(text: String): Range {
        require(text.length <= MAX_RANGE_LEN) { "range too long" }
        val match = EXPRESSION.matchEntire(text) ?: throw IllegalArgumentException("invalid range")
        val operator = match.groupValues[1]
        val lower = SemVer.parse(match.groupValues[2])
        require(lower.prerelease.isEmpty()) { "prerelease bounds not supported" }
        fun increment(value: Long): Long {
            require(value < Long.MAX_VALUE) { "range upper bound overflow" }
            return value + 1
        }
        fun version(major: Long, minor: Long, patch: Long) = SemVer(major, minor, patch, emptyList(), emptyList())
        val upper = when (operator) {
            "~" -> version(lower.major, increment(lower.minor), 0)
            "^" -> when {
                lower.major > 0 -> version(increment(lower.major), 0, 0)
                lower.minor > 0 -> version(0, increment(lower.minor), 0)
                else -> version(0, 0, increment(lower.patch))
            }
            else -> null
        }
        return Range(operator, lower, upper)
    }

    fun satisfies(version: String, range: String): Boolean = parse(range).satisfiedBy(SemVer.parse(version))
    fun isValid(text: String): Boolean = try { parse(text); true } catch (_: IllegalArgumentException) { false }
}

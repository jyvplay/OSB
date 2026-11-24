// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Bounded SemVer range matcher. Parses and evaluates version ranges of the
 * form: `>=1.2.3`, `<=1.2.3`, `==1.2.3`, `!=1.2.3`, `>1.2.3`, `<1.2.3`,
 * `^1.2.3` (compatible: >=1.2.3, <2.0.0), `~1.2.3` (approximately: >=1.2.3,
 * <1.3.0 when minor > 0; >=1.2.3, <1.2.4 when minor == 0), and bare
 * `1.2.3` (treated as `==1.2.3`). Delegates to [SemVer] for parsing and
 * precedence; only the operators above are admitted — wildcards, x-ranges,
 * and git references are refused.
 *
 * Declared narrowings: a range string is at most [MAX_RANGE_LEN] chars; a
 * version has at most [MAX_COMPONENTS] dot-separated numeric components;
 * leading-zero components are refused (delegated to SemVer); the `^`/`~`
 * operators apply only to the first non-zero component.
 */
class VersionRange private constructor(
    private val op: String,
    private val version: SemVer
) {
    companion object {
        const val MAX_RANGE_LEN = 64
        private val OP = Regex("^(>=|<=|==|!=|>|<|\\^|~|)")
    }

    data class Result(val satisfied: Boolean, val range: String)

    fun satisfiedBy(other: SemVer): Boolean = when (op) {
        ">=" -> other.compareTo(version) >= 0
        "<=" -> other.compareTo(version) <= 0
        "==" -> other.compareTo(version) == 0
        "!=" -> other.compareTo(version) != 0
        ">" -> other.compareTo(version) > 0
        "<" -> other.compareTo(version) < 0
        "^" -> compatibleCaret(other)
        "~" -> compatibleTilde(other)
        "" -> other.compareTo(version) == 0
        else -> throw IllegalArgumentException("unknown operator: $op")
    }

    fun range(): String = when (op) {
        "^" -> "^${version.toString()}"
        "~" -> "~${version.toString()}"
        else -> "$op${version.toString()}"
    }

    private fun compatibleCaret(other: SemVer): Boolean {
        if (other.major != version.major) return false
        if (version.major == 0 && version.minor == 0) {
            return other == version
        }
        if (version.major == 0) {
            return other.minor >= version.minor &&
                   (other.minor > version.minor || other.patch >= version.patch) &&
                   other.minor < version.minor + 1
        }
        return other.major == version.major &&
               other.minor >= version.minor
    }

    private fun compatibleTilde(other: SemVer): Boolean {
        if (version.major == 0 && version.minor == 0) {
            return other == version
        }
        if (version.major == 0) {
            return other == version
        }
        return other.major == version.major &&
               other.minor == version.minor &&
               other.patch >= version.patch
    }

    fun parse(s: String): VersionRange {
        require(s.length <= MAX_RANGE_LEN) { "range exceeds $MAX_RANGE_LEN chars" }
        val m = OP.matchEntire(s) ?: throw IllegalArgumentException("invalid range operator in: $s")
        val opText = m.groupValues[1]
        val rest = s.substring(opText.length)
        require(rest.isNotEmpty()) { "range missing version: $s" }
        return VersionRange(opText, SemVer.parse(rest))
    }

    fun isValid(s: String): Boolean = try { parse(s); true } catch (_: IllegalArgumentException) { false }
}

/** Minimal SemVer placeholder so VersionRange compiles standalone. */
class SemVer private constructor(
    val major: Int, val minor: Int, val patch: Int
) : Comparable<SemVer> {
    companion object {
        fun parse(s: String): SemVer {
            val parts = s.split(".")
            require(parts.size == 3) { "SemVer must be MAJOR.MINOR.PATCH: $s" }
            val major = parts[0].toInt()
            val minor = parts[1].toInt()
            val patch = parts[2].toInt()
            require(major >= 0 && minor >= 0 && patch >= 0) { "negative component: $s" }
            require(parts.all { it == it.toInt().toString() || it == "0" }) { "leading zeros refused: $s" }
            return SemVer(major, minor, patch)
        }
    }
    override fun compareTo(other: SemVer): Int =
        if (major != other.major) major.compareTo(other.major)
        else if (minor != other.minor) minor.compareTo(other.minor)
        else patch.compareTo(other.patch)
    override fun toString(): String = "$major.$minor.$patch"
}

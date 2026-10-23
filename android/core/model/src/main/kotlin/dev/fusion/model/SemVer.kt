// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Semantic Versioning 2.0.0 parser and precedence comparator, pinned to
 * semver.org (spec items 2, 9, 10, 11):
 *
 * - version core is MAJOR.MINOR.PATCH, each a non-negative integer with
 *   no leading zeroes.
 * - pre-release is dot-separated identifiers of [0-9A-Za-z-]; numeric
 *   identifiers must not have leading zeroes; identifiers must not be
 *   empty.
 * - build metadata (after '+') is dot-separated [0-9A-Za-z-] identifiers
 *   (leading zeroes allowed) and is IGNORED in precedence (item 10).
 * - precedence (item 11): compare major, minor, patch numerically; a
 *   pre-release version has lower precedence than its normal version;
 *   identifiers compare left to right — numeric identifiers numerically,
 *   alphanumeric ones in ASCII sort order, numeric < alphanumeric, and a
 *   larger set wins when all preceding identifiers are equal. Canonical
 *   chain: 1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-alpha.beta < 1.0.0-beta
 *   < 1.0.0-beta.2 < 1.0.0-beta.11 < 1.0.0-rc.1 < 1.0.0.
 *
 * Pure JVM logic; refusal is IllegalArgumentException.
 */
data class SemVer(
    val major: Long,
    val minor: Long,
    val patch: Long,
    val prerelease: List<String>,
    val build: List<String>,
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
        if (prerelease.isEmpty()) return 1   // normal > pre-release
        if (other.prerelease.isEmpty()) return -1
        val n = minOf(prerelease.size, other.prerelease.size)
        for (i in 0 until n) {
            val a = prerelease[i]
            val b = other.prerelease[i]
            val an = a.all { it in '0'..'9' }
            val bn = b.all { it in '0'..'9' }
            val c = when {
                an && bn -> a.toLong().compareTo(b.toLong())
                an -> -1 // numeric < alphanumeric
                bn -> 1
                else -> a.compareTo(b) // ASCII sort
            }
            if (c != 0) return c
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    override fun toString(): String {
        val sb = StringBuilder("$major.$minor.$patch")
        if (prerelease.isNotEmpty()) sb.append('-').append(prerelease.joinToString("."))
        if (build.isNotEmpty()) sb.append('+').append(build.joinToString("."))
        return sb.toString()
    }

    companion object {
        fun parse(text: String): SemVer {
            require(text.isNotEmpty()) { "empty version" }
            var core = text
            var build: List<String> = emptyList()
            var pre: List<String> = emptyList()
            val plus = core.indexOf('+')
            if (plus >= 0) {
                build = splitIdents(core.substring(plus + 1), allowLeadingZero = true)
                core = core.substring(0, plus)
            }
            val dash = core.indexOf('-')
            if (dash >= 0) {
                pre = splitIdents(core.substring(dash + 1), allowLeadingZero = false)
                core = core.substring(0, dash)
            }
            val parts = core.split('.')
            require(parts.size == 3) { "version core must be MAJOR.MINOR.PATCH" }
            return SemVer(number(parts[0]), number(parts[1]), number(parts[2]), pre, build)
        }

        private fun number(s: String): Long {
            require(s.isNotEmpty() && s.all { it in '0'..'9' }) { "non-numeric version field: '$s'" }
            require(s == "0" || !s.startsWith("0")) { "leading zero in version field: '$s'" }
            return s.toLongOrNull() ?: throw IllegalArgumentException("version field overflow")
        }

        private fun splitIdents(s: String, allowLeadingZero: Boolean): List<String> {
            require(s.isNotEmpty()) { "empty identifier section" }
            val parts = s.split('.')
            for (p in parts) {
                require(p.isNotEmpty()) { "empty identifier" }
                require(p.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' || it == '-' }) { "bad identifier char in '$p'" }
                if (!allowLeadingZero && p.length > 1 && p.startsWith("0") && p.all { it in '0'..'9' }) {
                    throw IllegalArgumentException("leading zero in numeric pre-release identifier: '$p'")
                }
            }
            return parts
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Bounded semantic version (MAJOR.MINOR.PATCH) parser and total-order comparator.
 */
class SemVerComparator private constructor() {
    companion object {
        private val SEMVER_RE = Regex("^(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})$")

        data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
            override fun compareTo(other: SemVer): Int {
                if (major != other.major) return major.compareTo(other.major)
                if (minor != other.minor) return minor.compareTo(other.minor)
                return patch.compareTo(other.patch)
            }
        }

        fun parse(version: String): SemVer {
            val m = SEMVER_RE.matchEntire(version) ?: throw IllegalArgumentException("invalid semver: $version")
            return SemVer(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }

        fun compare(a: String, b: String): Int = parse(a).compareTo(parse(b))
    }
}

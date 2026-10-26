package dev.fusion.model

/**
 * Caret/tilde version ranges over [SemVer] (the npm-convention
 * expansions, implemented from their published definitions):
 * `^1.2.3` = [1.2.3, 2.0.0); `^0.2.3` = [0.2.3, 0.3.0); `^0.0.3` =
 * [0.0.3, 0.0.4); `~1.2.3` = [1.2.3, 1.3.0). Lower bound inclusive,
 * upper exclusive; prereleases of the upper bound are OUTSIDE.
 *
 * Declared narrowings: single caret/tilde ranges only — no unions
 * (`||`), no wildcards (`1.x`), no hyphen ranges; range strings with
 * prerelease lower bounds refuse (the npm prerelease-matching rule is
 * a separate, gnarlier contract).
 */
object VersionRange {
    const val SCOPE = "Single ^/~ ranges with exclusive upper bounds; unions/wildcards/prerelease bounds refused."

    data class Range(val lowerInclusive: SemVer, val upperExclusive: SemVer)

    fun parse(text: String): Range {
        require(text.length >= 6 && (text[0] == '^' || text[0] == '~')) { "'$text' is not a ^/~ range" }
        val base = SemVer.parse(text.substring(1))
        require(base.prerelease.isEmpty()) { "prerelease lower bounds are out of declared scope" }
        val upper = when {
            text[0] == '~' -> SemVer(base.major, base.minor + 1, 0, emptyList(), emptyList())
            base.major > 0L -> SemVer(base.major + 1, 0, 0, emptyList(), emptyList())
            base.minor > 0L -> SemVer(0, base.minor + 1, 0, emptyList(), emptyList())
            else -> SemVer(0, 0, base.patch + 1, emptyList(), emptyList())
        }
        return Range(base, upper)
    }

    fun satisfies(version: SemVer, range: Range): Boolean =
        version >= range.lowerInclusive && version < range.upperExclusive && version.prerelease.isEmpty()

    fun satisfies(version: String, rangeText: String): Boolean =
        satisfies(SemVer.parse(version), parse(rangeText))
}

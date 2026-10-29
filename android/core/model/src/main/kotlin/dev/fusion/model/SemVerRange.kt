// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Semantic Versioning 2.0.0 (`https://semver.org/`) parser, strict precedence total order,
 * and npm/Cargo range constraint resolver (`^`, `~`, `>=`, `<=`, `>`, `<`, `=`, `*`, `1.2.x`).
 */
object SemVerRange {
    const val SCOPE =
        "SemVer 2.0.0 strict parser/comparator + caret/tilde/wildcard/comparator-set range resolver; OR ('||') compound unions out of scope."

    private val SEMVER_RE = Regex(
        """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$""",
    )

    data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: List<String> = emptyList(),
        val buildMetadata: String = "",
    ) : Comparable<Version> {
        init {
            require(major >= 0 && minor >= 0 && patch >= 0) { "negative SemVer component" }
        }

        val isPreRelease: Boolean get() = preRelease.isNotEmpty()

        override fun compareTo(other: Version): Int {
            major.compareTo(other.major).let { if (it != 0) return it }
            minor.compareTo(other.minor).let { if (it != 0) return it }
            patch.compareTo(other.patch).let { if (it != 0) return it }
            // SemVer §11: normal version has HIGHER precedence than a pre-release version!
            if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
            if (preRelease.isEmpty()) return 1
            if (other.preRelease.isEmpty()) return -1
            val n = minOf(preRelease.size, other.preRelease.size)
            for (i in 0 until n) {
                val a = preRelease[i]
                val b = other.preRelease[i]
                val aNum = a.toLongOrNull()
                val bNum = b.toLongOrNull()
                val c = when {
                    aNum != null && bNum != null -> aNum.compareTo(bNum)
                    aNum != null -> -1 // numeric identifiers always have lower precedence than non-numeric
                    bNum != null -> 1
                    else -> a.compareTo(b)
                }
                if (c != 0) return c
            }
            return preRelease.size.compareTo(other.preRelease.size)
        }

        override fun toString(): String = buildString {
            append("$major.$minor.$patch")
            if (preRelease.isNotEmpty()) append('-').append(preRelease.joinToString("."))
            if (buildMetadata.isNotEmpty()) append('+').append(buildMetadata)
        }
    }

    private enum class CmpOp { EQ, GT, GTE, LT, LTE }
    private data class Bound(val op: CmpOp, val ver: Version) {
        fun test(v: Version): Boolean = when (op) {
            CmpOp.EQ -> v.compareTo(ver) == 0
            CmpOp.GT -> v > ver
            CmpOp.GTE -> v >= ver
            CmpOp.LT -> v < ver
            CmpOp.LTE -> v <= ver
        }
    }

    class Constraint private constructor(private val bounds: List<Bound>, private val allowPreRelease: Boolean) {
        fun satisfies(v: Version): Boolean {
            if (v.isPreRelease && !allowPreRelease) return false
            return bounds.all { it.test(v) }
        }

        fun selectHighest(candidates: Iterable<Version>): Version? =
            candidates.filter(::satisfies).maxOrNull()

        companion object {
            fun parse(expr: String): Constraint {
                val trimmed = expr.trim()
                require(trimmed.isNotEmpty()) { "empty SemVer constraint" }
                require("||" !in trimmed) { "union '||' range out of scope" }
                if (trimmed == "*" || trimmed.equals("x", ignoreCase = true)) {
                    return Constraint(emptyList(), allowPreRelease = false)
                }
                val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
                val bounds = ArrayList<Bound>()
                var hasPre = false
                for (tok in tokens) {
                    when {
                        tok.startsWith("^") -> {
                            val v = parseVersion(tok.substring(1))
                            if (v.isPreRelease) hasPre = true
                            val upper = when {
                                v.major > 0 -> Version(v.major + 1, 0, 0)
                                v.minor > 0 -> Version(0, v.minor + 1, 0)
                                else -> Version(0, 0, v.patch + 1)
                            }
                            bounds.add(Bound(CmpOp.GTE, v))
                            bounds.add(Bound(CmpOp.LT, upper))
                        }
                        tok.startsWith("~") -> {
                            val v = parseVersion(tok.substring(1))
                            if (v.isPreRelease) hasPre = true
                            bounds.add(Bound(CmpOp.GTE, v))
                            bounds.add(Bound(CmpOp.LT, Version(v.major, v.minor + 1, 0)))
                        }
                        tok.endsWith(".x", true) || tok.endsWith(".*") -> {
                            val parts = tok.split('.')
                            require(parts.size in 2..3) { "invalid wildcard range '$tok'" }
                            val maj = parts[0].toInt()
                            if (parts[1].equals("x", true) || parts[1] == "*") {
                                bounds.add(Bound(CmpOp.GTE, Version(maj, 0, 0)))
                                bounds.add(Bound(CmpOp.LT, Version(maj + 1, 0, 0)))
                            } else {
                                val min = parts[1].toInt()
                                bounds.add(Bound(CmpOp.GTE, Version(maj, min, 0)))
                                bounds.add(Bound(CmpOp.LT, Version(maj, min + 1, 0)))
                            }
                        }
                        tok.startsWith(">=") -> parseVersion(tok.substring(2)).also { if (it.isPreRelease) hasPre = true }
                            .let { bounds.add(Bound(CmpOp.GTE, it)) }
                        tok.startsWith("<=") -> parseVersion(tok.substring(2)).also { if (it.isPreRelease) hasPre = true }
                            .let { bounds.add(Bound(CmpOp.LTE, it)) }
                        tok.startsWith(">") -> parseVersion(tok.substring(1)).also { if (it.isPreRelease) hasPre = true }
                            .let { bounds.add(Bound(CmpOp.GT, it)) }
                        tok.startsWith("<") -> parseVersion(tok.substring(1)).also { if (it.isPreRelease) hasPre = true }
                            .let { bounds.add(Bound(CmpOp.LT, it)) }
                        tok.startsWith("=") -> parseVersion(tok.substring(1)).also { if (it.isPreRelease) hasPre = true }
                            .let { bounds.add(Bound(CmpOp.EQ, it)) }
                        else -> parseVersion(tok).also { if (it.isPreRelease) hasPre = true }
                            .let { bounds.add(Bound(CmpOp.EQ, it)) }
                    }
                }
                return Constraint(bounds, hasPre)
            }
        }
    }

    fun parseVersion(raw: String): Version {
        val m = SEMVER_RE.matchEntire(raw.trim())
            ?: throw IllegalArgumentException("invalid SemVer 2.0.0 '$raw'")
        val maj = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val pat = m.groupValues[3].toInt()
        val pre = m.groupValues[4].takeIf { it.isNotEmpty() }?.split('.').orEmpty()
        val bld = m.groupValues[5]
        return Version(maj, min, pat, pre, bld)
    }
}

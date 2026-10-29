// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * Clean-room Niklas Luhmann **Zettelkasten Folgezettel (`1`, `1a`, `1a1`, `1b`, `2`, `10a`)**
 * Alternating Alphanumeric Branching ID Parser, Natural Comparator, and Next-Slot Allocator.
 */
object ZettelkastenFolgezettel {
    const val SCOPE =
        "Luhmann Zettelkasten Folgezettel alternating number/letter branching ID parser, natural order comparator, and child/sibling slot allocator."

    sealed interface Segment : Comparable<Segment> {
        data class Num(val v: Int) : Segment {
            override fun compareTo(other: Segment): Int = when (other) {
                is Num -> v.compareTo(other.v)
                is Alpha -> -1
            }
        }

        data class Alpha(val s: String) : Segment {
            override fun compareTo(other: Segment): Int = when (other) {
                is Num -> 1
                is Alpha -> if (s.length != other.s.length) s.length.compareTo(other.s.length) else s.compareTo(other.s)
            }
        }
    }

    data class FolgezettelId(val raw: String, val segments: List<Segment>) : Comparable<FolgezettelId> {
        override fun compareTo(other: FolgezettelId): Int {
            val minLen = minOf(segments.size, other.segments.size)
            for (i in 0 until minLen) {
                val c = segments[i].compareTo(other.segments[i])
                if (c != 0) return c
            }
            return segments.size.compareTo(other.segments.size)
        }
    }

    fun parse(raw: String): FolgezettelId {
        val clean = raw.trim().lowercase()
        require(clean.isNotEmpty() && clean[0].isDigit() && clean.all { it.isDigit() || it in 'a'..'z' }) {
            "invalid Folgezettel ID '$raw' (must start with digit and contain only [0-9a-z])"
        }
        val segs = ArrayList<Segment>()
        var i = 0
        while (i < clean.length) {
            if (clean[i].isDigit()) {
                val st = i
                while (i < clean.length && clean[i].isDigit()) i++
                segs.add(Segment.Num(clean.substring(st, i).toInt()))
            } else {
                val st = i
                while (i < clean.length && clean[i] in 'a'..'z') i++
                segs.add(Segment.Alpha(clean.substring(st, i)))
            }
        }
        return FolgezettelId(clean, segs)
    }

    fun sortIds(ids: List<String>): List<String> =
        ids.map(::parse).sorted().map { it.raw }

    /** Allocates the first unused branch child under `parent` (e.g. `12` -> `12a`, `12b`; `12a` -> `12a1`, `12a2`). */
    fun allocateNextChild(parentRaw: String, existingIds: Set<String>): String {
        val parent = parse(parentRaw)
        val normExisting = existingIds.map { it.trim().lowercase() }.toSet()
        val last = parent.segments.last()
        var n = 1
        while (true) {
            val suffix = when (last) {
                is Segment.Num -> intToLetters(n)
                is Segment.Alpha -> n.toString()
            }
            val candidate = parent.raw + suffix
            if (candidate !in normExisting) return candidate
            n++
        }
    }

    private fun intToLetters(nOneBased: Int): String {
        var v = nOneBased
        val sb = StringBuilder()
        while (v > 0) {
            val rem = (v - 1) % 26
            sb.append('a' + rem)
            v = (v - 1) / 26
        }
        return sb.reverse().toString()
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

/**
 * IDPF / W3C **EPUB Canonical Fragment Identifier** (`epubcfi(/6/4[chap01]!/4/2/1:12)`) parser,
 * canonical comparator, and spine-item + DOM-path + UTF-16 character-offset resolver.
 */
object EpubCfiFragmentLocator {
    const val SCOPE =
        "W3C EPUB Canonical Fragment Identifier (epubcfi(/6/N!...:offset)) parser, validator, and lexicographic spine-order comparator."

    data class ParsedCfi(
        val spineEvenStep: Int,
        val spineItemIndexZeroBased: Int,
        val spineIdAssertion: String?,
        val domSteps: List<Int>,
        val charOffset: Int,
    ) : Comparable<ParsedCfi> {
        override fun compareTo(other: ParsedCfi): Int {
            val sp = spineEvenStep.compareTo(other.spineEvenStep)
            if (sp != 0) return sp
            val len = minOf(domSteps.size, other.domSteps.size)
            for (i in 0 until len) {
                val c = domSteps[i].compareTo(other.domSteps[i])
                if (c != 0) return c
            }
            val sz = domSteps.size.compareTo(other.domSteps.size)
            return if (sz != 0) sz else charOffset.compareTo(other.charOffset)
        }
    }

    fun parse(cfi: String): ParsedCfi {
        val trimmed = cfi.trim()
        require(trimmed.startsWith("epubcfi(") && trimmed.endsWith(")")) { "CFI must match epubcfi(...)" }
        val inner = trimmed.substring(8, trimmed.length - 1)
        val bangParts = inner.split('!', limit = 2)
        require(bangParts.size == 2) { "EPUB CFI requires '!' indirection from package spine to content document" }

        val pkgSteps = bangParts[0].split('/').filter { it.isNotEmpty() }
        require(pkgSteps.size >= 2 && pkgSteps[0] == "6") { "EPUB CFI package path must start with /6/<evenSpineStep>" }
        val spineRaw = pkgSteps[1]
        val bracketIdx = spineRaw.indexOf('[')
        val spineStep = (if (bracketIdx >= 0) spineRaw.substring(0, bracketIdx) else spineRaw).toInt()
        require(spineStep >= 2 && spineStep % 2 == 0) { "Spine CFI step must be positive even integer, got $spineStep" }
        val idAssert = if (bracketIdx >= 0 && spineRaw.endsWith("]")) {
            spineRaw.substring(bracketIdx + 1, spineRaw.length - 1)
        } else null

        val docAndOff = bangParts[1].split(':', limit = 2)
        val charOff = if (docAndOff.size == 2) docAndOff[1].toInt().also { require(it >= 0) } else 0
        val domSteps = docAndOff[0].split('/').filter { it.isNotEmpty() }.map { raw ->
            raw.substringBefore('[').toInt().also { require(it > 0) }
        }
        require(domSteps.isNotEmpty()) { "Content document CFI path must contain at least 1 step" }
        return ParsedCfi(
            spineEvenStep = spineStep,
            spineItemIndexZeroBased = (spineStep / 2) - 1,
            spineIdAssertion = idAssert,
            domSteps = domSteps,
            charOffset = charOff,
        )
    }

    fun format(spineZeroBased: Int, spineId: String?, domSteps: List<Int>, charOffset: Int = 0): String {
        require(spineZeroBased >= 0 && domSteps.isNotEmpty() && charOffset >= 0)
        val even = (spineZeroBased + 1) * 2
        val idPart = if (!spineId.isNullOrBlank()) "[$spineId]" else ""
        val domPart = domSteps.joinToString("/")
        val offPart = if (charOffset > 0) ":$charOffset" else ""
        return "epubcfi(/6/$even$idPart!/$domPart$offPart)"
    }
}

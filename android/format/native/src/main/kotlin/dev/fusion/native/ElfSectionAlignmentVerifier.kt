// SPDX-License-Identifier: Apache-2.0
package dev.fusion.native

/**
 * Bounded ELF section alignment verifier. Validates that section alignments
 * are 0, 1, or powers of two, and that `vaddr % align == offset % align`
 * when `align > 1`.
 */
class ElfSectionAlignmentVerifier private constructor() {
    companion object {
        const val MAX_SECTIONS = 512

        data class SectionSpec(
            val name: String,
            val vaddr: Long,
            val offset: Long,
            val align: Long
        )

        fun isPowerOfTwoOrZeroOrOne(align: Long): Boolean {
            if (align < 0) return false
            if (align == 0L || align == 1L) return true
            return (align and (align - 1L)) == 0L
        }

        fun verifySection(section: SectionSpec): Boolean {
            require(section.vaddr >= 0 && section.offset >= 0) { "negative address or offset" }
            if (!isPowerOfTwoOrZeroOrOne(section.align)) return false
            if (section.align <= 1L) return true
            return (section.vaddr % section.align) == (section.offset % section.align)
        }

        fun verifyAll(sections: List<SectionSpec>): Boolean {
            require(sections.size <= MAX_SECTIONS) { "too many sections: ${sections.size}" }
            return sections.all { verifySection(it) }
        }
    }
}

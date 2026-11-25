// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

/**
 * Bounded wiki-link slug normalizer.
 */
class NotesLinkSlugNormalizer private constructor() {
    companion object {
        fun toSlug(title: String): String =
            title.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}

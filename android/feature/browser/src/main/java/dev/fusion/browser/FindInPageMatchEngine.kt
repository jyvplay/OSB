// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

import java.text.Normalizer

/**
 * Clean-room Browser Find-in-Page (`PR-07n`) & Desktop Browser Shortcut Dispatcher (`PR-07i`).
 *
 * Supports:
 * - Case-sensitive or case-insensitive + optional diacritic-folded (`café` <-> `cafe`) substring
 *   matching across DOM text blocks with wrap-around next/previous navigation.
 * - Canonical desktop browser keyboard shortcut resolution (`Ctrl+L`, `Ctrl+T`, `Ctrl+W`,
 *   `Ctrl+Shift+T`, `Ctrl+F`, `Ctrl+G`, `Ctrl+Shift+G`, `Ctrl+R`, `Ctrl+Tab`, `F12` refused/scoped).
 */
object FindInPageMatchEngine {
    data class MatchRange(
        val nodeIndex: Int,
        val startOffset: Int,
        val endOffsetExclusive: Int,
        val matchedSubstring: String,
    )

    data class FindSession(
        val query: String,
        val matches: List<MatchRange>,
        val activeMatchIndex: Int, // -1 if empty
    ) {
        fun next(): FindSession =
            if (matches.isEmpty()) this else copy(activeMatchIndex = (activeMatchIndex + 1) % matches.size)

        fun previous(): FindSession =
            if (matches.isEmpty()) this else copy(activeMatchIndex = (activeMatchIndex - 1 + matches.size) % matches.size)
    }

    enum class BrowserAction {
        FOCUS_OMNIBOX,
        NEW_TAB,
        CLOSE_TAB,
        REOPEN_CLOSED_TAB,
        FIND_IN_PAGE,
        FIND_NEXT,
        FIND_PREVIOUS,
        RELOAD_PAGE,
        NEXT_TAB,
        PREVIOUS_TAB,
        TOGGLE_READER_MODE,
        TOGGLE_SPLIT_VIEW,
    }

    fun findAll(
        textNodes: List<String>,
        query: String,
        caseSensitive: Boolean = false,
        ignoreDiacritics: Boolean = true,
    ): FindSession {
        if (query.isEmpty()) return FindSession("", emptyList(), -1)
        val normQuery = normalizeForSearch(query, caseSensitive, ignoreDiacritics)
        if (normQuery.isEmpty()) return FindSession(query, emptyList(), -1)

        val hits = ArrayList<MatchRange>()
        for ((nodeIdx, rawNode) in textNodes.withIndex()) {
            if (rawNode.isEmpty()) continue
            // Build per-codepoint index mapping when stripping combining marks so offsets map back to `rawNode`!
            val mappedChars = StringBuilder(rawNode.length)
            val origStartOffsets = ArrayList<Int>(rawNode.length + 1)
            var i = 0
            while (i < rawNode.length) {
                val piece = rawNode.substring(i, i + 1)
                val folded = normalizeForSearch(piece, caseSensitive, ignoreDiacritics)
                for (ch in folded) {
                    mappedChars.append(ch)
                    origStartOffsets.add(i)
                }
                i++
            }
            origStartOffsets.add(rawNode.length)
            val haystack = mappedChars.toString()

            var pos = 0
            while (pos <= haystack.length - normQuery.length) {
                val found = haystack.indexOf(normQuery, startIndex = pos)
                if (found < 0) break
                val origS = origStartOffsets[found]
                val origE = origStartOffsets[found + normQuery.length]
                hits.add(MatchRange(nodeIdx, origS, origE, rawNode.substring(origS, origE)))
                pos = found + normQuery.length.coerceAtLeast(1)
            }
        }
        return FindSession(query, hits, if (hits.isEmpty()) -1 else 0)
    }

    private fun normalizeForSearch(s: String, caseSensitive: Boolean, ignoreDiacritics: Boolean): String {
        val decomposed = if (ignoreDiacritics) {
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        } else {
            s
        }
        return if (caseSensitive) decomposed else decomposed.lowercase()
    }

    fun resolveShortcut(ctrlOrMeta: Boolean, shift: Boolean, alt: Boolean, keyUpper: String): BrowserAction? {
        if (!ctrlOrMeta || alt) return null
        return when (keyUpper.uppercase()) {
            "L" -> if (!shift) BrowserAction.FOCUS_OMNIBOX else null
            "T" -> if (shift) BrowserAction.REOPEN_CLOSED_TAB else BrowserAction.NEW_TAB
            "W" -> if (!shift) BrowserAction.CLOSE_TAB else null
            "F" -> if (!shift) BrowserAction.FIND_IN_PAGE else null
            "G" -> if (shift) BrowserAction.FIND_PREVIOUS else BrowserAction.FIND_NEXT
            "R" -> if (shift) BrowserAction.TOGGLE_READER_MODE else BrowserAction.RELOAD_PAGE
            "TAB" -> if (shift) BrowserAction.PREVIOUS_TAB else BrowserAction.NEXT_TAB
            "\\" -> if (!shift) BrowserAction.TOGGLE_SPLIT_VIEW else null
            else -> null
        }
    }
}

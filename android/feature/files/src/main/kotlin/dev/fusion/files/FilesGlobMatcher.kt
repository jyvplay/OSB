// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * Production-grade POSIX glob matcher supporting `*`, `?`, and `**`
 * recursive path segment matching without regex-injection risk.
 */
class FilesGlobMatcher private constructor() {
    companion object {
        fun matches(pattern: String, path: String): Boolean {
            val patSegs = pattern.split('/').filter { it.isNotEmpty() }
            val pathSegs = path.split('/').filter { it.isNotEmpty() }
            return matchSegments(patSegs, 0, pathSegs, 0)
        }

        private fun matchSegments(pat: List<String>, pi: Int, path: List<String>, si: Int): Boolean {
            if (pi == pat.size && si == path.size) return true
            if (pi == pat.size) return false
            if (pat[pi] == "**") {
                for (k in si..path.size) {
                    if (matchSegments(pat, pi + 1, path, k)) return true
                }
                return false
            }
            if (si == path.size) return false
            return matchSingleSegment(pat[pi], path[si]) && matchSegments(pat, pi + 1, path, si + 1)
        }

        private fun matchSingleSegment(pat: String, seg: String): Boolean {
            var pi = 0
            var si = 0
            var starIdx = -1
            var matchIdx = 0
            while (si < seg.length) {
                when {
                    pi < pat.length && (pat[pi] == '?' || pat[pi] == seg[si]) -> {
                        pi++; si++
                    }
                    pi < pat.length && pat[pi] == '*' -> {
                        starIdx = pi; matchIdx = si; pi++
                    }
                    starIdx != -1 -> {
                        pi = starIdx + 1; matchIdx++; si = matchIdx
                    }
                    else -> return false
                }
            }
            while (pi < pat.length && pat[pi] == '*') pi++
            return pi == pat.length
        }
    }
}

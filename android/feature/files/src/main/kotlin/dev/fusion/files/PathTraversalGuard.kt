// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * Bounded path traversal guard. Validates file paths against directory
 * traversal attacks: rejects paths containing `..` segments, absolute
 * paths, paths with NUL bytes, and paths that resolve outside an allowed
 * root prefix. Returns the normalized safe path or refuses. Does not
 * touch the filesystem — validation is pure.
 *
 * Declared narrowings: paths at most [MAX_PATH_LEN] chars; root prefix at
 * most [MAX_ROOT_LEN] chars; at most [MAX_SEGMENTS] path segments; only
 * printable ASCII plus `/` and `.` allowed in paths; empty paths refused.
 */
class PathTraversalGuard private constructor(
    private val root: String
) {
    companion object {
        const val MAX_PATH_LEN = 4096
        const val MAX_ROOT_LEN = 1024
        const val MAX_SEGMENTS = 512
        private val PATH_RE = Regex("[ -~]*") // printable ASCII

        fun create(root: String): PathTraversalGuard {
            require(root.isNotEmpty() && root.length <= MAX_ROOT_LEN) { "invalid root" }
            require(!root.contains("..")) { "root must not contain .." }
            require(!root.contains("\u0000")) { "root must not contain NUL" }
            return PathTraversalGuard(root)
        }
    }

    fun validate(path: String): String {
        require(path.isNotEmpty() && path.length <= MAX_PATH_LEN) { "invalid path" }
        require(PATH_RE.matches(path)) { "path contains invalid chars" }
        require(!path.contains("\u0000")) { "path must not contain NUL" }

        val segments = path.split("/").filter { it.isNotEmpty() }
        require(segments.size <= MAX_SEGMENTS) { "too many path segments" }

        // Reject absolute paths
        if (path.startsWith("/")) {
            throw IllegalArgumentException("absolute paths refused: $path")
        }

        // Reject .. segments
        for (seg in segments) {
            if (seg == "..") {
                throw IllegalArgumentException("traversal segment refused: $path")
            }
            if (seg == ".") continue
        }

        // Build resolved path under root
        val resolved = if (root.endsWith("/")) "$root$path" else "$root/$path"
        require(resolved.startsWith(root)) { "resolved path escapes root: $path" }

        return resolved
    }

    fun isSafe(path: String): Boolean = try { validate(path); true } catch (_: IllegalArgumentException) { false }

    fun root(): String = root
}

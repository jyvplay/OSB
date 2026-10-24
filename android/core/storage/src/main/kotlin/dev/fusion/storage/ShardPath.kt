package dev.fusion.storage

/**
 * Content-addressed shard paths (the git-object-store layout family:
 * split a lowercase hex digest into fixed-width directory levels so no
 * single directory holds millions of entries).
 *
 * Shape: `depth` levels of `width` hex chars each, then the remainder:
 * depth=1,width=2 gives git's `ab/cdef...`; depth=2 gives
 * `ab/cd/ef...`. The digest must be lowercase hex and strictly longer
 * than depth*width (an empty leaf name would alias the directory).
 *
 * Declared narrowings: path STRING construction/parsing only — no
 * filesystem IO, no hashing (Fnv1a and real digests live elsewhere);
 * separators are `/` always (callers on other filesystems convert at
 * the boundary, once).
 */
object ShardPath {
    const val SCOPE = "Digest-to-path string math only; no IO, no hashing; '/' separators by contract."

    fun shard(digestHex: String, depth: Int = 1, width: Int = 2): String {
        require(depth in 1..4 && width in 1..4) { "depth/width outside 1..4" }
        require(digestHex.length > depth * width) { "digest too short to leave a leaf name" }
        require(digestHex.all { it.isDigit() || it in 'a'..'f' }) { "digest must be lowercase hex" }
        val sb = StringBuilder()
        var pos = 0
        repeat(depth) {
            sb.append(digestHex, pos, pos + width).append('/')
            pos += width
        }
        sb.append(digestHex.substring(pos))
        return sb.toString()
    }

    fun unshard(path: String): String {
        require(path.isNotEmpty() && !path.startsWith('/') && !path.endsWith('/')) { "malformed shard path" }
        val joined = path.replace("/", "")
        require(joined.all { it.isDigit() || it in 'a'..'f' }) { "non-hex content in shard path" }
        return joined
    }

    /** True when [path] is exactly shard(digest, depth, width) for its own digest. */
    fun isCanonical(path: String, depth: Int = 1, width: Int = 2): Boolean =
        try { shard(unshard(path), depth, width) == path } catch (_: IllegalArgumentException) { false }
}

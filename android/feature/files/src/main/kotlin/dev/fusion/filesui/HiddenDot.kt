package dev.fusion.filesui

/**
 * Dot-hidden classification for the files UI: names starting with
 * '.' are hidden by Unix convention; '.nomedia' additionally marks
 * its whole DIRECTORY as media-scan-excluded (Android convention) —
 * two different scopes the UI must not conflate, so they are two
 * different predicates here.
 *
 * Declared narrowings: name-level classification only (no attribute
 * bits, no filesystem); '.' and '..' are path tokens, refused.
 */
object HiddenDot {
    const val SCOPE = "Dotfile and .nomedia predicates kept distinct; names only; path tokens refuse."

    fun isDotHidden(name: String): Boolean {
        require(name.isNotEmpty() && name != "." && name != "..") { "path token or empty" }
        require('/' !in name) { "single names only" }
        return name.startsWith(".")
    }

    fun isNoMediaMarker(name: String): Boolean = name == ".nomedia"

    fun visibleCount(names: List<String>, showHidden: Boolean): Int =
        if (showHidden) names.size else names.count { !isDotHidden(it) }

    fun directoryMediaExcluded(names: List<String>): Boolean = names.any { isNoMediaMarker(it) }
}

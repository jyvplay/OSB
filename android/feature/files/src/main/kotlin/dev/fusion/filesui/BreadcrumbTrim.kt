package dev.fusion.filesui

/**
 * Breadcrumb path trimming for the files UI: keep the FIRST component
 * and as many TRAILING components as fit a character budget, with a
 * single ellipsis component where the middle was removed — component
 * boundaries are never split (a half-folder-name is worse than a
 * shorter trail).
 *
 * Complements FilesScreen (which renders): this kernel decides WHAT
 * to render. Widths here are char counts; display-column composition
 * (wide chars) happens at the caller via the documents LineWidth
 * kernel.
 *
 * Declared narrowings: at minimum first + ellipsis + last are always
 * kept even over budget (a breadcrumb that loses its tail is useless
 * — the over-budget case is reported, not hidden).
 */
object BreadcrumbTrim {
    const val SCOPE = "Component-boundary trimming with first+...+last floor; char budget; widths compose at caller."
    const val ELLIPSIS = "\u2026"

    data class Trail(val components: List<String>, val overBudget: Boolean)

    private fun width(parts: List<String>) = parts.sumOf { it.length } + (parts.size - 1)

    fun trim(components: List<String>, maxChars: Int): Trail {
        require(components.isNotEmpty()) { "empty path" }
        components.forEach { require(it.isNotEmpty() && '/' !in it) { "component '$it' malformed" } }
        require(maxChars >= 1) { "budget $maxChars" }
        if (width(components) <= maxChars) return Trail(components, false)
        if (components.size <= 2) return Trail(components, true)
        for (keepTail in components.size - 1 downTo 1) {
            val candidate = listOf(components.first(), ELLIPSIS) + components.takeLast(keepTail)
            if (width(candidate) <= maxChars) return Trail(candidate, false)
        }
        val floor = listOf(components.first(), ELLIPSIS, components.last())
        return Trail(floor, true)
    }
}

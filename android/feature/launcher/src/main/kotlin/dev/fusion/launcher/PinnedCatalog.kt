package dev.fusion.launcher

import java.util.Locale

data class LaunchTarget(val key: String, val title: String, val running: Boolean = false)

/** Reconciles caller-observed targets, never invents apps/windows or drops temporarily absent pins.
 * Immutable operation results; this is not installed-app discovery, persistence or a Home role.
 */
class PinnedCatalog(pins: List<String> = emptyList()) {
    private val order = pins.also { require(it.size <= MAX_PINS) }.toList()
    init {
        require(order.size <= MAX_PINS && order.distinct().size == order.size)
        order.forEach(::validKey)
    }
    fun pins(): List<String> = order.toList()
    fun pin(key: String): PinnedCatalog {
        validKey(key)
        return if (key in order) this else PinnedCatalog(order + key)
    }
    fun unpin(key: String): PinnedCatalog = PinnedCatalog(order.filterNot { it == key })
    /** Destination is final index, not insertion index in the pre-removal list. */
    fun move(key: String, destination: Int): PinnedCatalog {
        require(key in order && destination in order.indices)
        return PinnedCatalog(order.toMutableList().apply { remove(key); add(destination, key) })
    }
    fun shelf(targets: List<LaunchTarget>): List<LaunchTarget> {
        validate(targets)
        val byKey = targets.associateBy { it.key }
        return order.mapNotNull(byKey::get) + targets.filter { it.running && it.key !in order }.sortedBy { it.key }
    }
    fun search(targets: List<LaunchTarget>, query: String): List<LaunchTarget> {
        validate(targets); require(query.length <= 256)
        val q = query.trim().lowercase(Locale.ROOT)
        return targets.filter { it.title.lowercase(Locale.ROOT).contains(q) || it.key.lowercase(Locale.ROOT).contains(q) }
            .sortedWith(compareBy<LaunchTarget> { it.title.lowercase(Locale.ROOT) }.thenBy { it.key })
    }
    private fun validate(targets: List<LaunchTarget>) {
        require(targets.size <= MAX_TARGETS && targets.map { it.key }.distinct().size == targets.size)
        targets.forEach { validKey(it.key); require(it.title.length in 1..256 && it.title.none { c -> c.isISOControl() }) }
    }
    companion object {
        const val MAX_PINS = 128
        const val MAX_TARGETS = 4096
        private fun validKey(key: String) { require(key.length in 1..256 && key.none { it.isWhitespace() || it.isISOControl() }) }
    }
}

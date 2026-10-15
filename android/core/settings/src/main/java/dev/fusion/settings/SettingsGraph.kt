package dev.fusion.settings

import android.content.Context
import android.content.SharedPreferences

data class SettingChange(val id: String, val old: String, val now: String)

/**
 * Small, honest settings store: SharedPreferences + in-memory undo stack.
 * Every behavior has exactly one ID; aliases exist for search only.
 */
class SettingsGraph(context: Context, defs: List<SettingDef> = DEFAULTS) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fusion_settings", Context.MODE_PRIVATE)
    private val catalog = SettingsCatalog(defs)
    private val byId = catalog.all().associateBy { it.id }
    private val history = ArrayDeque<SettingChange>()

    fun all(): List<SettingDef> = byId.values.toList()
    fun get(id: String): String = prefs.getString(id, byId[id]?.default ?: "") ?: ""
    fun search(q: String): List<SettingDef> = catalog.search(q)
    /** Explicit export only; decoding a snapshot never applies settings. */
    fun exportSnapshot(): ByteArray = catalog.encode(byId.keys.associateWith(::get))
    fun inspectSnapshot(bytes: ByteArray): Map<String, String> = catalog.decode(bytes)
    fun set(id: String, value: String): Boolean {
        if (!byId.containsKey(id)) return false
        try { catalog.encode(mapOf(id to value)) } catch (_: Exception) { return false }
        val old = get(id)
        if (old == value) return true
        prefs.edit().putString(id, value).apply()
        history.addLast(SettingChange(id, old, value))
        if (history.size > 100) history.removeFirst()
        return true
    }
    fun undo(): SettingChange? {
        val last = history.removeLastOrNull() ?: return null
        prefs.edit().putString(last.id, last.old).apply()
        return last
    }
    companion object {
        /** The only behaviours this build actually honours. */
        val DEFAULTS: List<SettingDef> = listOf(
            SettingDef("density", "Density profile", listOf("compact", "touch", "display size"), "balanced"),
            SettingDef("shortcuts", "Shortcut preset", listOf("hybrid", "windows", "mac", "keyboard"), "hybrid"),
            SettingDef("theme", "Theme", listOf("dark", "dynamic", "accent"), "dark")
        )
    }
}

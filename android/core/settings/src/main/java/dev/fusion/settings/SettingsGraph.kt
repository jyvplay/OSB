package dev.fusion.settings

import android.content.Context
import android.content.SharedPreferences

data class SettingDef(
    val id: String,
    val title: String,
    val aliases: List<String> = emptyList(),
    val default: String = ""
)

data class SettingChange(val id: String, val old: String, val now: String)

/**
 * Small, honest settings store: SharedPreferences + in-memory undo stack.
 * Every behavior has exactly one ID; aliases exist for search only.
 */
class SettingsGraph(context: Context, defs: List<SettingDef> = DEFAULTS) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fusion_settings", Context.MODE_PRIVATE)
    private val byId = defs.associateBy { it.id }
    private val history = ArrayDeque<SettingChange>()

    fun all(): List<SettingDef> = byId.values.toList()
    fun get(id: String): String = prefs.getString(id, byId[id]?.default ?: "") ?: ""
    fun search(q: String): List<SettingDef> {
        val s = q.lowercase()
        if (s.isBlank()) return all()
        return all().filter {
            it.title.lowercase().contains(s) || it.id.lowercase().contains(s) ||
                it.aliases.any { a -> a.lowercase().contains(s) }
        }
    }
    fun set(id: String, value: String): Boolean {
        if (!byId.containsKey(id)) return false
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

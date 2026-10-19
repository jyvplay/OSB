package dev.fusion.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.asStateFlow

data class SettingChange(val id: String, val old: String, val now: String)

/**
 * Small, honest settings store: SharedPreferences + in-memory undo stack.
 * Every behavior has exactly one ID; aliases exist for search only.
 */
class SettingsGraph(context: Context, defs: List<SettingDef> = DEFAULTS) : java.io.Closeable {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fusion_settings", Context.MODE_PRIVATE)
    private val catalog = SettingsCatalog(defs)
    private val byId = catalog.all().associateBy { it.id }
    private val history = ArrayDeque<SettingChange>()

    private val observed=kotlinx.coroutines.flow.MutableStateFlow(snapshot())
    val snapshots:kotlinx.coroutines.flow.StateFlow<Map<String,String>> = observed.asStateFlow()
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,id->
        if(id==null || id in byId) observed.value=snapshot()
    }
    init {prefs.registerOnSharedPreferenceChangeListener(listener)}
    private fun snapshot():Map<String,String> = byId.keys.associateWith(::get)
    override fun close() {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    /** One edit for the entire validated appearance patch; no partial key mutation on validation failure.
     * apply() is observable/in-memory atomic, NOT a disk/power-loss durability receipt.
     */
    fun setAppearance(changes:Map<String,String>):Boolean {
        if(!AppearancePreferences.canonical(changes) || changes.keys.any {it !in byId}) return false
        try {catalog.encode(changes)} catch(_:Exception) {return false}
        val edit=prefs.edit();changes.forEach {(key,value)->edit.putString(key,value)};edit.apply()
        observed.value=snapshot()
        return true
    }
    fun all(): List<SettingDef> = byId.values.toList()
    fun get(id: String): String {
        val fallback=byId[id]?.default ?: return ""
        return try {prefs.getString(id,fallback) ?: fallback} catch(_:ClassCastException) {fallback}
    }
    fun search(q: String): List<SettingDef> = catalog.search(q)
    /** Explicit export only; decoding a snapshot never applies settings. */
    fun exportSnapshot(): ByteArray = catalog.encode(byId.keys.associateWith(::get))
    fun inspectSnapshot(bytes: ByteArray): Map<String, String> = catalog.decode(bytes)
    fun set(id: String, value: String): Boolean {
        if (!byId.containsKey(id)) return false
        if(id in AppearancePreferences.keys) return setAppearance(mapOf(id to value))
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
            SettingDef("theme", "Professional palette", listOf("office blue", "studio graphite", "studio silver", "light", "dark"), "office_blue"),
            SettingDef("layout", "Desktop layout", listOf("business", "art", "taskbar", "dock"), "business"),
            SettingDef("desktop_scene", "Desktop surface", listOf("tabletop", "flat", "walls", "pins", "saved views"), "tabletop"),
            SettingDef("dock_zoom", "Dock magnification", listOf("pointer", "hover", "reduce motion"), "true")
        )
    }
}

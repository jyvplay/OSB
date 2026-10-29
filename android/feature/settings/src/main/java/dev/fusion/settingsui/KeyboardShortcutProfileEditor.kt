// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settingsui

/**
 * Clean-room Custom Keyboard Shortcut Profile Editor validating reserved system chords and
 * exporting deterministic JSON keymaps.
 */
object KeyboardShortcutProfileEditor {
    const val SCOPE =
        "Pure-JVM custom keyboard shortcut editor enforcing modifier safety, reserved OS chord refusal, and JSON keymap serialization."

    private val RESERVED_OS_CHORDS = setOf("CTRL+ALT+DEL", "ALT+F4", "META+L")

    fun validateAndSerializeProfile(
        profileName: String,
        bindings: Map<String, String>, // actionId -> chord
    ): String {
        require(profileName.isNotBlank() && bindings.isNotEmpty()) { "profileName and bindings required" }
        val normalized = LinkedHashMap<String, String>()
        val seenChords = HashSet<String>()
        for ((action, rawChord) in bindings.toSortedMap()) {
            val chord = rawChord.trim().uppercase().replace(" ", "")
            require('+' in chord || chord.startsWith("F")) { "plain unmodified key '$chord' refused for global shortcut" }
            require(chord !in RESERVED_OS_CHORDS) { "reserved OS chord '$chord' cannot be rebound" }
            require(seenChords.add(chord)) { "duplicate chord '$chord' in profile '$profileName'" }
            normalized[action] = chord
        }
        val entriesJson = normalized.entries.joinToString(",\n") { (k, v) ->
            "    \"${esc(k)}\": \"${esc(v)}\""
        }
        return "{\n  \"profile\": \"${esc(profileName.trim())}\",\n  \"bindings\": {\n$entriesJson\n  }\n}"
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * R146 — key-to-touch mapper overlay model (owner:v2.0 accessibility spec:
 * "Key-to-touch mapper overlay").
 *
 * A desktop-style keyboard overlay that rewrites accessibility key chords
 * into touch gestures for apps that only understand touch. This kernel is the
 * pure decision layer: it validates a mapping table, refuses chords the
 * operating system reserves, and replays a chord sequence into a bounded list
 * of touch plans with per-action cooldown suppression. It draws nothing and
 * injects nothing; an Android accessibility service would consume the plans.
 *
 * Chord grammar: zero or more modifiers in the fixed order
 * `ctrl+`, `alt+`, `shift+`, `meta+` followed by one key from [KEYS].
 * Duplicate or out-of-order modifiers are refused rather than re-ordered.
 *
 * Action grammar (x/y are permille of the overlay viewport, 0..1000):
 *  - `tap:X:Y`                    single tap
 *  - `swipe:up|down|left|right`   flick, length fixed by the overlay
 *  - `hold:X:Y:MS`                long press, MS in [50, 5000]
 *  - `pass:CODE`                  let the key through untouched,
 *                                 CODE `[A-Za-z0-9_]{1,16}`
 *
 * Declared narrowings: at most [MAX_MAPPINGS] entries per app scope, at most
 * [MAX_SEQUENCE] chords per replay, cooldown in [0, 5000] ms, action
 * coordinates within 0..1000 permille. A mapping whose chord carries `meta`
 * or equals the reserved `ctrl+alt+delete` chord is refused at definition
 * time (`!REF`); an unmapped chord during replay yields `UNMAPPED`.
 */
object KeyToTouchMapper {
    const val SCOPE =
        "Bounded chord-to-touch overlay planner with OS-reserved chord refusal and cooldown suppression; no injection, no drawing."

    const val MAX_MAPPINGS = 64
    const val MAX_SEQUENCE = 256
    const val MAX_COOLDOWN_MS = 5000
    const val MAX_ID_LEN = 64

    private val APP_RE = Regex("[a-z0-9._-]{1,64}")
    private val PASS_RE = Regex("[A-Za-z0-9_]{1,16}")
    private val KEY_SET = setOf(
        "enter", "tab", "esc", "space", "backspace", "delete",
        "up", "down", "left", "right", "home", "end", "pageup", "pagedown",
        "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10", "f11", "f12",
    ) + ('a'..'z').map { it.toString() } + ('0'..'9').map { it.toString() }

    private val MODIFIERS = listOf("ctrl", "alt", "shift", "meta")
    private const val RESERVED = "ctrl+alt+delete"

    sealed interface Action {
        data class Tap(val x: Int, val y: Int) : Action
        data class Swipe(val direction: String) : Action
        data class Hold(val x: Int, val y: Int, val ms: Int) : Action
        data class Pass(val code: String) : Action
    }

    data class Mapping(val chord: String, val action: Action)

    /** Canonical chord text: modifiers in fixed order, then the key. */
    fun canonicalize(chord: String): String {
        require(chord.length <= 64) { "chord too long" }
        val parts = chord.split('+')
        require(parts.size in 1..5) { "chord must carry one key and 0..4 modifiers" }
        val key = parts.last()
        require(key.lowercase() in KEY_SET) { "unknown key: $key" }
        val mods = parts.dropLast(1).map { it.lowercase() }
        require(mods.size == mods.toSet().size) { "duplicate modifier" }
        require(mods == mods.sortedBy { MODIFIERS.indexOf(it) }) {
            "modifiers must appear in order ctrl, alt, shift, meta"
        }
        require(mods.all { it in MODIFIERS }) { "unknown modifier" }
        val built = mods + key.lowercase()
        return built.joinToString("+")
    }

    fun isReserved(chord: String): Boolean {
        val c = canonicalize(chord)
        return c == RESERVED || c.startsWith("meta")
    }

    fun parseAction(spec: String): Action {
        val parts = spec.split(':')
        return when (parts[0]) {
            "tap" -> {
                require(parts.size == 3) { "tap takes x:y" }
                Action.Tap(permille(parts[1]), permille(parts[2]))
            }
            "swipe" -> {
                require(parts.size == 2) { "swipe takes a direction" }
                require(parts[1] in setOf("up", "down", "left", "right")) {
                    "bad swipe direction"
                }
                Action.Swipe(parts[1])
            }
            "hold" -> {
                require(parts.size == 4) { "hold takes x:y:ms" }
                val ms = parts[3].toIntOrNull() ?: throw IllegalArgumentException("hold ms not an int")
                require(ms in 50..5000) { "hold ms out of range" }
                Action.Hold(permille(parts[1]), permille(parts[2]), ms)
            }
            "pass" -> {
                require(parts.size == 2) { "pass takes a code" }
                require(PASS_RE.matches(parts[1])) { "bad pass code" }
                Action.Pass(parts[1])
            }
            else -> throw IllegalArgumentException("unknown action: ${parts[0]}")
        }
    }

    private fun permille(raw: String): Int {
        val v = raw.toIntOrNull() ?: throw IllegalArgumentException("coordinate not an int: $raw")
        require(v in 0..1000) { "coordinate out of 0..1000 permille" }
        return v
    }

    fun renderAction(a: Action): String = when (a) {
        is Action.Tap -> "tap:${a.x}:${a.y}"
        is Action.Swipe -> "swipe:${a.direction}"
        is Action.Hold -> "hold:${a.x}:${a.y}:${a.ms}"
        is Action.Pass -> "pass:${a.code}"
    }

    /**
     * Parse `chord=>action` pairs (comma separated) for one app scope.
     * Reserved chords are refused here so a bad table can never be applied.
     */
    fun parseTable(spec: String): Map<String, Action> {
        require(spec.isNotEmpty()) { "empty mapping table" }
        val entries = spec.split(',')
        require(entries.size <= MAX_MAPPINGS) { "too many mappings: $MAX_MAPPINGS" }
        val table = LinkedHashMap<String, Action>()
        for (entry in entries) {
            val arrow = entry.indexOf("=>")
            require(arrow > 0) { "mapping must be chord=>action" }
            val chord = canonicalize(entry.substring(0, arrow))
            require(!isReserved(chord)) { "reserved chord cannot be mapped: $chord" }
            val action = parseAction(entry.substring(arrow + 2))
            require(chord !in table) { "duplicate mapping: $chord" }
            table[chord] = action
        }
        return table
    }

    /** `resolve` lane: one chord against a prepared table. */
    fun resolve(tableSpec: String, chord: String): String {
        val table = parseTable(tableSpec)
        val c = canonicalize(chord)
        val action = table[c] ?: return "UNMAPPED"
        return renderAction(action)
    }

    /**
     * `sequence` lane: replay `chord@tsMs` pairs in order. Timestamps must be
     * non-decreasing epoch milliseconds. An action identical to the previous
     * *emitted* action whose timestamp is within [cooldownMs] of that emit is
     * suppressed (`-`); cooldown 0 never suppresses. An unmapped chord or a
     * decreasing timestamp refuses the whole sequence.
     */
    fun sequence(tableSpec: String, chordsSpec: String, cooldownMs: Int): String {
        require(cooldownMs in 0..MAX_COOLDOWN_MS) { "cooldown out of range" }
        val table = parseTable(tableSpec)
        val chords = if (chordsSpec.isEmpty()) emptyList() else chordsSpec.split(',')
        require(chords.size <= MAX_SEQUENCE) { "sequence too long: $MAX_SEQUENCE" }
        val out = ArrayList<String>(chords.size)
        var lastAction: String? = null
        var lastEmittedTs = -1L
        var previousTs = -1L
        for (raw in chords) {
            val at = raw.lastIndexOf('@')
            require(at > 0 && at < raw.length - 1) { "sequence entry must be chord@tsMs" }
            val c = canonicalize(raw.substring(0, at))
            val ts = raw.substring(at + 1).toLongOrNull()
                ?: throw IllegalArgumentException("sequence timestamp not an integer")
            require(ts >= 0) { "sequence timestamp must be >= 0" }
            require(ts >= previousTs) { "sequence timestamps must be non-decreasing" }
            previousTs = ts
            val action = table[c] ?: throw IllegalArgumentException("unmapped chord in sequence: $c")
            val rendered = renderAction(action)
            val suppressed = cooldownMs > 0 &&
                rendered == lastAction &&
                lastEmittedTs >= 0 &&
                ts - lastEmittedTs <= cooldownMs
            if (suppressed) {
                out.add("-")
            } else {
                out.add(rendered)
                lastAction = rendered
                lastEmittedTs = ts
            }
        }
        return out.joinToString(",")
    }
}

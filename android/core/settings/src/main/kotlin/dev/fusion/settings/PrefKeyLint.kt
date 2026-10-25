package dev.fusion.settings

/**
 * Settings-key naming rules (the repo's stated convention, enforced
 * so KeyMigrate's rename maps stay unambiguous forever): dot-separated
 * lowercase segments `[a-z][a-z0-9_]*`, depth 2..5, total <= 80 chars;
 * no segment named `tmp` or `debug` in release key-space.
 *
 * Declared narrowings: the convention IS this kernel's spec (stated
 * contract, like TurnOrder); legacy keys that predate it are listed
 * by the caller as exemptions, never silently passed.
 */
object PrefKeyLint {
    const val SCOPE = "Stated key-naming convention enforced; legacy exemptions are caller-explicit."
    private val SEGMENT = Regex("[a-z][a-z0-9_]*")

    fun validate(key: String): String {
        require(key.length in 3..80) { "key length " + key.length + " outside 3..80" }
        val segs = key.split('.')
        require(segs.size in 2..5) { "depth " + segs.size + " outside 2..5" }
        for (s in segs) {
            require(SEGMENT.matches(s)) { "segment '" + s + "' breaks [a-z][a-z0-9_]*" }
            require(s != "tmp" && s != "debug") { "segment '" + s + "' is reserved out of release key-space" }
        }
        return key
    }

    fun isValid(key: String, exemptions: Set<String> = emptySet()): Boolean =
        key in exemptions || try { validate(key); true } catch (_: IllegalArgumentException) { false }
}

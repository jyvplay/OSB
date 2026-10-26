package dev.fusion.commands

/**
 * Shell-history deduplication semantics (the readline/zsh family,
 * stated precisely): IGNORE_DUPS drops a command equal to the
 * IMMEDIATELY previous entry; ERASE_DUPS keeps only the newest
 * occurrence of each command (earlier ones removed); IGNORE_SPACE
 * drops entries starting with a space (the privacy convention).
 *
 * Declared narrowings: exact-string equality (no normalization);
 * policies compose in the documented order space -> dups.
 */
object HistDedup {
    const val SCOPE = "ignoredups/erasedups/ignorespace semantics, exact equality, documented compose order."

    enum class Policy { KEEP_ALL, IGNORE_DUPS, ERASE_DUPS }

    fun append(history: List<String>, entry: String, policy: Policy, ignoreSpace: Boolean): List<String> {
        require(entry.isNotEmpty()) { "empty history entry" }
        if (ignoreSpace && entry.startsWith(" ")) return history
        return when (policy) {
            Policy.KEEP_ALL -> history + entry
            Policy.IGNORE_DUPS -> if (history.lastOrNull() == entry) history else history + entry
            Policy.ERASE_DUPS -> history.filter { it != entry } + entry
        }
    }

    fun replay(entries: List<String>, policy: Policy, ignoreSpace: Boolean): List<String> {
        var h = emptyList<String>()
        for (e in entries) h = append(h, e, policy, ignoreSpace)
        return h
    }
}

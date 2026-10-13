package dev.fusion.commands

/** Searchable command with explicit provenance. No execution side effects here. */
data class Command(
    val id: String,
    val title: String,
    val hint: String,
    val keywords: List<String> = emptyList()
)

class CommandRegistry(cmds: List<Command> = emptyList()) {
    private val all = cmds.toList()
    fun search(query: String, limit: Int = 12): List<Command> {
        if (query.isBlank()) return all.take(limit)
        val q = query.lowercase()
        return all.map { c ->
            var s = 0
            if (c.title.lowercase().contains(q)) s += 10
            if (c.hint.lowercase().contains(q)) s += 4
            s += c.keywords.count { it.lowercase().contains(q) } * 3
            if (c.title.lowercase().startsWith(q)) s += 6
            c to s
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }
    fun all(): List<Command> = all
}

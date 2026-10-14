package dev.fusion.commands

import java.util.Collections
import java.util.Locale

/** Declarative action identity/provenance only; execution stays in the owning UI. */
data class Command(val id: String, val title: String, val hint: String, val keywords: List<String> = emptyList())

class CommandRegistry(cmds: List<Command> = emptyList()) {
    companion object { const val MAX_COMMANDS=4096; const val MAX_QUERY=1024; const val MAX_RESULTS=100 }
    private val all: List<Command>
    init {
        require(cmds.size<=MAX_COMMANDS)
        require(cmds.map { it.id }.distinct().size==cmds.size) { "Duplicate action identities" }
        all=Collections.unmodifiableList(cmds.map { command ->
            require(command.id.isNotBlank() && command.id.length<=128 && command.title.length<=512 && command.hint.length<=1024)
            require(command.keywords.size<=32 && command.keywords.all { it.length<=128 })
            command.copy(keywords=Collections.unmodifiableList(ArrayList(command.keywords)))
        })
    }
    fun search(query: String, limit: Int = 12): List<Command> {
        require(query.length<=MAX_QUERY && limit in 0..MAX_RESULTS)
        val q=query.trim().lowercase(Locale.ROOT)
        if(q.isEmpty()) return all.take(limit)
        return all.map { c ->
            val title=c.title.lowercase(Locale.ROOT)
            var score=0
            if(title.contains(q)) score+=10
            if(title.startsWith(q)) score+=6
            if(c.hint.lowercase(Locale.ROOT).contains(q)) score+=4
            score+=c.keywords.count { it.lowercase(Locale.ROOT).contains(q) }*3
            c to score
        }.filter { it.second>0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }
    fun all(): List<Command> = all
}

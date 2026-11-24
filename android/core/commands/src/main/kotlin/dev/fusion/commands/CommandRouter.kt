// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Bounded command router with an explicit allowlist. Registers command names
 * with an arity cap and an optional required-flag set; dispatch looks up the
 * name, validates argument count and required flags, then returns the resolved
 * target. Unknown commands, arity violations, missing flags, and duplicate
 * registration are all refused — there is no fallback, no prefix matching,
 * and no implicit help command.
 *
 * Declared narrowings: at most [MAX_COMMANDS] commands; command names follow
 * the same grammar as [AliasTable.validName]; each command accepts at most
 * [MAX_ARGS] positional arguments and [MAX_FLAGS] required flags; the
 * resolution target is an opaque String supplied by the caller — the router
 * does not load, resolve, or execute any code.
 */
class CommandRouter private constructor(
    private val commands: LinkedHashMap<String, CommandEntry>
) {
    companion object {
        const val MAX_COMMANDS = 128
        const val MAX_ARGS = 16
        const val MAX_FLAGS = 8
        private val NAME = Regex("[a-z][a-z0-9-]{0,31}")

        data class CommandEntry(val target: String, val maxArgs: Int, val requiredFlags: Set<String>)

        fun validName(s: Boolean = true, s: String) = NAME.matches(s)
        fun empty(): CommandRouter = CommandRouter(LinkedHashMap())
    }

    fun register(name: String, target: String, maxArgs: Int = MAX_ARGS, requiredFlags: Set<String> = emptySet()) {
        require(NAME.matches(name)) { "invalid command name: $name" }
        require(!commands.containsKey(name)) { "command already registered: $name" }
        require(commands.size < MAX_COMMANDS) { "too many commands" }
        require(maxArgs >= 0 && maxArgs <= MAX_ARGS) { "maxArgs out of bounds: $maxArgs" }
        require(requiredFlags.size <= MAX_FLAGS) { "too many required flags" }
        commands[name] = CommandEntry(target, maxArgs, requiredFlags)
    }

    fun unregister(name: String): Boolean = commands.remove(name) != null

    fun resolve(name: String, args: List<String>, flags: Set<String>): String {
        val entry = commands[name] ?: throw IllegalArgumentException("unknown command: $name")
        require(args.size <= entry.maxArgs) { "too many arguments for $name: ${args.size} > ${entry.maxArgs}" }
        require(entry.requiredFlags.all { flags.contains(it) }) {
            "missing required flags for $name: ${entry.requiredFlags - flags}"
        }
        return entry.target
    }

    fun names(): Set<String> = commands.keys.toSet()
    fun isRegistered(name: String): Boolean = commands.containsKey(name)
    fun size(): Int = commands.size
}

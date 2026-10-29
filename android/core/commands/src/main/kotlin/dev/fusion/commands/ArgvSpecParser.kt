// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

import kotlin.math.min

/**
 * Clean-room POSIX/GNU command-line option, flag, subcommand & typo-suggestion parser (`ArgvSpecParser`).
 *
 * Powers `:core:cli` headless tools and `:feature:terminal` built-in applets:
 * - Long options `--output=out.pdf`, `--output out.pdf`, boolean `--verbose` and negated `--no-verbose`.
 * - Short option clusters `-vxf file.tar` (bundled boolean flags + trailing value-taking short option).
 * - POSIX `--` end-of-options sentinel (everything after `--` becomes positional even if prefixed with `-`).
 * - Subcommand dispatch + bounded Levenshtein (`<= 2` edits) typo hint (`Unknown option '--verbos'; did you mean '--verbose'?`).
 */
object ArgvSpecParser {
    const val SCOPE =
        "POSIX/GNU CLI option & subcommand parser (--long=v, --no-flag, -abc short clusters, -- sentinel, Levenshtein typo hint, help generator); shell word-splitting out of scope."

    data class OptSpec(
        val longName: String,
        val shortName: Char? = null,
        val takesValue: Boolean = false,
        val repeatable: Boolean = false,
        val defaultValue: String? = null,
        val description: String = "",
    ) {
        init {
            require(longName.matches(Regex("^[a-z0-9][a-z0-9-]*$"))) { "Invalid longName: $longName" }
        }
    }

    data class CommandSpec(
        val name: String,
        val summary: String,
        val options: List<OptSpec> = emptyList(),
        val subcommands: List<CommandSpec> = emptyList(),
    )

    data class ParsedArgs(
        val commandPath: List<String>,
        val flags: Map<String, Boolean>,
        val values: Map<String, List<String>>,
        val positionals: List<String>,
    ) {
        fun flag(name: String): Boolean = flags[name] ?: false
        fun singleValue(name: String): String? = values[name]?.lastOrNull()
    }

    class CliParseException(message: String) : IllegalArgumentException(message)

    fun parse(spec: CommandSpec, argv: List<String>): ParsedArgs {
        var activeSpec = spec
        val cmdPath = mutableListOf(spec.name)
        var idx = 0

        while (idx < argv.size && activeSpec.subcommands.isNotEmpty() && !argv[idx].startsWith("-")) {
            val sub = activeSpec.subcommands.firstOrNull { it.name == argv[idx] } ?: break
            activeSpec = sub
            cmdPath.add(sub.name)
            idx++
        }

        val byLong = activeSpec.options.associateBy { it.longName }
        val byShort = activeSpec.options.filter { it.shortName != null }.associateBy { it.shortName!! }
        val flags = linkedMapOf<String, Boolean>()
        val values = linkedMapOf<String, MutableList<String>>()
        for (opt in activeSpec.options) {
            if (!opt.takesValue) flags[opt.longName] = false
            else if (opt.defaultValue != null) values[opt.longName] = mutableListOf(opt.defaultValue)
        }

        val positionals = mutableListOf<String>()
        var optionsEnded = false

        fun recordValue(opt: OptSpec, v: String) {
            val list = values.getOrPut(opt.longName) { mutableListOf() }
            if (!opt.repeatable && list.isNotEmpty() && (opt.defaultValue == null || list != listOf(opt.defaultValue))) {
                list.clear()
            } else if (!opt.repeatable && opt.defaultValue != null && list == listOf(opt.defaultValue)) {
                list.clear()
            }
            list.add(v)
        }

        while (idx < argv.size) {
            val tok = argv[idx++]
            if (optionsEnded || tok == "-" || !tok.startsWith("-")) {
                positionals.add(tok)
                continue
            }
            if (tok == "--") {
                optionsEnded = true
                continue
            }
            if (tok.startsWith("--")) {
                val eq = tok.indexOf('=')
                val rawName = if (eq >= 0) tok.substring(2, eq) else tok.substring(2)
                val inlineVal = if (eq >= 0) tok.substring(eq + 1) else null

                if (rawName.startsWith("no-") && inlineVal == null) {
                    val base = byLong[rawName.removePrefix("no-")]
                    if (base != null && !base.takesValue) {
                        flags[base.longName] = false
                        continue
                    }
                }
                val opt = byLong[rawName] ?: throw unknownLongOption(rawName, byLong.keys)
                if (!opt.takesValue) {
                    if (inlineVal != null) throw CliParseException("Flag '--${opt.longName}' does not take a value")
                    flags[opt.longName] = true
                } else {
                    val v = inlineVal ?: if (idx < argv.size) argv[idx++] else {
                        throw CliParseException("Option '--${opt.longName}' requires a value")
                    }
                    recordValue(opt, v)
                }
            } else {
                // Short cluster: -abc or -oVal or -o Val
                var cIdx = 1
                while (cIdx < tok.length) {
                    val ch = tok[cIdx]
                    val opt = byShort[ch] ?: throw CliParseException("Unknown short option '-$ch'")
                    if (!opt.takesValue) {
                        flags[opt.longName] = true
                        cIdx++
                    } else {
                        val rest = tok.substring(cIdx + 1)
                        val v = if (rest.isNotEmpty()) rest else if (idx < argv.size) argv[idx++] else {
                            throw CliParseException("Short option '-$ch' requires a value")
                        }
                        recordValue(opt, v)
                        break
                    }
                }
            }
        }

        return ParsedArgs(
            commandPath = cmdPath,
            flags = flags,
            values = values.mapValues { it.value.toList() },
            positionals = positionals,
        )
    }

    fun formatHelp(spec: CommandSpec): String = buildString {
        append(spec.name).append(" - ").append(spec.summary).append('\n')
        if (spec.subcommands.isNotEmpty()) {
            append("\nSubcommands:\n")
            for (sub in spec.subcommands) {
                append("  ").append(sub.name.padEnd(16)).append(sub.summary).append('\n')
            }
        }
        if (spec.options.isNotEmpty()) {
            append("\nOptions:\n")
            for (opt in spec.options) {
                val shortStr = if (opt.shortName != null) "-${opt.shortName}, " else "    "
                val valStr = if (opt.takesValue) " <val>" else ""
                val lhs = "  $shortStr--${opt.longName}$valStr"
                append(lhs.padEnd(28)).append(opt.description).append('\n')
            }
        }
    }

    private fun unknownLongOption(unknown: String, candidates: Set<String>): CliParseException {
        val best = candidates.map { it to levenshtein(unknown, it) }
            .filter { it.second <= 2 }
            .minByOrNull { it.second }
            ?.first
        val hint = if (best != null) "; did you mean '--$best'?" else ""
        return CliParseException("Unknown option '--$unknown'$hint")
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val cur = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + cost)
                prev = cur
            }
        }
        return dp[b.length]
    }
}

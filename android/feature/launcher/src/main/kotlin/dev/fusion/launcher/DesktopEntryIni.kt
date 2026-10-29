// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

/**
 * Clean-room freedesktop.org Desktop Entry Specification 1.5 (`.desktop`) Parser & Safe `Exec=`
 * Argument Vector Expander.
 *
 * Supports `[Desktop Entry]` validation (`Type=Application|Link|Directory`), locale-fallback
 * lookup (`Name[fr_CA]` -> `Name[fr]` -> `Name`), `Categories` semicolon lists, and safe `Exec=`
 * tokenization (`%f`, `%F`, `%u`, `%U`, `%c`, `%%`) into `List<String>` argv **without** passing
 * untrusted filenames through `/bin/sh -c`!
 */
object DesktopEntryIni {
    const val SCOPE =
        "freedesktop.org Desktop Entry 1.5 parser, locale key resolver, and shell-free Exec= %f/%F/%u/%U argv expander; DBusActivatable service launch out of scope."

    const val MAX_BYTES = 65_536

    data class Entry(
        val type: String,
        val defaultName: String,
        val localizedNames: Map<String, String>,
        val execTemplate: String?,
        val icon: String?,
        val categories: List<String>,
        val noDisplay: Boolean,
        val terminal: Boolean,
    ) {
        fun nameForLocale(langCountry: String): String {
            localizedNames[langCountry]?.let { return it }
            val langOnly = langCountry.substringBefore('_').substringBefore('-')
            localizedNames[langOnly]?.let { return it }
            return defaultName
        }

        fun expandExecArgv(targets: List<String> = emptyList()): List<String> {
            val tmpl = execTemplate ?: throw IllegalStateException("Desktop Entry has no Exec= key")
            val rawTokens = tokenizeExec(tmpl)
            val argv = ArrayList<String>()
            for (tok in rawTokens) {
                when (tok) {
                    "%f", "%u" -> if (targets.isNotEmpty()) argv.add(targets.first())
                    "%F", "%U" -> argv.addAll(targets)
                    "%i" -> if (!icon.isNullOrBlank()) {
                        argv.add("--icon"); argv.add(icon)
                    }
                    "%c" -> argv.add(defaultName)
                    "%d", "%D", "%n", "%N", "%v", "%m" -> {} // deprecated codes stripped per spec §Exec
                    else -> {
                        val sb = StringBuilder()
                        var i = 0
                        while (i < tok.length) {
                            if (tok[i] == '%' && i + 1 < tok.length) {
                                when (val code = tok[i + 1]) {
                                    '%' -> sb.append('%')
                                    'f', 'u' -> if (targets.isNotEmpty()) sb.append(targets.first())
                                    'c' -> sb.append(defaultName)
                                    else -> throw IllegalArgumentException("unsupported inline Exec field code '%$code'")
                                }
                                i += 2
                            } else {
                                sb.append(tok[i])
                                i++
                            }
                        }
                        if (sb.isNotEmpty()) argv.add(sb.toString())
                    }
                }
            }
            require(argv.isNotEmpty()) { "expanded Exec= produced empty argv" }
            return argv
        }
    }

    fun parse(content: String): Entry {
        require(content.length <= MAX_BYTES) { ".desktop file exceeds $MAX_BYTES chars" }
        var currentGroup: String? = null
        val desktopFields = LinkedHashMap<String, String>()

        for ((lineIdx, rawLine) in content.replace("\r\n", "\n").split('\n').withIndex()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentGroup = trimmed.substring(1, trimmed.length - 1)
                continue
            }
            require(currentGroup != null) { "key-value before group header on line ${lineIdx + 1}" }
            if (currentGroup != "Desktop Entry") continue
            val eq = rawLine.indexOf('=')
            require(eq > 0) { "missing '=' on line ${lineIdx + 1}" }
            val key = rawLine.substring(0, eq).trim()
            val value = unescapeValue(rawLine.substring(eq + 1).trim())
            desktopFields[key] = value
        }

        require(desktopFields.isNotEmpty()) { "missing [Desktop Entry] group" }
        val type = desktopFields["Type"] ?: throw IllegalArgumentException("missing required Type= in [Desktop Entry]")
        require(type in setOf("Application", "Link", "Directory")) { "invalid Desktop Entry Type='$type'" }
        val defaultName = desktopFields["Name"] ?: throw IllegalArgumentException("missing required Name= in [Desktop Entry]")
        val exec = desktopFields["Exec"]
        if (type == "Application") {
            require(!exec.isNullOrBlank()) { "Type=Application requires non-empty Exec=" }
        }

        val localized = LinkedHashMap<String, String>()
        for ((k, v) in desktopFields) {
            if (k.startsWith("Name[") && k.endsWith("]")) {
                localized[k.substring(5, k.length - 1)] = v
            }
        }
        val categories = desktopFields["Categories"]
            ?.split(';')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        return Entry(
            type = type,
            defaultName = defaultName,
            localizedNames = localized,
            execTemplate = exec,
            icon = desktopFields["Icon"],
            categories = categories,
            noDisplay = desktopFields["NoDisplay"] == "true",
            terminal = desktopFields["Terminal"] == "true",
        )
    }

    private fun tokenizeExec(tmpl: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var hasTok = false
        var i = 0
        while (i < tmpl.length) {
            val c = tmpl[i]
            if (inQuotes) {
                if (c == '\\' && i + 1 < tmpl.length) {
                    val nxt = tmpl[i + 1]
                    require(nxt != '`' && nxt != '$') { "shell expansion refused inside Exec= quoted arg" }
                    cur.append(nxt); i += 2; continue
                }
                if (c == '"') inQuotes = false
                else {
                    require(c != '`' && c != '$') { "shell expansion character '$c' refused in Exec=" }
                    cur.append(c)
                }
            } else {
                when (c) {
                    '"' -> { inQuotes = true; hasTok = true }
                    ' ', '\t' -> if (hasTok) {
                        out.add(cur.toString()); cur.setLength(0); hasTok = false
                    }
                    ';', '|', '&', '`', '$', '>', '<', '\n' ->
                        throw IllegalArgumentException("reserved shell character '$c' unquoted in Exec=")
                    else -> { hasTok = true; cur.append(c) }
                }
            }
            i++
        }
        require(!inQuotes) { "unclosed double quote in Exec=" }
        if (hasTok) out.add(cur.toString())
        return out
    }

    private fun unescapeValue(s: String): String = buildString(s.length) {
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    's' -> append(' ')
                    'n' -> append('\n')
                    't' -> append('\t')
                    'r' -> append('\r')
                    '\\' -> append('\\')
                    else -> append(s[i + 1])
                }
                i += 2
            } else {
                append(s[i])
                i++
            }
        }
    }
}

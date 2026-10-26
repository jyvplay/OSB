package dev.fusion.terminal

/**
 * HOME-prefix display collapse for the terminal's title/prompt
 * (`/data/user/0/app/files/projects` → `~/projects` given HOME): the
 * inverse expansion, and the boundary law that makes `/homework` NOT
 * collapse under HOME=/home — prefix match is component-wise, never
 * substring (the classic bug this kernel exists to refuse).
 *
 * Declared narrowings: absolute POSIX-style paths; display only (no
 * filesystem access); `~user` forms are shell features, refused.
 */
object CwdTilde {
    const val SCOPE = "Component-wise HOME collapse/expand for display; ~user refused; no filesystem."

    fun collapse(path: String, home: String): String {
        require(path.startsWith("/") && home.startsWith("/")) { "absolute paths only" }
        require(!home.endsWith("/") || home == "/") { "HOME without trailing slash" }
        if (path == home) return "~"
        if (path.startsWith(home + "/")) return "~" + path.substring(home.length)
        return path
    }

    fun expand(display: String, home: String): String {
        require(home.startsWith("/")) { "absolute HOME" }
        require(!display.startsWith("~") || display == "~" || display.startsWith("~/")) {
            "~user forms are shell features, refused here"
        }
        return when {
            display == "~" -> home
            display.startsWith("~/") -> home + display.substring(1)
            else -> display
        }
    }
}

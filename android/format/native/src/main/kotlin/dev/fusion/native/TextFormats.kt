package dev.fusion.native

/** Real text-format helpers: encoding sniff, CSV split, word count. No Office, no PSD. */
object TextFormats {
    fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }
    fun csvRows(text: String): List<List<String>> {
        return text.lineSequence().filter { it.isNotBlank() }.map { line ->
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            var inQ = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '"') {
                    if (inQ && i + 1 < line.length && line[i + 1] == '"') { cur.append('"'); i++ }
                    else inQ = !inQ
                } else if (c == ',' && !inQ) { out.add(cur.toString()); cur.clear() }
                else cur.append(c)
                i++
            }
            out.add(cur.toString())
            out
        }.toList()
    }
    fun isProbablyUtf8(bytes: ByteArray): Boolean {
        return try { String(bytes, Charsets.UTF_8); true } catch (_: Exception) { false }
    }
}

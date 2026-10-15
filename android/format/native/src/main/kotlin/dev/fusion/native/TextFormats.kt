package dev.fusion.native

/** Bounded strict CSV, UTF8 validation and simple word count. No Office, no PSD. */
object TextFormats {
    fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }
    /** Strict comma/double-quote subset: LF or CRLF records, embedded quoted newlines,
     * doubled quotes, empty cells/records. Never trims, drops blanks or evaluates formulas.
     * Bounds are UTF16 code units, per call; not whole-process memory or spreadsheet fidelity.
     */
    fun csvRows(text: String): List<List<String>> {
        require(text.length <= 2 * 1024 * 1024)
        if (text.isEmpty()) return emptyList()
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false; var closed = false; var i = 0
        fun field() {
            require(row.size < 256) { "Too many CSV columns" }
            row.add(cell.toString()); cell.setLength(0); closed = false
        }
        fun record() {
            field(); require(rows.size < 10000) { "Too many CSV rows" }
            rows.add(row.toList()); row = mutableListOf()
        }
        fun append(c: Char) { require(cell.length < 65536) { "CSV cell exceeds 65536 UTF16 units" }; cell.append(c) }
        while (i < text.length) {
            val c = text[i++]
            if (quoted) {
                if (c == '"') {
                    if (i < text.length && text[i] == '"') { append('"'); i++ }
                    else { quoted = false; closed = true }
                } else append(c)
            } else when (c) {
                ',' -> field()
                '\n' -> record()
                '\r' -> { require(i < text.length && text[i++] == '\n') { "Bare CR record separator" }; record() }
                '"' -> { require(cell.isEmpty() && !closed) { "Quote inside an unquoted CSV cell" }; quoted = true }
                else -> { require(!closed) { "Data after closing quote" }; append(c) }
            }
        }
        require(!quoted) { "Unterminated quoted CSV cell" }
        // A terminal record separator ends the last record; it does not create another.
        if (row.isNotEmpty() || cell.isNotEmpty() || closed || text.last() == ',') record()
        return rows.toList()
    }
    fun isProbablyUtf8(bytes: ByteArray): Boolean {
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
            true
        } catch (_: java.nio.charset.CharacterCodingException) { false }
    }
}

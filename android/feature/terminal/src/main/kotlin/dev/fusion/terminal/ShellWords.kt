package dev.fusion.terminal

/** Bounded local builtin argument grammar. NOT a Unix shell: no expansions, substitution or chaining.
 * Backslash quotes the next character outside single quotes (also inside double quotes).
 */
object ShellWords {
    fun parse(line: String): List<String> {
        require(line.length<=4096 && '\u0000' !in line)
        val out=mutableListOf<String>();val word=StringBuilder()
        var quote: Char?=null;var escaped=false;var started=false
        fun append(c: Char) { require(word.length<2048);word.append(c) }
        fun finish() { if(started) { require(out.size<64);out.add(word.toString());word.setLength(0);started=false } }
        for(c in line) {
            if(escaped) { append(c);escaped=false;started=true;continue }
            when {
                quote=='\'' -> if(c=='\'') quote=null else append(c)
                c=='\\' -> { escaped=true;started=true }
                quote=='"' -> if(c=='"') quote=null else append(c)
                c=='\'' || c=='"' -> { quote=c;started=true }
                c.isWhitespace() -> finish()
                else -> { append(c);started=true }
            }
        }
        require(!escaped && quote==null) { "Unfinished escape or quotation" };finish()
        return out.toList()
    }
}

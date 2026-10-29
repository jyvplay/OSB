// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Multi-language incremental line-state lexical tokenizer (Kotlin, Python, Rust, SQL, JSON/XML)
 * preserving multi-line block-comment (`/* ... */`) and triple-quoted raw-string (`""" ... """`)
 * continuation states across line edits in Fusion Editor.
 */
object SyntaxHighlightLexicalScanner {
    const val SCOPE =
        "Incremental line-state lexical scanner for Kotlin/Python/SQL/JSON preserving multi-line block-comment and triple-quote states."

    enum class TokenType { KEYWORD, STRING, COMMENT, NUMBER, IDENTIFIER, PUNCTUATION, WHITESPACE }
    enum class LineState { NORMAL, IN_BLOCK_COMMENT, IN_TRIPLE_STRING }

    data class TokenSpan(val type: TokenType, val startCol: Int, val endColExclusive: Int)
    data class ScannedLine(val tokens: List<TokenSpan>, val endState: LineState)

    private val KEYWORDS = setOf(
        "fun", "val", "var", "object", "class", "interface", "return", "when", "if", "else", "for", "while",
        "def", "import", "from", "select", "where", "join", "group", "by", "true", "false", "null",
    )

    fun scanLine(line: String, startState: LineState = LineState.NORMAL): ScannedLine {
        val tokens = ArrayList<TokenSpan>()
        var i = 0
        var state = startState

        while (i < line.length) {
            when (state) {
                LineState.IN_BLOCK_COMMENT -> {
                    val close = line.indexOf("*/", i)
                    if (close < 0) {
                        tokens.add(TokenSpan(TokenType.COMMENT, i, line.length))
                        return ScannedLine(tokens, LineState.IN_BLOCK_COMMENT)
                    } else {
                        tokens.add(TokenSpan(TokenType.COMMENT, i, close + 2))
                        i = close + 2
                        state = LineState.NORMAL
                    }
                }
                LineState.IN_TRIPLE_STRING -> {
                    val close = line.indexOf("\"\"\"", i)
                    if (close < 0) {
                        tokens.add(TokenSpan(TokenType.STRING, i, line.length))
                        return ScannedLine(tokens, LineState.IN_TRIPLE_STRING)
                    } else {
                        tokens.add(TokenSpan(TokenType.STRING, i, close + 3))
                        i = close + 3
                        state = LineState.NORMAL
                    }
                }
                LineState.NORMAL -> {
                    when {
                        line[i].isWhitespace() -> {
                            val s = i
                            while (i < line.length && line[i].isWhitespace()) i++
                            tokens.add(TokenSpan(TokenType.WHITESPACE, s, i))
                        }
                        line.startsWith("//", i) || line[i] == '#' -> {
                            tokens.add(TokenSpan(TokenType.COMMENT, i, line.length))
                            i = line.length
                        }
                        line.startsWith("/*", i) -> {
                            val close = line.indexOf("*/", i + 2)
                            if (close < 0) {
                                tokens.add(TokenSpan(TokenType.COMMENT, i, line.length))
                                return ScannedLine(tokens, LineState.IN_BLOCK_COMMENT)
                            } else {
                                tokens.add(TokenSpan(TokenType.COMMENT, i, close + 2))
                                i = close + 2
                            }
                        }
                        line.startsWith("\"\"\"", i) -> {
                            val close = line.indexOf("\"\"\"", i + 3)
                            if (close < 0) {
                                tokens.add(TokenSpan(TokenType.STRING, i, line.length))
                                return ScannedLine(tokens, LineState.IN_TRIPLE_STRING)
                            } else {
                                tokens.add(TokenSpan(TokenType.STRING, i, close + 3))
                                i = close + 3
                            }
                        }
                        line[i] == '"' || line[i] == '\'' -> {
                            val q = line[i]
                            val s = i; i++
                            while (i < line.length && line[i] != q) {
                                if (line[i] == '\\' && i + 1 < line.length) i += 2 else i++
                            }
                            if (i < line.length) i++
                            tokens.add(TokenSpan(TokenType.STRING, s, i))
                        }
                        line[i].isDigit() -> {
                            val s = i
                            while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '.' || line[i] == '_')) i++
                            tokens.add(TokenSpan(TokenType.NUMBER, s, i))
                        }
                        line[i].isLetter() || line[i] == '_' -> {
                            val s = i
                            while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                            val word = line.substring(s, i)
                            val kind = if (word.lowercase() in KEYWORDS) TokenType.KEYWORD else TokenType.IDENTIFIER
                            tokens.add(TokenSpan(kind, s, i))
                        }
                        else -> {
                            tokens.add(TokenSpan(TokenType.PUNCTUATION, i, i + 1))
                            i++
                        }
                    }
                }
            }
        }
        return ScannedLine(tokens, state)
    }
}

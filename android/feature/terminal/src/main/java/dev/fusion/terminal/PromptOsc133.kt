// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/**
 * Clean-room FinalTerm / Kitty / Ghostty **OSC 133** + **OSC 7** semantic shell-integration parser (`PromptOsc133`).
 *
 * Parses terminal streams containing Operating System Command (`ESC ] ... BEL` or `ESC ] ... ESC \`)
 * shell-integration markers into structured, navigable command blocks (`CommandBlock`):
 * - `OSC 133 ; A`: Prompt start.
 * - `OSC 133 ; B`: Command line input start (end of prompt).
 * - `OSC 133 ; C`: Command execution output start.
 * - `OSC 133 ; D [; exitCode]`: Command finished with integer exit status (`0` = success).
 * - `OSC 7 ; file://hostname/path`: Working directory update (percent-decoded, control-char stripped).
 */
object PromptOsc133 {
    const val SCOPE =
        "OSC 133 (A/B/C/D;exitCode) + OSC 7 (file://cwd) semantic shell-integration parser (BEL & ST terminators); DEC private mode screen buffers out of scope."

    data class CommandBlock(
        val prompt: String,
        val command: String,
        val output: String,
        val exitCode: Int,
        val cwd: String?,
    ) {
        val succeeded: Boolean get() = exitCode == 0
    }

    data class ParseSessionResult(
        val completedBlocks: List<CommandBlock>,
        val lastCwd: String?,
        val trailingVisibleText: String,
    )

    private enum class Phase { IDLE, IN_PROMPT, IN_COMMAND, IN_OUTPUT }

    fun parseStream(rawStream: String): ParseSessionResult {
        val completed = mutableListOf<CommandBlock>()
        var cwd: String? = null
        var phase = Phase.IDLE
        val promptBuf = StringBuilder()
        val cmdBuf = StringBuilder()
        val outBuf = StringBuilder()
        val trailingBuf = StringBuilder()

        fun activeBuf(): StringBuilder = when (phase) {
            Phase.IDLE -> trailingBuf
            Phase.IN_PROMPT -> promptBuf
            Phase.IN_COMMAND -> cmdBuf
            Phase.IN_OUTPUT -> outBuf
        }

        var i = 0
        while (i < rawStream.length) {
            if (rawStream[i] == '\u001B' && i + 1 < rawStream.length && rawStream[i + 1] == ']') {
                val term = findOscTerminator(rawStream, i + 2)
                if (term != null) {
                    val (endPayload, nextIdx) = term
                    val payload = rawStream.substring(i + 2, endPayload)
                    i = nextIdx
                    when {
                        payload.startsWith("133;") -> {
                            val rest = payload.substring(4)
                            val kind = rest.firstOrNull()
                            when (kind) {
                                'A' -> {
                                    promptBuf.clear(); cmdBuf.clear(); outBuf.clear()
                                    phase = Phase.IN_PROMPT
                                }
                                'B' -> if (phase == Phase.IN_PROMPT) phase = Phase.IN_COMMAND
                                'C' -> if (phase == Phase.IN_COMMAND || phase == Phase.IN_PROMPT) phase = Phase.IN_OUTPUT
                                'D' -> {
                                    val code = rest.substringAfter(';', "0")
                                        .substringBefore(';')
                                        .toIntOrNull() ?: 0
                                    if (phase != Phase.IDLE) {
                                        completed.add(
                                            CommandBlock(
                                                prompt = promptBuf.toString(),
                                                command = cmdBuf.toString().trim(),
                                                output = outBuf.toString(),
                                                exitCode = code,
                                                cwd = cwd,
                                            ),
                                        )
                                        promptBuf.clear(); cmdBuf.clear(); outBuf.clear()
                                        phase = Phase.IDLE
                                    }
                                }
                            }
                        }
                        payload.startsWith("7;") -> {
                            parseOsc7FileUri(payload.substring(2))?.let { cwd = it }
                        }
                    }
                    continue
                }
            }
            activeBuf().append(rawStream[i])
            i++
        }

        return ParseSessionResult(completed, cwd, trailingBuf.toString())
    }

    private fun findOscTerminator(s: String, start: Int): Pair<Int, Int>? {
        var idx = start
        while (idx < s.length) {
            if (s[idx] == '\u0007') return idx to (idx + 1)
            if (s[idx] == '\u001B' && idx + 1 < s.length && s[idx + 1] == '\\') return idx to (idx + 2)
            idx++
        }
        return null
    }

    private fun parseOsc7FileUri(uri: String): String? {
        if (!uri.startsWith("file://")) return null
        val afterScheme = uri.substring(7)
        val slash = afterScheme.indexOf('/')
        if (slash < 0) return null
        val rawPath = afterScheme.substring(slash)
        val sb = StringBuilder(rawPath.length)
        var i = 0
        while (i < rawPath.length) {
            if (rawPath[i] == '%' && i + 2 < rawPath.length) {
                val byteVal = rawPath.substring(i + 1, i + 3).toIntOrNull(16)
                if (byteVal != null && byteVal in 0x20..0x7E) {
                    sb.append(byteVal.toChar())
                    i += 3
                    continue
                }
            }
            val ch = rawPath[i++]
            if (ch.code >= 0x20 && ch.code != 0x7F) sb.append(ch)
        }
        return sb.toString()
    }
}

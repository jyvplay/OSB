// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

/**
 * R146 — versioned exchange codec for recorded macros (import/export between
 * devices; pairs with [MacroRecorder], which owns live record/playback).
 *
 * Wire format: `FMC1;` then steps joined by `;`. A step is
 * `type:field:field…` where type is one of `wait`, `tap`, `swipe`, `type`,
 * `launch`. Field separators `;` and `:` inside free text are escaped with
 * a backslash; backslash itself escapes to `\\`. The codec always publishes
 * the **canonical** form (no redundant escapes), so
 * `decode → encode` is a fixed point and the host oracle can diff bytes.
 *
 * Declared narrowings: at most [MAX_STEPS] steps; `type` text <= 512 units;
 * wait <= 60 000 ms; tap/swipe coordinates 0..1000 permille with swipe
 * duration 50..5000 ms; `launch` package `[a-z0-9._-]{1,64}`; unknown
 * version, unknown type, bad escape and overflow all refuse.
 */
object MacroExchangeCodec {
    const val SCOPE =
        "Deterministic FMC1 macro export/import codec with canonical escaping; no execution, no file IO."

    const val MAX_STEPS = 1024
    const val MAX_TEXT = 512
    const val MAX_WAIT_MS = 60_000
    const val HEADER = "FMC1;"

    private val PKG_RE = Regex("[a-z0-9._-]{1,64}")

    sealed interface Step {
        data class Wait(val ms: Int) : Step
        data class Tap(val x: Int, val y: Int) : Step
        data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val ms: Int) : Step
        data class TypeText(val text: String) : Step
        data class Launch(val pkg: String) : Step
    }

    private fun permille(v: Int, what: String): Int {
        require(v in 0..1000) { "$what out of 0..1000" }
        return v
    }

    private fun unescape(field: String): String {
        val out = StringBuilder(field.length)
        var i = 0
        while (i < field.length) {
            val c = field[i]
            if (c == '\\') {
                require(i + 1 < field.length) { "dangling escape" }
                val n = field[i + 1]
                require(n == '\\' || n == ':' || n == ';') { "illegal escape: $n" }
                out.append(n)
                i += 2
            } else {
                require(c != '\n' && c != '\r' && c != '\t') { "control character in field" }
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun escape(field: String): String {
        val out = StringBuilder(field.length + 4)
        for (c in field) {
            when (c) {
                '\\', ':', ';' -> { out.append('\\'); out.append(c) }
                '\n', '\r', '\t' -> throw IllegalArgumentException("control character in field")
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    fun decode(wire: String): List<Step> {
        require(wire.startsWith(HEADER)) { "bad macro header" }
        val body = wire.substring(HEADER.length)
        if (body.isEmpty()) return emptyList()
        val tokens = splitEscaped(body, ';')
        require(tokens.size <= MAX_STEPS) { "too many steps: $MAX_STEPS" }
        return tokens.map { token ->
            val fields = splitEscaped(token, ':')
            require(fields.isNotEmpty()) { "empty step" }
            when (fields[0]) {
                "wait" -> {
                    require(fields.size == 2) { "wait takes one field" }
                    val ms = fields[1].toIntOrNull() ?: throw IllegalArgumentException("wait not an int")
                    require(ms in 0..MAX_WAIT_MS) { "wait out of range" }
                    Step.Wait(ms)
                }
                "tap" -> {
                    require(fields.size == 3) { "tap takes x:y" }
                    Step.Tap(
                        permille(fields[1].toIntOrNull() ?: throw IllegalArgumentException("tap x not an int"), "x"),
                        permille(fields[2].toIntOrNull() ?: throw IllegalArgumentException("tap y not an int"), "y"),
                    )
                }
                "swipe" -> {
                    require(fields.size == 6) { "swipe takes x1:y1:x2:y2:ms" }
                    val ms = fields[5].toIntOrNull() ?: throw IllegalArgumentException("swipe ms not an int")
                    require(ms in 50..5000) { "swipe duration out of range" }
                    Step.Swipe(
                        permille(fields[1].toIntOrNull() ?: throw IllegalArgumentException("x1 not an int"), "x1"),
                        permille(fields[2].toIntOrNull() ?: throw IllegalArgumentException("y1 not an int"), "y1"),
                        permille(fields[3].toIntOrNull() ?: throw IllegalArgumentException("x2 not an int"), "x2"),
                        permille(fields[4].toIntOrNull() ?: throw IllegalArgumentException("y2 not an int"), "y2"),
                        ms,
                    )
                }
                "type" -> {
                    require(fields.size == 2) { "type takes one field" }
                    val text = unescape(fields[1])
                    require(text.length <= MAX_TEXT) { "type text over $MAX_TEXT" }
                    Step.TypeText(text)
                }
                "launch" -> {
                    require(fields.size == 2) { "launch takes one field" }
                    val pkg = unescape(fields[1])
                    require(PKG_RE.matches(pkg)) { "bad package name" }
                    Step.Launch(pkg)
                }
                else -> throw IllegalArgumentException("unknown step type: ${fields[0]}")
            }
        }
    }

    fun encode(steps: List<Step>): String {
        require(steps.size <= MAX_STEPS) { "too many steps: $MAX_STEPS" }
        if (steps.isEmpty()) return HEADER
        val body = steps.joinToString(";") { step ->
            when (step) {
                is Step.Wait -> "wait:${step.ms}"
                is Step.Tap -> "tap:${step.x}:${step.y}"
                is Step.Swipe -> "swipe:${step.x1}:${step.y1}:${step.x2}:${step.y2}:${step.ms}"
                is Step.TypeText -> {
                    require(step.text.length <= MAX_TEXT) { "type text over $MAX_TEXT" }
                    require(step.text.isNotEmpty()) { "empty type text is not exchangeable" }
                    "type:${escape(step.text)}"
                }
                is Step.Launch -> {
                    require(PKG_RE.matches(step.pkg)) { "bad package name" }
                    "launch:${escape(step.pkg)}"
                }
            }
        }
        return HEADER + body
    }

    /** Canonical round-trip: decode then re-encode, refusing malformed wire. */
    fun canonicalize(wire: String): String = encode(decode(wire))

    /** Splits on `sep` while honouring backslash escapes (no empty fields). */
    private fun splitEscaped(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        var saw = false
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                cur.append(c).append(s[i + 1])
                saw = true
                i += 2
                continue
            }
            if (c == sep) {
                require(saw || cur.isNotEmpty()) { "empty field" }
                out.add(cur.toString())
                cur.setLength(0)
                saw = false
                i++
                continue
            }
            cur.append(c)
            saw = true
            i++
        }
        require(saw || cur.isNotEmpty()) { "empty field at end" }
        out.add(cur.toString())
        return out
    }
}

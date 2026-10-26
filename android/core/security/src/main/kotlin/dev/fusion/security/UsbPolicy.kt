// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import java.util.Collections
import java.util.Locale

/**
 * Bounded USBGuard-subset policy model for the laptop/host companion workflow.
 *
 * This parses and evaluates ordered USB authorization rules (allow/block/reject, id match,
 * with-interface class match, and the stateful "no second keyboard" condition). It is a
 * READ-ONLY policy helper: it never authorizes hardware, and policy generation REFUSES to
 * run against an untrusted initial inventory (generating rules from a compromised machine
 * would bless the attacker). VID/PID/interface strings are match keys, not authenticated
 * device identity: descriptors can be spoofed and a passive inline logger is invisible.
 *
 * Grammar subset (one rule per line, '#' comments, blank lines allowed):
 *   target id <vid>:<pid|*> [with-interface { <cc:ss:pp> ... }] [if <condition>]
 *   target ::= allow | block | reject
 *   condition ::= !allowed-matches(with-interface one-of { <cc:ss:pp> ... })
 * Unmatched devices hit the implicit default: block.
 */
object UsbPolicy {
    enum class Target { ALLOW, BLOCK, REJECT }
    data class InterfaceClass(val cls: Int, val sub: Int, val proto: Int) {
        init { require(cls in 0..255 && sub in 0..255 && proto in 0..255) }
        override fun toString() = "%02x:%02x:%02x".format(cls, sub, proto)
        companion object {
            val HID_KEYBOARD_BOOT = InterfaceClass(3, 1, 1)
            val HID_MOUSE_BOOT = InterfaceClass(3, 1, 2)
            fun parse(token: String): InterfaceClass {
                val parts = token.split(':')
                require(parts.size == 3) { "Interface class needs cc:ss:pp" }
                fun hex(s: String) = s.lowercase(Locale.ROOT).let {
                    require(it.length == 2 && it.all { c -> c in '0'..'9' || c in 'a'..'f' }) { "Bad hex byte: $s" }
                    it.toInt(16)
                }
                return InterfaceClass(hex(parts[0]), hex(parts[1]), hex(parts[2]))
            }
        }
    }
    data class Condition(val notAllowedInterfaces: Set<InterfaceClass>) {
        init { require(notAllowedInterfaces.isNotEmpty() && notAllowedInterfaces.size <= 16) }
    }
    data class Rule(val target: Target, val vid: Int?, val pid: Int?, val interfaces: Set<InterfaceClass>?, val condition: Condition?) {
        init {
            require(vid == null || vid in 0..0xffff) ; require(pid == null || pid in 0..0xffff)
            require(interfaces == null || (interfaces.isNotEmpty() && interfaces.size <= 32))
        }
        val audit: String get() = buildString {
            append(target.name.lowercase(Locale.ROOT))
            append(if (vid == null) " id *:*" else " id %04x:%s".format(vid, if (pid == null) "*" else "%04x".format(pid)))
            if (interfaces != null) append(" with-interface { " + interfaces.sortedBy { it.toString() }.joinToString(" ") + " }")
            if (condition != null) append(" if !allowed-matches(with-interface one-of { " +
                condition.notAllowedInterfaces.sortedBy { it.toString() }.joinToString(" ") + " })")
        }
    }
    data class Device(val vid: Int, val pid: Int, val interfaces: Set<InterfaceClass>, val label: String = "") {
        init {
            require(vid in 0..0xffff && pid in 0..0xffff && interfaces.size in 1..32)
            require(label.length <= 128 && label.none { it.isISOControl() })
        }
    }
    data class Verdict(val target: Target, val ruleIndex: Int?, val defaulted: Boolean)

    const val MAX_RULES = 256
    const val MAX_LINE = 1024

    fun parse(text: String): List<Rule> {
        require(text.length <= 65536) { "Policy text exceeds 64KiB" }
        val rules = text.lines().mapIndexedNotNull { n, raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@mapIndexedNotNull null
            require(line.length <= MAX_LINE) { "Line ${n + 1} exceeds $MAX_LINE chars" }
            parseRule(line, n + 1)
        }
        require(rules.size in 1..MAX_RULES) { "Need 1..$MAX_RULES rules" }
        return Collections.unmodifiableList(rules)
    }

    private fun parseRule(line: String, n: Int): Rule {
        val tokens = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun fail(): Nothing = throw IllegalArgumentException("Bad rule at line $n: $line")
        if (tokens.size < 3) fail()
        val target = try { Target.valueOf(tokens[0].uppercase(Locale.ROOT)) } catch (_: IllegalArgumentException) { fail() }
        if (tokens[1] != "id") fail()
        val id = tokens[2].split(':')
        if (id.size != 2) fail()
        fun hex4(s: String, allowStar: Boolean): Int? {
            if (s == "*") { if (!allowStar) fail(); return null }
            val l = s.lowercase(Locale.ROOT)
            if (l.length != 4 || !l.all { it in '0'..'9' || it in 'a'..'f' }) fail()
            return l.toInt(16)
        }
        val vid = hex4(id[0], true)
        val pid = if (vid == null && id[1] == "*") null else hex4(id[1], true)
        if (vid == null && pid != null) fail()
        var interfaces: Set<InterfaceClass>? = null
        var condition: Condition? = null
        var i = 3
        fun braces(opener: String): List<String> {
            if (i >= tokens.size || tokens[i] != opener) fail()
            i++
            val out = mutableListOf<String>()
            while (i < tokens.size && tokens[i] != "}") { out += tokens[i]; i++ }
            if (i >= tokens.size) fail()
            i++
            return out
        }
        if (i < tokens.size && tokens[i] == "with-interface") { i++; interfaces = braces("{").map { InterfaceClass.parse(it) }.toSet(); if (interfaces.isEmpty()) fail() }
        if (i < tokens.size && tokens[i] == "if") {
            i++
            if (i + 1 >= tokens.size || tokens[i] != "!allowed-matches(with-interface" || tokens[i + 1] != "one-of") fail()
            i += 2
            condition = Condition(braces("{").map { InterfaceClass.parse(it) }.toSet())
            if (condition.notAllowedInterfaces.isEmpty()) fail()
        }
        if (i != tokens.size) fail()
        return Rule(target, vid, pid, interfaces, condition)
    }

    /**
     * First-match evaluation with already-allowed context for stateful conditions.
     * Returns implicit BLOCK when nothing matches.
     */
    fun evaluate(rules: List<Rule>, device: Device, alreadyAllowed: List<Device> = emptyList()): Verdict {
        require(rules.size in 1..MAX_RULES && alreadyAllowed.size <= 1024)
        rules.forEachIndexed { index, rule ->
            if (rule.vid != null && rule.vid != device.vid) return@forEachIndexed
            if (rule.pid != null && rule.pid != device.pid) return@forEachIndexed
            // with-interface matches when the device exposes ALL listed interfaces (suspicious-combo detection).
            if (rule.interfaces != null && !device.interfaces.containsAll(rule.interfaces)) return@forEachIndexed
            if (rule.condition != null) {
                val blocked = rule.condition.notAllowedInterfaces
                if (alreadyAllowed.any { it.interfaces.any { i -> i in blocked } }) return@forEachIndexed
            }
            return Verdict(rule.target, index, false)
        }
        return Verdict(Target.BLOCK, null, true)
    }

    /**
     * Generates an allowlist from a connected-device inventory. REFUSES unless the caller
     * asserts a trusted inventory ([trustedInventory]) gathered on a clean machine: running
     * this on an untrusted machine would bless an attacker's device. Output is a proposal
     * for human review, never an applied policy.
     */
    fun generatePolicy(devices: List<Device>, trustedInventory: Boolean): List<Rule> {
        require(!trustedInventory || devices.size in 1..128) { "Inventory needs 1..128 devices" }
        if (!trustedInventory) throw IllegalStateException(
            "Refused: allowlist generation on an untrusted inventory would bless unknown hardware. " +
                "Re-run on a clean machine with physical custody, then review every rule.")
        return Collections.unmodifiableList(devices.map {
            Rule(Target.ALLOW, it.vid, it.pid, it.interfaces, null)
        } + Rule(Target.BLOCK, null, null, null, null))
    }
}

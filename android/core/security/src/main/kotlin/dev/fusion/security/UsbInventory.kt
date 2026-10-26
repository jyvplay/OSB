// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import java.util.Collections
import java.util.Locale

/**
 * USB inventory diffing for the laptop/host companion workflow. Parses `lsusb`-style
 * lines (`Bus 002 Device 003: ID 046d:c52b Logitech...`) with an optional extended
 * `{ 03:01:01 03:00:00 }` interface suffix (our own export format), then diffs two
 * inventories to flag arrivals, removals and label changes.
 *
 * Honest limits: plain lsusb lines carry NO interface data, so keyboard-channel
 * arrivals from plain lines are UNKNOWN (need `lsusb -v`, sysfs, or physical check),
 * never "safe". Descriptors are spoofable match keys, not authenticated identity, and
 * a passive inline logger is invisible to any inventory. This never authorizes hardware.
 */
object UsbInventory {
    const val MAX_BYTES = 256 * 1024
    const val MAX_DEVICES = 512

    data class Entry(val bus: Int, val device: Int, val vid: Int, val pid: Int, val label: String,
                     val interfaces: Set<UsbPolicy.InterfaceClass>) {
        init {
            require(bus in 1..999 && device in 1..999 && vid in 0..0xffff && pid in 0..0xffff)
            require(label.length <= 128 && label.none { it.isISOControl() })
            require(interfaces.size <= 32)
        }
        val id: String get() = "%04x:%04x".format(vid, pid)
        val keyboardCapable: Boolean? get() = if (interfaces.isEmpty()) null
            else interfaces.any { it.cls == 3 && it.sub == 1 && it.proto == 1 }
    }

    enum class Severity { INFO, UNKNOWN, HIGH }

    data class Finding(val severity: Severity, val text: String)

    data class Diff(val arrivals: List<Entry>, val removals: List<Entry>, val findings: List<Finding>)

    private val line = Regex("^Bus\\s+(\\d{1,3})\\s+Device\\s+(\\d{1,3}):\\s+ID\\s+([0-9a-fA-F]{4}):([0-9a-fA-F]{4})(\\s+(.*))?$")

    fun parse(text: String): List<Entry> {
        require(text.length <= MAX_BYTES) { "Inventory exceeds $MAX_BYTES bytes" }
        val entries = text.lines().mapIndexedNotNull { n, raw ->
            val lineNo = n + 1
            val row = raw.trim()
            if (row.isEmpty() || row.startsWith("#")) return@mapIndexedNotNull null
            require(row.length <= 512) { "Line $lineNo too long" }
            val m = line.matchEntire(row.substringBefore(" {")) ?: throw IllegalArgumentException("Bad lsusb line $lineNo")
            var interfaces = emptySet<UsbPolicy.InterfaceClass>()
            val brace = row.indexOf(" {")
            if (brace >= 0) {
                require(row.endsWith("}")) { "Unbalanced interface suffix at line $lineNo" }
                val tokens = row.substring(brace + 2, row.length - 1).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                require(tokens.isNotEmpty()) { "Empty interface suffix at line $lineNo" }
                interfaces = tokens.map {
                    try { UsbPolicy.InterfaceClass.parse(it) } catch (_: IllegalArgumentException) { throw IllegalArgumentException("Bad interface token at line $lineNo: $it") }
                }.toSet()
            }
            Entry(m.groupValues[1].toInt(), m.groupValues[2].toInt(),
                m.groupValues[3].lowercase(Locale.ROOT).toInt(16), m.groupValues[4].lowercase(Locale.ROOT).toInt(16),
                (m.groupValues.getOrNull(6) ?: "").trim().take(128), interfaces)
        }
        require(entries.size in 1..MAX_DEVICES) { "Need 1..$MAX_DEVICES inventory entries" }
        return Collections.unmodifiableList(entries)
    }

    /** Identity = bus:device:vid:pid. Port-path stability is NOT assumed across reboots. */
    fun diff(before: List<Entry>, after: List<Entry>): Diff {
        require(before.size <= MAX_DEVICES && after.size <= MAX_DEVICES)
        fun key(e: Entry) = "${e.bus}:${e.device}:${e.id}"
        val old = before.associateBy(::key)
        val new = after.associateBy(::key)
        val arrivals = after.filter { it !in old.values && key(it) !in old }.let { list ->
            Collections.unmodifiableList(list)
        }
        val removals = Collections.unmodifiableList(before.filter { key(it) !in new })
        val findings = mutableListOf<Finding>()
        arrivals.forEach { e ->
            when (e.keyboardCapable) {
                true -> findings += Finding(Severity.HIGH, "New keyboard-capable device ${e.id} (${e.label.take(64)}): verify physically before use.")
                null -> findings += Finding(Severity.UNKNOWN, "New device ${e.id} without interface data: keyboard capability UNKNOWN, verify physically.")
                false -> findings += Finding(Severity.INFO, "New device ${e.id} (${e.label.take(64)}): no keyboard interface in its descriptor.")
            }
        }
        removals.forEach { e -> findings += Finding(Severity.INFO, "Removed device ${e.id} (${e.label.take(64)}).") }
        after.forEach { e ->
            val was = old[key(e)]
            if (was != null && (was.label != e.label || was.interfaces != e.interfaces))
                findings += Finding(Severity.UNKNOWN, "Descriptor changed for ${e.id}: was '${was.label.take(48)}', now '${e.label.take(48)}'. Descriptors are spoofable; verify physically.")
        }
        return Diff(arrivals, removals, Collections.unmodifiableList(findings))
    }
}

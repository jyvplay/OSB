// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Bounded CPU capability detector. Parses a /proc/cpuinfo-style block and
 * extracts the CPU vendor, model name, feature flags, and core count.
 * Only the flags explicitly listed in [KNOWN_FLAGS] are reported; all
 * others are ignored. Does not issue CPUID — input is supplied by the
 * caller (the kernel/ABI layer reads /proc/cpuinfo).
 *
 * Declared narrowings: input at most [MAX_INPUT] chars; at most
 * [MAX_FLAGS] feature flags reported; at most [MAX_CORES] cores counted;
 * model name and vendor strings are ASCII-only and capped at
 * [MAX_STRING_LEN] chars. Unknown flags are silently dropped, not refused.
 */
class CpuCapabilities private constructor(
    private val vendor: String?,
    private val model: String?,
    private val cores: Int,
    private val flags: Set<String>
) {
    companion object {
        const val MAX_INPUT = 1 shl 14  // 16 KiB
        const val MAX_FLAGS = 64
        const val MAX_CORES = 1024
        const val MAX_STRING_LEN = 128
        private val KNOWN_FLAGS = setOf(
            "fpu", "vme", "de", "pse", "tsc", "msr", "pae", "mce",
            "cx8", "apic", "sep", "mtrr", "pge", "mca", "cmov", "pat",
            "pse36", "clflush", "mmx", "fxsr", "sse", "sse2", "ss",
            "ht", "tm", "ia64", "sse3", "pclmulqdq", "dtes64", "monitor",
            "ds_cpl", "vmx", "smx", "est", "tm2", "sse4_1", "sse4_2",
            "x2apic", "movbe", "popcnt", "tsc_deadline_timer", "aes",
            "xsave", "osxsave", "avx", "f16c", "rdrand", "hypervisor"
        )

        data class Report(
            val vendor: String?,
            val model: String?,
            val coreCount: Int,
            val featureFlags: Set<String>
        )

        fun empty(): CpuCapabilities = CpuCapabilities(null, null, 0, emptySet())
    }

    fun parse(input: String): Report {
        require(input.length <= MAX_INPUT) { "input exceeds $MAX_INPUT chars" }
        var vendor: String? = null
        var model: String? = null
        var cores = 0
        val flags = mutableSetOf<String>()

        for (line in input.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val colon = trimmed.indexOf(':')
            if (colon < 0) continue
            val key = trimmed.substring(0, colon).trim()
            val value = trimmed.substring(colon + 1).trim()
            when (key) {
                "vendor_id" -> vendor = ascii(value)
                "model name" -> model = ascii(value)
                "cpu cores" -> cores = value.toIntOrNull()?.coerceIn(0, MAX_CORES) ?: 0
                "processor" -> cores = (cores + 1).coerceAtMost(MAX_CORES)
                "flags" -> {
                    for (f in value.split(" ")) {
                        val flag = f.trim().lowercase()
                        if (flag.isNotEmpty() && KNOWN_FLAGS.contains(flag) && flags.size < MAX_FLAGS) {
                            flags.add(flag)
                        }
                    }
                }
            }
        }
        return Report(vendor, model, cores, flags.toSet())
    }

    fun hasFlag(flag: String): Boolean = flags.contains(flag.lowercase())
    fun coreCount(): Int = cores

    private fun ascii(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            if (c.code in 0x20..0x7E) sb.append(c)
            else break
        }
        return sb.toString().take(if (sb.length > MAX_STRING_LEN) MAX_STRING_LEN else sb.length)
    }

}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.baselineprofile

/**
 * ART `profgen` / DEX **Hot-Startup Method Layout Partitioner (`HSPL`)** scoring call-graph trace
 * records into `Hot` (`H`), `Startup` (`S`), and `Post-Startup` (`P`) flag masks while respecting
 * 4 KiB / 16 KiB `mmap` page locality!
 */
object StartupDexLayoutHotnessScorer {
    const val SCOPE =
        "ART Baseline Profile HSPL (Hot/Startup/Post-Startup) method scorer and 16 KiB page-aligned DEX locality packer."

    data class MethodSample(
        val descriptor: String,
        val invokedBeforeFirstFrame: Boolean,
        val invocationCount: Int,
        val bytecodeBytes: Int,
    )

    data class PackedProfilePlan(
        val hsplLines: List<String>,
        val hotStartupBytecodeBytes: Int,
        val pages16KiBRequired: Int,
    )

    fun planProfile(samples: List<MethodSample>, hotCallThreshold: Int = 5): PackedProfilePlan {
        require(hotCallThreshold >= 1)
        val lines = ArrayList<String>()
        var startupBytes = 0
        for (s in samples.sortedBy { it.descriptor }) {
            require(s.descriptor.startsWith("L") && "->" in s.descriptor) {
                "Invalid JVM/DEX method descriptor '${s.descriptor}'"
            }
            val flags = buildString {
                if (s.invocationCount >= hotCallThreshold) append('H')
                if (s.invokedBeforeFirstFrame) append('S')
                if (!s.invokedBeforeFirstFrame && s.invocationCount > 0) append('P')
            }
            if (flags.isNotEmpty()) {
                lines.add("$flags${s.descriptor}")
                if ('S' in flags || 'H' in flags) startupBytes += s.bytecodeBytes.coerceAtLeast(0)
            }
        }
        val pages16k = if (startupBytes == 0) 0 else (startupBytes + 16_383) / 16_384
        return PackedProfilePlan(lines, startupBytes, pages16k)
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Pure-JVM **IEC 80000-13 Binary (`KiB`, `MiB`, `GiB`) & SI Decimal (`KB`, `MB`, `GB`) Human-Readable
 * Byte Size Formatter & Lossless Parser** for `:core:common`, Recall archive quotas, and Files.
 */
object HumanByteSizeCodec {
    const val SCOPE =
        "Lossless IEC 80000-13 (KiB/MiB/GiB/TiB) and SI (KB/MB/GB/TB) byte formatter & parser."

    private val BINARY_UNITS = listOf("B" to 1L, "KiB" to 1024L, "MiB" to 1_048_576L, "GiB" to 1_073_741_824L, "TiB" to 1_099_511_627_776L)

    fun formatIec(bytes: Long): String {
        require(bytes >= 0L) { "Negative byte count: $bytes" }
        if (bytes < 1024L) return "$bytes B"
        val unit = BINARY_UNITS.last { bytes >= it.second }
        val scaled = bytes.toDouble() / unit.second.toDouble()
        return String.format(java.util.Locale.US, "%.2f %s", scaled, unit.first)
    }

    fun parseBytes(spec: String): Long {
        val m = Regex("""^\s*(\d+(?:\.\d+)?)\s*([A-Za-z]*)\s*$""").matchEntire(spec)
            ?: throw IllegalArgumentException("Malformed byte size: '$spec'")
        val num = m.groupValues[1].toDouble()
        val unit = m.groupValues[2].uppercase()
        val mult = when (unit) {
            "", "B" -> 1.0
            "KIB" -> 1024.0
            "MIB" -> 1_048_576.0
            "GIB" -> 1_073_741_824.0
            "TIB" -> 1_099_511_627_776.0
            "KB" -> 1_000.0
            "MB" -> 1_000_000.0
            "GB" -> 1_000_000_000.0
            "TB" -> 1_000_000_000_000.0
            else -> throw IllegalArgumentException("Unknown byte unit: '$unit'")
        }
        return kotlin.math.round(num * mult).toLong()
    }
}

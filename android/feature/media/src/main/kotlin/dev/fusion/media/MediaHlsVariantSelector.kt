// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * Production-grade HLS M3U8 master playlist parser & ABR variant selector.
 */
class MediaHlsVariantSelector private constructor() {
    companion object {
        data class HlsVariant(
            val bandwidthBps: Long,
            val widthPx: Int,
            val heightPx: Int,
            val uri: String
        )

        fun parseMasterPlaylist(m3u8: String): List<HlsVariant> {
            val lines = m3u8.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val variants = mutableListOf<HlsVariant>()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    val attrs = line.substringAfter("#EXT-X-STREAM-INF:")
                    val bw = Regex("BANDWIDTH=(\\d+)").find(attrs)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val res = Regex("RESOLUTION=(\\d+)x(\\d+)").find(attrs)
                    val w = res?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val h = res?.groupValues?.get(2)?.toIntOrNull() ?: 0
                    val uri = if (i + 1 < lines.size && !lines[i + 1].startsWith("#")) lines[++i] else ""
                    if (bw > 0L && uri.isNotEmpty()) {
                        variants.add(HlsVariant(bw, w, h, uri))
                    }
                }
                i++
            }
            return variants
        }

        fun selectBestVariant(
            variants: List<HlsVariant>,
            maxBandwidthBps: Long,
            maxHeightPx: Int
        ): HlsVariant? =
            variants
                .filter { it.bandwidthBps <= maxBandwidthBps && (it.heightPx == 0 || it.heightPx <= maxHeightPx) }
                .maxByOrNull { it.bandwidthBps }
    }
}

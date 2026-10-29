// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * HTML5 `<picture><source type media srcset>` + `<img srcset>` highest-fidelity / viewport-matched
 * asset selector for **Recall** (`TIER_1_DIRECT_RANGE`), ensuring Recall archives the full-resolution
 * AVIF/WebP/PNG master asset rather than a low-res blurry thumbnail.
 */
object RecallSrcsetPictureDensitySelector {
    const val SCOPE =
        "HTML5 <picture>/<source>/srcset width (w) and pixel-density (x) descriptor parser selecting optimal or highest-resolution archival media candidate."

    data class SrcsetCandidate(
        val url: String,
        val mimeType: String,
        val widthPx: Int?,
        val densityX: Double?,
    )

    private val FORMAT_PRIORITY = mapOf(
        "image/avif" to 4,
        "image/webp" to 3,
        "image/png" to 2,
        "image/jpeg" to 1,
    )

    fun parseSrcsetList(srcset: String, defaultMime: String = "image/jpeg"): List<SrcsetCandidate> {
        if (srcset.isBlank()) return emptyList()
        return srcset.split(',')
            .mapNotNull { entry ->
                val parts = entry.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (parts.isEmpty()) return@mapNotNull null
                val url = parts[0]
                var width: Int? = null
                var density: Double? = null
                if (parts.size >= 2) {
                    val desc = parts[1].lowercase()
                    when {
                        desc.endsWith("w") -> width = desc.dropLast(1).toIntOrNull()?.takeIf { it > 0 }
                        desc.endsWith("x") -> density = desc.dropLast(1).toDoubleOrNull()?.takeIf { it > 0.0 }
                    }
                }
                SrcsetCandidate(url, defaultMime.lowercase(), width, density)
            }
    }

    fun selectArchivalMaster(candidates: List<SrcsetCandidate>, preferArchivalMaxResolution: Boolean = true, viewportWidthPx: Int = 1440): SrcsetCandidate? {
        if (candidates.isEmpty()) return null
        return if (preferArchivalMaxResolution) {
            candidates.maxWithOrNull(
                compareBy<SrcsetCandidate>(
                    { it.widthPx ?: ((it.densityX ?: 1.0) * 1000.0).toInt() },
                    { FORMAT_PRIORITY[it.mimeType] ?: 0 },
                ),
            )
        } else {
            val sufficient = candidates.filter { (it.widthPx ?: viewportWidthPx) >= viewportWidthPx }
            (sufficient.minByOrNull { it.widthPx ?: viewportWidthPx } ?: candidates.maxByOrNull { it.widthPx ?: 0 })
        }
    }
}

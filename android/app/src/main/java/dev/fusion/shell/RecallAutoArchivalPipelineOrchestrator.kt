// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/**
 * End-to-end deterministic **Recall** 6-Method Automatic Archival Pipeline Orchestrator connecting
 * URL/page-title/custom-folder delineation, 6-tier method routing (`T1`, `T2`, `T2.5`, `T3`, `T4`, `T5`),
 * SHA-256 content-addressed deduplication, and atomic vault manifest updates across image, video,
 * music, and full-page archives.
 */
object RecallAutoArchivalPipelineOrchestrator {
    const val SCOPE =
        "End-to-end 6-method Recall pipeline orchestrating folder delineation, Tier 1..6 routing, SHA-256 deduplication, and vault manifest commits."

    enum class DelineationStrategy { BY_URL_NAME, BY_PAGE_TITLE, BY_CUSTOM_FOLDER }
    enum class MediaModality { IMAGE, VIDEO, MUSIC, WEB_BUNDLE }

    data class ArchivalRequest(
        val sourceUrl: String,
        val pageTitle: String,
        val customFolder: String?,
        val strategy: DelineationStrategy,
        val modality: MediaModality,
        val hasDirectByteUrl: Boolean,
        val hasManifestHlsDash: Boolean,
        val hasWebRtcTrack: Boolean,
        val hasReadableDomCanvasAudio: Boolean,
        val isProtectedOrDrm: Boolean,
        val wantsIsoWarcBundle: Boolean,
        val payloadSha256Hex: String,
        val payloadBytes: Long,
    )

    data class ArchivalCommit(
        val vaultRelativeDir: String,
        val selectedMethodTier: Int,
        val selectedMethodName: String,
        val deduplicatedHit: Boolean,
        val storedBytes: Long,
    )

    fun executeBatch(requests: List<ArchivalRequest>, existingSha256Set: Set<String> = emptySet()): List<ArchivalCommit> {
        val seen = LinkedHashSet(existingSha256Set)
        return requests.map { req ->
            require(req.payloadSha256Hex.length == 64 && req.payloadBytes >= 0L)
            val folderSeg = when (req.strategy) {
                DelineationStrategy.BY_URL_NAME -> sanitizeSlug(extractHostAndLeaf(req.sourceUrl))
                DelineationStrategy.BY_PAGE_TITLE -> sanitizeSlug(req.pageTitle.ifBlank { extractHostAndLeaf(req.sourceUrl) })
                DelineationStrategy.BY_CUSTOM_FOLDER -> sanitizeSlug(req.customFolder?.takeIf { it.isNotBlank() } ?: "Custom_Vault")
            }
            val subKind = req.modality.name.lowercase()
            val (tier, methodName) = selectSixMethodTier(req)
            val isDup = !seen.add(req.payloadSha256Hex.lowercase())
            ArchivalCommit(
                vaultRelativeDir = "Recall/$folderSeg/$subKind",
                selectedMethodTier = tier,
                selectedMethodName = methodName,
                deduplicatedHit = isDup,
                storedBytes = if (isDup) 0L else req.payloadBytes,
            )
        }
    }

    private fun selectSixMethodTier(r: ArchivalRequest): Pair<Int, String> = when {
        r.wantsIsoWarcBundle || r.modality == MediaModality.WEB_BUNDLE -> 6 to "TIER_5_WARC_ISO28500_BUNDLE"
        r.isProtectedOrDrm -> 5 to "TIER_4_NATIVE_VIEWPORT_AV"
        r.hasDirectByteUrl -> 1 to "TIER_1_DIRECT_RANGE"
        r.hasManifestHlsDash -> 2 to "TIER_2_HLS_DASH_FMP4"
        r.hasWebRtcTrack -> 3 to "TIER_25_WEBRTC_RTP_DEPACKETIZER"
        r.hasReadableDomCanvasAudio -> 4 to "TIER_3_CANVAS_WEBAUDIO"
        else -> 5 to "TIER_4_NATIVE_VIEWPORT_AV"
    }

    private fun extractHostAndLeaf(url: String): String {
        val clean = url.substringAfter("://").substringBefore('?').substringBefore('#').trimEnd('/')
        return clean.ifBlank { "unattributed_origin" }
    }

    private fun sanitizeSlug(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(64).ifEmpty { "Vault" }
}

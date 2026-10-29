// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import java.security.MessageDigest

/**
 * Pure-JVM session coordinator for **Recall** inside `:app` (`dev.fusion.shell`).
 *
 * Tracks active recording state, folder-delineation mode (`BY_URL_NAME`, `BY_PAGE_TITLE`,
 * `BY_CUSTOM_TEMPLATE`), per-tier capture counters (`T1..T4`), incognito auto-pause posture,
 * and reversible session archive receipts so any auto-saved media item can be undone/purged
 * cleanly by the user.
 */
object RecallSessionCoordinator {
    const val SCOPE =
        "Recall app-level session coordinator tracking delineation mode (BY_URL_NAME / BY_PAGE_TITLE / BY_CUSTOM_TEMPLATE), 4-tier capture counters, incognito bypass, and reversible capture receipts."

    enum class FolderMode { BY_URL_NAME, BY_PAGE_TITLE, BY_CUSTOM_TEMPLATE }

    data class CaptureEventReceipt(
        val receiptId: String,
        val mediaKind: String, // IMAGE, VIDEO, MUSIC
        val tierUsed: Int,     // 1..4
        val relativeArchivePath: String,
        val byteSize: Long,
        val pageTitle: String,
        val pageUrl: String,
        val deduplicated: Boolean,
    )

    data class SessionState(
        val autoRecordEnabled: Boolean = true,
        val recordImages: Boolean = true,
        val recordVideos: Boolean = true,
        val recordMusic: Boolean = true,
        val folderMode: FolderMode = FolderMode.BY_URL_NAME,
        val customTemplate: String = "{custom}/{yyyy}-{mm}-{dd}/{domain}/{kind}",
        val customVaultName: String = "MyRecallArchive",
        val receipts: List<CaptureEventReceipt> = emptyList(),
        val refusedDrmOrSecureCount: Int = 0,
        val skippedIncognitoCount: Int = 0,
    ) {
        val tierCounts: Map<Int, Int>
            get() = (1..6).associateWith { t -> receipts.count { it.tierUsed == t } }

        val kindCounts: Map<String, Int>
            get() = listOf("IMAGE", "VIDEO", "MUSIC").associateWith { k -> receipts.count { it.mediaKind == k } }

        val totalUniqueSavedBytes: Long
            get() = receipts.filter { !it.deduplicated }.sumOf { it.byteSize }
    }

    fun shouldRecordKind(state: SessionState, mediaKind: String): Boolean {
        if (!state.autoRecordEnabled) return false
        return when (mediaKind.uppercase()) {
            "IMAGE" -> state.recordImages
            "VIDEO" -> state.recordVideos
            "MUSIC" -> state.recordMusic
            else -> false
        }
    }

    fun appendCaptured(
        state: SessionState,
        mediaKind: String,
        tierUsed: Int,
        relativeArchivePath: String,
        byteSize: Long,
        pageTitle: String,
        pageUrl: String,
        deduplicated: Boolean,
    ): SessionState {
        require(tierUsed in 1..6 && byteSize > 0L && relativeArchivePath.isNotBlank()) {
            "Invalid Recall capture receipt parameters"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$relativeArchivePath|$tierUsed|${state.receipts.size}".toByteArray(Charsets.UTF_8))
            .take(6).joinToString("") { "%02x".format(it) }
        val receipt = CaptureEventReceipt(
            receiptId = "rcpt-$digest",
            mediaKind = mediaKind.uppercase(),
            tierUsed = tierUsed,
            relativeArchivePath = relativeArchivePath,
            byteSize = byteSize,
            pageTitle = pageTitle,
            pageUrl = pageUrl,
            deduplicated = deduplicated,
        )
        return state.copy(receipts = state.receipts + receipt)
    }

    fun undoReceipt(state: SessionState, receiptId: String): Pair<SessionState, CaptureEventReceipt?> {
        val target = state.receipts.firstOrNull { it.receiptId == receiptId } ?: return state to null
        return state.copy(receipts = state.receipts.filterNot { it.receiptId == receiptId }) to target
    }
}

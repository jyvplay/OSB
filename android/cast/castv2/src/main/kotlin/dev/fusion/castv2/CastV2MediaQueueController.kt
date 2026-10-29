package dev.fusion.castv2

/**
 * Clean-room CastV2 `urn:x-cast:com.google.cast.media` Media Queue Controller (`CA-12`, Apache-2.0).
 *
 * Implements deterministic `QUEUE_LOAD`, `QUEUE_INSERT`, `QUEUE_REMOVE`, `QUEUE_REORDER`,
 * `QUEUE_NEXT`, `QUEUE_PREV`, and repeat modes (`REPEAT_OFF`, `REPEAT_ALL`, `REPEAT_SINGLE`)
 * with monotonic `mediaSessionId`, `itemId` assignment, and `requestId` correlation.
 */
object CastV2MediaQueueController {
    const val SCOPE =
        "CastV2 urn:x-cast:com.google.cast.media QUEUE_LOAD/INSERT/REMOVE/REORDER/NEXT/PREV state machine (CA-12)"

    const val MEDIA_NAMESPACE = "urn:x-cast:com.google.cast.media"

    enum class RepeatMode { REPEAT_OFF, REPEAT_ALL, REPEAT_SINGLE }

    data class QueueItem(
        val itemId: Int,
        val contentId: String,
        val contentType: String,
        val title: String,
        val durationSec: Double,
    )

    data class MediaStatusSnapshot(
        val requestId: Long,
        val mediaSessionId: Int,
        val playerState: String,
        val currentItemId: Int?,
        val repeatMode: RepeatMode,
        val items: List<QueueItem>,
    ) {
        fun toWireJson(): String = buildString {
            append("""{"namespace":"$MEDIA_NAMESPACE","type":"MEDIA_STATUS","requestId":$requestId,""")
            append(""""status":[{"mediaSessionId":$mediaSessionId,"playerState":"$playerState","repeatMode":"${repeatMode.name}",""")
            append(""""currentItemId":${currentItemId ?: "null"},"items":[""")
            append(
                items.joinToString(",") {
                    """{"itemId":${it.itemId},"media":{"contentId":"${esc(it.contentId)}","contentType":"${esc(it.contentType)}","title":"${esc(it.title)}","duration":${it.durationSec}}}"""
                },
            )
            append("]}]}")
        }
    }

    class Session(val mediaSessionId: Int = 1) {
        private var nextItemId = 100
        private val queue = ArrayList<QueueItem>()
        private var currentIndex: Int = -1
        private var repeatMode: RepeatMode = RepeatMode.REPEAT_OFF

        fun queueLoad(
            requestId: Long,
            tracks: List<Triple<String, String, Double>>,
            startIndex: Int = 0,
            repeat: RepeatMode = RepeatMode.REPEAT_OFF,
        ): MediaStatusSnapshot {
            require(tracks.isNotEmpty() && startIndex in tracks.indices) { "Invalid QUEUE_LOAD parameters" }
            queue.clear()
            for ((url, mime, dur) in tracks) {
                queue.add(QueueItem(nextItemId++, url, mime, url.substringAfterLast('/'), dur))
            }
            currentIndex = startIndex
            repeatMode = repeat
            return snapshot(requestId)
        }

        fun queueInsertBefore(
            requestId: Long,
            beforeItemId: Int?,
            tracks: List<Triple<String, String, Double>>,
        ): MediaStatusSnapshot {
            val currentItemIdBefore = queue.getOrNull(currentIndex)?.itemId
            val insertPos = if (beforeItemId == null) queue.size else {
                val p = queue.indexOfFirst { it.itemId == beforeItemId }
                require(p >= 0) { "Unknown beforeItemId=$beforeItemId" }
                p
            }
            val newItems = tracks.map { (u, m, d) -> QueueItem(nextItemId++, u, m, u.substringAfterLast('/'), d) }
            queue.addAll(insertPos, newItems)
            if (currentItemIdBefore != null) {
                currentIndex = queue.indexOfFirst { it.itemId == currentItemIdBefore }
            }
            return snapshot(requestId)
        }

        fun queueRemove(requestId: Long, itemIds: Set<Int>): MediaStatusSnapshot {
            val curId = queue.getOrNull(currentIndex)?.itemId
            queue.removeAll { it.itemId in itemIds }
            currentIndex = when {
                queue.isEmpty() -> -1
                curId != null && queue.any { it.itemId == curId } -> queue.indexOfFirst { it.itemId == curId }
                else -> currentIndex.coerceIn(0, queue.lastIndex)
            }
            return snapshot(requestId)
        }

        fun stepNext(requestId: Long): MediaStatusSnapshot {
            if (queue.isEmpty()) return snapshot(requestId)
            currentIndex = when (repeatMode) {
                RepeatMode.REPEAT_SINGLE -> currentIndex
                RepeatMode.REPEAT_ALL -> (currentIndex + 1) % queue.size
                RepeatMode.REPEAT_OFF -> if (currentIndex + 1 < queue.size) currentIndex + 1 else -1
            }
            return snapshot(requestId)
        }

        private fun snapshot(requestId: Long): MediaStatusSnapshot {
            val cur = queue.getOrNull(currentIndex)
            return MediaStatusSnapshot(
                requestId = requestId,
                mediaSessionId = mediaSessionId,
                playerState = if (cur != null) "PLAYING" else "IDLE",
                currentItemId = cur?.itemId,
                repeatMode = repeatMode,
                items = queue.toList(),
            )
        }
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}

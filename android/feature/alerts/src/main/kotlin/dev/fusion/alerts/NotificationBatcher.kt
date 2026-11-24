// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

/**
 * Bounded notification batcher. Groups notifications into batches by
 * category and time window, enforcing per-batch and global caps. When a
 * batch reaches its size or time limit, it is sealed and returned for
 * dispatch. Notifications are not actually sent — the transport layer
 * handles delivery.
 *
 * Declared narrowings: at most [MAX_BATCHES] open batches; at most
 * [MAX_NOTIFICATIONS_PER_BATCH] notifications per batch; notification IDs
 * at most [MAX_ID_LEN] chars; categories at most [MAX_CATEGORY_LEN] chars;
 * time windows in milliseconds must be positive and at most [MAX_WINDOW_MS].
 */
class NotificationBatcher private constructor(
    private val batches: LinkedHashMap<String, Batch>
) {
    companion object {
        const val MAX_BATCHES = 128
        const val MAX_NOTIFICATIONS_PER_BATCH = 256
        const val MAX_ID_LEN = 128
        const val MAX_CATEGORY_LEN = 64
        const val MAX_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
        private val ID_RE = Regex("[a-zA-Z0-9_-]{1,64}")
        private val CAT_RE = Regex("[a-z][a-z0-9_-]*")

        data class Batch(
            val category: String,
            val windowMs: Long,
            val notifications: MutableList<String>,
            val sealed: Boolean = false
        )

        data class SealResult(
            val sealedBatch: String,
            val notificationCount: Int
        )

        fun empty(): NotificationBatcher = NotificationBatcher(LinkedHashMap())
    }

    fun openBatch(category: String, windowMs: Long): String {
        require(CAT_RE.matches(category)) { "invalid category: $category" }
        require(category.length <= MAX_CATEGORY_LEN) { "category too long" }
        require(windowMs > 0 && windowMs <= MAX_WINDOW_MS) { "window out of bounds" }
        require(batches.size < MAX_BATCHES || batches.values.any { it.category == category && !it.sealed }) {
            "too many batches: $MAX_BATCHES"
        }
        // Return existing open batch for same category or create new
        val existing = batches.values.firstOrNull { it.category == category && !it.sealed }
        return existing?.category ?: category
    }

    fun add(batchId: String, notificationId: String): Boolean {
        require(ID_RE.matches(notificationId)) { "invalid notification id: $notificationId" }
        require(notificationId.length <= MAX_ID_LEN) { "notification id too long" }
        val batch = batches[batchId] ?: return false
        require(!batch.sealed) { "batch $batchId is sealed" }
        require(batch.notifications.size < MAX_NOTIFICATIONS_PER_BATCH) {
            "batch full: $MAX_NOTIFICATIONS_PER_BATCH"
        }
        require(!batch.notifications.contains(notificationId)) { "duplicate notification" }
        batch.notifications.add(notificationId)
        return true
    }

    fun sealBatch(batchId: String): SealResult? {
        val batch = batches[batchId] ?: return null
        require(!batch.sealed) { "already sealed" }
        val sealed = batch.copy(sealed = true)
        batches[batchId] = sealed
        return SealResult(batchId, batch.notifications.size)
    }

    fun batchSize(batchId: String): Int? = batches[batchId]?.notifications?.size
    fun openBatchCount(): Int = batches.values.count { !it.sealed }
    fun sealedBatchCount(): Int = batches.values.count { it.sealed }
    fun clear() = batches.clear()
}

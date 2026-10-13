package dev.fusion.alerts

import android.app.Notification
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.fusion.storage.AlertRow
import dev.fusion.storage.AlertStore
import dev.fusion.storage.FusionDb

/**
 * Real listener. Honest constraints:
 *  - VISIBILITY_SECRET notifications are never written to disk.
 *  - Ongoing/group-summary rows are skipped (no synthetic "activity" claims).
 *  - Flood limited to 20 posts / 5s window.
 *  - Log is cleared when the listener is disconnected or access is revoked.
 */
class FusionNotificationListener : NotificationListenerService() {

    private val store: AlertStore by lazy { AlertStore(FusionDb(this)) }
    private var windowStart = 0L
    private var windowCount = 0

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (notification.visibility == Notification.VISIBILITY_SECRET) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val now = System.currentTimeMillis()
        if (now - windowStart > WINDOW_MS) { windowStart = now; windowCount = 0 }
        windowCount++
        if (windowCount > MAX_PER_WINDOW) return

        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return

        runCatching {
            store.insert(
                AlertRow(
                    key = sbn.key,
                    pkg = sbn.packageName,
                    title = title.take(200),
                    body = body.take(500),
                    posted = sbn.postTime
                )
            )
            store.prune(now - RETENTION_MS)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        runCatching { store.remove(key) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Access revoked or service torn down: do not keep a stale shadow copy.
        runCatching { store.clear() }
    }

    companion object {
        private const val WINDOW_MS = 5_000L
        private const val MAX_PER_WINDOW = 20
        private const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000

        /** Reads the real system setting; never assumes access was granted. */
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ).orEmpty()
            return flat.split(':').any { it.contains(context.packageName) }
        }

        fun settingsAction(): String = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
    }
}

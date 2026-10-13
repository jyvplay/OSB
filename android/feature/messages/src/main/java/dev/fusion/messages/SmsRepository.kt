package dev.fusion.messages

import android.content.Context
import android.provider.Telephony
import android.telephony.SmsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmsMessage(
    val address: String,
    val body: String,
    val date: Long,
    val direction: String // "inbox" or "sent"
)

/**
 * Real SMS access via the platform Telephony provider and SmsManager.
 *
 * Honest scope:
 *  - READ: SMS inbox + sent box via Telephony.Sms (needs READ_SMS at runtime).
 *  - SEND: SMS text only via SmsManager (needs SEND_SMS at runtime).
 *  - NOT the default SMS app. This app does not hold the SMS role, so it does
 *    not receive SMS_DELIVER broadcasts and does not send/receive MMS.
 *  - MMS send, RCS, and iMessage/Matrix are not implemented.
 */
class SmsRepository(private val context: Context) {

    suspend fun inbox(limit: Int = 50): Result<List<SmsMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            query(Telephony.Sms.Inbox.CONTENT_URI, limit) +
                query(Telephony.Sms.Sent.CONTENT_URI, limit)
        }
    }

    private fun query(uri: android.net.Uri, limit: Int): List<SmsMessage> {
        val direction = if (uri == Telephony.Sms.Inbox.CONTENT_URI) "inbox" else "sent"
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null, null, Telephony.Sms.DEFAULT_SORT_ORDER
        ) ?: return emptyList()
        return cursor.use { c ->
            val addr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            buildList {
                while (c.moveToNext() && size < limit) {
                    add(SmsMessage(c.getString(addr), c.getString(body), c.getLong(date), direction))
                }
            }
        }.sortedByDescending { it.date }.take(limit)
    }

    suspend fun send(address: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(address.isNotBlank()) { "Enter a recipient number" }
            require(body.isNotEmpty()) { "Message body is empty" }
            val mgr = context.getSystemService(SmsManager::class.java)
                ?: throw IllegalStateException("SmsManager is unavailable on this device")
            mgr.sendTextMessage(address, null, body, null, null)
        }
    }
}

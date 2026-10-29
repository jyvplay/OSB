// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Clean-room `RFC 3501` IMAP4rev1 untagged `FETCH (UID FLAGS ENVELOPE)` parser, offline triage
 * queue (`ARCHIVE`, `STAR`, `READ`, `SPAM`), and `RFC 6068` `mailto:` handoff URI builder (`PR-06`).
 */
object ImapEnvelopeTriageStore {
    const val SCOPE =
        "Pure-JVM RFC 3501 IMAP4rev1 FETCH ENVELOPE/FLAGS parser, offline UID STORE triage command builder, and RFC 6068 mailto handoff encoder (PR-06)."

    data class MailEnvelope(
        val uid: Long,
        val flags: Set<String>,
        val dateHeader: String,
        val subject: String,
        val senderAddress: String,
    )

    private val FETCH_RE = Regex(
        """^\*\s+\d+\s+FETCH\s+\(UID\s+(\d+)\s+FLAGS\s+\(([^)]*)\)\s+ENVELOPE\s+\("([^"]*)"\s+"([^"]*)"\s+\(\("([^"]*)"\s+NIL\s+"([^"]+)"\s+"([^"]+)"\)\)""",
        RegexOption.IGNORE_CASE,
    )

    fun parseUntaggedFetch(line: String): MailEnvelope {
        val m = FETCH_RE.find(line.trim())
            ?: throw IllegalArgumentException("malformed IMAP FETCH ENVELOPE response: '$line'")
        val uid = m.groupValues[1].toLong()
        val flags = m.groupValues[2].trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        val date = m.groupValues[3]
        val subj = m.groupValues[4]
        val mailbox = m.groupValues[6]
        val host = m.groupValues[7]
        return MailEnvelope(uid, flags, date, subj, "$mailbox@$host")
    }

    fun formatUidStoreCommand(tag: String, uids: List<Long>, addFlags: List<String>, silent: Boolean = true): String {
        require(tag.matches(Regex("^[A-Za-z0-9]+$")) && uids.isNotEmpty() && uids.all { it > 0L }) {
            "invalid IMAP tag or UID set"
        }
        require(addFlags.isNotEmpty() && addFlags.all { it.startsWith("\\") || it.matches(Regex("^[A-Za-z0-9_-]+$")) }) {
            "invalid IMAP flag token"
        }
        val op = if (silent) "+FLAGS.SILENT" else "+FLAGS"
        return "$tag UID STORE ${uids.sorted().distinct().joinToString(",")} $op (${addFlags.joinToString(" ")})\r\n"
    }

    fun buildMailtoUri(to: String, subject: String, body: String): String {
        require('@' in to && !to.contains('\n') && !to.contains('\r')) { "invalid recipient '$to'" }
        val s = URLEncoder.encode(subject, StandardCharsets.UTF_8).replace("+", "%20")
        val b = URLEncoder.encode(body, StandardCharsets.UTF_8).replace("+", "%20")
        return "mailto:$to?subject=$s&body=$b"
    }
}

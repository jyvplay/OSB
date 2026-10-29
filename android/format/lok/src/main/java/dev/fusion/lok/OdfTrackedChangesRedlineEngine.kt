// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

/**
 * Pure-JVM **OASIS ODF 1.3 `<text:tracked-changes>` (`<text:insertion>` / `<text:deletion>`)
 * Accept & Reject Redline Serializer/Resolver** for `:format:odf`.
 */
object OdfTrackedChangesRedlineEngine {
    const val SCOPE =
        "OASIS ODF 1.3 text:tracked-changes redline revision serializer and deterministic ACCEPT_ALL / REJECT_ALL text resolver."

    enum class ChangeKind { INSERTION, DELETION }

    data class RedlineSegment(
        val id: String,
        val author: String,
        val isoTimestamp: String,
        val kind: ChangeKind?, // null = unchanged baseline run
        val text: String,
    )

    fun resolveView(segments: List<RedlineSegment>, acceptAllChanges: Boolean): String = buildString {
        for (s in segments) {
            when (s.kind) {
                null -> append(s.text)
                ChangeKind.INSERTION -> if (acceptAllChanges) append(s.text)
                ChangeKind.DELETION -> if (!acceptAllChanges) append(s.text)
            }
        }
    }

    fun toTrackedChangesXml(segments: List<RedlineSegment>): String = buildString {
        append("<office:text xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" ")
        append("xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" ")
        append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\">")
        val changed = segments.filter { it.kind != null }
        if (changed.isNotEmpty()) {
            append("<text:tracked-changes>")
            for (c in changed) {
                append("<text:changed-region text:id=\"").append(c.id).append("\">")
                val tag = if (c.kind == ChangeKind.INSERTION) "text:insertion" else "text:deletion"
                append("<").append(tag).append("><office:change-info><dc:creator>")
                    .append(c.author).append("</dc:creator><dc:date>").append(c.isoTimestamp)
                    .append("</dc:date></office:change-info>")
                if (c.kind == ChangeKind.DELETION) append("<text:p>").append(c.text).append("</text:p>")
                append("</").append(tag).append("></text:changed-region>")
            }
            append("</text:tracked-changes>")
        }
        append("<text:p>")
        for (s in segments) {
            when (s.kind) {
                null -> append(s.text)
                ChangeKind.INSERTION -> append("<text:change-start text:change-id=\"").append(s.id).append("\"/>")
                    .append(s.text).append("<text:change-end text:change-id=\"").append(s.id).append("\"/>")
                ChangeKind.DELETION -> append("<text:change text:change-id=\"").append(s.id).append("\"/>")
            }
        }
        append("</text:p></office:text>")
    }
}

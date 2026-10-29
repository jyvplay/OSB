// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Clean-room OASIS OpenDocument 1.3 (`<text:tracked-changes>`) revision-tracking XML codec,
 * bringing ODF `.odt` tracked changes to parity with OOXML `w:ins` / `w:del`.
 *
 * Supports:
 * - `<text:changed-region text:id="cr1">` with `<text:insertion>` and `<text:deletion>` metadata
 *   (`<office:change-info>`, `<dc:creator>`, `<dc:date>`) and deleted text recovery.
 * - Inline change anchors `<text:change-start text:change-id="..."/>`,
 *   `<text:change-end text:change-id="..."/>`, and `<text:change text:change-id="..."/>`.
 * - Deterministic accept-all / reject-all plain-text projection plus round-trip XML emission.
 */
object OdfTrackedChangesCodec {
    enum class ChangeKind { INSERTION, DELETION }

    data class ChangeRecord(
        val id: String,
        val kind: ChangeKind,
        val author: String,
        val isoTimestamp: String,
        val text: String,
        val positionIndex: Int,
    ) {
        init {
            require(id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "invalid ODF change id '$id'" }
            require(author.isNotBlank()) { "author required" }
            require(isoTimestamp.matches(Regex("\\d{4}-\\d{2}-\\d{2}T[0-9:.+-Z]+"))) {
                "invalid ISO-8601 timestamp '$isoTimestamp'"
            }
        }
    }

    data class TrackedParagraph(
        val segments: List<Segment>,
    ) {
        sealed interface Segment {
            data class Unchanged(val text: String) : Segment
            data class Inserted(val id: String, val author: String, val isoTimestamp: String, val text: String) : Segment
            data class Deleted(val id: String, val author: String, val isoTimestamp: String, val deletedText: String) : Segment
        }

        fun acceptAll(): String = buildString {
            for (s in segments) {
                when (s) {
                    is Segment.Unchanged -> append(s.text)
                    is Segment.Inserted -> append(s.text)
                    is Segment.Deleted -> {}
                }
            }
        }

        fun rejectAll(): String = buildString {
            for (s in segments) {
                when (s) {
                    is Segment.Unchanged -> append(s.text)
                    is Segment.Inserted -> {}
                    is Segment.Deleted -> append(s.deletedText)
                }
            }
        }
    }

    fun encodeOdtBodyXml(paragraph: TrackedParagraph): String {
        val regions = StringBuilder()
        val body = StringBuilder()
        for (seg in paragraph.segments) {
            when (seg) {
                is TrackedParagraph.Segment.Unchanged -> body.append(escapeXml(seg.text))
                is TrackedParagraph.Segment.Inserted -> {
                    regions.append(
                        """<text:changed-region xml:id="${seg.id}" text:id="${seg.id}"><text:insertion><office:change-info><dc:creator>${escapeXml(seg.author)}</dc:creator><dc:date>${seg.isoTimestamp}</dc:date></office:change-info></text:insertion></text:changed-region>""",
                    )
                    body.append("""<text:change-start text:change-id="${seg.id}"/>""")
                    body.append(escapeXml(seg.text))
                    body.append("""<text:change-end text:change-id="${seg.id}"/>""")
                }
                is TrackedParagraph.Segment.Deleted -> {
                    regions.append(
                        """<text:changed-region xml:id="${seg.id}" text:id="${seg.id}"><text:deletion><office:change-info><dc:creator>${escapeXml(seg.author)}</dc:creator><dc:date>${seg.isoTimestamp}</dc:date></office:change-info><text:p>${escapeXml(seg.deletedText)}</text:p></text:deletion></text:changed-region>""",
                    )
                    body.append("""<text:change text:change-id="${seg.id}"/>""")
                }
            }
        }
        return buildString {
            append("""<office:text>""")
            if (regions.isNotEmpty()) {
                append("""<text:tracked-changes>""").append(regions).append("""</text:tracked-changes>""")
            }
            append("""<text:p>""").append(body).append("""</text:p></office:text>""")
        }
    }

    fun decodeOdtBodyXml(xml: String): TrackedParagraph {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) {
            "XXE DOCTYPE/ENTITY declarations refused in ODF tracked-changes XML"
        }
        data class RegionMeta(val kind: ChangeKind, val author: String, val date: String, val deletedText: String)
        val regionMap = LinkedHashMap<String, RegionMeta>()
        val regRx = Regex("""<text:changed-region[^>]*text:id="([^"]+)"[^>]*>(.*?)</text:changed-region>""", RegexOption.DOT_MATCHES_ALL)
        for (m in regRx.findAll(xml)) {
            val id = m.groupValues[1]
            val inner = m.groupValues[2]
            val author = unescapeXml(Regex("""<dc:creator>(.*?)</dc:creator>""").find(inner)?.groupValues?.get(1) ?: "Unknown")
            val date = Regex("""<dc:date>(.*?)</dc:date>""").find(inner)?.groupValues?.get(1)?.trim() ?: "2026-01-01T00:00:00Z"
            if (inner.contains("<text:insertion>")) {
                regionMap[id] = RegionMeta(ChangeKind.INSERTION, author, date, "")
            } else if (inner.contains("<text:deletion>")) {
                val delTxt = unescapeXml(Regex("""<text:p>(.*?)</text:p>""", RegexOption.DOT_MATCHES_ALL).find(inner)?.groupValues?.get(1) ?: "")
                regionMap[id] = RegionMeta(ChangeKind.DELETION, author, date, delTxt)
            }
        }

        val cleaned = xml.replace(Regex("""<text:tracked-changes>.*?</text:tracked-changes>""", RegexOption.DOT_MATCHES_ALL), "")
        val pBody = Regex("""<text:p>(.*?)</text:p>""", RegexOption.DOT_MATCHES_ALL).find(cleaned)?.groupValues?.get(1)
            ?: return TrackedParagraph(emptyList())

        val segments = ArrayList<TrackedParagraph.Segment>()
        val tagRx = Regex("""<text:change-start\s+text:change-id="([^"]+)"/>|<text:change-end\s+text:change-id="([^"]+)"/>|<text:change\s+text:change-id="([^"]+)"/>""")
        var cursor = 0
        var activeInsId: String? = null
        val insBuf = StringBuilder()

        for (tm in tagRx.findAll(pBody)) {
            val chunk = unescapeXml(pBody.substring(cursor, tm.range.first))
            if (chunk.isNotEmpty()) {
                if (activeInsId != null) insBuf.append(chunk)
                else segments.add(TrackedParagraph.Segment.Unchanged(chunk))
            }
            val startId = tm.groupValues[1]
            val endId = tm.groupValues[2]
            val pointId = tm.groupValues[3]
            when {
                startId.isNotEmpty() -> {
                    activeInsId = startId
                    insBuf.setLength(0)
                }
                endId.isNotEmpty() -> {
                    require(activeInsId == endId) { "mismatched change-end '$endId' vs '$activeInsId'" }
                    val meta = regionMap[endId] ?: error("unknown changed-region '$endId'")
                    segments.add(TrackedParagraph.Segment.Inserted(endId, meta.author, meta.date, insBuf.toString()))
                    activeInsId = null
                    insBuf.setLength(0)
                }
                pointId.isNotEmpty() -> {
                    val meta = regionMap[pointId] ?: error("unknown deletion region '$pointId'")
                    segments.add(TrackedParagraph.Segment.Deleted(pointId, meta.author, meta.date, meta.deletedText))
                }
            }
            cursor = tm.range.last + 1
        }
        val tail = unescapeXml(pBody.substring(cursor))
        if (tail.isNotEmpty()) segments.add(TrackedParagraph.Segment.Unchanged(tail))
        require(activeInsId == null) { "unclosed text:change-start '$activeInsId'" }
        return TrackedParagraph(segments)
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun unescapeXml(s: String): String = s
        .replace("&quot;", "\"")
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")
}

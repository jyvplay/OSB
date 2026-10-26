// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import javax.xml.parsers.DocumentBuilderFactory

/**
 * R28 EXECUTED host kernel. Podlove Simple Chapters reader: bounded chapter
 * list (start + title + optional href) for podcasts. Parsing only — no fetch,
 * no playback, no artwork.
 */
class ChapterMarks private constructor(val chapters: List<Chapter>) {
    data class Chapter(val startMs: Long, val title: String, val href: String?) {
        init {
            require(startMs in 0..86_400_000L) { "Chapter start must be within 24h" }
            require(title.length in 1..256) { "Chapter title must be 1..256 chars" }
            if (href != null) require(href.length in 1..2048 && href.all { it.code in 0x21..0x7e }) {
                "Chapter href must be bounded ASCII"
            }
        }
    }

    companion object {
        const val MAX_CHAPTERS = 256
        const val MAX_XML_BYTES = 1 shl 20
        private const val NS = "http://podlove.org/simple-chapters"
        private val START = Regex("(?:(\\d{1,3}):)?([0-5]?\\d):([0-5]\\d)(?:\\.(\\d{1,3}))?")

        fun parse(xml: String): ChapterMarks {
            require(xml.toByteArray(Charsets.UTF_8).size in 1..MAX_XML_BYTES) { "Bounded chapter XML required" }
            val db = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }.newDocumentBuilder()
            val doc = try {
                db.parse(xml.byteInputStream())
            } catch (t: Exception) {
                throw IllegalArgumentException("Chapter XML does not parse: ${t.message?.take(120)}")
            }
            val nodes = doc.getElementsByTagNameNS(NS, "chapter")
            require(nodes.length in 1..MAX_CHAPTERS) { "1..$MAX_CHAPTERS chapters required" }
            val out = (0 until nodes.length).map { i ->
                val el = nodes.item(i)
                val start = el.attributes.getNamedItem("start")?.nodeValue
                    ?: throw IllegalArgumentException("Chapter ${i + 1} lacks a start time")
                val title = el.attributes.getNamedItem("title")?.nodeValue
                    ?: throw IllegalArgumentException("Chapter ${i + 1} lacks a title")
                Chapter(parseStart(start.trim(), i), title.trim(), el.attributes.getNamedItem("href")?.nodeValue?.trim())
            }
            out.zipWithNext { a, b -> require(a.startMs < b.startMs) { "Chapter starts must strictly increase" } }
            return ChapterMarks(out)
        }

        private fun parseStart(raw: String, index: Int): Long {
            val m = START.matchEntire(raw) ?: throw IllegalArgumentException("Chapter ${index + 1} has a bad start time")
            val h = m.groupValues[1].ifEmpty { "0" }.toLong()
            val min = m.groupValues[2].toLong()
            val sec = m.groupValues[3].toLong()
            val frac = m.groupValues[4]
            val ms = if (frac.isEmpty()) 0 else frac.padEnd(3, '0').toLong()
            val total = h * 3_600_000L + min * 60_000L + sec * 1000L + ms
            require(total in 0..86_400_000L) { "Chapter ${index + 1} start out of range" }
            return total
        }
    }
}

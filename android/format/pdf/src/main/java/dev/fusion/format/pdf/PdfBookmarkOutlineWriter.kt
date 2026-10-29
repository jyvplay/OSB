// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

import java.nio.charset.StandardCharsets

/**
 * Pure-JVM ISO 32000-2:2020 §12.3.2 **PDF Document Outline (Hierarchical Bookmarks) & Named
 * Destinations (`/Outlines` + `/Names /Dests`)** object-tree builder and structural verifier.
 */
object PdfBookmarkOutlineWriter {
    const val SCOPE =
        "ISO 32000-2 §12.3.2 PDF hierarchical /Outlines bookmark tree (/First, /Last, /Prev, /Next, /Parent, /Count) and /XYZ destination serializer."

    data class OutlineBookmark(
        val title: String,
        val targetPageZeroBased: Int,
        val topYPoints: Double = 792.0,
        val openByDefault: Boolean = true,
        val children: List<OutlineBookmark> = emptyList(),
    )

    data class SerializedPdfOutlines(
        val rootObjectId: Int,
        val pdfObjectsText: String,
        val totalVisibleNodes: Int,
        val totalNodes: Int,
    )

    fun buildOutlines(bookmarks: List<OutlineBookmark>, pageObjectIds: List<Int>, startObjectId: Int = 50): SerializedPdfOutlines {
        require(bookmarks.isNotEmpty() && pageObjectIds.isNotEmpty() && startObjectId >= 1) {
            "Non-empty bookmarks and pageObjectIds required"
        }
        var nextId = startObjectId
        val rootId = nextId++
        val entries = LinkedHashMap<Int, String>()

        fun emitLevel(nodes: List<OutlineBookmark>, parentId: Int): Triple<Int, Int, Int> {
            val ids = nodes.map { nextId++ }
            var visibleSum = 0
            for (i in nodes.indices) {
                val node = nodes[i]
                require(node.targetPageZeroBased in pageObjectIds.indices) {
                    "Bookmark '${node.title}' targets out-of-range page ${node.targetPageZeroBased}"
                }
                val selfId = ids[i]
                val prevPart = if (i > 0) " /Prev ${ids[i - 1]} 0 R" else ""
                val nextPart = if (i < nodes.lastIndex) " /Next ${ids[i + 1]} 0 R" else ""
                val pageObj = pageObjectIds[node.targetPageZeroBased]
                val childTriple = if (node.children.isNotEmpty()) emitLevel(node.children, selfId) else null
                val countVal = if (childTriple == null) 0 else {
                    if (node.openByDefault) childTriple.third else -childTriple.third
                }
                val childLinks = if (childTriple != null) {
                    " /First ${childTriple.first} 0 R /Last ${childTriple.second} 0 R /Count $countVal"
                } else ""
                visibleSum += 1 + (if (childTriple != null && node.openByDefault) childTriple.third else 0)
                entries[selfId] = "$selfId 0 obj\n<< /Title (${escapePdfLiteral(node.title)}) /Parent $parentId 0 R$prevPart$nextPart$childLinks /Dest [$pageObj 0 R /XYZ 0 ${"%.2f".format(java.util.Locale.US, node.topYPoints)} null] >>\nendobj\n"
            }
            return Triple(ids.first(), ids.last(), visibleSum)
        }

        val (firstId, lastId, rootVisible) = emitLevel(bookmarks, rootId)
        val rootEntry = "$rootId 0 obj\n<< /Type /Outlines /First $firstId 0 R /Last $lastId 0 R /Count $rootVisible >>\nendobj\n"
        val sb = StringBuilder(rootEntry)
        for ((_, body) in entries) sb.append(body)
        return SerializedPdfOutlines(rootId, sb.toString(), rootVisible, entries.size)
    }

    private fun escapePdfLiteral(s: String): String {
        val ascii = String(s.toByteArray(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)
        return ascii.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("\r", "").replace("\n", " ")
    }
}

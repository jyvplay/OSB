// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * SURT-sorted **CDXJ** (`Crawl Data Index JSON` — Internet Archive / Webrecorder standard)
 * index writer and binary-search replay lookup for **Recall** `WARC/1.1` (`TIER_5_WARC_ISO28500_BUNDLE`)
 * web archives.
 *
 * Converts URLs to Sort-friendly URI Reordering Transform (`https://sub.example.org/a/b?x=1` ->
 * `org,example,sub)/a/b?x=1`) and emits sorted `<surt> <timestamp14> {json}` lines for `O(log N)` replay!
 */
object RecallCdxjReplayIndexCodec {
    const val SCOPE =
        "Webrecorder/IIPC SURT-sorted CDXJ index line encoder and O(log N) binary-search WARC offset lookup for Recall Tier-5 archives."

    data class CdxjEntry(
        val url: String,
        val timestamp14: String,
        val warcFilename: String,
        val offsetBytes: Long,
        val lengthBytes: Long,
        val mime: String,
        val status: Int,
        val digestSha256: String,
    )

    fun toSurtKey(url: String): String {
        val clean = url.trim()
        val schemeSep = clean.indexOf("://")
        val withoutScheme = if (schemeSep >= 0) clean.substring(schemeSep + 3) else clean
        val slash = withoutScheme.indexOf('/')
        val hostPort = if (slash >= 0) withoutScheme.substring(0, slash) else withoutScheme
        val pathQuery = if (slash >= 0) withoutScheme.substring(slash) else "/"
        val colon = hostPort.indexOf(':')
        val host = (if (colon >= 0) hostPort.substring(0, colon) else hostPort).lowercase()
        val portSuffix = if (colon >= 0) hostPort.substring(colon) else ""
        val reversedLabels = host.split('.').filter { it.isNotEmpty() }.reversed().joinToString(",")
        return "$reversedLabels$portSuffix)$pathQuery"
    }

    fun buildSortedCdxj(entries: List<CdxjEntry>): List<String> {
        return entries.map { e ->
            require(e.timestamp14.length == 14 && e.timestamp14.all { it.isDigit() }) {
                "CDXJ timestamp must be 14 digits YYYYMMDDhhmmss"
            }
            val surt = toSurtKey(e.url)
            val json = "{\"url\":\"${esc(e.url)}\",\"mime\":\"${esc(e.mime)}\",\"status\":${e.status}," +
                "\"digest\":\"sha256:${esc(e.digestSha256)}\",\"length\":${e.lengthBytes}," +
                "\"offset\":${e.offsetBytes},\"filename\":\"${esc(e.warcFilename)}\"}"
            "$surt ${e.timestamp14} $json"
        }.sorted()
    }

    fun lookupExactUrlClosestTimestamp(sortedCdxjLines: List<String>, url: String, targetTimestamp14: String): String? {
        val prefix = toSurtKey(url) + " "
        var lo = 0
        var hi = sortedCdxjLines.lastIndex
        var firstMatch = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val line = sortedCdxjLines[mid]
            if (line >= prefix) {
                if (line.startsWith(prefix)) firstMatch = mid
                hi = mid - 1
            } else {
                lo = mid + 1
            }
        }
        if (firstMatch < 0) return null
        val targetVal = targetTimestamp14.toLongOrNull() ?: 0L
        var bestLine: String? = null
        var bestDist = Long.MAX_VALUE
        var i = firstMatch
        while (i < sortedCdxjLines.size && sortedCdxjLines[i].startsWith(prefix)) {
            val tsStr = sortedCdxjLines[i].substring(prefix.length).take(14)
            val tsVal = tsStr.toLongOrNull() ?: 0L
            val dist = kotlin.math.abs(tsVal - targetVal)
            if (dist < bestDist) { bestDist = dist; bestLine = sortedCdxjLines[i] }
            i++
        }
        return bestLine
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}

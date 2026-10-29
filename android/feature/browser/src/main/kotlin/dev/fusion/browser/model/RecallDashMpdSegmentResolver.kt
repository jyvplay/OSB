// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * Pure-JVM **ISO/IEC 23009-1 MPEG-DASH `.mpd` (`SegmentTemplate` `$Number$` / `$Time$` / `$Bandwidth$`)
 * Representation Resolver** for **Recall Method 2**, complementing `RecallTier2HlsDashAssembler`
 * so sites serving adaptive `.mpd` manifests (YouTube-style / Vimeo-style / PeerTube non-DRM DASH)
 * resolve directly into highest-bitrate video + highest-bitrate audio initialization & media segments!
 */
object RecallDashMpdSegmentResolver {
    const val SCOPE =
        "Recall Method 2 ISO/IEC 23009-1 MPEG-DASH .mpd SegmentTemplate (\$RepresentationID\$, \$Bandwidth\$, \$Number\$, \$Time\$) parser and highest-bitrate A/V representation selector."

    data class DashRepresentationPlan(
        val contentType: String, // video or audio
        val representationId: String,
        val codecs: String,
        val bandwidthBps: Long,
        val initializationUrl: String,
        val mediaSegmentUrls: List<String>,
    )

    fun resolveMpd(mpdXml: String, baseUrl: String, segmentCount: Int = 5): List<DashRepresentationPlan> {
        require(segmentCount in 1..10_000)
        val cleanBase = baseUrl.substringBeforeLast('/', baseUrl).trimEnd('/')
        val adaptRegex = Regex("""<AdaptationSet\b([^>]*)>([\s\S]*?)</AdaptationSet>""", RegexOption.IGNORE_CASE)
        val repRegex = Regex("""<Representation\b([^>]*)(?:/>|>([\s\S]*?)</Representation>)""", RegexOption.IGNORE_CASE)
        val segTplRegex = Regex("""<SegmentTemplate\b([^>]*)/?>""", RegexOption.IGNORE_CASE)

        val selected = ArrayList<DashRepresentationPlan>()
        for (am in adaptRegex.findAll(mpdXml)) {
            val aAttrs = parseXmlAttrs(am.groupValues[1])
            val aBody = am.groupValues[2]
            val outerTpl = segTplRegex.find(aBody)?.let { parseXmlAttrs(it.groupValues[1]) }.orEmpty()
            val cType = (aAttrs["contentType"] ?: aAttrs["mimeType"]?.substringBefore('/') ?: "video").lowercase()

            var best: DashRepresentationPlan? = null
            for (rm in repRegex.findAll(aBody)) {
                val rAttrs = parseXmlAttrs(rm.groupValues[1])
                val rBody = rm.groupValues[2]
                val innerTpl = segTplRegex.find(rBody)?.let { parseXmlAttrs(it.groupValues[1]) }.orEmpty()
                val tpl = outerTpl + innerTpl

                val repId = rAttrs["id"] ?: continue
                val bw = rAttrs["bandwidth"]?.toLongOrNull() ?: 0L
                val codecs = rAttrs["codecs"] ?: aAttrs["codecs"] ?: ""
                val startNum = tpl["startNumber"]?.toIntOrNull() ?: 1
                val initPattern = tpl["initialization"] ?: continue
                val mediaPattern = tpl["media"] ?: continue

                val initUrl = joinBase(cleanBase, expandDashTemplate(initPattern, repId, bw, startNum, 0L))
                val segs = (0 until segmentCount).map { idx ->
                    val num = startNum + idx
                    val dur = tpl["duration"]?.toLongOrNull() ?: 2000L
                    joinBase(cleanBase, expandDashTemplate(mediaPattern, repId, bw, num, idx * dur))
                }
                val cand = DashRepresentationPlan(cType, repId, codecs, bw, initUrl, segs)
                if (best == null || cand.bandwidthBps > best.bandwidthBps) best = cand
            }
            if (best != null) selected.add(best)
        }
        return selected
    }

    private fun expandDashTemplate(pattern: String, repId: String, bw: Long, number: Int, time: Long): String {
        var s = pattern.replace("\$RepresentationID\$", repId)
            .replace("\$Bandwidth\$", bw.toString())
            .replace("\$Time\$", time.toString())
        s = Regex("\\\$Number(?:\\\$(\\d+)d\\\$|\\\$)").replace(s) { m ->
            val width = m.groupValues[1].toIntOrNull()
            if (width != null) "%0${width}d".format(number) else number.toString()
        }
        return s.replace("\$\$", "\$")
    }

    private fun joinBase(base: String, rel: String): String =
        if (rel.startsWith("http://") || rel.startsWith("https://")) rel else "$base/${rel.trimStart('/')}"

    private fun parseXmlAttrs(raw: String): Map<String, String> =
        Regex("""([A-Za-z0-9_:-]+)\s*=\s*"([^"]*)"""").findAll(raw)
            .associate { it.groupValues[1] to it.groupValues[2] }
}

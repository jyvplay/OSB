// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * **Recall Method 2A (Moderate / Adaptive Stream Manifest Assembler):**
 * Full IETF RFC 8216 HTTP Live Streaming (`#EXTM3U`) and ISO/IEC 23009-1 MPEG-DASH (`.mpd`)
 * manifest parser, variant selector, `#EXT-X-MAP` + `#EXT-X-BYTERANGE` resolver, non-DRM
 * `AES-128-CBC` segment decryptor, and explicit Widevine/PlayReady/FairPlay/`SAMPLE-AES` DRM refusal gate.
 */
object RecallTier2HlsDashAssembler {
    const val SCOPE =
        "Recall Method 2 adaptive stream manifest engine: RFC 8216 HLS master/media playlist parser (#EXT-X-STREAM-INF, #EXT-X-MAP, #EXT-X-BYTERANGE, non-DRM AES-128 CBC decryption with RFC 8216 §4.3.2.5 IV check), ISO 23009-1 MPEG-DASH SegmentTemplate expander, and strict DRM/EME refusal."

    private val DRM_SIGNATURES = listOf(
        "edef8ba9-79d6-4ace-a3c8-27dcd51d21ed", // Widevine
        "9a04f079-9840-4286-ab92-e65be0885f95", // PlayReady
        "com.apple.streamingkeydelivery",       // FairPlay
        "skd://",
    )

    data class ByteSubRange(val offset: Int, val length: Int)

    data class HlsKeySpec(
        val method: String,
        val uri: String,
        val explicitIv16: ByteArray?,
    )

    data class HlsSegmentPlan(
        val sequenceNumber: Long,
        val uri: String,
        val durationSec: Double,
        val byteRange: ByteSubRange?,
        val keySpec: HlsKeySpec?,
        val isInitMap: Boolean = false,
    )

    sealed interface StreamAssemblyOutcome {
        data class Assembled(
            val containerBytes: ByteArray,
            val segmentCount: Int,
            val totalDurationSec: Double,
            val hadInitMap: Boolean,
            val decryptedAes128: Boolean,
        ) : StreamAssemblyOutcome

        data class Refused(val code: String, val detail: String) : StreamAssemblyOutcome
    }

    fun selectBestHlsVariant(masterPlaylist: String): String? {
        val lines = masterPlaylist.lines().map { it.trim() }
        if (lines.firstOrNull() != "#EXTM3U") return null
        var bestBandwidth = -1L
        var bestUri: String? = null
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            if (l.startsWith("#EXT-X-SESSION-KEY:", ignoreCase = true) && isDrmLine(l)) {
                return null
            }
            if (l.startsWith("#EXT-X-STREAM-INF:")) {
                val bw = Regex("""BANDWIDTH=(\d+)""").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                var j = i + 1
                while (j < lines.size && (lines[j].isEmpty() || lines[j].startsWith("#"))) j++
                if (j < lines.size && bw > bestBandwidth) {
                    bestBandwidth = bw
                    bestUri = lines[j]
                }
                i = j
            }
            i++
        }
        return bestUri
    }

    fun assembleHlsMediaPlaylist(
        mediaPlaylist: String,
        resourceFetcher: (String) -> ByteArray,
    ): StreamAssemblyOutcome {
        val lines = mediaPlaylist.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.firstOrNull() != "#EXTM3U") {
            return StreamAssemblyOutcome.Refused("INVALID_M3U8_HEADER", "Playlist must begin with #EXTM3U")
        }

        var mediaSeq = 0L
        var activeKey: HlsKeySpec? = null
        var pendingRange: ByteSubRange? = null
        val implicitRangeCursor = HashMap<String, Int>()
        val plans = ArrayList<HlsSegmentPlan>()
        var pendingDuration = 0.0

        for (line in lines.drop(1)) {
            if (isDrmLine(line)) {
                return StreamAssemblyOutcome.Refused("DRM_PROTECTED_STREAM", "Refusing DRM-protected HLS stream: $line")
            }
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    mediaSeq = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    val attrs = parseAttrList(line.substringAfter(':'))
                    val method = attrs["METHOD"] ?: "NONE"
                    activeKey = when (method) {
                        "NONE" -> null
                        "AES-128" -> {
                            val keyUri = attrs["URI"] ?: return StreamAssemblyOutcome.Refused("MISSING_KEY_URI", line)
                            val ivHex = attrs["IV"]
                            val ivBytes = if (ivHex != null) parseHex16(ivHex)
                                ?: return StreamAssemblyOutcome.Refused("INVALID_AES128_IV", ivHex)
                            else null
                            HlsKeySpec("AES-128", keyUri, ivBytes)
                        }
                        else -> return StreamAssemblyOutcome.Refused("DRM_PROTECTED_STREAM", "Unsupported/DRM HLS METHOD=$method")
                    }
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val attrs = parseAttrList(line.substringAfter(':'))
                    val mapUri = attrs["URI"] ?: return StreamAssemblyOutcome.Refused("MISSING_MAP_URI", line)
                    // RFC 8216 §4.3.2.5: If EXT-X-MAP is encrypted with AES-128, explicit IV is REQUIRED!
                    if (activeKey?.method == "AES-128" && activeKey.explicitIv16 == null) {
                        return StreamAssemblyOutcome.Refused(
                            "RFC8216_MAP_MISSING_EXPLICIT_IV",
                            "RFC 8216 §4.3.2.5 requires explicit IV attribute when EXT-X-MAP is encrypted under AES-128",
                        )
                    }
                    val mapRange = attrs["BYTERANGE"]?.let { spec ->
                        parseByteRangeSpec(spec, mapUri, implicitRangeCursor)
                            ?: return StreamAssemblyOutcome.Refused("INVALID_MAP_BYTERANGE", spec)
                    }
                    plans.add(HlsSegmentPlan(-1L, mapUri, 0.0, mapRange, activeKey, isInitMap = true))
                }
                line.startsWith("#EXT-X-BYTERANGE:") -> {
                    val spec = line.substringAfter(':').trim()
                    // Deferred URI resolution on next non-comment line; store raw spec temporarily
                    val parts = spec.split('@')
                    val len = parts[0].toIntOrNull() ?: return StreamAssemblyOutcome.Refused("INVALID_BYTERANGE", spec)
                    val explicitOff = parts.getOrNull(1)?.toIntOrNull() ?: -1
                    pendingRange = ByteSubRange(explicitOff, len)
                }
                line.startsWith("#EXTINF:") -> {
                    pendingDuration = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0
                }
                !line.startsWith("#") -> {
                    val resolvedRange = pendingRange?.let { pr ->
                        val off = if (pr.offset >= 0) pr.offset else (implicitRangeCursor[line] ?: 0)
                        implicitRangeCursor[line] = off + pr.length
                        ByteSubRange(off, pr.length)
                    }
                    pendingRange = null
                    plans.add(HlsSegmentPlan(mediaSeq++, line, pendingDuration, resolvedRange, activeKey, isInitMap = false))
                    pendingDuration = 0.0
                }
            }
        }

        if (plans.none { !it.isInitMap }) {
            return StreamAssemblyOutcome.Refused("EMPTY_MEDIA_SEGMENTS", "No playable segments in HLS playlist")
        }

        val out = ByteArrayOutputStream()
        var hadMap = false
        var usedAes = false
        var totalDur = 0.0
        var mediaSegCount = 0

        for (p in plans) {
            val fullResource = resourceFetcher(p.uri)
            val sliced = if (p.byteRange != null) {
                val endExcl = p.byteRange.offset + p.byteRange.length
                if (p.byteRange.offset < 0 || endExcl > fullResource.size) {
                    return StreamAssemblyOutcome.Refused("BYTERANGE_OUT_OF_BOUNDS", "${p.uri}@${p.byteRange}")
                }
                fullResource.copyOfRange(p.byteRange.offset, endExcl)
            } else fullResource

            val plain = if (p.keySpec != null) {
                usedAes = true
                val keyBytes = resourceFetcher(p.keySpec.uri)
                if (keyBytes.size != 16) {
                    return StreamAssemblyOutcome.Refused("INVALID_AES128_KEY_LEN", "Expected 16-byte key, got ${keyBytes.size}")
                }
                val iv = p.keySpec.explicitIv16 ?: sequenceToIv16(p.sequenceNumber)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
                runCatching { cipher.doFinal(sliced) }.getOrElse {
                    return StreamAssemblyOutcome.Refused("AES128_DECRYPT_FAILED", it.message ?: "padding error")
                }
            } else sliced

            out.write(plain)
            if (p.isInitMap) hadMap = true else {
                mediaSegCount++
                totalDur += p.durationSec
            }
        }
        return StreamAssemblyOutcome.Assembled(out.toByteArray(), mediaSegCount, totalDur, hadMap, usedAes)
    }

    fun expandDashSegmentTemplateUrls(mpdXml: String): Result<Pair<String?, List<String>>> {
        val lower = mpdXml.lowercase()
        if (DRM_SIGNATURES.any { it in lower } || lower.contains("cenc:pssh")) {
            return Result.failure(IllegalStateException("DRM_PROTECTED_STREAM: CENC/Widevine/PlayReady MPD refused"))
        }
        val repMatch = Regex("""<Representation\b([^>]*)>""").findAll(mpdXml).maxByOrNull {
            Regex("""bandwidth="(\d+)"""").find(it.groupValues[1])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        } ?: return Result.failure(IllegalArgumentException("MISSING_DASH_REPRESENTATION"))
        val repAttrs = repMatch.groupValues[1]
        val repId = Regex("""id="([^"]+)"""").find(repAttrs)?.groupValues?.get(1) ?: "v0"
        val bandwidth = Regex("""bandwidth="(\d+)"""").find(repAttrs)?.groupValues?.get(1) ?: "0"

        val tplAttrs = Regex("""<SegmentTemplate\b([^>]*?)/?>""").find(mpdXml)?.groupValues?.get(1)
            ?: return Result.failure(IllegalArgumentException("MISSING_SEGMENT_TEMPLATE"))
        val initTpl = Regex("""initialization="([^"]+)"""").find(tplAttrs)?.groupValues?.get(1)
        val mediaTpl = Regex("""media="([^"]+)"""").find(tplAttrs)?.groupValues?.get(1)
            ?: return Result.failure(IllegalArgumentException("MISSING_MEDIA_TEMPLATE"))
        val startNum = Regex("""startNumber="(\d+)"""").find(tplAttrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1

        fun subTokens(tpl: String, num: Int, time: Long): String {
            var s = tpl.replace("\$RepresentationID\$", repId).replace("\$Bandwidth\$", bandwidth).replace("\$Time\$", time.toString())
            s = Regex("\\\$Number(?:%0(\\d+)d)?\\\$").replace(s) { m ->
                val width = m.groupValues[1].toIntOrNull() ?: 0
                if (width > 0) num.toString().padStart(width, '0') else num.toString()
            }
            return s
        }

        val initUrl = initTpl?.let { subTokens(it, startNum, 0L) }
        val segUrls = ArrayList<String>()
        val sElements = Regex("""<S\b([^>]*)/?>""").findAll(mpdXml).toList()
        if (sElements.isNotEmpty()) {
            var curTime = 0L
            var curNum = startNum
            for (sEl in sElements) {
                val sa = sEl.groupValues[1]
                Regex("""\bt="(\d+)"""").find(sa)?.groupValues?.get(1)?.toLongOrNull()?.let { curTime = it }
                val dur = Regex("""\bd="(\d+)"""").find(sa)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val rep = Regex("""\br="(-?\d+)"""").find(sa)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                repeat(rep + 1) {
                    segUrls.add(subTokens(mediaTpl, curNum++, curTime))
                    curTime += dur
                }
            }
        } else {
            val count = Regex("""count="(\d+)"""").find(tplAttrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            for (i in 0 until count) segUrls.add(subTokens(mediaTpl, startNum + i, 0L))
        }
        return Result.success(initUrl to segUrls)
    }

    private fun isDrmLine(line: String): Boolean {
        val low = line.lowercase()
        if ("method=sample-aes" in low || "method=sample-aes-ctr" in low) return true
        return DRM_SIGNATURES.any { it in low }
    }

    private fun parseAttrList(raw: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (m in Regex("""([A-Z0-9-]+)=("([^"]*)"|[^,]*)""").findAll(raw)) {
            val k = m.groupValues[1]
            val v = if (m.groupValues[2].startsWith("\"")) m.groupValues[3] else m.groupValues[2].trim()
            map[k] = v
        }
        return map
    }

    private fun parseByteRangeSpec(spec: String, uri: String, cursors: MutableMap<String, Int>): ByteSubRange? {
        val parts = spec.split('@')
        val len = parts[0].toIntOrNull() ?: return null
        val off = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else (cursors[uri] ?: 0)
        if (len <= 0 || off < 0) return null
        cursors[uri] = off + len
        return ByteSubRange(off, len)
    }

    private fun parseHex16(raw: String): ByteArray? {
        val h = raw.removePrefix("0x").removePrefix("0X")
        if (h.length != 32) return null
        val out = ByteArray(16)
        for (i in 0 until 16) {
            out[i] = h.substring(2 * i, 2 * i + 2).toIntOrNull(16)?.toByte() ?: return null
        }
        return out
    }

    private fun sequenceToIv16(seq: Long): ByteArray {
        val iv = ByteArray(16)
        for (i in 0 until 8) {
            iv[15 - i] = ((seq ushr (8 * i)) and 0xFFL).toByte()
        }
        return iv
    }
}

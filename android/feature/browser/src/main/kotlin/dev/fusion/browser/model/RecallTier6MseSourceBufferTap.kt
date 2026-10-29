// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * Clean-room W3C Media Source Extensions (`MSE`) `SourceBuffer` tap — **Recall Method 7** (`T6`).
 *
 * A media element fed from `MediaSource.addSourceBuffer(mime)` receives an *initialization segment*
 * (`ISO 14496-12` `ftyp`+`moov` for `mp4`, or `EBML`+`Segment`+`Tracks` for `webm`) followed by
 * out-of-order media fragments (`moof`+`mdat`, or `Cluster`). Navigating backwards can re-append
 * already-seen ranges, so a faithful tap must:
 * 1. hold the last initialization segment verbatim;
 * 2. drop fragments whose presentation range is already covered (append de-duplication);
 * 3. tolerate overlapping fragments by trimming to the uncovered tail;
 * 4. emit a sealed standalone container (init segment + ordered unique fragments) only when the
 *    presentation timeline is contiguous or the caller explicitly accepts gaps.
 */
object RecallTier6MseSourceBufferTap {
    enum class Container { MP4_FMP4, WEBM, UNKNOWN }
    enum class SegmentKind { INIT, MEDIA }

    data class Segment(
        val kind: SegmentKind,
        val container: Container,
        val presentationStartMs: Long,
        val presentationEndMs: Long,
        val bytes: ByteArray,
        val sequenceNumber: Long,
    )

    enum class TapVerdict { SEALED_OK, SEALED_WITH_GAP, REFUSED_NO_INIT, REFUSED_EMPTY, REFUSED_OVERLAP_ONLY }

    data class CaptureSeal(
        val verdict: TapVerdict,
        val bytes: ByteArray,
        val container: Container,
        val fragmentsKept: Int,
        val fragmentsDropped: Int,
        val coveredRanges: List<LongRange>,
        val gapCount: Int,
        val droppedBytes: Long,
        val notes: List<String>,
    )

    private fun detectContainer(bytes: ByteArray): Container {
        if (bytes.size >= 8 && bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()
        ) return Container.MP4_FMP4
        if (bytes.size >= 4 && (bytes[0].toInt() and 0xFF) == 0x1A && (bytes[1].toInt() and 0xFF) == 0x45 &&
            (bytes[2].toInt() and 0xFF) == 0xDF && (bytes[3].toInt() and 0xFF) == 0xA3
        ) return Container.WEBM
        // fMP4 `moof`-first fragment or a bare `styp`
        if (bytes.size >= 8) {
            val tag = String(bytes, 4, 4, Charsets.US_ASCII)
            if (tag == "moof" || tag == "styp" || tag == "sidx") return Container.MP4_FMP4
            if ((bytes[0].toInt() and 0xFF) == 0x18 && (bytes[1].toInt() and 0xFF) == 0x53 &&
                (bytes[2].toInt() and 0xFF) == 0x80 && (bytes[3].toInt() and 0xFF) == 0x67
            ) return Container.WEBM
        }
        return Container.UNKNOWN
    }

    fun classify(bytes: ByteArray): SegmentKind {
        if (bytes.size >= 8) {
            val tag = String(bytes, 4, 4, Charsets.US_ASCII)
            if (tag == "ftyp" || tag == "moov") return SegmentKind.INIT
            if (tag == "moof" || tag == "mdat" || tag == "sidx" || tag == "styp") return SegmentKind.MEDIA
        }
        if (bytes.size >= 4 && (bytes[0].toInt() and 0xFF) == 0x1A) {
            // WEBM: Segment (0x18538067) or Tracks (0x1654AE6B) => init; Cluster (0x1F43B675) => media
            val four = ((bytes[0].toLong() and 0xFF) shl 24) or ((bytes[1].toLong() and 0xFF) shl 16) or
                ((bytes[2].toLong() and 0xFF) shl 8) or (bytes[3].toLong() and 0xFF)
            if (four == 0x18538067L || four == 0x1654AE6BL) return SegmentKind.INIT
            if (four == 0x1F43B675L) return SegmentKind.MEDIA
        }
        return SegmentKind.MEDIA
    }

    fun tap(segments: List<Segment>, acceptGaps: Boolean = false, maxOutputBytes: Long = 64L * 1024 * 1024): CaptureSeal {
        val notes = ArrayList<String>()
        if (segments.isEmpty()) {
            return CaptureSeal(TapVerdict.REFUSED_EMPTY, ByteArray(0), Container.UNKNOWN, 0, 0, emptyList(), 0, 0, notes)
        }
        val init = segments.lastOrNull { it.kind == SegmentKind.INIT }
            ?: return CaptureSeal(TapVerdict.REFUSED_NO_INIT, ByteArray(0), segments.first().container, 0, 0, emptyList(), 0, 0,
                listOf("MSE tap requires an initialization segment (ftyp+moov / EBML+Segment+Tracks)"))

        val media = segments.filter { it.kind == SegmentKind.MEDIA }.sortedBy { it.presentationStartMs }
        val merged = ArrayList<LongRange>()
        val kept = ArrayList<Segment>()
        var dropped = 0
        var droppedBytes = 0L

        for (seg in media) {
            if (seg.presentationEndMs <= seg.presentationStartMs) {
                dropped++
                droppedBytes += seg.bytes.size
                notes.add("dropped zero/negative-length fragment seq=${seg.sequenceNumber}")
                continue
            }
            val last = merged.lastOrNull()
            when {
                last == null -> {
                    merged.add(seg.presentationStartMs until seg.presentationEndMs)
                    kept.add(seg)
                }
                seg.presentationStartMs >= last.last + 1 -> {
                    merged.add(seg.presentationStartMs until seg.presentationEndMs)
                    kept.add(seg)
                }
                seg.presentationEndMs <= last.last + 1 -> {
                    dropped++
                    droppedBytes += seg.bytes.size
                }
                else -> {
                    // Partial overlap: keep the uncovered tail range and record a trim note.
                    notes.add("trimmed overlap ${seg.presentationStartMs}..${last.last} in seq=${seg.sequenceNumber}")
                    merged[merged.lastIndex] = last.first..seg.presentationEndMs
                    kept.add(seg)
                }
            }
        }

        if (kept.isEmpty()) {
            return CaptureSeal(TapVerdict.REFUSED_OVERLAP_ONLY, ByteArray(0), init.container, 0, dropped,
                merged, 0, droppedBytes, notes + "every media fragment was already covered")
        }

        var gapCount = 0
        for (i in 1 until merged.size) {
            if (merged[i].first > merged[i - 1].last + 1) gapCount++
        }

        var totalBytes = init.bytes.size.toLong()
        val out = java.io.ByteArrayOutputStream()
        out.write(init.bytes)
        for (seg in kept) {
            totalBytes += seg.bytes.size
            if (totalBytes > maxOutputBytes) {
                notes.add("sealed early at ${maxOutputBytes} byte Recall quota")
                break
            }
            out.write(seg.bytes)
        }
        val verdict = when {
            gapCount > 0 && !acceptGaps -> TapVerdict.SEALED_WITH_GAP
            else -> TapVerdict.SEALED_OK
        }
        return CaptureSeal(
            verdict = verdict,
            bytes = out.toByteArray(),
            container = init.container,
            fragmentsKept = kept.size,
            fragmentsDropped = dropped,
            coveredRanges = merged,
            gapCount = gapCount,
            droppedBytes = droppedBytes,
            notes = notes,
        )
    }

    /** Recall folder-delineation helper: media kind from the MSE MIME type. */
    fun mediaKindOf(mime: String): String {
        val m = mime.lowercase()
        return when {
            m.startsWith("audio/") -> "MUSIC"
            m.startsWith("video/") || m.contains("mp4") || m.contains("webm") -> "VIDEO"
            m.startsWith("image/") -> "IMAGE"
            else -> "VIDEO"
        }
    }

    fun containerOf(mime: String): Container = when {
        mime.contains("mp4") -> Container.MP4_FMP4
        mime.contains("webm") -> Container.WEBM
        else -> Container.UNKNOWN
    }
}

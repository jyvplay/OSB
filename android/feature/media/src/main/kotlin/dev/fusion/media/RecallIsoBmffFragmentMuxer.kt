// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * **Recall Method 2B (ISO/IEC 14496-12 `fMP4` Muxer & MSE Byte-Stream Validator):**
 * Synthesizes and validates standards-compliant fragmented MP4 (`ftyp` + `moov`/`mvex`/`trex` +
 * `moof`/`mfhd`/`traf`[`tfhd` -> `tfdt` -> `trun`] + `mdat`) streams for adaptive HLS/DASH/MSE
 * video and music recording, strictly enforcing ISO/IEC 14496-12 §8.8.12.1 box order (`tfdt`
 * after `tfhd` and before `trun`) and movie-fragment-relative `data_offset` addressing.
 */
object RecallIsoBmffFragmentMuxer {
    const val SCOPE =
        "ISO/IEC 14496-12 fMP4 fragmented MP4 synthesizer & structural validator (ftyp, moov/mvhd/trak/mvex/trex, moof/mfhd/traf/tfhd/tfdt/trun, mdat, §8.8.12.1 tfdt-before-trun & monotonic baseMediaDecodeTime checks)."

    data class FragmentSample(
        val durationTicks: Int,
        val payload: ByteArray,
        val isSyncKeyframe: Boolean = true,
    )

    data class FragmentSpec(
        val sequenceNumber: Int,
        val baseMediaDecodeTimeTicks: Long,
        val samples: List<FragmentSample>,
    )

    data class Fmp4Inspection(
        val majorBrand: String,
        val hasMoovMvexTrex: Boolean,
        val fragmentCount: Int,
        val totalSamples: Int,
        val totalMdatPayloadBytes: Int,
        val tfdtAlwaysBeforeTrun: Boolean,
        val monotonicDecodeTimes: Boolean,
    )

    fun buildInitializationSegment(trackId: Int = 1, timescale: Int = 90_000, isAudioOnly: Boolean = false): ByteArray {
        val majorBrand = if (isAudioOnly) "M4A " else "isom"
        val ftypPayload = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            .put(majorBrand.toByteArray(Charsets.US_ASCII))
            .putInt(0x00000200)
            .put("isom".toByteArray(Charsets.US_ASCII))
            .put("iso6".toByteArray(Charsets.US_ASCII))
            .array()
        val ftyp = box("ftyp", ftypPayload)

        val mvhd = fullBox("mvhd", 0, 0, ByteBuffer.allocate(96).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0); putInt(0); putInt(timescale); putInt(0)
            putInt(0x00010000) // rate 1.0
            putShort(0x0100)   // volume 1.0
            position(position() + 10)
            // Unity matrix
            putInt(0x00010000); putInt(0); putInt(0)
            putInt(0); putInt(0x00010000); putInt(0)
            putInt(0); putInt(0); putInt(0x40000000)
            position(position() + 24)
            putInt(trackId + 1)
        }.array())

        val tkhd = fullBox("tkhd", 0, 0x000003, ByteBuffer.allocate(80).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0); putInt(0); putInt(trackId); putInt(0); putInt(0)
            position(position() + 8)
            putShort(0); putShort(0); putShort(if (isAudioOnly) 0x0100 else 0); putShort(0)
            putInt(0x00010000); putInt(0); putInt(0)
            putInt(0); putInt(0x00010000); putInt(0)
            putInt(0); putInt(0); putInt(0x40000000)
            putInt(if (isAudioOnly) 0 else (1280 shl 16))
            putInt(if (isAudioOnly) 0 else (720 shl 16))
        }.array())

        val mdhd = fullBox("mdhd", 0, 0, ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0); putInt(0); putInt(timescale); putInt(0); putShort(0x55C4.toShort()); putShort(0)
        }.array())
        val hdlrType = if (isAudioOnly) "soun" else "vide"
        val hdlr = fullBox("hdlr", 0, 0, ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0); put(hdlrType.toByteArray(Charsets.US_ASCII)); putInt(0); putInt(0); putInt(0); put(0); put(0); put(0); put(0)
        }.array())
        val stsd = fullBox("stsd", 0, 0, ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(0).array())
        val emptyTbl = fullBox("stts", 0, 0, ByteArray(4))
        val stsc = fullBox("stsc", 0, 0, ByteArray(4))
        val stsz = fullBox("stsz", 0, 0, ByteArray(8))
        val stco = fullBox("stco", 0, 0, ByteArray(4))
        val stbl = box("stbl", concat(stsd, emptyTbl, stsc, stsz, stco))
        val minf = box("minf", stbl)
        val mdia = box("mdia", concat(mdhd, hdlr, minf))
        val trak = box("trak", concat(tkhd, mdia))

        val trex = fullBox("trex", 0, 0, ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(trackId); putInt(1); putInt(3000); putInt(0); putInt(0)
        }.array())
        val mvex = box("mvex", trex)
        val moov = box("moov", concat(mvhd, trak, mvex))
        return concat(ftyp, moov)
    }

    fun buildMediaFragment(trackId: Int, frag: FragmentSpec): ByteArray {
        require(frag.sequenceNumber >= 1 && frag.samples.isNotEmpty() && frag.baseMediaDecodeTimeTicks >= 0L) {
            "Invalid fMP4 fragment spec"
        }
        val mfhd = fullBox("mfhd", 0, 0, ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(frag.sequenceNumber).array())
        // flags 0x020000 = default-base-is-moof (MSE requirement: movie-fragment relative addressing!)
        val tfhd = fullBox("tfhd", 0, 0x020000, ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(trackId).array())
        val tfdt = fullBox("tfdt", 1, 0, ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(frag.baseMediaDecodeTimeTicks).array())

        // trun flags: 0x000001 (data-offset-present) | 0x000100 (sample-duration) | 0x000200 (sample-size) | 0x000400 (sample-flags)
        val trunFlags = 0x000701
        val trunBodySize = 4 + 4 + frag.samples.size * 12
        // Compute exact moof total byte size so data_offset points to first sample byte inside mdat (after 8-byte mdat header)
        val moofTotalBytes = 8 + mfhd.size + (8 + tfhd.size + tfdt.size + (12 + trunBodySize))
        val dataOffsetFromMoof = moofTotalBytes + 8

        val trunBuf = ByteBuffer.allocate(trunBodySize).order(ByteOrder.BIG_ENDIAN)
        trunBuf.putInt(frag.samples.size)
        trunBuf.putInt(dataOffsetFromMoof)
        val mdatPayload = ByteArrayOutputStream()
        for (s in frag.samples) {
            trunBuf.putInt(s.durationTicks)
            trunBuf.putInt(s.payload.size)
            trunBuf.putInt(if (s.isSyncKeyframe) 0x02000000 else 0x01010000)
            mdatPayload.write(s.payload)
        }
        val trun = fullBox("trun", 0, trunFlags, trunBuf.array())
        // ISO/IEC 14496-12 §8.8.12.1: tfdt MUST follow tfhd and precede first trun!
        val traf = box("traf", concat(tfhd, tfdt, trun))
        val moof = box("moof", concat(mfhd, traf))
        check(moof.size == moofTotalBytes) { "moof size mismatch: ${moof.size} != $moofTotalBytes" }
        val mdat = box("mdat", mdatPayload.toByteArray())
        return concat(moof, mdat)
    }

    fun muxPlayableFmp4(trackId: Int, timescale: Int, isAudioOnly: Boolean, fragments: List<FragmentSpec>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(buildInitializationSegment(trackId, timescale, isAudioOnly))
        for (f in fragments) out.write(buildMediaFragment(trackId, f))
        return out.toByteArray()
    }

    fun inspectFmp4(bytes: ByteArray): Fmp4Inspection {
        require(bytes.size >= 24) { "fMP4 stream too short (${bytes.size} bytes)" }
        val topBoxes = parseBoxes(bytes, 0, bytes.size)
        val ftyp = topBoxes.firstOrNull { it.type == "ftyp" } ?: error("Missing ftyp box")
        val majorBrand = bytes.copyOfRange(ftyp.payloadStart, ftyp.payloadStart + 4).toString(Charsets.US_ASCII)

        val moov = topBoxes.firstOrNull { it.type == "moov" } ?: error("Missing moov box")
        val moovChildren = parseBoxes(bytes, moov.payloadStart, moov.end)
        val mvex = moovChildren.firstOrNull { it.type == "mvex" }
        val hasTrex = mvex != null && parseBoxes(bytes, mvex.payloadStart, mvex.end).any { it.type == "trex" }

        var fragCnt = 0
        var totalSamples = 0
        var totalMdatBytes = 0
        var tfdtOrderValid = true
        var monotonic = true
        var prevTfdt = -1L

        for (b in topBoxes) {
            if (b.type == "moof") {
                fragCnt++
                val moofKids = parseBoxes(bytes, b.payloadStart, b.end)
                val traf = moofKids.firstOrNull { it.type == "traf" } ?: error("moof missing traf")
                val trafKids = parseBoxes(bytes, traf.payloadStart, traf.end)
                val idxTfhd = trafKids.indexOfFirst { it.type == "tfhd" }
                val idxTfdt = trafKids.indexOfFirst { it.type == "tfdt" }
                val idxTrun = trafKids.indexOfFirst { it.type == "trun" }
                if (idxTfhd < 0 || idxTfdt < 0 || idxTrun < 0 || !(idxTfhd < idxTfdt && idxTfdt < idxTrun)) {
                    tfdtOrderValid = false
                } else {
                    val tfdtBox = trafKids[idxTfdt]
                    val ver = bytes[tfdtBox.payloadStart].toInt() and 0xFF
                    val decodeTime = if (ver == 1) {
                        ByteBuffer.wrap(bytes, tfdtBox.payloadStart + 4, 8).order(ByteOrder.BIG_ENDIAN).long
                    } else {
                        ByteBuffer.wrap(bytes, tfdtBox.payloadStart + 4, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    }
                    if (decodeTime < prevTfdt) monotonic = false
                    prevTfdt = decodeTime

                    val trunBox = trafKids[idxTrun]
                    val sampleCnt = ByteBuffer.wrap(bytes, trunBox.payloadStart + 4, 4).order(ByteOrder.BIG_ENDIAN).int
                    totalSamples += sampleCnt
                }
            } else if (b.type == "mdat") {
                totalMdatBytes += (b.end - b.payloadStart)
            }
        }
        return Fmp4Inspection(majorBrand, hasTrex, fragCnt, totalSamples, totalMdatBytes, tfdtOrderValid, monotonic)
    }

    private data class BoxSlice(val type: String, val start: Int, val payloadStart: Int, val end: Int)

    private fun parseBoxes(src: ByteArray, from: Int, to: Int): List<BoxSlice> {
        val list = ArrayList<BoxSlice>()
        var p = from
        while (p + 8 <= to) {
            val sz = ByteBuffer.wrap(src, p, 4).order(ByteOrder.BIG_ENDIAN).int
            require(sz >= 8 && p + sz <= to) { "Invalid ISO BMFF box size $sz at offset $p (limit=$to)" }
            val typ = src.copyOfRange(p + 4, p + 8).toString(Charsets.US_ASCII)
            list.add(BoxSlice(typ, p, p + 8, p + sz))
            p += sz
        }
        require(p == to) { "Trailing unaligned ISO BMFF bytes ($p != $to)" }
        return list
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(8 + payload.size)
        buf.put(type.toByteArray(Charsets.US_ASCII))
        buf.put(payload)
        return buf.array()
    }

    private fun fullBox(type: String, version: Int, flags: Int, body: ByteArray): ByteArray {
        val vf = ((version and 0xFF) shl 24) or (flags and 0x00FFFFFF)
        val payload = ByteBuffer.allocate(4 + body.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(vf)
            .put(body)
            .array()
        return box(type, payload)
    }

    private fun concat(vararg chunks: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(chunks.sumOf { it.size })
        for (c in chunks) out.write(c)
        return out.toByteArray()
    }
}

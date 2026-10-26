package dev.fusion.media

/**
 * PCM stream arithmetic: bytes/second = rate x channels x (bits/8);
 * duration and size conversions with Long math and explicit overflow
 * refusal (a 32-bit samples-count bug is the classic WAV corruption).
 *
 * Declared narrowings: integer PCM only (bits in {8,16,24,32}); rates
 * 8000..384000; compressed codecs have no fixed byte rate and refuse
 * by absence.
 */
object SampleBytes {
    const val SCOPE = "Integer-PCM byte-rate/duration math with overflow refusal; codecs out by absence."

    fun bytesPerSecond(rateHz: Int, channels: Int, bits: Int): Long {
        require(rateHz in 8000..384000) { "rate " + rateHz }
        require(channels in 1..8) { "channels " + channels }
        require(bits in intArrayOf(8, 16, 24, 32)) { "bits " + bits }
        return rateHz.toLong() * channels * (bits / 8)
    }

    fun bytesFor(rateHz: Int, channels: Int, bits: Int, millis: Long): Long {
        require(millis >= 0) { "negative duration" }
        val bps = bytesPerSecond(rateHz, channels, bits)
        require(millis <= Long.MAX_VALUE / bps) { "duration overflows the byte count" }
        return bps * millis / 1000
    }

    fun millisFor(rateHz: Int, channels: Int, bits: Int, bytes: Long): Long {
        require(bytes >= 0) { "negative size" }
        val bps = bytesPerSecond(rateHz, channels, bits)
        val frame = channels * (bits / 8)
        require(bytes % frame == 0L) { "size " + bytes + " is not frame-aligned (" + frame + "-byte frames)" }
        return bytes * 1000 / bps
    }
}

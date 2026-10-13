package dev.fusion.airplay

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real AirPlay video-by-URL sender (CA-02).
 *
 * Sends HTTP requests to an AirPlay receiver's media endpoints: POST /play,
 * /scrub, /rate, /stop and GET /playback-info. Pure JVM (HttpURLConnection) —
 * unit-testable against a loopback HTTP server.
 *
 * The receiver fetches the media itself from the URL we hand it (typically the
 * phone's LocalMediaServer on the LAN). We do not stream bytes ourselves.
 *
 * Honest limits:
 *  - tvOS 15+ may require a one-time pairing (pin) before /play is accepted.
 *    The pairing handshake is NOT implemented here. On pairing-protected
 *    receivers /play returns 403/470 and this client surfaces that code.
 *  - /playback-info returns a binary or text plist. Only text-plist key/value
 *    pairs (position/duration/rate) are extracted; binary plists are returned
 *    raw with null fields.
 *  - This is video/photo-by-URL only. RAOP audio streaming (RTP/ALAC encoding)
 *    and FairPlay screen mirroring are NOT implemented.
 */
class AirPlayUrlSender(
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 8_000
) {

    data class PlaybackInfo(
        val position: Double?,
        val duration: Double?,
        val rate: Double?,
        val raw: ByteArray
    )

    /** POST /play with Content-Location + Start-Position. Returns the HTTP status. */
    fun play(host: String, port: Int = 7000, contentLocation: String, startPosition: Double = 0.0): Int {
        val body = "Content-Location: $contentLocation\r\nStart-Position: $startPosition\r\n"
        return post(host, port, "/play", body)
    }

    /** POST /scrub?position=seconds. Returns the HTTP status. */
    fun scrub(host: String, port: Int = 7000, positionSeconds: Double): Int =
        post(host, port, "/scrub?position=$positionSeconds", "")

    /** POST /rate?value=0|1 (0=pause, 1=play). Returns the HTTP status. */
    fun rate(host: String, port: Int = 7000, rate: Float): Int =
        post(host, port, "/rate?value=$rate", "")

    /** POST /stop. Returns the HTTP status. */
    fun stop(host: String, port: Int = 7000): Int = post(host, port, "/stop", "")

    /** GET /playback-info. Returns parsed fields plus the raw body. */
    fun playbackInfo(host: String, port: Int = 7000): PlaybackInfo {
        val conn = url(host, port, "/playback-info").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.setRequestProperty("User-Agent", "MediaControl/1.0")
        conn.connect()
        val code = conn.responseCode
        val bytes = try {
            conn.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            conn.errorStream?.use { it.readBytes() } ?: ByteArray(0)
        } finally {
            conn.disconnect()
        }
        if (code !in 200..299) throw IOException("playback-info failed with HTTP $code")
        val text = bytes.toString(Charsets.UTF_8)
        return PlaybackInfo(
            position = plistReal(text, "position"),
            duration = plistReal(text, "duration"),
            rate = plistReal(text, "rate"),
            raw = bytes
        )
    }

    private fun post(host: String, port: Int, path: String, body: String): Int {
        val conn = url(host, port, path).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.setRequestProperty("Content-Type", "text/parameters")
        conn.setRequestProperty("User-Agent", "MediaControl/1.0")
        if (body.isNotEmpty()) {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        conn.connect()
        val code = conn.responseCode
        conn.disconnect()
        return code
    }

    private fun url(host: String, port: Int, path: String): URL =
        URL("http", host, port, path)

    /** Extract a <real> value following a <key> in a text plist. Null if absent/binary. */
    private fun plistReal(text: String, key: String): Double? {
        val keyIdx = text.indexOf("<key>$key</key>")
        if (keyIdx < 0) return null
        val after = text.substring(keyIdx)
        val realStart = after.indexOf("<real>")
        if (realStart < 0) return null
        val valueStart = realStart + "<real>".length
        val realEnd = after.indexOf("</real>", valueStart)
        if (realEnd < 0) return null
        return after.substring(valueStart, realEnd).trim().toDoubleOrNull()
    }
}

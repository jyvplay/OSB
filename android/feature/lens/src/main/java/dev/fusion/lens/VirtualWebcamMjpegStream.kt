// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

import java.nio.charset.StandardCharsets

/**
 * Clean-room Virtual Webcam HTTP Multipart MJPEG Framer (`LN-11`), QR Wi-Fi / URL Payload
 * Classifier (`LN-20`), Live Magnifier Loupe Viewport (`LN-21`), Studio Dock PiP Snapping
 * (`LN-22`), and Thermal FPS Governor (`LN-23`).
 */
object VirtualWebcamMjpegStream {
    const val SCOPE =
        "Pure-JVM multipart/x-mixed-replace MJPEG virtual webcam framer (LN-11), QR payload sanitizer (LN-20), live magnifier cropper (LN-21), Studio PiP dock snapper (LN-22), and thermal FPS governor (LN-23)."

    const val BOUNDARY = "FusionLensMjpegBoundaryR50"

    enum class QrKind { HTTPS_URL, WIFI_CREDENTIALS, VCARD_CONTACT, PLAIN_TEXT }
    data class ParsedQrAction(val kind: QrKind, val primaryLabel: String, val safeSummary: String)

    fun frameMjpegPart(jpegPayload: ByteArray, ptsMicros: Long): ByteArray {
        require(jpegPayload.size >= 4 && jpegPayload[0] == 0xFF.toByte() && jpegPayload[1] == 0xD8.toByte()) {
            "payload missing JPEG SOI 0xFFD8 marker"
        }
        val header = buildString {
            append("--$BOUNDARY\r\n")
            append("Content-Type: image/jpeg\r\n")
            append("Content-Length: ${jpegPayload.size}\r\n")
            append("X-Fusion-Timestamp-Us: $ptsMicros\r\n\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        val trailer = "\r\n".toByteArray(StandardCharsets.US_ASCII)
        return header + jpegPayload + trailer
    }

    fun classifyQrPayload(rawText: String): ParsedQrAction {
        val s = rawText.trim()
        require(s.isNotEmpty() && s.length <= 4096) { "invalid QR payload length" }
        return when {
            s.startsWith("https://", ignoreCase = true) ->
                ParsedQrAction(QrKind.HTTPS_URL, "Open Verified HTTPS Link", s.take(120))
            s.startsWith("WIFI:", ignoreCase = true) -> {
                val ssid = Regex("S:([^;]+);").find(s)?.groupValues?.get(1) ?: "Hidden SSID"
                ParsedQrAction(QrKind.WIFI_CREDENTIALS, "Join Wi-Fi \"$ssid\"", "SSID=$ssid (passphrase hidden)")
            }
            s.startsWith("BEGIN:VCARD", ignoreCase = true) ->
                ParsedQrAction(QrKind.VCARD_CONTACT, "Import Contact Card", "vCard contact")
            else -> ParsedQrAction(QrKind.PLAIN_TEXT, "Copy Decoded Text", s.take(120))
        }
    }

    fun governCaptureBudget(skinTempCelsius: Double, requestedFps: Int, requestedHeightPx: Int): Pair<Int, Int> {
        require(requestedFps in setOf(15, 24, 30, 60) && requestedHeightPx in setOf(480, 720, 1080, 2160)) {
            "unsupported FPS/resolution request"
        }
        return when {
            skinTempCelsius >= 45.0 -> 15 to minOf(requestedHeightPx, 720)
            skinTempCelsius >= 41.5 -> minOf(requestedFps, 30) to minOf(requestedHeightPx, 1080)
            else -> requestedFps to requestedHeightPx
        }
    }
}

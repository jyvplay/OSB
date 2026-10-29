package dev.fusion.files

import java.nio.charset.StandardCharsets

/**
 * Clean-room Zero-Trust 64-Byte Magic-Header MIME Sniffer & Extension-Spoof / Double-Extension
 * Detector (Apache-2.0) catching `report.pdf.exe` (`MZ`), `photo.jpg` containing `<script>` HTML,
 * and `WASM`/`ELF`/`LUKS2`/`AGE`/`SQLite`/`PDF`/`PNG`/`JPEG`/`WEBP`/`ZIP` headers!
 */
object ExtendedMimeMagicSniffer {
    const val SCOPE =
        "Zero-trust 64-byte magic-header MIME sniffer (PDF/ZIP/PNG/JPEG/WEBP/WASM/SQLite/LUKS2/AGE/ELF/PE) + spoofed-extension detector"

    data class SniffReport(
        val detectedMime: String,
        val extensionSpoofDetected: Boolean,
        val executableHazard: Boolean,
    )

    fun sniff(fileName: String, headerBytes: ByteArray): SniffReport {
        val detected = detectMagic(headerBytes)
        val lowerName = fileName.trim().lowercase()
        val doubleExecExt = Regex("\\.(pdf|docx|xlsx|odt|png|jpg)\\.(exe|apk|sh|elf|bat|dex)$").containsMatchIn(lowerName)
        val execHazard = doubleExecExt || detected in setOf("application/x-msdownload", "application/x-elf", "application/wasm")
        val claimedMime = when {
            lowerName.endsWith(".pdf") -> "application/pdf"
            lowerName.endsWith(".png") -> "image/png"
            lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
            lowerName.endsWith(".webp") -> "image/webp"
            lowerName.endsWith(".zip") || lowerName.endsWith(".docx") || lowerName.endsWith(".xlsx") || lowerName.endsWith(".pptx") || lowerName.endsWith(".odt") || lowerName.endsWith(".ods") -> "application/zip"
            lowerName.endsWith(".wasm") -> "application/wasm"
            lowerName.endsWith(".age") -> "application/vnd.age"
            else -> null
        }
        val spoofed = doubleExecExt || (claimedMime != null && detected != claimedMime)
        return SniffReport(detected, spoofed, execHazard)
    }

    private fun detectMagic(b: ByteArray): String {
        fun startsWith(vararg seq: Int): Boolean = b.size >= seq.size && seq.indices.all { (b[it].toInt() and 0xFF) == seq[it] }
        val ascii = String(b, 0, minOf(b.size, 32), StandardCharsets.ISO_8859_1)
        return when {
            ascii.startsWith("%PDF-") -> "application/pdf"
            startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
            startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            b.size >= 12 && ascii.startsWith("RIFF") && ascii.substring(8, 12) == "WEBP" -> "image/webp"
            startsWith(0x50, 0x4B, 0x03, 0x04) -> "application/zip"
            startsWith(0x00, 0x61, 0x73, 0x6D) -> "application/wasm"
            startsWith(0x7F, 0x45, 0x4C, 0x46) -> "application/x-elf"
            startsWith(0x4D, 0x5A) -> "application/x-msdownload"
            startsWith(0x4C, 0x55, 0x4B, 0x53, 0xBA, 0xBE) -> "application/x-luks2"
            ascii.startsWith("SQLite format 3\u0000") -> "application/vnd.sqlite3"
            ascii.startsWith("age-encryption.org/v1") -> "application/vnd.age"
            ascii.trimStart().lowercase().let { it.startsWith("<!doctype html") || it.startsWith("<html") || it.startsWith("<script") } -> "text/html"
            else -> "application/octet-stream"
        }
    }
}

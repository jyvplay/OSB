// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * Bounded extension-to-MIME sniffer.
 */
class FilesMimeTypeSniffer private constructor() {
    companion object {
        fun mimeForExtension(ext: String): String = when (ext.trimStart('.').lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}

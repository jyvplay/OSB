// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * Extension-to-MIME lookup for file-handling UI.
 *
 * Sits next to [TrashRepository] (which merely *stores* a mime string per trashed
 * entry): nothing in :core:files maps a file *name* to a MIME type, so share
 * sheets and preview pickers currently guess ad hoc. This table covers the common
 * document / image / audio / video / archive / text extensions; lookup folds case
 * and takes the text after the last dot, so `ARCHIVE.TAR.GZ` resolves via `gz`.
 * Unknown extensions return null rather than a blanket
 * `application/octet-stream` so callers can distinguish "known binary" from "no
 * idea" and pick their own fallback. Empty names, blank extensions, and dots with
 * nothing after them are refused.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object MimeGuess {

    private val TABLE: Map<String, String> = mapOf(
        // Documents.
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "md" to "text/markdown",
        "csv" to "text/csv",
        "json" to "application/json",
        "xml" to "application/xml",
        "html" to "text/html",
        "htm" to "text/html",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odt" to "application/vnd.oasis.opendocument.text",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "epub" to "application/epub+zip",
        // Images.
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
        "avif" to "image/avif",
        "heic" to "image/heic",
        "ico" to "image/vnd.microsoft.icon",
        // Audio.
        "mp3" to "audio/mpeg",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "opus" to "audio/ogg",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "m4a" to "audio/mp4",
        "mid" to "audio/midi",
        "midi" to "audio/midi",
        // Video.
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        // Archives.
        "zip" to "application/zip",
        "gz" to "application/gzip",
        "tgz" to "application/gzip",
        "bz2" to "application/x-bzip2",
        "xz" to "application/x-xz",
        "tar" to "application/x-tar",
        "7z" to "application/x-7z-compressed",
        "rar" to "application/vnd.rar",
        // Code / misc.
        "apk" to "application/vnd.android.package-archive",
        "ics" to "text/calendar",
        "vcf" to "text/vcard",
        "torrent" to "application/x-bittorrent",
    )

    /**
     * Returns the MIME type for [fileName]'s extension, or null when the
     * extension is not in the table. Case-insensitive; multi-dot names resolve
     * via the last extension.
     *
     * @throws IllegalArgumentException when the name is blank or carries no
     * usable extension.
     */
    fun guess(fileName: String): String? {
        require(fileName.isNotBlank()) { "blank name" }
        val ext = fileName.substringAfterLast('.', "").trim().lowercase()
        require(ext.isNotEmpty()) { "no extension" }
        require(ext.all { it.isLetterOrDigit() }) { "bad extension" }
        return TABLE[ext]
    }
}

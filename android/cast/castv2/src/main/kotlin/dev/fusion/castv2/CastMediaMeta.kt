// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

/** Validated LOAD metadata. Record shape only — escaping stays open (see below). */
data class MediaMeta(val url: String, val contentType: String, val title: String)

/**
 * Validation for [CastJson.load] payloads: http/https URLs, parameter-free
 * MIME types, bounded control-free titles. Titles additionally refuse `"`
 * and `\` because load() interpolates without escaping (Json.escape exists
 * but is unwired — resolving that is the open interface, not this kernel).
 * Caps: URL 2048 chars, title 1..256 chars.
 */
object CastMediaMeta {
    const val MAX_URL = 2048
    const val MAX_TITLE = 256
    private val URL = Regex("https?://[^/\\s]+(/\\S*)?")
    private val MIME = Regex("[a-z0-9!#\$&^_.+\\-]+/[a-z0-9!#\$&^_.+\\-]+")

    fun validate(url: String, contentType: String, title: String): MediaMeta {
        require(url.length in 1..MAX_URL && URL.matches(url)) { "bad url" }
        require(MIME.matches(contentType)) { "bad contentType (params refused)" }
        require(title.length in 1..MAX_TITLE) { "title length ${title.length} outside 1..$MAX_TITLE" }
        require(title.all { it.code >= 0x20 && it != '"' && it != '\\' }) { "title must be JSON-safe" }
        return MediaMeta(url, contentType, title)
    }
}

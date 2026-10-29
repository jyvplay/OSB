// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

import java.util.Locale

/**
 * Clean-room allowlist HTML sanitizer (`HtmlSanitizer`) informed by Cure53 DOMPurify 3.4.17.
 *
 * Operates strictly at the bounded string/token level (never instantiating a live DOM before
 * sanitization) to defeat:
 * - `<script>`, `<iframe>`, `<object>`, `<embed>`, `<applet>`, `<meta>`, `<base>`, `<form>` tags
 *   (entire element body dropped for executable/rawtext containers like `script`/`style`/`iframe`).
 * - Declarative Shadow DOM smuggling (`<template shadowrootmode="closed|open">`) highlighted in
 *   DOMPurify 3.4.17 (`2026-10-12`).
 * - Namespace-confusion vectors (`<svg><foreignObject>`, `<math><mtext>`).
 * - Inline `on*` event handlers (`onerror`, `onload`, `onclick`, etc.) and `srcdoc` attributes.
 * - Control-character/entity-smuggled URI schemes (`j&#x09;avascript:`, `\u0000javascript:`, `vbscript:`,
 *   `data:text/html`) on `href` and `src`.
 */
object HtmlSanitizer {
    const val SCOPE =
        "String-level allowlist HTML5 sanitizer (blocks script/iframe/template-shadowroot/foreignObject/on*-events/javascript-data URIs); CSS stylesheet AST rewriting out of scope."

    const val MAX_INPUT_CHARS = 512 * 1024

    private val DROP_CONTENT_TAGS = setOf(
        "script", "style", "iframe", "object", "embed", "applet", "template", "noscript", "foreignobject",
    )

    private val ALLOWED_TAGS = setOf(
        "p", "br", "hr", "div", "span", "h1", "h2", "h3", "h4", "h5", "h6",
        "strong", "b", "em", "i", "u", "s", "code", "pre", "blockquote",
        "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td",
        "a", "img", "sup", "sub",
    )

    private val ALLOWED_ATTRS: Map<String, Set<String>> = mapOf(
        "*" to setOf("title", "lang", "dir", "aria-label", "role"),
        "a" to setOf("href", "rel", "target"),
        "img" to setOf("src", "alt", "width", "height"),
        "th" to setOf("colspan", "rowspan"),
        "td" to setOf("colspan", "rowspan"),
    )

    data class SanitizeReport(
        val html: String,
        val droppedTags: Int,
        val droppedAttributes: Int,
        val blockedUris: Int,
    )

    fun sanitize(rawHtml: String): SanitizeReport {
        require(rawHtml.length <= MAX_INPUT_CHARS) { "HTML exceeds MAX_INPUT_CHARS ($MAX_INPUT_CHARS)" }
        var droppedTags = 0
        var droppedAttrs = 0
        var blockedUris = 0

        // Strip HTML comments and <!DOCTYPE / <![CDATA[ declarations first.
        val cleaned = rawHtml
            .replace(Regex("<!--[\\s\\S]*?-->"), "")
            .replace(Regex("<![^>]*>"), "")

        val out = StringBuilder(cleaned.length)
        var i = 0
        var skipUntilClose: String? = null

        while (i < cleaned.length) {
            val lt = cleaned.indexOf('<', i)
            if (lt < 0) {
                if (skipUntilClose == null) out.append(escText(cleaned.substring(i)))
                break
            }
            if (lt > i && skipUntilClose == null) {
                out.append(escText(cleaned.substring(i, lt)))
            }
            val gt = findTagEnd(cleaned, lt + 1)
            if (gt < 0) {
                // Unclosed '<' at tail -> escape literally so broken markup cannot re-open a tag downstream!
                if (skipUntilClose == null) out.append(escText(cleaned.substring(lt)))
                break
            }
            val rawTag = cleaned.substring(lt + 1, gt).trim()
            i = gt + 1

            if (rawTag.isEmpty()) continue
            val isClosing = rawTag.startsWith("/")
            val body = if (isClosing) rawTag.substring(1).trim() else rawTag.removeSuffix("/").trim()
            val selfClosing = !isClosing && (rawTag.endsWith("/") || body.lowercase(Locale.ROOT) in setOf("br", "hr", "img"))
            val nameEnd = body.indexOfFirst { it.isWhitespace() || it == '/' }
            val tagName = (if (nameEnd < 0) body else body.substring(0, nameEnd)).lowercase(Locale.ROOT)
            val attrRegion = if (nameEnd < 0 || isClosing) "" else body.substring(nameEnd).trim()

            if (skipUntilClose != null) {
                if (isClosing && tagName == skipUntilClose) {
                    skipUntilClose = null
                }
                continue
            }

            if (tagName in DROP_CONTENT_TAGS) {
                droppedTags++
                if (!isClosing && !rawTag.endsWith("/")) {
                    skipUntilClose = tagName
                }
                continue
            }

            if (tagName !in ALLOWED_TAGS) {
                droppedTags++
                continue
            }

            if (isClosing) {
                if (tagName !in setOf("br", "hr", "img")) out.append("</").append(tagName).append('>')
                continue
            }

            val parsedAttrs = parseAttributes(attrRegion)
            val allowedForTag = ALLOWED_ATTRS["*"].orEmpty() + ALLOWED_ATTRS[tagName].orEmpty()
            val safeAttrs = LinkedHashMap<String, String>()

            for ((rawK, rawV) in parsedAttrs) {
                val k = rawK.lowercase(Locale.ROOT)
                if (k.startsWith("on") || k == "style" || k == "srcdoc" || k == "formaction" || k.contains("shadowroot")) {
                    droppedAttrs++
                    continue
                }
                if (k !in allowedForTag) {
                    droppedAttrs++
                    continue
                }
                if (k == "href" || k == "src") {
                    val safeUri = sanitizeUri(rawV, isImage = (k == "src" && tagName == "img"))
                    if (safeUri == null) {
                        blockedUris++
                        droppedAttrs++
                        continue
                    }
                    safeAttrs[k] = safeUri
                } else if (k == "width" || k == "height" || k == "colspan" || k == "rowspan") {
                    val num = rawV.trim().toIntOrNull()
                    if (num != null && num in 1..4096) safeAttrs[k] = num.toString() else droppedAttrs++
                } else {
                    safeAttrs[k] = rawV
                }
            }

            if (tagName == "a" && safeAttrs.containsKey("target")) {
                safeAttrs["rel"] = "noopener noreferrer"
            }

            out.append('<').append(tagName)
            for ((k, v) in safeAttrs) {
                out.append(' ').append(k).append("=\"").append(escAttr(v)).append('"')
            }
            if (selfClosing && tagName in setOf("br", "hr", "img")) out.append(" />") else out.append('>')
        }

        return SanitizeReport(out.toString(), droppedTags, droppedAttrs, blockedUris)
    }

    fun sanitizeUri(raw: String, isImage: Boolean = false): String? {
        val decoded = decodeNumericAndNamedEntities(raw)
        val stripped = buildString(decoded.length) {
            for (ch in decoded) {
                if (ch.code > 0x20 && ch.code != 0x7F) append(ch)
            }
        }
        if (stripped.isEmpty()) return null
        val lower = stripped.lowercase(Locale.ROOT)
        if (lower.startsWith("#") || lower.startsWith("/") && !lower.startsWith("//")) {
            return raw.trim()
        }
        val colon = lower.indexOf(':')
        if (colon < 0) {
            // Relative path without scheme
            return if (!lower.startsWith("//")) raw.trim() else null
        }
        val scheme = lower.substring(0, colon)
        return when {
            scheme == "https" || scheme == "http" || scheme == "mailto" -> raw.trim()
            isImage && lower.startsWith("data:image/png;base64,") -> raw.trim()
            isImage && lower.startsWith("data:image/jpeg;base64,") -> raw.trim()
            isImage && lower.startsWith("data:image/webp;base64,") -> raw.trim()
            else -> null // Rejects javascript:, vbscript:, data:text/html, data:image/svg+xml, file:, blob:
        }
    }

    private fun decodeNumericAndNamedEntities(s: String): String {
        val numRe = Regex("&#(?:x([0-9a-fA-F]{1,6})|([0-9]{1,7}));?")
        return numRe.replace(s) { m ->
            val cp = if (m.groupValues[1].isNotEmpty()) {
                m.groupValues[1].toIntOrNull(16)
            } else {
                m.groupValues[2].toIntOrNull(10)
            }
            if (cp != null && cp in 1..0x10FFFF) String(Character.toChars(cp)) else ""
        }.replace("&colon;", ":", ignoreCase = true)
            .replace("&tab;", "\t", ignoreCase = true)
            .replace("&newline;", "\n", ignoreCase = true)
    }

    private fun findTagEnd(s: String, start: Int): Int {
        var quote: Char? = null
        var idx = start
        while (idx < s.length) {
            val ch = s[idx]
            if (quote != null) {
                if (ch == quote) quote = null
            } else if (ch == '"' || ch == '\'') {
                quote = ch
            } else if (ch == '>') {
                return idx
            }
            idx++
        }
        return -1
    }

    private fun parseAttributes(region: String): List<Pair<String, String>> {
        val res = mutableListOf<Pair<String, String>>()
        var i = 0
        val n = region.length
        while (i < n) {
            while (i < n && (region[i].isWhitespace() || region[i] == '/')) i++
            if (i >= n) break
            val kStart = i
            while (i < n && !region[i].isWhitespace() && region[i] != '=' && region[i] != '/') i++
            val key = region.substring(kStart, i)
            if (key.isEmpty()) {
                i++
                continue
            }
            while (i < n && region[i].isWhitespace()) i++
            var value = ""
            if (i < n && region[i] == '=') {
                i++
                while (i < n && region[i].isWhitespace()) i++
                if (i < n && (region[i] == '"' || region[i] == '\'')) {
                    val q = region[i++]
                    val vStart = i
                    while (i < n && region[i] != q) i++
                    value = region.substring(vStart, i)
                    if (i < n && region[i] == q) i++
                } else {
                    val vStart = i
                    while (i < n && !region[i].isWhitespace()) i++
                    value = region.substring(vStart, i)
                }
            }
            res.add(key to value)
        }
        return res
    }

    private fun escText(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(ch)
            }
        }
    }

    private fun escAttr(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }
}

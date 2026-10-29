// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

import java.util.Locale

/**
 * Clean-room static Safe-SVG (`SVG-tiny-static`) firewall (`SvgSafeSubset`).
 *
 * Untrusted `.svg` images embedded in documents, chats, or web pages are full XML+JS documents capable of:
 * - Executing `<script>` or inline `onload`/`onerror` handlers.
 * - Embedding arbitrary HTML via `<foreignObject>`.
 * - Mutating links via SMIL `<set attributeName="href" to="javascript:...">` / `<animate>`.
 * - Phoning home via external `<image href="https://tracker.example/pixel.png">` or CSS `@import`.
 * - Triggering Billion-Laughs XML entity expansion (`<!ENTITY ...>`).
 *
 * [SvgSafeSubset] validates and rewrites untrusted SVG into a guaranteed script-free, network-free,
 * entity-free static vector subset!
 */
object SvgSafeSubset {
    const val SCOPE =
        "Static SVG security firewall (refuses <!DOCTYPE/ENTITY, strips script/foreignObject/animate/set/use-external/on*-events/external-URIs, preserves vector geometry); SVG filter feImage network refs blocked."

    const val MAX_SVG_CHARS = 256 * 1024

    private val DROP_SUBTREE_TAGS = setOf(
        "script", "foreignobject", "iframe", "object", "embed", "style", "animate", "animatetransform", "animatemotion", "set",
    )

    private val ALLOWED_ELEMENTS = setOf(
        "svg", "g", "defs", "lineargradient", "radialgradient", "stop", "clippath",
        "path", "rect", "circle", "ellipse", "line", "polyline", "polygon",
        "text", "tspan", "title", "desc",
    )

    private val ALLOWED_ATTRIBUTES = setOf(
        "xmlns", "width", "height", "viewbox", "preserveaspectratio", "id",
        "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry", "d", "points",
        "fill", "fill-opacity", "fill-rule", "stroke", "stroke-width", "stroke-linecap",
        "stroke-linejoin", "stroke-dasharray", "stroke-opacity", "opacity", "transform",
        "font-family", "font-size", "font-weight", "font-style", "text-anchor",
        "offset", "stop-color", "stop-opacity", "gradientunits", "clip-path",
        "role", "aria-label",
    )

    data class SvgAudit(
        val sanitizedSvg: String,
        val removedElements: Int,
        val removedAttributes: Int,
    )

    fun sanitize(rawSvg: String): SvgAudit {
        require(rawSvg.length <= MAX_SVG_CHARS) { "SVG exceeds MAX_SVG_CHARS ($MAX_SVG_CHARS)" }
        require(!rawSvg.contains("<!ENTITY", ignoreCase = true) && !rawSvg.contains("<!DOCTYPE", ignoreCase = true)) {
            "Refusing SVG with <!DOCTYPE or <!ENTITY declaration (XXE / Billion-Laughs defense)"
        }
        val commentStripped = rawSvg
            .replace(Regex("<!--[\\s\\S]*?-->"), "")
            .replace(Regex("<\\?[\\s\\S]*?\\?>"), "")

        val out = StringBuilder(commentStripped.length)
        var removedElems = 0
        var removedAttrs = 0
        var skipUntilClose: String? = null
        var seenRootSvg = false
        var i = 0

        while (i < commentStripped.length) {
            val lt = commentStripped.indexOf('<', i)
            if (lt < 0) break
            if (lt > i && skipUntilClose == null && seenRootSvg) {
                out.append(escText(commentStripped.substring(i, lt)))
            }
            val gt = findCloseAngle(commentStripped, lt + 1)
            require(gt > lt) { "Unclosed '<' in SVG markup" }
            val rawInside = commentStripped.substring(lt + 1, gt).trim()
            i = gt + 1
            if (rawInside.isEmpty()) continue

            val closing = rawInside.startsWith("/")
            val selfClosing = !closing && rawInside.endsWith("/")
            val body = if (closing) rawInside.substring(1).trim() else rawInside.removeSuffix("/").trim()
            val nameEnd = body.indexOfFirst { it.isWhitespace() }
            val rawName = if (nameEnd < 0) body else body.substring(0, nameEnd)
            val localLower = rawName.substringAfter(':').lowercase(Locale.ROOT)
            val attrRegion = if (nameEnd < 0 || closing) "" else body.substring(nameEnd).trim()

            if (skipUntilClose != null) {
                if (closing && localLower == skipUntilClose) skipUntilClose = null
                continue
            }

            if (localLower in DROP_SUBTREE_TAGS) {
                removedElems++
                if (!closing && !selfClosing) skipUntilClose = localLower
                continue
            }

            if (localLower !in ALLOWED_ELEMENTS) {
                removedElems++
                continue
            }

            if (closing) {
                out.append("</").append(canonicalSvgCase(localLower)).append('>')
                continue
            }

            if (localLower == "svg") seenRootSvg = true
            val canonicalName = canonicalSvgCase(localLower)
            out.append('<').append(canonicalName)
            if (localLower == "svg" && !attrRegion.contains("xmlns")) {
                out.append(""" xmlns="http://www.w3.org/2000/svg"""")
            }

            for ((rawK, rawV) in parseAttrs(attrRegion)) {
                val k = rawK.lowercase(Locale.ROOT)
                if (k.startsWith("on") || k == "style" || k.endsWith("href")) {
                    removedAttrs++
                    continue
                }
                if (k !in ALLOWED_ATTRIBUTES) {
                    removedAttrs++
                    continue
                }
                // Block external url(https://...) in fill/stroke/clip-path; allow only local fragment url(#id)!
                val vTrim = rawV.trim()
                if (vTrim.contains("url(", ignoreCase = true) && !SAFE_LOCAL_URL_RE.matches(vTrim)) {
                    removedAttrs++
                    continue
                }
                if (vTrim.contains("javascript:", ignoreCase = true) || vTrim.contains("expression(", ignoreCase = true)) {
                    removedAttrs++
                    continue
                }
                out.append(' ').append(canonicalAttrCase(k)).append("=\"").append(escAttr(vTrim)).append('"')
            }
            out.append(if (selfClosing) "/>" else ">")
        }

        require(seenRootSvg) { "Input does not contain an <svg> root element" }
        return SvgAudit(out.toString(), removedElems, removedAttrs)
    }

    private val SAFE_LOCAL_URL_RE = Regex("^url\\(#[A-Za-z0-9_.-]+\\)$", RegexOption.IGNORE_CASE)

    private fun canonicalSvgCase(lower: String): String = when (lower) {
        "lineargradient" -> "linearGradient"
        "radialgradient" -> "radialGradient"
        "clippath" -> "clipPath"
        else -> lower
    }

    private fun canonicalAttrCase(lower: String): String = when (lower) {
        "viewbox" -> "viewBox"
        "preserveaspectratio" -> "preserveAspectRatio"
        "gradientunits" -> "gradientUnits"
        else -> lower
    }

    private fun findCloseAngle(s: String, start: Int): Int {
        var q: Char? = null
        for (idx in start until s.length) {
            val ch = s[idx]
            if (q != null) { if (ch == q) q = null }
            else if (ch == '"' || ch == '\'') q = ch
            else if (ch == '>') return idx
        }
        return -1
    }

    private fun parseAttrs(region: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < region.length) {
            while (i < region.length && region[i].isWhitespace()) i++
            if (i >= region.length) break
            val ks = i
            while (i < region.length && !region[i].isWhitespace() && region[i] != '=') i++
            val k = region.substring(ks, i)
            while (i < region.length && region[i].isWhitespace()) i++
            var v = ""
            if (i < region.length && region[i] == '=') {
                i++
                while (i < region.length && region[i].isWhitespace()) i++
                if (i < region.length && (region[i] == '"' || region[i] == '\'')) {
                    val q = region[i++]
                    val vs = i
                    while (i < region.length && region[i] != q) i++
                    v = region.substring(vs, i)
                    if (i < region.length) i++
                }
            }
            if (k.isNotEmpty()) out.add(k to v)
        }
        return out
    }

    private fun escText(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun escAttr(s: String): String = escText(s).replace("\"", "&quot;")
}

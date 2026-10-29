// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

import java.net.URI

/**
 * Clean-room W3C Web Application Manifest parser & standalone desktop Web-App Window host policy
 * (`CM-02`).
 */
object WebAppWindowManifestHost {
    const val SCOPE =
        "Pure-JVM W3C Web App Manifest validator, scope-origin boundary enforcer, and standalone window chrome planner (CM-02)."

    enum class DisplayMode { STANDALONE, MINIMAL_UI, FULLSCREEN, BROWSER }

    data class WebAppWindowDescriptor(
        val appId: String,
        val name: String,
        val startUrl: String,
        val scopePrefix: String,
        val displayMode: DisplayMode,
        val themeColorHex: String,
        val showMinimalOriginPill: Boolean,
    )

    fun parseManifestFields(
        manifestOriginUrl: String,
        fields: Map<String, String>,
    ): WebAppWindowDescriptor {
        val baseUri = URI(manifestOriginUrl)
        require(baseUri.scheme?.lowercase() == "https" && !baseUri.host.isNullOrBlank()) {
            "Web-app windows require an HTTPS origin, got '$manifestOriginUrl'"
        }
        val originRoot = "https://${baseUri.host.lowercase()}${if (baseUri.port > 0) ":${baseUri.port}" else ""}"
        val rawStart = fields["start_url"]?.trim().orEmpty().ifEmpty { "/" }
        val resolvedStart = baseUri.resolve(rawStart).toString()
        require(resolvedStart.lowercase().startsWith(originRoot)) {
            "cross-origin start_url '$resolvedStart' outside '$originRoot' refused"
        }
        val rawScope = fields["scope"]?.trim().orEmpty().ifEmpty { "/" }
        val resolvedScope = baseUri.resolve(rawScope).toString()
        require(resolvedScope.lowercase().startsWith(originRoot) && resolvedStart.startsWith(resolvedScope)) {
            "start_url '$resolvedStart' outside manifest scope '$resolvedScope'"
        }
        val mode = when (fields["display"]?.trim()?.lowercase()) {
            "standalone" -> DisplayMode.STANDALONE
            "minimal-ui" -> DisplayMode.MINIMAL_UI
            "fullscreen" -> DisplayMode.FULLSCREEN
            else -> DisplayMode.BROWSER
        }
        val name = (fields["short_name"] ?: fields["name"])?.trim()?.takeIf { it.isNotEmpty() }
            ?: baseUri.host
        val color = fields["theme_color"]?.trim()?.uppercase()?.takeIf {
            Regex("^#[0-9A-F]{6}$").matches(it)
        } ?: "#0F172A"
        // Anti-spoofing rule: fullscreen/standalone web-app windows MUST still show a transient/minimal origin pill!
        return WebAppWindowDescriptor(
            appId = "webapp:${baseUri.host.lowercase()}",
            name = name,
            startUrl = resolvedStart,
            scopePrefix = resolvedScope,
            displayMode = mode,
            themeColorHex = color,
            showMinimalOriginPill = true,
        )
    }

    fun isNavigationInAppScope(desc: WebAppWindowDescriptor, targetUrl: String): Boolean =
        targetUrl.startsWith(desc.scopePrefix)
}

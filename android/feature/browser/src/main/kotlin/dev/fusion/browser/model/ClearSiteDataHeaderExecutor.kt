// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * W3C `Clear-Site-Data` HTTP response header parser & origin wipe planner (`"cache"`, `"cookies"`,
 * `"storage"`, `"executionContexts"`, `"*"`), ensuring Recall archives in the user's local vault
 * remain untouched when a remote origin clears browser session state.
 */
object ClearSiteDataHeaderExecutor {
    const val SCOPE =
        "W3C Clear-Site-Data HTTP header directive parser and origin-scoped browser storage wipe planner preserving offline Recall vault files."

    data class WipePlan(
        val origin: String,
        val clearCache: Boolean,
        val clearCookies: Boolean,
        val clearDomStorage: Boolean,
        val reloadExecutionContexts: Boolean,
        val preservesRecallVault: Boolean = true,
    )

    fun parseHeader(origin: String, secureTransportHttps: Boolean, headerValue: String): WipePlan {
        require(origin.isNotBlank())
        if (!secureTransportHttps) {
            // W3C Clear-Site-Data §4.1: MUST ignore over non-secure HTTP transport!
            return WipePlan(origin, false, false, false, false, preservesRecallVault = true)
        }
        val tokens = Regex("\"([^\"]+)\"").findAll(headerValue).map { it.groupValues[1].trim() }.toSet()
        val wildcard = "*" in tokens
        return WipePlan(
            origin = origin.lowercase(),
            clearCache = wildcard || "cache" in tokens,
            clearCookies = wildcard || "cookies" in tokens,
            clearDomStorage = wildcard || "storage" in tokens,
            reloadExecutionContexts = wildcard || "executionContexts" in tokens,
            preservesRecallVault = true,
        )
    }
}

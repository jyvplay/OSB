// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * W3C Storage Access API (`document.requestStorageAccess()`) origin-pair partition guard for
 * Fusion Browser, preventing cross-site tracking iframes from unpartitioning cookies silently.
 */
object BrowserStorageAccessPartitionGuard {
    const val SCOPE =
        "W3C Storage Access API third-party iframe unpartitioned-cookie request evaluator enforcing user-gesture, tracker-blocklist, and per-top-site cap."

    enum class AccessVerdict { GRANTED_SAME_SITE, PROMPT_REQUIRED, DENIED_NO_USER_GESTURE, DENIED_KNOWN_TRACKER, DENIED_CAP_EXCEEDED }

    fun evaluateRequest(
        topLevelEtldPlusOne: String,
        embeddedEtldPlusOne: String,
        hasTransientUserActivation: Boolean,
        knownTrackerDomains: Set<String>,
        existingGrantsForTopSite: Int,
        maxGrantsPerTopSite: Int = 3,
    ): AccessVerdict {
        val top = topLevelEtldPlusOne.trim().lowercase()
        val sub = embeddedEtldPlusOne.trim().lowercase()
        require(top.isNotEmpty() && sub.isNotEmpty())
        if (top == sub) return AccessVerdict.GRANTED_SAME_SITE
        if (sub in knownTrackerDomains) return AccessVerdict.DENIED_KNOWN_TRACKER
        if (!hasTransientUserActivation) return AccessVerdict.DENIED_NO_USER_GESTURE
        if (existingGrantsForTopSite >= maxGrantsPerTopSite) return AccessVerdict.DENIED_CAP_EXCEEDED
        return AccessVerdict.PROMPT_REQUIRED
    }
}

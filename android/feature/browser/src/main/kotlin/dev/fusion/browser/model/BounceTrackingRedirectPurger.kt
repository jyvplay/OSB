// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * Clean-room **Navigation Bounce-Tracking Mitigation Engine** (inspired by W3C PrivacyCG
 * Bounce Tracking Mitigations & Firefox/Brave/Tor Browser stateless bounce purge).
 *
 * Identifies intermediate domains `A.example -> bounce-tracker.net -> B.example` that:
 * 1. Redirect within `< 10_000 ms` without any user activation/gesture on the intermediate hop;
 * 2. Mutate stateful storage (`IndexedDB`, `localStorage`, or cookies) during the redirect;
 * 3. Have no top-level user-interacted visit in the preceding 45-day interaction allowlist;
 * while strictly preserving legitimate OAuth/OIDC/SAML SSO hops initiated after explicit user click!
 */
object BounceTrackingRedirectPurger {
    const val SCOPE =
        "W3C PrivacyCG-style stateful redirect bounce-tracking detector & storage partition purger with OAuth/SSO user-activation allowlist."

    const val BOUNCE_DWELL_LIMIT_MS = 10_000L

    data class RedirectHop(
        val registrableDomain: String,
        val dwellMs: Long,
        val hadUserActivationOnHop: Boolean,
        val wroteStorageOrCookies: Boolean,
    )

    data class NavigationChain(
        val initialDomain: String,
        val intermediateHops: List<RedirectHop>,
        val finalDomain: String,
        val recentlyInteractedDomains: Set<String> = emptySet(),
    )

    data class BouncePurgePlan(
        val domainsToPurge: List<String>,
        val preservedDomains: List<String>,
    )

    fun analyzeChain(chain: NavigationChain): BouncePurgePlan {
        val purge = LinkedHashSet<String>()
        val keep = LinkedHashSet<String>()
        for (hop in chain.intermediateHops) {
            val dom = hop.registrableDomain.lowercase().trim()
            val isEndpoint = dom == chain.initialDomain.lowercase() || dom == chain.finalDomain.lowercase()
            val isInteracted = dom in chain.recentlyInteractedDomains.map { it.lowercase() } || hop.hadUserActivationOnHop
            if (!isEndpoint && !isInteracted && hop.wroteStorageOrCookies && hop.dwellMs in 0L..BOUNCE_DWELL_LIMIT_MS) {
                purge.add(dom)
            } else {
                keep.add(dom)
            }
        }
        return BouncePurgePlan(purge.toList(), keep.toList())
    }
}

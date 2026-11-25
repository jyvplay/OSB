// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Bounded tracking query-parameter stripper (`utm_*`, `fbclid`, `gclid`).
 */
class PrivacyQueryParamStripper private constructor() {
    companion object {
        private val TRACKING_EXACT = setOf("fbclid", "gclid", "mc_cid", "mc_eid")

        fun isTrackingParam(name: String): Boolean {
            val low = name.lowercase()
            return low.startsWith("utm_") || low in TRACKING_EXACT
        }

        fun stripQuery(query: String): String {
            if (query.isEmpty()) return ""
            return query.split('&')
                .filter { it.isNotEmpty() && !isTrackingParam(it.substringBefore('=')) }
                .joinToString("&")
        }
    }
}

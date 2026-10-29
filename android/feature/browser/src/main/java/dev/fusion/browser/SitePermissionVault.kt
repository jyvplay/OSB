// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

import java.net.URI
import java.util.Locale

/**
 * Clean-room Profile-Isolated Origin Permission Matrix (`PR-07l`, `PR-07o`).
 *
 * Partitions per-site `CAMERA`, `MICROPHONE`, `GEOLOCATION`, `NOTIFICATIONS`, `POPUPS`, and
 * `JAVASCRIPT` grants strictly by `(profileId, scheme://host:port)` with separate data-directory
 * slugs and ephemeral `ALLOW_THIS_SESSION` grants wiped automatically on session close!
 */
class SitePermissionVault {
    enum class Capability { CAMERA, MICROPHONE, GEOLOCATION, NOTIFICATIONS, POPUPS, JAVASCRIPT }
    enum class Decision { ALLOW_PERSISTENT, ALLOW_THIS_SESSION, ASK, BLOCK }

    data class BrowserProfile(
        val profileId: String,
        val displayName: String,
        val dataDirSlug: String,
        val ephemeralIncognito: Boolean = false,
    ) {
        init {
            require(profileId.matches(Regex("[a-z0-9_-]{2,32}"))) { "invalid profileId '$profileId'" }
            require(dataDirSlug.matches(Regex("profile_[a-z0-9_-]{2,32}"))) {
                "profile data directory '$dataDirSlug' must be isolated under profile_*"
            }
        }
    }

    private val profiles = LinkedHashMap<String, BrowserProfile>()
    private val grants = HashMap<Triple<String, String, Capability>, Decision>()

    fun registerProfile(profile: BrowserProfile) {
        val dupDir = profiles.values.firstOrNull { it.profileId != profile.profileId && it.dataDirSlug == profile.dataDirSlug }
        require(dupDir == null) {
            "PR-07o violation: profile '${profile.profileId}' cannot share dataDir '${profile.dataDirSlug}' with '${dupDir?.profileId}'"
        }
        profiles[profile.profileId] = profile
    }

    fun canonicalOrigin(url: String): String {
        val uri = URI(url.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: throw IllegalArgumentException("missing URI scheme")
        require(scheme in setOf("https", "http", "file")) { "unsupported origin scheme '$scheme'" }
        if (scheme == "file") return "file://local"
        val host = uri.host?.lowercase(Locale.ROOT) ?: throw IllegalArgumentException("missing URI host")
        val defaultPort = if (scheme == "https") 443 else 80
        val port = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
        return "$scheme://$host$port"
    }

    fun setDecision(profileId: String, url: String, capability: Capability, decision: Decision) {
        val profile = profiles[profileId] ?: throw IllegalArgumentException("unknown profile '$profileId'")
        val origin = canonicalOrigin(url)
        // Sensitive hardware sensors (CAMERA, MICROPHONE, GEOLOCATION) NEVER allow cleartext http:// (except loopback)!
        if (capability in setOf(Capability.CAMERA, Capability.MICROPHONE, Capability.GEOLOCATION) &&
            (decision == Decision.ALLOW_PERSISTENT || decision == Decision.ALLOW_THIS_SESSION)
        ) {
            val secure = origin.startsWith("https://") || origin.startsWith("http://127.0.0.1") || origin.startsWith("http://localhost")
            require(secure) { "insecure origin '$origin' cannot receive $capability grant" }
        }
        val effective = if (profile.ephemeralIncognito && decision == Decision.ALLOW_PERSISTENT) {
            Decision.ALLOW_THIS_SESSION
        } else {
            decision
        }
        grants[Triple(profileId, origin, capability)] = effective
    }

    fun effectiveDecision(profileId: String, url: String, capability: Capability): Decision {
        require(profileId in profiles) { "unknown profile '$profileId'" }
        val origin = canonicalOrigin(url)
        grants[Triple(profileId, origin, capability)]?.let { return it }
        return when (capability) {
            Capability.JAVASCRIPT -> Decision.ALLOW_PERSISTENT
            Capability.POPUPS -> Decision.BLOCK
            else -> Decision.ASK
        }
    }

    fun clearSessionGrants(profileId: String): Int {
        val profile = profiles[profileId] ?: return 0
        val iter = grants.entries.iterator()
        var removed = 0
        while (iter.hasNext()) {
            val e = iter.next()
            if (e.key.first == profileId && (profile.ephemeralIncognito || e.value == Decision.ALLOW_THIS_SESSION)) {
                iter.remove()
                removed++
            }
        }
        return removed
    }
}

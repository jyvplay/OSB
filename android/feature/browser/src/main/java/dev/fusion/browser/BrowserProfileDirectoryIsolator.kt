// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

/**
 * Clean-room Separate-Data-Directory Browser Profile Isolator (`PR-07o`) and Integrated `Files`
 * Download Quarantine & Collision-Safe Filename Planner (`PR-07m`).
 */
object BrowserProfileDirectoryIsolator {
    const val SCOPE =
        "Pure-JVM canonical browser profile directory partitioner (disjoint cookies/storage/cache roots, path-traversal refusal) and Files-integrated download planner (PR-07o, PR-07m)."

    data class ProfilePartition(
        val profileId: String,
        val displayName: String,
        val ephemeralIncognito: Boolean,
        val cookiesDir: String,
        val indexedDbDir: String,
        val httpCacheDir: String,
        val downloadsSubdir: String,
    )

    data class PlannedDownload(
        val targetRelativePath: String,
        val resolvedFileName: String,
        val requiresExecutableWarning: Boolean,
        val mimeType: String,
    )

    private val SLUG_RE = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")
    private val DANGEROUS_EXTS = setOf("apk", "dex", "so", "sh", "elf", "exe", "bat", "msi", "jar", "js")

    fun allocateProfile(profileId: String, displayName: String, ephemeralIncognito: Boolean = false): ProfilePartition {
        val slug = profileId.trim().lowercase()
        require(SLUG_RE.matches(slug)) {
            "refusing non-canonical browser profile id '$profileId' (path traversal / invalid chars)"
        }
        val tier = if (ephemeralIncognito) "ram_ephemeral" else "persisted_profiles"
        val base = "browser_data/$tier/$slug"
        return ProfilePartition(
            profileId = slug,
            displayName = displayName.trim().ifEmpty { slug },
            ephemeralIncognito = ephemeralIncognito,
            cookiesDir = "$base/cookies",
            indexedDbDir = "$base/site_storage",
            httpCacheDir = "$base/http_cache",
            downloadsSubdir = "Downloads/Fusion/$slug",
        )
    }

    fun planDownloadToFiles(
        partition: ProfilePartition,
        rawSuggestedName: String,
        mimeType: String,
        existingNamesInDir: Set<String>,
    ): PlannedDownload {
        val stripped = rawSuggestedName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001F<>:\"|?*]"), "_")
            .trim('.')
            .trim()
            .ifEmpty { "download.bin" }
        val dot = stripped.lastIndexOf('.')
        val stem = if (dot > 0) stripped.substring(0, dot) else stripped
        val ext = if (dot > 0 && dot + 1 < stripped.length) stripped.substring(dot + 1) else ""
        var candidate = stripped
        var suffix = 1
        val lowerExisting = existingNamesInDir.map { it.lowercase() }.toSet()
        while (candidate.lowercase() in lowerExisting) {
            candidate = if (ext.isNotEmpty()) "$stem ($suffix).$ext" else "$stem ($suffix)"
            suffix++
        }
        val warn = ext.lowercase() in DANGEROUS_EXTS || mimeType.lowercase().contains("android.package-archive")
        return PlannedDownload(
            targetRelativePath = "${partition.downloadsSubdir}/$candidate",
            resolvedFileName = candidate,
            requiresExecutableWarning = warn,
            mimeType = mimeType.ifBlank { "application/octet-stream" },
        )
    }
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Clean-room Deterministic Configuration Export / Import Bundle Codec (`BK-03`).
 *
 * Exports non-secret workspace, shortcut, appearance, and window-rule preferences into a canonical
 * sorted key-value bundle authenticated by a SHA-256 integrity footer, strips forbidden secret/key
 * namespaces (`vault.*`, `auth.*`, `token.*`, `password.*`) unconditionally, and computes a dry-run
 * import diff before mutating live settings!
 */
object ConfigExportBundleCodec {
    const val FORMAT_HEADER = "FUSION-CONFIG-BUNDLE-V1"
    private val FORBIDDEN_SECRET_PREFIXES = listOf("vault.", "auth.", "token.", "secret.", "password.", "keystore.")

    data class ConfigDiffEntry(
        val key: String,
        val previousValue: String?,
        val importedValue: String,
    )

    data class DecodedBundle(
        val schemaVersion: Int,
        val entries: Map<String, String>,
        val sha256Hex: String,
    )

    fun isExportableKey(key: String): Boolean {
        val lower = key.trim().lowercase()
        if (lower.isEmpty() || !lower.matches(Regex("[a-z0-9_.-]{2,96}"))) return false
        return FORBIDDEN_SECRET_PREFIXES.none { lower.startsWith(it) || (".$it") in lower }
    }

    fun exportCanonical(settings: Map<String, String>, schemaVersion: Int = 1): String {
        require(schemaVersion in 1..99) { "invalid schemaVersion $schemaVersion" }
        val filtered = settings.entries
            .filter { isExportableKey(it.key) }
            .sortedBy { it.key }
        val body = buildString {
            append(FORMAT_HEADER).append('\n')
            append("schema_version=").append(schemaVersion).append('\n')
            for ((k, v) in filtered) {
                append("entry:").append(k.trim()).append('=').append(escapeVal(v)).append('\n')
            }
        }
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(body.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "${body}sha256=$sha\n"
    }

    fun importAndVerify(bundleText: String): DecodedBundle {
        val normalized = bundleText.replace("\r\n", "\n")
        val shaIdx = normalized.lastIndexOf("\nsha256=")
        require(shaIdx > 0) { "missing sha256 integrity footer in config bundle" }
        val bodyWithNewline = normalized.substring(0, shaIdx + 1)
        val claimedSha = normalized.substring(shaIdx + "\nsha256=".length).trim().lowercase()
        val computedSha = MessageDigest.getInstance("SHA-256")
            .digest(bodyWithNewline.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        require(claimedSha == computedSha) { "config bundle SHA-256 integrity mismatch" }

        val lines = bodyWithNewline.lines().filter { it.isNotEmpty() }
        require(lines.firstOrNull() == FORMAT_HEADER) { "invalid config bundle header" }
        var schemaVer = 1
        val entries = LinkedHashMap<String, String>()
        for (i in 1 until lines.size) {
            val l = lines[i]
            when {
                l.startsWith("schema_version=") -> {
                    schemaVer = l.substringAfter('=').toInt()
                    require(schemaVer in 1..1) { "unsupported future config schema_version $schemaVer" }
                }
                l.startsWith("entry:") -> {
                    val kv = l.removePrefix("entry:")
                    val eq = kv.indexOf('=')
                    require(eq > 0) { "malformed entry line" }
                    val k = kv.substring(0, eq)
                    require(isExportableKey(k)) { "forbidden or invalid key '$k' rejected during import" }
                    entries[k] = unescapeVal(kv.substring(eq + 1))
                }
            }
        }
        return DecodedBundle(schemaVer, entries, computedSha)
    }

    fun previewImportDiff(current: Map<String, String>, imported: DecodedBundle): List<ConfigDiffEntry> =
        imported.entries.mapNotNull { (k, newVal) ->
            val oldVal = current[k]
            if (oldVal == newVal) null else ConfigDiffEntry(k, oldVal, newVal)
        }

    private fun escapeVal(s: String): String = s.replace("\\", "\\\\").replace("\n", "\\n")
    private fun unescapeVal(s: String): String = s.replace("\\n", "\n").replace("\\\\", "\\")
}

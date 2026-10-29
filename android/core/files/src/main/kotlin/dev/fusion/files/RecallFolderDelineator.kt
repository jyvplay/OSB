// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

import java.net.IDN
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Clean-room folder delineation, path-traversal confinement, and SHA-256 content-addressed
 * local archival engine for **Recall** (automatic browser image, video, and music recorder).
 *
 * Supports all 3 user-selectable folder delineation modes:
 * 1. [DelineationMode.BY_URL_NAME] — normalized Punycode/NFKC host + clean path slug (`by-url/<url_slug>/<kind>/...`)
 * 2. [DelineationMode.BY_PAGE_TITLE] — filesystem-safe NFKC `<title>` slug + 6-hex origin discriminator (`by-title/<title_slug>/<kind>/...`)
 * 3. [DelineationMode.BY_CUSTOM_TEMPLATE] — user template (`{custom}/{yyyy}-{mm}-{dd}/{domain}/{title}/{kind}`) strictly confined inside `archiveRoot`.
 */
object RecallFolderDelineator {
    const val SCOPE =
        "Recall local media archiver folder delineator (BY_URL_NAME, BY_PAGE_TITLE, BY_CUSTOM_TEMPLATE), NFKC/IDNA/Windows-device-name traversal sanitizer, atomic temp-write-rename disk persister, and SHA-256 deduplicated manifest.recall.json ledger."

    const val MAX_SEGMENT_UTF8_BYTES = 96
    const val MANIFEST_FILENAME = "manifest.recall.json"

    private val WINDOWS_RESERVED = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    private val TRACKING_QUERY_KEYS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "dclid", "msclkid", "mc_eid", "si", "spm", "igshid",
    )

    enum class DelineationMode { BY_URL_NAME, BY_PAGE_TITLE, BY_CUSTOM_TEMPLATE }
    enum class MediaKind(val folderName: String, val defaultExt: String) {
        IMAGE("images", "png"),
        VIDEO("videos", "mp4"),
        MUSIC("music", "wav"),
    }

    data class ViewedMediaMetadata(
        val pageUrl: String,
        val pageTitle: String,
        val assetUrl: String,
        val mediaKind: MediaKind,
        val suggestedFilename: String,
        val capturedAtEpochMs: Long = 1_793_145_600_000L, // deterministic default (2026-10-28T00:00:00Z)
        val tierId: String = "TIER_1_DIRECT_RANGE",
        val customLabel: String = "DefaultVault",
    )

    data class PlannedArchiveLocation(
        val mode: DelineationMode,
        val relativeFolder: String,
        val relativeFilePath: String,
        val canonicalUrlKey: String,
        val sha256Hex: String,
    )

    data class SavedRecallEntry(
        val sha256Hex: String,
        val mediaKind: MediaKind,
        val mode: DelineationMode,
        val relativeFilePath: String,
        val byteSize: Long,
        val deduplicatedHit: Boolean,
        val viewCount: Int,
        val tierId: String,
        val pageUrl: String,
        val pageTitle: String,
    )

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sanitizeSegment(raw: String, fallback: String = "untitled"): String {
        val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .replace('\u0000', ' ')
            .replace("%2e", "_", ignoreCase = true)
            .replace("%2f", "_", ignoreCase = true)
            .replace("%5c", "_", ignoreCase = true)
        val cleaned = buildString(nfkc.length) {
            for (ch in nfkc) {
                when {
                    ch.code < 32 || ch.code == 127 -> append(' ')
                    ch in "<>:\"/\\|?*" -> append('_')
                    else -> append(ch)
                }
            }
        }
            .trim()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('.', '_', ' ')

        var candidate = if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") fallback else cleaned
        while (candidate.contains("..")) candidate = candidate.replace("..", "_")
        val stemUpper = candidate.substringBefore('.').uppercase()
        if (stemUpper in WINDOWS_RESERVED) {
            candidate = "_${candidate}"
        }
        // Clamp UTF-8 byte length cleanly on codepoint boundaries
        while (candidate.toByteArray(Charsets.UTF_8).size > MAX_SEGMENT_UTF8_BYTES && candidate.length > 1) {
            candidate = candidate.dropLast(1).trimEnd('.', '_', ' ')
        }
        return candidate.ifEmpty { fallback }
    }

    fun normalizeUrlSlug(rawUrl: String): Pair<String, String> {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull()
        val rawHost = uri?.host?.takeIf { it.isNotBlank() } ?: "local-origin"
        val asciiHost = runCatching { IDN.toASCII(rawHost, IDN.ALLOW_UNASSIGNED).lowercase() }
            .getOrDefault(rawHost.lowercase())
            .removePrefix("www.")
        val rawPath = (uri?.rawPath ?: "")
            .split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." && !it.equals("%2e%2e", ignoreCase = true) }
            .joinToString("_") { sanitizeSegment(it, "seg") }
        val keptQuery = (uri?.rawQuery ?: "")
            .split('&')
            .mapNotNull { kv ->
                val k = kv.substringBefore('=').lowercase()
                if (k.isBlank() || k in TRACKING_QUERY_KEYS || k.startsWith("utm_")) null
                else sanitizeSegment(kv, "")
            }
            .filter { it.isNotBlank() }
            .joinToString("_")
        val canonicalKey = listOf(asciiHost, rawPath, keptQuery).filter { it.isNotBlank() }.joinToString("/")
        val slug = sanitizeSegment(
            listOf(asciiHost, rawPath).filter { it.isNotBlank() }.joinToString("__"),
            fallback = "unknown_url",
        )
        return canonicalKey to slug
    }

    fun planLocation(
        mode: DelineationMode,
        meta: ViewedMediaMetadata,
        payloadBytes: ByteArray,
        customTemplate: String = "{custom}/{yyyy}-{mm}-{dd}/{domain}/{kind}",
    ): PlannedArchiveLocation {
        val hash = sha256Hex(payloadBytes)
        val hash12 = hash.take(12)
        val (canonicalUrl, urlSlug) = normalizeUrlSlug(meta.pageUrl)
        val domainOnly = sanitizeSegment(canonicalUrl.substringBefore('/'), "local-origin")
        val originTag6 = sha256Hex(domainOnly.toByteArray(Charsets.UTF_8)).take(6)
        val titleSlug = if (meta.pageTitle.isBlank()) {
            "${domainOnly}_${originTag6}"
        } else {
            "${sanitizeSegment(meta.pageTitle, domainOnly)}_${originTag6}"
        }
        val customSlug = sanitizeSegment(meta.customLabel, "DefaultVault")
        val utcDate = Instant.ofEpochMilli(meta.capturedAtEpochMs).atZone(ZoneOffset.UTC)
        val yyyy = DateTimeFormatter.ofPattern("yyyy").format(utcDate)
        val mm = DateTimeFormatter.ofPattern("MM").format(utcDate)
        val dd = DateTimeFormatter.ofPattern("dd").format(utcDate)

        val relFolder = when (mode) {
            DelineationMode.BY_URL_NAME -> "by-url/$urlSlug/${meta.mediaKind.folderName}"
            DelineationMode.BY_PAGE_TITLE -> "by-title/$titleSlug/${meta.mediaKind.folderName}"
            DelineationMode.BY_CUSTOM_TEMPLATE -> {
                require(!customTemplate.contains("..") && !customTemplate.contains('\\')) {
                    "Custom Recall template must not contain '..' or backslash traversal: $customTemplate"
                }
                val expanded = customTemplate
                    .replace("{custom}", customSlug)
                    .replace("{yyyy}", yyyy)
                    .replace("{mm}", mm)
                    .replace("{dd}", dd)
                    .replace("{domain}", domainOnly)
                    .replace("{url}", urlSlug)
                    .replace("{title}", titleSlug)
                    .replace("{kind}", meta.mediaKind.folderName)
                val parts = expanded.split('/')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                require(parts.isNotEmpty() && parts.none { it == "." || it == ".." }) {
                    "Custom Recall template resolved to invalid path segments: $customTemplate"
                }
                val safeParts = parts.map { sanitizeSegment(it, "vault") }.toMutableList()
                if (meta.mediaKind.folderName !in safeParts) {
                    safeParts.add(meta.mediaKind.folderName)
                }
                "custom/" + safeParts.joinToString("/")
            }
        }

        val rawBase = meta.suggestedFilename.substringAfterLast('/').substringAfterLast('\\')
        val ext = rawBase.substringAfterLast('.', "").lowercase()
            .filter { it.isLetterOrDigit() }
            .take(8)
            .ifEmpty { meta.mediaKind.defaultExt }
        val stem = sanitizeSegment(rawBase.substringBeforeLast('.', rawBase), meta.mediaKind.name.lowercase())
        val fileName = "${hash12}_${stem}.${ext}"
        val relFile = "$relFolder/$fileName"
        return PlannedArchiveLocation(mode, relFolder, relFile, canonicalUrl, hash)
    }

    fun saveToDisk(
        archiveRoot: Path,
        mode: DelineationMode,
        meta: ViewedMediaMetadata,
        payloadBytes: ByteArray,
        customTemplate: String = "{custom}/{yyyy}-{mm}-{dd}/{domain}/{kind}",
    ): SavedRecallEntry {
        require(payloadBytes.isNotEmpty()) { "Recall refuses to archive 0-byte empty media payload" }
        val rootNorm = archiveRoot.toAbsolutePath().normalize()
        Files.createDirectories(rootNorm)
        val plan = planLocation(mode, meta, payloadBytes, customTemplate)
        val targetFile = rootNorm.resolve(plan.relativeFilePath).normalize()
        require(targetFile.startsWith(rootNorm)) {
            "Path traversal blocked: $targetFile escaped archiveRoot $rootNorm"
        }

        val manifestPath = rootNorm.resolve(MANIFEST_FILENAME)
        val existingEntries = readManifestEntries(manifestPath).toMutableList()
        val priorIdx = existingEntries.indexOfFirst { it.sha256Hex == plan.sha256Hex && it.mode == mode }

        val entry = if (priorIdx >= 0 && Files.isRegularFile(rootNorm.resolve(existingEntries[priorIdx].relativeFilePath))) {
            val prev = existingEntries[priorIdx]
            val updated = prev.copy(viewCount = prev.viewCount + 1)
            existingEntries[priorIdx] = updated
            updated
        } else {
            Files.createDirectories(targetFile.parent)
            val tmp = targetFile.parent.resolve(".recall-tmp-${plan.sha256Hex.take(16)}.part")
            Files.write(tmp, payloadBytes)
            Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            val created = SavedRecallEntry(
                sha256Hex = plan.sha256Hex,
                mediaKind = meta.mediaKind,
                mode = mode,
                relativeFilePath = plan.relativeFilePath,
                byteSize = payloadBytes.size.toLong(),
                deduplicatedHit = false,
                viewCount = 1,
                tierId = meta.tierId,
                pageUrl = meta.pageUrl,
                pageTitle = meta.pageTitle,
            )
            existingEntries.add(created)
            created
        }

        writeManifestEntries(manifestPath, existingEntries)
        return if (priorIdx >= 0) entry.copy(deduplicatedHit = true) else entry
    }

    fun readManifestEntries(manifestPath: Path): List<SavedRecallEntry> {
        if (!Files.isRegularFile(manifestPath)) return emptyList()
        val lines = Files.readAllLines(manifestPath, Charsets.UTF_8)
        val out = ArrayList<SavedRecallEntry>()
        for (line in lines) {
            val t = line.trim().removeSuffix(",")
            if (!t.startsWith("{\"sha256\":\"")) continue
            fun field(k: String): String =
                Regex("\"$k\":\"((?:\\\\.|[^\"])*)\"").find(t)?.groupValues?.get(1)
                    ?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: ""
            fun numField(k: String): Long =
                Regex("\"$k\":(\\d+)").find(t)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            out.add(
                SavedRecallEntry(
                    sha256Hex = field("sha256"),
                    mediaKind = runCatching { MediaKind.valueOf(field("kind")) }.getOrDefault(MediaKind.IMAGE),
                    mode = runCatching { DelineationMode.valueOf(field("mode")) }.getOrDefault(DelineationMode.BY_URL_NAME),
                    relativeFilePath = field("path"),
                    byteSize = numField("bytes"),
                    deduplicatedHit = false,
                    viewCount = numField("views").toInt().coerceAtLeast(1),
                    tierId = field("tier"),
                    pageUrl = field("pageUrl"),
                    pageTitle = field("pageTitle"),
                ),
            )
        }
        return out
    }

    private fun writeManifestEntries(manifestPath: Path, entries: List<SavedRecallEntry>) {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
        val sb = StringBuilder()
        sb.append("{\n  \"schema\": \"fusion.recall.manifest.v1\",\n  \"entries\": [\n")
        entries.forEachIndexed { idx, e ->
            val comma = if (idx < entries.lastIndex) "," else ""
            sb.append(
                "    {\"sha256\":\"${esc(e.sha256Hex)}\",\"kind\":\"${e.mediaKind.name}\",\"mode\":\"${e.mode.name}\"," +
                    "\"path\":\"${esc(e.relativeFilePath)}\",\"bytes\":${e.byteSize},\"views\":${e.viewCount}," +
                    "\"tier\":\"${esc(e.tierId)}\",\"pageUrl\":\"${esc(e.pageUrl)}\",\"pageTitle\":\"${esc(e.pageTitle)}\"}$comma\n",
            )
        }
        sb.append("  ]\n}\n")
        val tmp = manifestPath.parent.resolve(".manifest.recall.tmp")
        Files.writeString(tmp, sb.toString(), Charsets.UTF_8)
        Files.move(tmp, manifestPath, StandardCopyOption.REPLACE_EXISTING)
    }
}

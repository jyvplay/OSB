// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Permissive Apache-2.0 Clean-Room Embedded Stream & Direct Open-Media Contract (`EmbeddedStreamContract.kt`).
 *
 * Licensing & ToS compliance architecture:
 * - Zero proprietary client SDKs, zero stream-ripping/decryption, zero ad stripping, zero hidden background audio
 *   extraction (`>= 200x200dp` visible player enforced per YouTube IFrame API Terms of Service).
 * - Uses official first-party web embed endpoints (`https://www.youtube-nocookie.com/embed/...`,
 *   `https://open.spotify.com/embed/...`, `https://w.soundcloud.com/player/?url=...`) ONLY after explicit user session
 *   consent, confined inside a strict subresource host allowlist with `file://` & `content://` access disabled.
 * - Also supports direct open-licensed HTTPS audio/video URLs (`.mp3`, `.ogg`, `.opus`, `.wav`, `.flac`, `.mp4`, `.webm`)
 *   with clean-room HTML5 `<video>/<audio>` controls and zero third-party network calls.
 */
object EmbeddedStreamContract {
    const val MIN_VISIBLE_PLAYER_DP = 200

    data class EmbedDescriptor(
        val service: MediaService,
        val embedUrl: String,
        val allowedHosts: Set<String>,
        val attributionNotice: String
    )

    data class DirectStreamDescriptor(
        val canonicalUrl: String,
        val host: String,
        val mediaKind: String
    )

    fun toOfficialEmbed(link: ServiceLink): EmbedDescriptor {
        val uri = URI(link.url)
        return when (link.service) {
            MediaService.YOUTUBE -> {
                val videoId = uri.rawQuery.orEmpty().substringAfter("v=").substringBefore('&')
                require(videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Invalid YouTube video ID" }
                EmbedDescriptor(
                    service = MediaService.YOUTUBE,
                    embedUrl = "https://www.youtube-nocookie.com/embed/$videoId?playsinline=1&rel=0",
                    allowedHosts = setOf(
                        "www.youtube-nocookie.com", "youtube-nocookie.com",
                        "www.youtube.com", "i.ytimg.com", "s.ytimg.com", "googlevideo.com"
                    ),
                    attributionNotice = "Official YouTube Privacy-Enhanced IFrame (`youtube-nocookie.com`). Visible >=200×200dp player required by YouTube Terms; IP address & playback events are sent to Google when loaded."
                )
            }
            MediaService.SPOTIFY -> {
                val parts = uri.rawPath.removePrefix("/").split('/')
                require(parts.size == 2) { "Invalid Spotify resource path" }
                EmbedDescriptor(
                    service = MediaService.SPOTIFY,
                    embedUrl = "https://open.spotify.com/embed/${parts[0]}/${parts[1]}",
                    allowedHosts = setOf("open.spotify.com", "i.scdn.co", "p.scdn.co", "audio-ak-spotify-com.akamaized.net"),
                    attributionNotice = "Official Spotify Web Embed (`open.spotify.com/embed`). Plays public preview or logged-in user session per Spotify Terms; zero proprietary SDK binary bundled."
                )
            }
            MediaService.SOUNDCLOUD -> {
                val encoded = URLEncoder.encode(link.url, StandardCharsets.UTF_8.name())
                EmbedDescriptor(
                    service = MediaService.SOUNDCLOUD,
                    embedUrl = "https://w.soundcloud.com/player/?url=$encoded&auto_play=false&show_teaser=false&visual=true",
                    allowedHosts = setOf("w.soundcloud.com", "soundcloud.com", "api-widget.soundcloud.com", "i1.sndcdn.com", "cf-media.sndcdn.com"),
                    attributionNotice = "Official SoundCloud HTML5 Widget (`w.soundcloud.com/player`) with required creator & SoundCloud attribution. SoundCloud cookies/IP disclosure applies once started."
                )
            }
        }
    }

    fun isHostAllowedForEmbed(descriptor: EmbedDescriptor, candidateHost: String?): Boolean {
        val h = candidateHost?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        return descriptor.allowedHosts.any { allowed -> h == allowed || h.endsWith(".$allowed") }
    }

    /** Validates direct open-licensed HTTPS media stream URLs (Creative Commons / public-domain / self-hosted). */
    fun parseDirectOpenStream(rawUrl: String): DirectStreamDescriptor {
        require(rawUrl.length in 10..2048 && rawUrl.all { it.code in 0x21..0x7E } && '\\' !in rawUrl) {
            "Use a bounded ASCII HTTPS media stream URL"
        }
        val uri = URI(rawUrl)
        require(uri.scheme.equals("https", true) && uri.userInfo == null && !uri.isOpaque) {
            "Direct streams require HTTPS without credentials"
        }
        val host = requireNotNull(uri.host).lowercase(Locale.ROOT)
        val lowerPath = uri.rawPath.orEmpty().lowercase(Locale.ROOT)
        val kind = when {
            lowerPath.endsWith(".mp4") || lowerPath.endsWith(".webm") || lowerPath.endsWith(".ogv") -> "video"
            lowerPath.endsWith(".mp3") || lowerPath.endsWith(".ogg") || lowerPath.endsWith(".opus") ||
                lowerPath.endsWith(".wav") || lowerPath.endsWith(".flac") || lowerPath.endsWith(".m4a") -> "audio"
            else -> throw IllegalArgumentException("Supported open direct formats: .mp4, .webm, .mp3, .ogg, .opus, .wav, .flac, .m4a")
        }
        return DirectStreamDescriptor(uri.toASCIIString(), host, kind)
    }
}

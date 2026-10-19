// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.net.URI
import java.util.Locale

enum class MediaService(val title: String) { YOUTUBE("YouTube"), SPOTIFY("Spotify"), SOUNDCLOUD("SoundCloud") }
class ServiceLink private constructor(val service: MediaService, val url: String) {
    companion object {
        /** Parse only public content URLs, locally. Never expands shorteners, extracts streams or contacts a service. */
        fun parse(text: String): ServiceLink {
            require(text.length in 1..2048 && text.all { it.code in 0x21..0x7e } && '\\' !in text) { "Use a bounded ASCII HTTPS content link" }
            val uri = URI(text)
            require(uri.scheme.equals("https", true) && uri.userInfo == null && uri.port == -1 && !uri.isOpaque)
            val host = requireNotNull(uri.host).lowercase(Locale.ROOT)
            require(uri.rawAuthority.equals(host, true)) { "Credentials, ports and encoded hosts are not accepted" }
            val path = uri.rawPath.orEmpty()
            val parts = path.removePrefix("/").split('/')
            val videoId = Regex("[A-Za-z0-9_-]{11}")
            return when (host) {
                "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be" -> {
                    val id = when {
                        host == "youtu.be" && parts.size == 1 -> parts[0]
                        path == "/watch" -> {
                            val ids = uri.rawQuery.orEmpty().split('&').filter { it.substringBefore('=') == "v" }
                            require(ids.size == 1 && ids[0].count { it == '=' } == 1) { "Exactly one video identifier required" }
                            // No decoding: encoded delimiters/ambiguous identifiers are refused.
                            ids[0].substringAfter('=')
                        }
                        parts.size == 2 && parts[0] in setOf("shorts", "live", "embed") -> parts[1]
                        else -> throw IllegalArgumentException("Use a YouTube video link, not a search/channel or redirect")
                    }
                    require(videoId.matches(id)) { "Invalid video identifier" }
                    ServiceLink(MediaService.YOUTUBE, "https://www.youtube.com/watch?v=$id")
                }
                "open.spotify.com" -> {
                    require(parts.size == 2 && parts[0] in setOf("track", "album", "playlist", "episode", "show") && parts[1].matches(Regex("[A-Za-z0-9]{22}"))) { "Use a public Spotify content link" }
                    ServiceLink(MediaService.SPOTIFY, "https://open.spotify.com/${parts[0]}/${parts[1]}")
                }
                "soundcloud.com", "www.soundcloud.com" -> {
                    require((parts.size == 2 || (parts.size == 3 && parts[1] == "sets")) && parts.all { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) }) { "Use a public SoundCloud track or set link" }
                    require(parts.first().lowercase(Locale.ROOT) !in setOf("discover", "search", "you", "settings", "connect", "oauth", "upload"))
                    require(!(uri.rawQuery.orEmpty().split('&').any { it.substringBefore('=').equals("secret_token", true) })) { "Private share tokens are not accepted" }
                    ServiceLink(MediaService.SOUNDCLOUD, "https://soundcloud.com/" + parts.joinToString("/"))
                }
                else -> throw IllegalArgumentException("Only exact YouTube, Spotify and SoundCloud content hosts are accepted")
            }
        }
    }
}

/** Callback epochs prevent a late prepare/completion from restarting a released player. */
class PlaybackEpoch {
    enum class Phase { EMPTY, PREPARING, READY, PLAYING, PAUSED, ENDED, FAILED, CLOSED }
    var phase = Phase.EMPTY; private set
    var generation = 0L; private set
    fun begin(): Long { check(generation < Long.MAX_VALUE); generation++; phase = Phase.PREPARING; return generation }
    fun prepared(token: Long): Boolean = if (token == generation && phase == Phase.PREPARING) { phase = Phase.READY; true } else false
    fun play(token: Long): Boolean = if (token == generation && phase in setOf(Phase.READY, Phase.PAUSED, Phase.ENDED)) { phase = Phase.PLAYING; true } else false
    fun pause(): Boolean = if (phase == Phase.PLAYING) { phase = Phase.PAUSED; true } else false
    fun ended(token: Long): Boolean = if (token == generation && phase == Phase.PLAYING) { phase = Phase.ENDED; true } else false
    fun fail(token: Long): Boolean = if (token == generation && phase != Phase.CLOSED) { phase = Phase.FAILED; true } else false
    fun close() { check(generation < Long.MAX_VALUE); generation++; phase = Phase.CLOSED }
}

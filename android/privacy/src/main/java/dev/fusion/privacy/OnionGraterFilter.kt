// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Clean-room Tails `onion-grater` Tor Control Port (`951`) Least-Privilege Proxy Filter.
 *
 * Models Tails' per-application Tor control-port isolation (`tails.net/contribute/design/`,
 * Tails `#20821` 2026 audit):
 * - Separates untrusted client sessions from the real Tor daemon control socket.
 * - Requires explicit session authentication before any command other than `PROTOCOLINFO` / `QUIT`.
 * - Enforces per-app command + `GETINFO` key allowlists; unlisted commands (`GETCONF`, `SETCONF`,
 *   `ADD_ONION`, `TAKEOWNERSHIP`, `HSFETCH`, raw `SIGNAL SHUTDOWN`) return `510 Command filtered`.
 * - Rewrites sensitive responses (e.g. `GETINFO net/listeners/socks` -> app's isolated SocksPort)
 *   and scopes `650 STREAM` asynchronous events strictly to streams owned by `clientSourceAddr`.
 */
object OnionGraterFilter {
    const val SCOPE =
        "Tails onion-grater-style Tor Control Port per-app command/GETINFO/SIGNAL allowlist, response rewriter, and STREAM-event isolator; live Tor AF_UNIX/TCP socket I/O out of scope."

    const val MAX_LINE_CHARS = 2_048

    data class AppProfile(
        val appId: String,
        val allowedSignals: Set<String> = emptySet(),
        val allowedGetInfoKeys: Set<String> = emptySet(),
        val allowedEvents: Set<String> = emptySet(),
        val responseRewrites: Map<String, String> = emptyMap(),
        val restrictStreamEvents: Boolean = true,
        val clientSourceIp: String = "127.0.0.1",
    ) {
        init {
            require(appId.isNotBlank()) { "appId must not be blank" }
        }
    }

    sealed interface FilterDecision {
        data class ForwardToTor(val sanitizedCommand: String) : FilterDecision
        data class SyntheticReply(val controlReply: String) : FilterDecision
        data class Denied(val replyCode: Int = 510, val reason: String) : FilterDecision {
            fun wireLine(): String = "$replyCode $reason\r\n"
        }
    }

    class Session(val profile: AppProfile) {
        var isAuthenticated: Boolean = false
            private set

        private val subscribedEvents = LinkedHashSet<String>()

        fun evaluateClientLine(rawLine: String): FilterDecision {
            val clean = rawLine.trimEnd('\r', '\n')
            if (clean.isEmpty() || clean.length > MAX_LINE_CHARS) {
                return FilterDecision.Denied(500, "Malformed or oversized control line")
            }
            if (clean.any { it.code < 0x20 }) {
                return FilterDecision.Denied(500, "Control characters refused")
            }

            val parts = clean.split(Regex("\\s+"))
            val verb = parts.first().uppercase()
            val args = parts.drop(1)

            if (verb == "QUIT") return FilterDecision.SyntheticReply("250 closing connection\r\n")
            if (verb == "PROTOCOLINFO") {
                return FilterDecision.SyntheticReply(
                    "250-PROTOCOLINFO 1\r\n250-AUTH METHODS=NULL,SAFECOOKIE\r\n250 OK\r\n",
                )
            }
            if (verb == "AUTHENTICATE") {
                isAuthenticated = true
                return FilterDecision.SyntheticReply("250 OK\r\n")
            }
            if (!isAuthenticated) {
                return FilterDecision.Denied(514, "Authentication required")
            }

            return when (verb) {
                "SIGNAL" -> {
                    if (args.size != 1) return FilterDecision.Denied(510, "Command filtered")
                    val sig = args[0].uppercase()
                    if (sig in profile.allowedSignals) FilterDecision.ForwardToTor("SIGNAL $sig")
                    else FilterDecision.Denied(510, "Command filtered")
                }
                "GETINFO" -> {
                    if (args.isEmpty()) return FilterDecision.Denied(510, "Command filtered")
                    if (args.any { it !in profile.allowedGetInfoKeys }) {
                        return FilterDecision.Denied(510, "Command filtered")
                    }
                    // If all requested keys have static synthetic rewrites, answer without touching Tor
                    if (args.all { it in profile.responseRewrites }) {
                        val body = buildString {
                            for (k in args) append("250-$k=${profile.responseRewrites.getValue(k)}\r\n")
                            append("250 OK\r\n")
                        }
                        FilterDecision.SyntheticReply(body)
                    } else {
                        FilterDecision.ForwardToTor("GETINFO ${args.joinToString(" ")}")
                    }
                }
                "SETEVENTS" -> {
                    val requested = args.map { it.uppercase() }
                    if (requested.any { it !in profile.allowedEvents }) {
                        return FilterDecision.Denied(510, "Command filtered")
                    }
                    subscribedEvents.clear()
                    subscribedEvents.addAll(requested)
                    FilterDecision.ForwardToTor("SETEVENTS ${requested.joinToString(" ")}".trim())
                }
                else -> FilterDecision.Denied(510, "Command filtered")
            }
        }

        /** Filters asynchronous `650 ...` Tor daemon event lines before delivery to the client. */
        fun allowAsyncTorEvent(rawEventLine: String): Boolean {
            if (!isAuthenticated) return false
            val clean = rawEventLine.trimEnd('\r', '\n')
            if (!clean.startsWith("650 ") && !clean.startsWith("650-")) return false
            val tokens = clean.substring(4).split(Regex("\\s+"))
            val eventType = tokens.firstOrNull()?.uppercase() ?: return false
            if (eventType !in subscribedEvents) return false
            if (eventType == "STREAM" && profile.restrictStreamEvents) {
                val srcAttr = tokens.firstOrNull { it.startsWith("SOURCE_ADDR=", ignoreCase = true) }
                    ?: return false
                val addrPort = srcAttr.substringAfter('=')
                val srcIp = addrPort.substringBeforeLast(':')
                return srcIp == profile.clientSourceIp
            }
            return true
        }
    }

    /** Preset mirroring Tails Onion Circuits viewer (`GETINFO circuit-status`, `STREAM`/`CIRC`). */
    fun onionCircuitsProfile(clientIp: String = "127.0.0.1", isolatedSocks: String = "127.0.0.1:9050"): AppProfile =
        AppProfile(
            appId = "onion-circuits",
            allowedSignals = setOf("NEWNYM"),
            allowedGetInfoKeys = setOf("circuit-status", "stream-status", "net/listeners/socks"),
            allowedEvents = setOf("CIRC", "STREAM"),
            responseRewrites = mapOf("net/listeners/socks" to "\"$isolatedSocks\""),
            restrictStreamEvents = true,
            clientSourceIp = clientIp,
        )
}

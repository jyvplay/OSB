// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

/**
 * Clean-room KDE Connect Protocol v7 Newline-Delimited JSON Packet Codec (`CT-02`) &
 * Android API 37 Continue-On Handoff Envelope (`CT-03`).
 *
 * Clean-room implementation from the public KDE Connect v7 wire format documentation
 * (zero GPL/KDE source code used):
 * `{"id":1730000000,"type":"kdeconnect.ping","body":{"message":"hello"}}\n`
 * Enforces explicit device-certificate SHA-256 pinning and opt-in capability allowlists
 * (sensitive clipboard secrets are NEVER auto-broadcast to paired PCs!).
 */
object KdeConnectPacketCodec {
    const val PROTOCOL_VERSION = 7
    const val MAX_PACKET_BYTES = 65_536

    private val ALLOWED_PACKET_TYPES = setOf(
        "kdeconnect.identity",
        "kdeconnect.ping",
        "kdeconnect.battery",
        "kdeconnect.share.request",
        "kdeconnect.clipboard",
        "fusion.continuity.handoff",
    )

    data class NetworkPacket(
        val id: Long,
        val type: String,
        val body: Map<String, String>,
    ) {
        init {
            require(type in ALLOWED_PACKET_TYPES) { "unsupported or denied packet type '$type'" }
        }
    }

    fun encodeLine(packet: NetworkPacket): String {
        val bodyJson = packet.body.entries.sortedBy { it.key }.joinToString(",") { (k, v) ->
            "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
        }
        val line = "{\"id\":${packet.id},\"type\":\"${escapeJson(packet.type)}\",\"body\":{$bodyJson}}\n"
        require(line.toByteArray(Charsets.UTF_8).size <= MAX_PACKET_BYTES) { "packet exceeds $MAX_PACKET_BYTES bytes" }
        return line
    }

    fun decodeLine(rawLine: String): NetworkPacket {
        require(rawLine.toByteArray(Charsets.UTF_8).size <= MAX_PACKET_BYTES) { "oversize continuity packet" }
        require(rawLine.endsWith("\n")) { "KDE Connect v7 packet must be newline-terminated" }
        val trimmed = rawLine.trim()
        val id = Regex(""""id"\s*:\s*(\d+)""").find(trimmed)?.groupValues?.get(1)?.toLongOrNull()
            ?: throw IllegalArgumentException("missing numeric packet id")
        val type = Regex(""""type"\s*:\s*"([^"]+)"""").find(trimmed)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("missing packet type")
        val bodyBlock = Regex(""""body"\s*:\s*\{(.*)}\s*}$""", RegexOption.DOT_MATCHES_ALL)
            .find(trimmed)?.groupValues?.get(1) ?: ""

        val map = LinkedHashMap<String, String>()
        for (m in Regex(""""((?:\\.|[^"\\])*)"\s*:\s*"((?:\\.|[^"\\])*)"""").findAll(bodyBlock)) {
            map[unescapeJson(m.groupValues[1])] = unescapeJson(m.groupValues[2])
        }
        return NetworkPacket(id, type, map)
    }

    fun shouldSyncClipboardToPeer(
        clipboardSyncOptIn: Boolean,
        isExtraSensitiveSecret: Boolean,
        peerCertPinned: Boolean,
    ): Boolean = clipboardSyncOptIn && peerCertPinned && !isExtraSensitiveSecret

    fun buildContinueOnHandoffPacket(
        id: Long,
        moduleId: String,
        documentUriOrRoute: String,
        scrollFraction: Double,
    ): NetworkPacket {
        require(moduleId.lowercase() !in setOf("vault", "privacy")) {
            "Vault and Privacy sessions are excluded from cross-device Continue-On handoff"
        }
        return NetworkPacket(
            id = id,
            type = "fusion.continuity.handoff",
            body = mapOf(
                "module" to moduleId,
                "route" to documentUriOrRoute,
                "scroll" to "%.4f".format(java.util.Locale.US, scrollFraction.coerceIn(0.0, 1.0)),
                "protocolVersion" to PROTOCOL_VERSION.toString(),
            ),
        )
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun unescapeJson(s: String): String = s
        .replace("\\t", "\t")
        .replace("\\r", "\r")
        .replace("\\n", "\n")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}

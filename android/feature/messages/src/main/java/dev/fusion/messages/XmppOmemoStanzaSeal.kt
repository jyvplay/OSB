// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

import java.util.Base64

/**
 * Clean-room `XEP-0384` OMEMO (`urn:xmpp:omemo:2`) Multi-Device Encrypted Stanza Builder &
 * XXE-Safe Parser (`MS-01`).
 */
object XmppOmemoStanzaSeal {
    const val SCOPE =
        "Pure-JVM XEP-0384 urn:xmpp:omemo:2 multi-device encrypted stanza serializer/parser with strict DOCTYPE/XXE refusal (MS-01)."

    const val OMEMO_NS = "urn:xmpp:omemo:2"

    data class RecipientKeySlot(val recipientJid: String, val deviceId: Int, val wrappedKeyAndTagB64: String, val preKey: Boolean = false)

    data class OmemoEnvelope(
        val senderDeviceId: Int,
        val keys: List<RecipientKeySlot>,
        val payloadCiphertextB64: String,
    )

    fun serializeStanza(env: OmemoEnvelope): String {
        require(env.senderDeviceId > 0 && env.keys.isNotEmpty() && env.payloadCiphertextB64.isNotBlank()) {
            "invalid OMEMO envelope parameters"
        }
        Base64.getDecoder().decode(env.payloadCiphertextB64)
        val byJid = env.keys.groupBy { it.recipientJid }
        return buildString {
            append("<encrypted xmlns='$OMEMO_NS'>")
            append("<header sid='${env.senderDeviceId}'>")
            for ((jid, slots) in byJid) {
                append("<keys jid='${escXml(jid)}'>")
                for (slot in slots) {
                    Base64.getDecoder().decode(slot.wrappedKeyAndTagB64)
                    val kexAttr = if (slot.preKey) " kex='true'" else ""
                    append("<key rid='${slot.deviceId}'$kexAttr>${slot.wrappedKeyAndTagB64}</key>")
                }
                append("</keys>")
            }
            append("</header>")
            append("<payload>${env.payloadCiphertextB64}</payload>")
            append("</encrypted>")
        }
    }

    fun parseStanza(xml: String): OmemoEnvelope {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) {
            "XXE / DOCTYPE declaration forbidden in XMPP OMEMO stanza"
        }
        require(OMEMO_NS in xml) { "missing urn:xmpp:omemo:2 namespace" }
        val sid = Regex("""<header\s+sid=['"](\d+)['"]>""").find(xml)?.groupValues?.get(1)?.toInt()
            ?: throw IllegalArgumentException("missing OMEMO header sid")
        val payload = Regex("""<payload>([A-Za-z0-9+/=]+)</payload>""").find(xml)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("missing OMEMO payload")
        val slots = ArrayList<RecipientKeySlot>()
        val keysGroupRe = Regex("""<keys\s+jid=['"]([^'"]+)['"]>(.*?)</keys>""")
        val keyItemRe = Regex("""<key\s+rid=['"](\d+)['"](?:\s+kex=['"](true|false)['"])?>([A-Za-z0-9+/=]+)</key>""")
        for (gm in keysGroupRe.findAll(xml)) {
            val jid = gm.groupValues[1]
            for (km in keyItemRe.findAll(gm.groupValues[2])) {
                slots.add(RecipientKeySlot(jid, km.groupValues[1].toInt(), km.groupValues[3], km.groupValues[2] == "true"))
            }
        }
        require(slots.isNotEmpty()) { "OMEMO stanza carries zero recipient key slots" }
        return OmemoEnvelope(sid, slots, payload)
    }

    private fun escXml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace("'", "&apos;")
}

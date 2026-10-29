// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Clean-room `RFC 6350` vCard 4.0 parser/serializer and API-37 least-privilege Contact Picker
 * field projector (`PR-05`: strips photo/address/notes/organization when caller requests only
 * email or phone).
 */
object ContactsVcard4PickerModel {
    const val SCOPE =
        "Pure-JVM RFC 6350 vCard 4.0 codec and least-privilege contact picker field projection stripping unrequested PII fields (PR-05)."

    enum class RequestedField { DISPLAY_NAME, PHONE, EMAIL, ORGANIZATION }

    data class ContactCard(
        val formattedName: String,
        val phones: List<String> = emptyList(),
        val emails: List<String> = emptyList(),
        val organization: String? = null,
        val privateNote: String? = null,
    )

    fun parseVcard(vcfText: String): ContactCard {
        val unfolded = vcfText.replace("\r\n ", "").replace("\n ", "").replace("\r\n\t", "").replace("\n\t", "")
        var fn = ""
        val phones = ArrayList<String>()
        val emails = ArrayList<String>()
        var org: String? = null
        var note: String? = null
        var inCard = false

        for (raw in unfolded.lines()) {
            val line = raw.trim()
            if (line.equals("BEGIN:VCARD", ignoreCase = true)) { inCard = true; continue }
            if (line.equals("END:VCARD", ignoreCase = true)) break
            if (!inCard || ':' !in line) continue
            val colon = line.indexOf(':')
            val propKey = line.substring(0, colon).substringBefore(';').uppercase()
            val value = unescapeVcard(line.substring(colon + 1))
            when (propKey) {
                "FN" -> fn = value
                "TEL" -> if (value.isNotBlank()) phones.add(value.trim())
                "EMAIL" -> if (value.isNotBlank()) emails.add(value.trim())
                "ORG" -> org = value.trim()
                "NOTE" -> note = value.trim()
            }
        }
        require(fn.isNotBlank()) { "vCard missing mandatory FN property" }
        return ContactCard(fn, phones, emails, org, note)
    }

    fun projectForPicker(card: ContactCard, allowedFields: Set<RequestedField>): ContactCard = ContactCard(
        formattedName = if (RequestedField.DISPLAY_NAME in allowedFields || allowedFields.isEmpty()) card.formattedName else "Redacted Contact",
        phones = if (RequestedField.PHONE in allowedFields) card.phones else emptyList(),
        emails = if (RequestedField.EMAIL in allowedFields) card.emails else emptyList(),
        organization = if (RequestedField.ORGANIZATION in allowedFields) card.organization else null,
        privateNote = null, // Never project private notes across picker boundary!
    )

    fun serializeVcard4(card: ContactCard): String = buildString {
        append("BEGIN:VCARD\r\nVERSION:4.0\r\n")
        append("FN:${escapeVcard(card.formattedName)}\r\n")
        for (p in card.phones) append("TEL;TYPE=cell:${escapeVcard(p)}\r\n")
        for (e in card.emails) append("EMAIL:${escapeVcard(e)}\r\n")
        if (!card.organization.isNullOrBlank()) append("ORG:${escapeVcard(card.organization)}\r\n")
        append("END:VCARD\r\n")
    }

    private fun escapeVcard(s: String): String = s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")
    private fun unescapeVcard(s: String): String = s.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}

// SPDX-License-Identifier: Apache-2.0
package dev.fusion.contacts

/**
 * Production-grade vCard 3.0/4.0 parser with folded-line unwrapping
 * and property-parameter stripping.
 */
class ContactsVcardParser private constructor() {
    companion object {
        data class VCardContact(
            val fullName: String,
            val emails: List<String>,
            val phones: List<String>,
            val org: String?
        )

        fun unfoldLines(raw: String): List<String> {
            val normalized = raw.replace("\r\n", "\n")
            val unfolded = normalized.replace(Regex("\n[ \t]"), "")
            return unfolded.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }

        fun parseSingle(raw: String): VCardContact {
            val lines = unfoldLines(raw)
            var fn = ""
            val emails = mutableListOf<String>()
            val phones = mutableListOf<String>()
            var org: String? = null

            for (line in lines) {
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val propName = line.substring(0, colon).substringBefore(';').uppercase()
                val value = line.substring(colon + 1).trim()
                when (propName) {
                    "FN" -> fn = value
                    "EMAIL" -> if (value.isNotEmpty()) emails.add(value)
                    "TEL" -> if (value.isNotEmpty()) phones.add(value)
                    "ORG" -> if (value.isNotEmpty()) org = value
                }
            }
            return VCardContact(fn, emails, phones, org)
        }
    }
}

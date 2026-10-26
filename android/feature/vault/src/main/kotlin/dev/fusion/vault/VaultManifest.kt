// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

import java.security.MessageDigest

/** One vault item's metadata. The sealed bytes live with VaultEnvelope. */
data class VaultItem(val id: String, val name: String, val bytes: Long, val sha256: String)

/**
 * Vault item manifest: validated descriptors with add/remove, totals, and a
 * canonical SHA-256 digest over id-sorted `id|name|bytes|sha` lines. Names
 * carry no separators (no traversal); digests are lowercase hex. This is
 * metadata only — encryption stays with VaultEnvelope.
 */
object VaultManifest {
    const val MAX_ITEMS = 4096
    const val MAX_TOTAL_BYTES = 1L shl 40
    const val MAX_NAME = 128
    private val ID = Regex("[a-z0-9][a-z0-9._\\-]{0,63}")
    private val SHA = Regex("[0-9a-f]{64}")

    fun add(items: List<VaultItem>, item: VaultItem): List<VaultItem> {
        checkItem(item)
        require(items.none { it.id == item.id }) { "duplicate id ${item.id}" }
        require(items.size < MAX_ITEMS) { "over $MAX_ITEMS items" }
        val out = items + item
        require(totalBytes(out) <= MAX_TOTAL_BYTES) { "over $MAX_TOTAL_BYTES total bytes" }
        return out
    }

    fun remove(items: List<VaultItem>, id: String): List<VaultItem> {
        require(ID.matches(id)) { "bad id" }
        require(items.any { it.id == id }) { "unknown id $id" }
        return items.filterNot { it.id == id }
    }

    fun totalBytes(items: List<VaultItem>): Long = items.sumOf { it.bytes }

    /** Lowercase hex SHA-256 over the canonical id-sorted rendering. */
    fun digest(items: List<VaultItem>): String {
        items.forEach(::checkItem)
        val canonical = items.sortedBy { it.id }
            .joinToString("\n") { "${it.id}|${it.name}|${it.bytes}|${it.sha256}" }
        val sum = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return sum.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun checkItem(item: VaultItem) {
        require(ID.matches(item.id)) { "bad id ${item.id}" }
        require(item.name.length in 1..MAX_NAME && '/' !in item.name && '\u0000' !in item.name) {
            "bad name"
        }
        require(item.bytes >= 0) { "negative bytes" }
        require(SHA.matches(item.sha256)) { "bad sha256" }
    }
}

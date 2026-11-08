// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/** An in-memory confirmation is valid only for the exact document and edit revision reviewed. */
class WriterCopyConsent(private val snapshot: TextDocument, private val revision: Long) {
    init { require(revision >= 0) { "Invalid draft revision" } }
    val documentId: String get() = snapshot.id
    fun permits(current: TextDocument, currentRevision: Long): Boolean = snapshot == current && revision == currentRevision
}

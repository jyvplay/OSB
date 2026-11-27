// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security.diagnostics

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * R176 review/picker/write/readback protocol, independent of Android and collectors.
 * One instance owns one operation. No payload/URI persistence, network or global
 * singleton state. Retain the instance across configuration changes, not process
 * death: a recreated instance MUST refuse an old picker result before opening it.
 *
 * A ticket binds a reviewed immutable snapshot, not human identity. Destination
 * opening is deferred until the ticket is consumed exactly once. Stream adapters
 * are real caller-owned providers; readback verifies bytes, not cloud durability.
 * Untrusted providers can block or lie; no timeout/durability promise is made.
 */
class DiagnosticSaveWorkflow {
    enum class Phase { EMPTY, REVIEW, PICKING, WRITING, SAVED, FAILED }
    class Ticket internal constructor(val sequence: Long, val sha256: String)
    data class Snapshot(val phase: Phase, val review: String?, val sha256: String?,
                        val utf8Bytes: Int, val message: String)
    interface Destination {
        fun openOutput(): OutputStream
        fun openInput(): InputStream
    }

    private var phase = Phase.EMPTY
    private var review: DiagnosticExportSession.Review? = null
    private var ticket: Ticket? = null
    private var sequence = 0L
    private var message = "Select fields, then prepare a review."

    @Synchronized fun snapshot(): Snapshot = Snapshot(phase, review?.text, review?.sha256,
        review?.utf8Bytes ?: 0, message)

    @Synchronized fun prepare(allow: String, records: List<Pair<String, String>>) {
        require(phase != Phase.PICKING && phase != Phase.WRITING) { "save in progress" }
        // Invalidate the old review BEFORE parsing. A failed replacement must
        // never leave the previous digest available for accidental approval.
        review = null; ticket = null; phase = Phase.EMPTY
        message = "Input refused; prepare a new review."
        review = DiagnosticExportSession.prepare(allow, records)
        phase = Phase.REVIEW
        message = "Read every line. Redaction is heuristic, not anonymity."
    }

    @Synchronized fun discard() {
        require(phase != Phase.WRITING) { "cannot discard an active provider write" }
        review = null; ticket = null; phase = Phase.EMPTY
        message = "Review discarded. No saved draft."
    }

    @Synchronized fun beginSave(approvedSha256: String): Ticket {
        require(phase == Phase.REVIEW) { "prepare and approve a review first" }
        val current = requireNotNull(review)
        require(approvedSha256 == current.sha256) { "stale approval" }
        require(sequence < Long.MAX_VALUE) { "ticket sequence exhausted" }
        val issued = Ticket(++sequence, current.sha256)
        ticket = issued; phase = Phase.PICKING
        message = "Choose a destination. Cloud providers may upload this report."
        return issued
    }

    @Synchronized fun cancelPicker(issued: Ticket) {
        require(phase == Phase.PICKING && ticket == issued) { "stale picker result" }
        ticket = null; phase = Phase.REVIEW
        message = "Save cancelled; no output was opened. Review again before saving."
    }

    /**
     * Blocking provider work: MUST run off the Android main thread. Exactly one
     * caller can claim a ticket. On an exception return a generic failure and
     * drop the snapshot; destination may contain partial bytes. Never delete an
     * arbitrary provider document in recovery or infer an atomic transaction.
     */
    fun save(issued: Ticket, destination: Destination): Boolean {
        val frozen = synchronized(this) {
            require(phase == Phase.PICKING && ticket == issued) { "stale picker result" }
            val current = requireNotNull(review)
            require(current.sha256 == issued.sha256) { "review changed" }
            phase = Phase.WRITING; ticket = null
            message = "Writing and reading back the chosen document…"
            current
        }
        return try {
            val archive = ByteArrayOutputStream().use {
                frozen.writeApproved(issued.sha256, it)
                it.toByteArray()
            }
            require(archive.size <= MAX_ARCHIVE_BYTES) { "archive too large" }
            destination.openOutput().use { it.write(archive) }
            // Bounded streaming comparison, including trailing data. Provider
            // bytes must match the entire ZIP, not just the diagnostics digest.
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0
            destination.openInput().use { input ->
                val chunk = ByteArray(8192)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    require(count > 0) { "provider made no read progress" }
                    require(count <= archive.size - total) { "provider returned extra bytes" }
                    digest.update(chunk, 0, count); total += count
                }
            }
            require(total == archive.size) { "truncated readback" }
            require(MessageDigest.isEqual(digest.digest(), MessageDigest.getInstance("SHA-256").digest(archive))) {
                "readback differs"
            }
            synchronized(this) {
                phase = Phase.SAVED; review = null
                message = "Saved and byte-checked through the selected provider. Not a cloud durability receipt."
            }
            true
        } catch (_: Exception) {
            synchronized(this) {
                phase = Phase.FAILED; review = null
                message = "Save/readback failed. A partial document may remain; inspect it in the provider. No automatic retry."
            }
            false
        }
    }

    companion object {
        // Selected values <=256*1024 UTF-16 units; even worst-case UTF-8 plus
        // ZIP framing is below this limit. This is a refusal cap, not telemetry.
        const val MAX_ARCHIVE_BYTES = 2 * 1024 * 1024
    }
}

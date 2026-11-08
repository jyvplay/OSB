// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-memory, one-attempt export. A late picker result cannot consume a different attempt's
 * payload; a process restart loses the attempt instead of replaying an external write.
 * It is not a provider lease, a durable receipt, or an exactly-once I/O guarantee.
 */
class FrozenExport(val requestId: String, bytes: ByteArray, forbiddenUris: List<String>) {
    private val payload = bytes.copyOf()
    private val consumed = AtomicBoolean(false)
    val forbiddenUris: List<String> = Collections.unmodifiableList(ArrayList(forbiddenUris))

    init {
        require(CopyProtocol.validRequestId(requestId)) { "Invalid export request identity" }
        require(payload.size <= TextBytes.MAX_BYTES) { "Export exceeds the copy limit" }
        require(forbiddenUris.size <= 64 && forbiddenUris.all { it.length <= 8192 })
    }

    /** Return a private copy only once, and only for the same request ID. */
    fun claim(id: String): ByteArray? =
        if (id == requestId && consumed.compareAndSet(false, true)) payload.copyOf() else null

    /** Cancellation consumes this attempt without opening any output. */
    fun cancel(id: String): Boolean = id == requestId && consumed.compareAndSet(false, true)
}

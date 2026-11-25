// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Bounded monotonic 64-bit ID generator with overflow guard.
 */
class ModelMonotonicIdGen private constructor(private var nextId: Long) {
    companion object {
        fun startingAt(initial: Long = 1L): ModelMonotonicIdGen {
            require(initial >= 0L) { "negative initial id" }
            return ModelMonotonicIdGen(initial)
        }
    }

    fun next(): Long {
        val id = nextId
        require(id < Long.MAX_VALUE) { "ID space exhausted" }
        nextId = id + 1L
        return id
    }
}

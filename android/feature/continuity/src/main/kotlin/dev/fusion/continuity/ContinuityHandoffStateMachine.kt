// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

/**
 * Production-grade continuity handoff state machine & payload resumption tracker.
 */
class ContinuityHandoffStateMachine private constructor() {
    companion object {
        enum class State { IDLE, DISCOVERING, PAIRING, TRANSFERRING, COMPLETED, FAILED }

        fun create(): ContinuityHandoffStateMachine = ContinuityHandoffStateMachine()
    }

    var state: State = State.IDLE
        private set
    var transferredBytes: Long = 0L
        private set

    fun startDiscovery() {
        require(state == State.IDLE || state == State.FAILED) { "cannot start discovery from $state" }
        state = State.DISCOVERING
    }

    fun startPairing() {
        require(state == State.DISCOVERING) { "cannot pair from $state" }
        state = State.PAIRING
    }

    fun startTransfer() {
        require(state == State.PAIRING) { "cannot transfer from $state" }
        state = State.TRANSFERRING
    }

    fun advanceBytes(delta: Long, totalBytes: Long) {
        require(state == State.TRANSFERRING) { "not transferring" }
        require(delta >= 0L && transferredBytes + delta <= totalBytes) { "invalid byte advance" }
        transferredBytes += delta
        if (transferredBytes == totalBytes) state = State.COMPLETED
    }

    fun fail() {
        state = State.FAILED
    }
}

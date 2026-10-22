// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

/**
 * Cast media player-state machine (transition validation).
 *
 * Sits next to [CastMediaMeta] (LOAD-record validation): the client stores
 * the wire's `playerState` as an unvalidated String, so nothing answers
 * "is PAUSED→PLAYING legal, and what does STOP do from BUFFERING". The
 * four Cast states (IDLE, BUFFERING, PLAYING, PAUSED) plus six events
 * (LOAD, BUFFER_OK, PLAY, PAUSE, STOP, FINISH) form a closed transition
 * table; illegal pairs (PAUSE from IDLE, BUFFER_OK from PAUSED) are
 * refused rather than ignored, because a swallowed illegal event would
 * desynchronize the UI from the receiver. [parse] grounds wire strings to
 * states, refusing unknown names — the wire may add states, but this
 * kernel will not guess what they mean.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object CastState {

    /** Player states (Cast protocol names). */
    enum class State { IDLE, BUFFERING, PLAYING, PAUSED }

    /** State-machine events. */
    enum class Event { LOAD, BUFFER_OK, PLAY, PAUSE, STOP, FINISH }

    private val TABLE: Map<Pair<State, Event>, State> = mapOf(
        (State.IDLE to Event.LOAD) to State.BUFFERING,
        (State.IDLE to Event.PLAY) to State.PLAYING,
        (State.BUFFERING to Event.BUFFER_OK) to State.PLAYING,
        (State.BUFFERING to Event.STOP) to State.IDLE,
        (State.PLAYING to Event.PAUSE) to State.PAUSED,
        (State.PLAYING to Event.LOAD) to State.BUFFERING,
        (State.PLAYING to Event.STOP) to State.IDLE,
        (State.PLAYING to Event.FINISH) to State.IDLE,
        (State.PAUSED to Event.PLAY) to State.PLAYING,
        (State.PAUSED to Event.LOAD) to State.BUFFERING,
        (State.PAUSED to Event.STOP) to State.IDLE,
    )

    /**
     * Grounds a wire `playerState` string. Case-sensitive, per protocol.
     *
     * @throws IllegalArgumentException on an unknown state name.
     */
    fun parse(wire: String): State = when (wire) {
        "IDLE" -> State.IDLE
        "BUFFERING" -> State.BUFFERING
        "PLAYING" -> State.PLAYING
        "PAUSED" -> State.PAUSED
        else -> throw IllegalArgumentException("unknown state $wire")
    }

    /**
     * Applies [event] to [state].
     *
     * @throws IllegalArgumentException on an illegal transition.
     */
    fun next(state: State, event: Event): State =
        TABLE[state to event] ?: throw IllegalArgumentException("illegal $state + $event")
}

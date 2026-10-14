package dev.fusion.windowing

/** One trusted, ordered producer per epoch. Identity is not authentication. */
data class DesktopCommand(val epoch: String, val sequence: Long, val command: WindowCommand)
enum class CommandDisposition { CONSUMED, ALREADY_CONSUMED, WRONG_EPOCH, GAP, INVALID_SEQUENCE, UNINITIALIZED }
data class DesktopTransition(val state: DesktopState, val disposition: CommandDisposition)

/**
 * Bounded at-most-once reduction for an ordered command stream, not arbitrary-key deduplication.
 * The producer retains and retries the SAME envelope. Re-enveloping an old action is a new action.
 * Sequence numbers never wrap; no future command is accepted across a gap. Consumed no-ops also
 * advance the watermark. Previously consumed payloads are not retained or compared for equality.
 *
 * The epoch and watermark are checkpointed with geometry. Effects are pure state transitions:
 * this does not provide durable acknowledgement, a multi-writer log or exactly-once external IO.
 * A checkpoint rollback loses the uncheckpointed suffix. Input lists must not mutate concurrently.
 */
object DesktopCommandStream {
    private val epochPattern = Regex("[A-Za-z0-9_-]{1,80}")

    internal fun validateMetadata(state: DesktopState) {
        require(state.lastSequence >= 0) { "Negative command watermark" }
        val epoch = state.commandEpoch
        require(if (epoch == null) state.lastSequence == 0L else epochPattern.matches(epoch)) {
            "Invalid command epoch/watermark pair"
        }
    }

    /** Caller supplies a new identity when creating/resetting a desktop, never from a clock here. */
    fun start(state: DesktopState, epoch: String): DesktopState {
        validateMetadata(state)
        require(state.commandEpoch == null && epochPattern.matches(epoch)) { "Cannot reseed an initialized stream" }
        return WindowEngine.restore(state.copy(commandEpoch = epoch, lastSequence = 0))
    }

    fun next(state: DesktopState, command: WindowCommand): DesktopCommand {
        validateMetadata(state)
        val epoch = checkNotNull(state.commandEpoch) { "Command stream is uninitialized" }
        check(state.lastSequence < Long.MAX_VALUE) { "Command sequence exhausted; explicitly reset the desktop" }
        return DesktopCommand(epoch, state.lastSequence + 1, command)
    }

    fun accept(state: DesktopState, envelope: DesktopCommand): DesktopTransition {
        // Detach caller-owned lists even on a rejected/no-op path.
        val owned = WindowEngine.restore(state)
        val disposition = when {
            owned.commandEpoch == null -> CommandDisposition.UNINITIALIZED
            envelope.epoch != owned.commandEpoch -> CommandDisposition.WRONG_EPOCH
            envelope.sequence <= 0 -> CommandDisposition.INVALID_SEQUENCE
            envelope.sequence <= owned.lastSequence -> CommandDisposition.ALREADY_CONSUMED
            owned.lastSequence == Long.MAX_VALUE || envelope.sequence != owned.lastSequence + 1 -> CommandDisposition.GAP
            else -> CommandDisposition.CONSUMED
        }
        val next = if (disposition == CommandDisposition.CONSUMED)
            WindowEngine.reduce(owned, envelope.command).copy(lastSequence = envelope.sequence)
        else owned
        return DesktopTransition(next, disposition)
    }
}

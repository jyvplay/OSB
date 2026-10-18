package dev.fusion.security

/** Event-stream policy, not event authenticity. Android must supply the real obscuration flags.
 * A rejected gesture cannot resume on a clean UP/MOVE after a view is replaced.
 */
class TouchAdmission {
    enum class Phase { DOWN, CONTINUE, UP, CANCEL, OTHER }
    private var active = false

    fun reset() { active = false }

    fun admit(phase: Phase, fullyObscured: Boolean, partlyObscured: Boolean): Boolean {
        if (fullyObscured || partlyObscured) { active = false; return false }
        if (phase == Phase.DOWN) { active = true; return true }
        val accepted = active && phase != Phase.OTHER
        if (phase == Phase.UP || phase == Phase.CANCEL || phase == Phase.OTHER) active = false
        return accepted
    }
}

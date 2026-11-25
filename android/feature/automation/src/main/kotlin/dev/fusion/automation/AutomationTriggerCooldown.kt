// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

/**
 * Bounded automation rule cooldown tracker.
 */
class AutomationTriggerCooldown private constructor(private val cooldownMs: Long) {
    companion object {
        const val MAX_RULES = 512

        fun create(cooldownMs: Long): AutomationTriggerCooldown {
            require(cooldownMs >= 0L) { "negative cooldownMs" }
            return AutomationTriggerCooldown(cooldownMs)
        }
    }

    private val lastFired = HashMap<String, Long>()

    fun tryFire(ruleId: String, nowMs: Long): Boolean {
        require(ruleId.isNotEmpty()) { "empty ruleId" }
        require(nowMs >= 0L) { "negative nowMs" }
        val prev = lastFired[ruleId]
        if (prev != null && nowMs - prev < cooldownMs) return false
        require(lastFired.size < MAX_RULES || lastFired.containsKey(ruleId)) { "too many rules" }
        lastFired[ruleId] = nowMs
        return true
    }
}

package dev.fusion.settingsui

/**
 * Clean-room 20-Vector Tails-Light & Hardware-Shield Privacy Posture Scorecard Calculator (Apache-2.0).
 */
object PrivacyPostureScorecardCalculator {
    const val SCOPE =
        "Weighted 20-vector Tails-Light & hardware-defense (V1..V20) posture scorecard + prioritized remediation ranker"

    data class DefenseControl(val vectorId: String, val label: String, val weight: Int, val enabled: Boolean)

    data class PostureSummary(
        val score0To100: Int,
        val grade: String,
        val activeVectorCount: Int,
        val totalVectorCount: Int,
        val topRemediations: List<String>,
    )

    fun evaluate(controls: List<DefenseControl>): PostureSummary {
        require(controls.isNotEmpty() && controls.all { it.weight > 0 })
        val totalW = controls.sumOf { it.weight }
        val earnedW = controls.filter { it.enabled }.sumOf { it.weight }
        val score = ((earnedW * 100.0) / totalW).toInt().coerceIn(0, 100)
        val grade = when {
            score >= 95 -> "TAILS_LIGHT_HARDENED"
            score >= 80 -> "STRONG"
            score >= 60 -> "MODERATE"
            else -> "EXPOSED"
        }
        val missing = controls.filter { !it.enabled }
            .sortedByDescending { it.weight }
            .map { "${it.vectorId}: ${it.label}" }
        return PostureSummary(score, grade, controls.count { it.enabled }, controls.size, missing)
    }
}

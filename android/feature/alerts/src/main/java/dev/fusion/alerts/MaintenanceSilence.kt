// SPDX-License-Identifier: Apache-2.0
package dev.fusion.alerts

/**
 * Clean-room Prometheus Alertmanager-style label-matcher maintenance silence evaluator (`MaintenanceSilence`).
 *
 * Powers `:feature:monitor` scheduled maintenance windows so planned backup/offline-sync tasks never
 * trigger spurious battery/IO alert storms while urgent security alerts remain unsilenced!
 */
object MaintenanceSilence {
    const val SCOPE =
        "Label-matcher alert silence engine (=, !=, =~ glob, !~ negated glob, [startsAtMs, endsAtMs) active window, mandatory author/comment audit); cluster gossip replication out of scope."

    enum class MatchOp { EQ, NEQ, GLOB_EQ, GLOB_NEQ }

    data class LabelMatcher(val name: String, val op: MatchOp, val pattern: String) {
        init {
            require(name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))) { "Invalid label name: $name" }
        }

        fun matches(labels: Map<String, String>): Boolean {
            val actual = labels[name] ?: ""
            return when (op) {
                MatchOp.EQ -> actual == pattern
                MatchOp.NEQ -> actual != pattern
                MatchOp.GLOB_EQ -> simpleGlob(pattern, actual)
                MatchOp.GLOB_NEQ -> !simpleGlob(pattern, actual)
            }
        }
    }

    data class SilenceRule(
        val id: String,
        val matchers: List<LabelMatcher>,
        val startsAtMs: Long,
        val endsAtMs: Long,
        val createdBy: String,
        val comment: String,
    ) {
        init {
            require(id.isNotBlank() && createdBy.isNotBlank() && comment.isNotBlank()) {
                "Silence rule requires non-blank id, createdBy, and comment"
            }
            require(matchers.isNotEmpty()) { "Silence rule requires at least one LabelMatcher" }
            require(endsAtMs > startsAtMs) { "endsAtMs ($endsAtMs) must be > startsAtMs ($startsAtMs)" }
        }

        fun isActiveAt(nowMs: Long): Boolean = nowMs in startsAtMs until endsAtMs
    }

    data class Evaluation(
        val silenced: Boolean,
        val matchedSilenceIds: List<String>,
    )

    fun evaluateAlert(
        alertLabels: Map<String, String>,
        silences: List<SilenceRule>,
        nowMs: Long,
    ): Evaluation {
        // Security invariant: severity=critical_security alerts can NEVER be muted by maintenance silences!
        if (alertLabels["severity"] == "critical_security") {
            return Evaluation(silenced = false, matchedSilenceIds = emptyList())
        }
        val hits = silences
            .filter { it.isActiveAt(nowMs) && it.matchers.all { m -> m.matches(alertLabels) } }
            .map { it.id }
        return Evaluation(silenced = hits.isNotEmpty(), matchedSilenceIds = hits)
    }

    private fun simpleGlob(pat: String, s: String): Boolean {
        val m = pat.length
        val n = s.length
        val dp = Array(m + 1) { BooleanArray(n + 1) }
        dp[m][n] = true
        for (i in m - 1 downTo 0) {
            for (j in n downTo 0) {
                dp[i][j] = when (pat[i]) {
                    '*' -> dp[i + 1][j] || (j < n && dp[i][j + 1])
                    '?' -> j < n && dp[i + 1][j + 1]
                    else -> j < n && pat[i] == s[j] && dp[i + 1][j + 1]
                }
            }
        }
        return dp[0][0]
    }
}

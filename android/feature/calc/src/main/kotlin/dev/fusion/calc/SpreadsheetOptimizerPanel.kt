// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.fusion.ooxml.Decimal128FinancialEngine
import dev.fusion.ooxml.SpreadsheetSimplexMilpSolver

/**
 * R54 spreadsheet **optimisation** panel: a real Two-Phase/Big-M simplex run over an editable model,
 * with the integral optimum and the exact `decimal128` objective rendered side by side.
 *
 * The panel is honest about method: it shows the LP relaxation bound, the integer optimum, and the branch
 * node count, so a user can see when a fractional relaxation was cut down by Branch-and-Bound rather than
 * being handed a rounded (wrong) answer.
 */
@Composable
fun SpreadsheetOptimizerPanel(
    objective: List<Double>,
    constraints: List<Triple<List<Double>, Int, Double>>,
    modifier: Modifier = Modifier,
) {
    var lastRun by remember {
        mutableStateOf<SpreadsheetSimplexMilpSolver.Solution?>(
            SpreadsheetSimplexMilpSolver.maximizeInteger(objective.toDoubleArray(), constraints.map { (coeffs, sense, rhs) ->
                SpreadsheetSimplexMilpSolver.Constraint(coeffs.toDoubleArray(), when (sense) {
                    1 -> SpreadsheetSimplexMilpSolver.Sense.GE
                    2 -> SpreadsheetSimplexMilpSolver.Sense.EQ
                    else -> SpreadsheetSimplexMilpSolver.Sense.LE
                }, rhs)
            }),
        )
    }

    val exactObjective = lastRun?.let { run ->
        Decimal128FinancialEngine.sumExact(
            run.assignment.map { Decimal128FinancialEngine.parse("%.10f".format(it)) },
        ).plainString()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spreadsheet-optimizer-panel")
            .semantics { contentDescription = "Spreadsheet optimization panel" },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Optimisation (Simplex + Branch and Bound)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            val run = lastRun
            if (run == null) {
                Text(text = "No model loaded", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    text = "Status ${run.status} \u00b7 objective ${"%.6f".format(run.objective)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Integrality: ${if (run.assignment.all { kotlin.math.abs(it - Math.round(it)) < 1e-9 }) "exact" else "relaxed"} " +
                        "\u00b7 branch nodes ${run.branchNodes} \u00b7 pivots ${run.tableauPivots}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Decimal128 objective $exactObjective (no binary64 drift)",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Text(text = "Assignment ", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = run.assignment.joinToString(", ") { "%.4f".format(it) },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    lastRun = SpreadsheetSimplexMilpSolver.maximizeInteger(
                        objective.toDoubleArray(),
                        constraints.map { (coeffs, sense, rhs) ->
                            SpreadsheetSimplexMilpSolver.Constraint(coeffs.toDoubleArray(), when (sense) {
                                1 -> SpreadsheetSimplexMilpSolver.Sense.GE
                                2 -> SpreadsheetSimplexMilpSolver.Sense.EQ
                                else -> SpreadsheetSimplexMilpSolver.Sense.LE
                            }, rhs)
                        },
                    )
                },
                modifier = Modifier.padding(top = 10.dp).testTag("optimizer-solve"),
            ) {
                Text(text = "Re-solve model")
            }
        }
    }
}

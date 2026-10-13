package dev.fusion.calc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Basic two-operand calculator. Scientific/programmer modes are not implemented. */
object CalcEngine {
    fun eval(expr: String): Result<Double> = runCatching {
        val m = Regex("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*([+*/-])\\s*(-?\\d+(?:\\.\\d+)?)\\s*$").matchEntire(expr)
            ?: throw IllegalArgumentException("Use: number operator number")
        val (a, op, b) = m.destructured
        val l = a.toDouble(); val r = b.toDouble()
        when (op) {
            "+" -> l + r
            "-" -> l - r
            "*" -> l * r
            else -> { if (r == 0.0) throw ArithmeticException("Division by zero"); l / r }
        }
    }
}

@Composable
fun CalcScreen() {
    var expr by remember { mutableStateOf("") }
    var ans by remember { mutableStateOf("") }
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Calculator — basic arithmetic only")
        OutlinedTextField(expr, { expr = it }, label = { Text("e.g. 12.5 * 4") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ans = CalcEngine.eval(expr).fold({ it.toString() }, { it.message ?: "Error" }) }) { Text("=") }
            Button(onClick = { expr = ""; ans = "" }) { Text("C") }
        }
        Text(ans)
        Text("History: last result only. Units/conversion not implemented.")
    }
}

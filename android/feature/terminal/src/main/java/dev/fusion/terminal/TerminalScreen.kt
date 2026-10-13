package dev.fusion.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TerminalScreen(shell: LocalShell) {
    val lines = remember { mutableStateListOf("Fusion local shell — type help", "Not a PTY. No Linux, no root.") }
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0E11)).padding(10.dp)) {
        LazyColumn(Modifier.weight(1f)) {
            items(lines) { Text(it, color = if (it.startsWith("$")) Color(0xFF65DDB4) else Color(0xFFD4D4D8), fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        }
        OutlinedTextField(input, { input = it }, Modifier.fillMaxSize(0.2f), singleLine = true, label = { Text("command") })
        androidx.compose.material3.Button(onClick = {
            val out = shell.run(input)
            if (out.firstOrNull() == "__clear__") lines.clear() else { lines.add("$ $input"); lines.addAll(out) }
            input = ""
        }) { Text("Run") }
    }
}

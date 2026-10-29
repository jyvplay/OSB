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
    var droppedOsc52 by remember { mutableStateOf(0) }
    var promptEvents by remember { mutableStateOf(0) }
    val paneLayoutSig = remember { TmuxPaneLayoutCodec.encode(TmuxPaneLayoutCodec.PaneNode.Leaf(80, 24, 0, 0, 0)).substringBefore(",") }
    val tablePreviewWidth = remember { MarkdownPipeTablePrettyFormatter.formatTable(listOf("|Cmd|Mode|", "|:---|---:|", "|help|local|")).first().length }
    val osc52ReadBlocked = remember { !TerminalOsc52ClipboardSanitizer.evaluateOsc52Sequence("\u001B]52;c;?\u0007").permitted }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0E11)).padding(10.dp)) {
        Text(
            "OSC 133 semantic prompts=$promptEvents • OSC 52 clipboard-exfil blocked=$droppedOsc52 (queryGuard=$osc52ReadBlocked) • tmux pane=$paneLayoutSig • GFM cols=$tablePreviewWidth",
            color = Color(0xFF94A3B8),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        LazyColumn(Modifier.weight(1f)) {
            items(lines) { Text(it, color = if (it.startsWith("$")) Color(0xFF65DDB4) else Color(0xFFD4D4D8), fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        }
        OutlinedTextField(input, { if (it.length <= 4096) input = it }, Modifier.fillMaxSize(0.2f), singleLine = true, label = { Text("command") })
        androidx.compose.material3.Button(onClick = {
            val rawOut = shell.run(input)
            if (rawOut.firstOrNull() == "__clear__") {
                lines.clear()
            } else {
                val parsed = PromptOsc133.parseStream(rawOut.joinToString("\n"))
                droppedOsc52 += parsed.droppedOsc52Count
                promptEvents += parsed.records.size
                lines.add("$ $input")
                lines.addAll(rawOut)
            }
            while (lines.size > 200) lines.removeAt(0)
            input = ""
        }) { Text("Run") }
    }
}

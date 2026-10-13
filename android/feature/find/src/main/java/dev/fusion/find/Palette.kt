package dev.fusion.find

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fusion.commands.CommandRegistry
import dev.fusion.index.LocalIndex

data class PaletteHit(val kind: String, val title: String, val sub: String, val key: String?)

/** Local-first palette: commands + local index. Provenance always shown. */
@Composable
fun PaletteBox(commands: CommandRegistry, index: LocalIndex, onOpenModule: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val cmdHits = remember(q) { commands.search(q, 6).map { PaletteHit("cmd", it.title, it.hint, null) } }
    val idxHits = remember(q) { if (q.length >= 2) index.search(q, 6).map { PaletteHit("local", it.doc.title, it.doc.source, null) } else emptyList() }
    val modules = remember(q) {
        listOf("files", "editor", "browser", "terminal", "vault", "notes", "settings", "media")
            .filter { q.isBlank() || it.contains(q, true) }.map { PaletteHit("app", it, "module", it) }
    }
    Column(Modifier.width(340.dp).padding(12.dp)) {
        OutlinedTextField(q, { q = it }, label = { Text("Find (local only)") }, modifier = Modifier.fillMaxWidth())
        (modules + cmdHits + idxHits).take(12).forEach { h ->
            Text("[${h.kind}] ${h.title} — ${h.sub}", Modifier.fillMaxWidth().clickable {
                if (h.key != null) onOpenModule(h.key)
            }.padding(8.dp))
        }
        Text("No web results. No private app data.", Modifier.padding(8.dp))
    }
}

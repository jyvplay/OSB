package dev.fusion.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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

@Composable
fun NotesScreen(store: NotesStore) {
    var names by remember { mutableStateOf(store.list()) }
    var active by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row {
            OutlinedTextField(active, { active = it }, Modifier.weight(1f), singleLine = true, label = { Text("Note name") })
            Button(onClick = {
                try { text = store.load(active); status = "Loaded." } catch (e: Exception) { status = e.message ?: "Failed" }
            }) { Text("Open") }
        }
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().weight(1f), label = { Text("Markdown") })
        Row {
            Button(onClick = {
                try { store.save(active, text); names = store.list(); status = "Saved locally." } catch (e: Exception) { status = e.message ?: "Failed" }
            }) { Text("Save") }
            Button(onClick = {
                if (store.delete(active)) { names = store.list(); text = ""; status = "Deleted." } else status = "Delete failed."
            }) { Text("Delete") }
        }
        names.forEach { n -> Text(n, Modifier.fillMaxWidth().clickable { active = n; text = store.load(n) }.padding(6.dp)) }
        if (status.isNotEmpty()) Text(status)
    }
}

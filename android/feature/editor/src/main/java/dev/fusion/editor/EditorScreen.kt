package dev.fusion.editor

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Edits UTF-8 text in memory; Save a copy uses ACTION_CREATE_DOCUMENT in the host Activity. */
@Composable
fun EditorScreen(initial: String, filename: String, onSaveCopy: (String, String) -> Unit) {
    var content by rememberSaveable(filename, initial) { mutableStateOf(initial) }
    val dirty = content != initial
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row {
            Text(filename + if (dirty) "  -  Unsaved" else "  -  Original unchanged", Modifier.weight(1f))
            Button(onClick = { onSaveCopy(filename, content) }) { Text("Save a copy") }
        }
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text("UTF-8 text (up to 2 MB)") }
        )
        Text("Saving creates a new document. Your source is never silently overwritten.")
    }
}
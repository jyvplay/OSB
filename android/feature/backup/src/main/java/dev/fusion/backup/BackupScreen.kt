package dev.fusion.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun BackupScreen(
    status: String,
    onExport: (CharArray) -> Unit,
    onRestore: (CharArray) -> Unit
) {
    var password by remember { mutableStateOf("") }
    val tooShort = password.length < 8

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Encrypted export", style = MaterialTheme.typography.titleSmall)
        Text("Exports app-private notes into one AES-256-GCM file you choose. SAF folders you browsed are NOT included.")
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (min 8 chars)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onExport(password.toCharArray()) }, enabled = !tooShort) { Text("Export") }
            OutlinedButton(onClick = { onRestore(password.toCharArray()) }, enabled = !tooShort) { Text("Restore") }
        }
        Text(status)
        Text(
            "No cloud, no escrow, no key recovery. A wrong password fails closed. " +
                "Restore never overwrites: clashes are saved as '(restored N)'.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

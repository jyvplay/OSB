package dev.fusion.vault

import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import dev.fusion.security.VaultCrypto
import java.security.SecureRandom

/** Real Keystore seal/open + generator. No passkeys, no autofill, no cloud. */
@Composable
fun VaultScreen(crypto: VaultCrypto = remember { VaultCrypto() }) {
    var pw by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Vault — Keystore AES-256-GCM. Excluded from any future screen-share.")
        Button(onClick = {
            val a = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#%"
            val r = SecureRandom(); val sb = StringBuilder()
            repeat(20) { sb.append(a[r.nextInt(a.length)]) }
            pw = sb.toString()
        }) { Text("Generate password") }
        if (pw.isNotEmpty()) Text(pw)
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Secret note") }, minLines = 2)
        Button(onClick = {
            try {
                val (iv, ct) = crypto.seal(note.toByteArray())
                val back = crypto.open(iv, ct).toString(Charsets.UTF_8)
                out = if (back == note) "Sealed ${ct.size}B, round-trip OK." else "Round-trip MISMATCH"
            } catch (e: Exception) { out = "Seal failed: ${e.message}" }
        }) { Text("Seal + verify") }
        if (out.isNotEmpty()) Text(out)
    }
}

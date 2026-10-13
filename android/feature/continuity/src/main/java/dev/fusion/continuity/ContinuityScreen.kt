package dev.fusion.continuity

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ContinuityScreen(context: Context, incomingShare: String?) {
    var refresh by remember { mutableIntStateOf(0) }
    val inputs = remember(refresh) { Peripherals.inputDevices() }
    val usb = remember(refresh) { Peripherals.usbDevices(context) }
    var outgoing by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Peripherals", style = MaterialTheme.typography.titleSmall)
        Button(onClick = { refresh++ }) { Text("Rescan") }
        if (inputs.isEmpty()) Text("No physical keyboard, mouse or trackpad detected.")
        inputs.forEach { Text("${it.name} — ${it.detail}") }
        Text("USB: ${if (usb.isEmpty()) "none attached" else ""}")
        usb.forEach { Text("${it.name} — ${it.detail}") }

        Text("Share out", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(outgoing, { outgoing = it }, Modifier.fillMaxWidth(), label = { Text("Text to share") })
        Button(enabled = outgoing.isNotBlank(), onClick = { Peripherals.shareText(context, outgoing) }) {
            Text("Open share sheet")
        }

        Text("Share in", style = MaterialTheme.typography.titleSmall)
        Text(incomingShare ?: "Nothing received. Send text to Fusion Shell from another app to see it here.")

        Text(
            "Bluetooth pairing lists are not read (that needs BLUETOOTH_CONNECT). " +
                "PC bridge, handoff and remote desktop are not implemented.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

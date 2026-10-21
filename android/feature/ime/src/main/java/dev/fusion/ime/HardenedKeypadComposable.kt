// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ime

import android.os.SystemClock
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HardenedKeypadScreen(engine: HardenedPinBuffer = remember { HardenedPinBuffer() }) {
    var keys by remember { mutableStateOf(engine.keys) }
    var length by remember { mutableStateOf(engine.length) }
    var status by remember { mutableStateOf("CSPRNG per-keystroke layout active · clipboard & predictive dictionary disabled") }

    fun sync(note: String? = null) {
        keys = engine.keys
        length = engine.length
        if (note != null) status = note
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Hardened Input & BadUSB Guard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Permutes digits every keystroke and quarantines <8 ms HID injection bursts or overlays.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
        Row(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = if (length == 0) "Buffer empty (0 symbols)" else "Masked symbols: " + "•".repeat(length),
                modifier = Modifier.weight(1f).testTag("ime-masked-length"),
                fontWeight = FontWeight.SemiBold
            )
            Button(
                onClick = { engine.zeroize(); sync("Volatile buffer zeroized") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("ime-zeroize")
            ) { Text("Wipe") }
        }
        for (row in 0 until 4) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 3) {
                    val idx = row * 3 + col
                    if (idx < keys.size) {
                        val symbol = keys[idx]
                        OutlinedButton(
                            onClick = {
                                val sample = HidEventSample(
                                    timestampNanos = SystemClock.elapsedRealtimeNanos(),
                                    deviceDescriptorHash = "touchscreen-internal"
                                )
                                val verdict = engine.pressSlot(idx, sample)
                                sync("Slot $idx entered · verdict=${verdict.name}")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("ime-slot-$idx")
                                .semantics { contentDescription = "Scrambled keypad digit $symbol" }
                        ) {
                            Text(symbol.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    } else if (idx == 10) {
                        OutlinedButton(
                            onClick = { engine.backspace(); sync("Deleted last symbol; keypad re-permuted") },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ime-backspace")
                        ) { Text("⌫") }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Text(status, Modifier.testTag("ime-status"), style = MaterialTheme.typography.bodySmall)
    }
}

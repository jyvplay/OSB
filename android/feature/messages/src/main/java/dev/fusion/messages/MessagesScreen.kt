package dev.fusion.messages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Messages surface.
 *  - SMS/MMS tab: REAL SMS inbox + sent read and SMS text send, gated on
 *    READ_SMS / SEND_SMS runtime permissions. No SMS role, no MMS, no RCS.
 *  - iMessage / Matrix tabs: honest non-functional boundaries (no transport).
 */
@Composable
fun MessagesScreen(context: Context) {
    val smsDeclared = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.contains(Manifest.permission.READ_SMS) == true
    }
    val repo = remember(context) { SmsRepository(context) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("SMS") }

    var smsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var sendGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val messages = remember { mutableStateListOf<SmsMessage>() }
    var status by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("MMM d HH:mm", Locale.getDefault()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        smsGranted = results[Manifest.permission.READ_SMS] == true
        sendGranted = results[Manifest.permission.SEND_SMS] == true
        if (!smsGranted) status = "SMS permission denied. Nothing is read."
    }

    LaunchedEffect(smsGranted, tab) {
        if (tab == "SMS" && smsGranted) {
            repo.inbox().fold(
                onSuccess = { messages.clear(); messages.addAll(it) },
                onFailure = { status = "Could not read SMS: ${it.message}" }
            )
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SMS", "iMessage", "Matrix").forEach {
                if (it == tab) Button(onClick = { tab = it }) { Text(it) }
                else OutlinedButton(onClick = { tab = it }) { Text(it) }
            }
        }

        when (tab) {
            "SMS" -> {
                if (!smsDeclared) {
                    Text("SMS access is not included in this flavor. The default-SMS role and Play policy review have not passed. Use your system messaging app.")
                } else if (!smsGranted) {
                    Text("SMS access is off. Grant READ_SMS to see your inbox and SEND_SMS to send.")
                    Button(onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)
                        )
                    }) { Text("Grant SMS permission") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(recipient, { recipient = it }, Modifier.weight(1f),
                            label = { Text("Recipient number") }, singleLine = true)
                    }
                    OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(),
                        label = { Text("SMS message") }, minLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(enabled = sendGranted && recipient.isNotBlank() && body.isNotBlank(), onClick = {
                            scope.launch {
                                repo.send(recipient.trim(), body).fold(
                                    onSuccess = {
                                        status = "Submitted to Android. Sending/delivery has not been confirmed."
                                        body = ""
                                        repo.inbox().fold(
                                            { messages.clear(); messages.addAll(it) },
                                            { status = "Refresh failed: ${it.message}" }
                                        )
                                    },
                                    onFailure = { status = "Send failed: ${it.message}" }
                                )
                            }
                        }) { Text("Send SMS") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                repo.inbox().fold(
                                    { messages.clear(); messages.addAll(it); status = "Refreshed." },
                                    { status = "Refresh failed: ${it.message}" }
                                )
                            }
                        }) { Text("Refresh") }
                    }
                    if (!sendGranted) Text("SEND_SMS not granted — sending is disabled.", style = MaterialTheme.typography.labelSmall)
                    LazyColumn(Modifier.weight(1f)) {
                        items(messages, key = { it.date.toString() + it.address + it.body }) { m ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text("${m.direction} · ${m.address} · ${fmt.format(Date(m.date))}",
                                    style = MaterialTheme.typography.labelSmall)
                                Text(m.body)
                            }
                        }
                    }
                    if (messages.isEmpty()) Text("No SMS messages. This is a real empty result.")
                }
                Text(
                    "SMS text only. No MMS, no RCS, and this app is not your default SMS app.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            "iMessage" -> Text("Not implemented. Per spec, own-Mac relay (BlueBubbles-compatible) only; no on-phone iMessage path is permitted.")
            else -> Text("Not implemented. No Matrix SDK or transport is present in this build.")
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelSmall)
    }
}

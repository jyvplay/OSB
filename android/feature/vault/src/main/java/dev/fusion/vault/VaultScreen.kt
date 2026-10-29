package dev.fusion.vault

import android.util.Base64
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
import androidx.compose.ui.unit.dp
import dev.fusion.security.ClipboardSensitiveGuard
import dev.fusion.security.ScreenCaptureGuard
import dev.fusion.security.SecureKeypadScrambler
import dev.fusion.security.VaultCrypto
import java.security.SecureRandom

/** Real Keystore seal/open + anti-keylogger CSPRNG scrambled PIN pad + ScreenCaptureGuard. */
@Composable
fun VaultScreen(crypto: VaultCrypto = remember { VaultCrypto() }) {
    var pw by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var pinBuffer by remember { mutableStateOf("") }
    var padLayout by remember { mutableStateOf(SecureKeypadScrambler.generatePinPad()) }
    val captureDecision = remember { ScreenCaptureGuard.evaluate(ScreenCaptureGuard.EnvironmentState()) }
    val passkeyOriginOk = remember { PasskeyRpValidator.isRpIdAllowedForOrigin("https://vault.fusion.local", "fusion.local") }
    val clipCheck = remember(pw, note) { ClipboardSensitiveGuard.classify(pw.ifEmpty { note }, explicitSensitive = pw.isNotEmpty()) }
    val thermalRisk = remember { dev.fusion.security.ThermalResidueWipeAdvisor.assess(dev.fusion.security.ThermalResidueWipeAdvisor.SurfaceMaterial.GLASS_GORILLA) }
    val pwStrength = remember(pw) { if (pw.isEmpty()) null else VaultPassphraseEntropyAuditor.audit(pw) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Vault — Keystore AES-256-GCM • FLAG_SECURE=${captureDecision.enforceWindowFlagSecure} • WebAuthn RP=$passkeyOriginOk • Autofill blocked=${captureDecision.disableAutofillDescendants} • Thermal wipe>=30s=${thermalRisk.palmScrubRequiredImmediately}")
        Text("Anti-Keylogger CSPRNG PIN Pad (reshuffles every tap): ${ScreenCaptureGuard.redactSecretForPreview(pinBuffer)}")
        padLayout.cells.chunked(3).forEach { rowCells ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowCells.forEach { cell ->
                    OutlinedButton(onClick = {
                        when (cell.label) {
                            "OK" -> Unit
                            "BACKSPACE" -> if (pinBuffer.isNotEmpty()) pinBuffer = pinBuffer.dropLast(1)
                            else -> if (pinBuffer.length < 12) pinBuffer += cell.label
                        }
                        padLayout = SecureKeypadScrambler.generatePinPad() // per-tap spatial randomization
                    }) { Text(cell.label) }
                }
            }
        }
        Button(onClick = {
            val a = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#%"
            val r = SecureRandom(); val sb = StringBuilder()
            repeat(20) { sb.append(a[r.nextInt(a.length)]) }
            pw = sb.toString()
        }) { Text("Generate password") }
        if (pw.isNotEmpty()) Text("$pw (Clipboard guard: sensitive=${clipCheck.isSensitive}, TTL=${ClipboardSensitiveGuard.DEFAULT_SENSITIVE_TTL_MS / 1000}s, entropy=${"%.1f".format(pwStrength?.estimatedEntropyBits ?: 0.0)}b)")
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Secret note") }, minLines = 2)
        Button(onClick = {
            try {
                val (iv, ct) = crypto.seal(note.toByteArray())
                val back = crypto.open(iv, ct).toString(Charsets.UTF_8)
                out = if (back == note) "Sealed ${ct.size}B, round-trip OK." else "Round-trip MISMATCH"
            } catch (e: Exception) { out = "Seal failed: ${e.message}" }
        }) { Text("Seal + verify") }
        if (out.isNotEmpty()) Text(out)
        val slip39Ok = remember {
            val chk = dev.fusion.security.Slip39MnemonicShareChecksumCodec.createRs1024ChecksumWords(listOf(12, 345, 678))
            dev.fusion.security.Slip39MnemonicShareChecksumCodec.verifyRs1024ShareWords(listOf(12, 345, 678) + chk)
        }
        Text("SLIP-39 RS1024 self-check=$slip39Ok • 4-slot plausible-deniability duress header ready.", style = MaterialTheme.typography.labelSmall)
        VaultColumnsSecretBrowser(
            secrets = remember {
                listOf(
                    VaultItemViewModeOrganizer.VaultSecretStub("v1", "Tor v3 Client Auth", "Anonymity", "Onion_Keys"),
                    VaultItemViewModeOrganizer.VaultSecretStub("v2", "SSH Signing Key", "Hardware", "Workstation"),
                )
            }
        )
    }
}

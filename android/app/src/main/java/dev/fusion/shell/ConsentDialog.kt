// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * R28 AUTHORED, NOT EXECUTED IN THIS SANDBOX. Requires Studio/CI compile plus
 * disposable-emulator verification before any behavior claim.
 *
 * Confirmation dialog for [dev.fusion.security.ConsentLedger] receipts: shows
 * the scope being authorized and its remaining lifetime. Confirming calls back
 * so the caller can burn the single-use receipt; the dialog itself holds no
 * receipt and grants nothing. Dismissal (back press, outside tap, Cancel)
 * authorizes nothing.
 */
@Composable
fun ConsentDialog(
    scopeTitle: String,
    scopeDetail: String,
    expiresInSeconds: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    require(scopeTitle.isNotEmpty() && scopeDetail.isNotEmpty())
    require(expiresInSeconds in 1..3600)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Authorize: $scopeTitle") },
        text = {
            Text("$scopeDetail\n\nSingle use. Expires in $expiresInSeconds seconds. " +
                "Dismissing authorizes nothing.")
        },
        confirmButton = {
            Button(onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp).testTag("consent-confirm")) {
                Text("Authorize once")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp).testTag("consent-cancel")) {
                Text("Cancel")
            }
        },
    )
}

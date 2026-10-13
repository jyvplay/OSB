package dev.fusion.launcher

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ShelfItem(val key: String, val title: String, val running: Boolean)

/** Shelf: pinned + running only. No fake task list, no badges without data. */
@Composable
fun ShelfBar(items: List<ShelfItem>, active: String?, onOpen: (String) -> Unit, workspaces: Int = 3, current: Int = 0, onWorkspace: (Int) -> Unit = {}) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            if (item.running) OutlinedButton(onClick = { onOpen(item.key) }) { Text(item.title + " ●") }
            else Button(onClick = { onOpen(item.key) }) { Text(item.title) }
        }
        (0 until workspaces).forEach { ws ->
            OutlinedButton(onClick = { onWorkspace(ws) }) { Text(if (ws == current) "[${ws + 1}]" else "${ws + 1}") }
        }
    }
}

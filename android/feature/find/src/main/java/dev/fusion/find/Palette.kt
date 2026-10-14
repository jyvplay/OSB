package dev.fusion.find

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.fusion.commands.Command
import dev.fusion.commands.CommandRegistry

/** SR-01 partial: real local module/window/workspace actions. No file/index coverage is implied. */
@Composable
fun CommandPalette(commands:List<Command>,onSelect:(String)->Unit,onDismiss:()->Unit) {
    var query by remember { mutableStateOf("") }
    var position by remember { mutableIntStateOf(0) }
    val registry=remember(commands) { CommandRegistry(commands) }
    val hits=remember(registry,query) { registry.search(query,32) }
    val selectedIndex=position.coerceIn(0,maxOf(0,hits.lastIndex))
    val inputFocus=remember { FocusRequester() }
    val scroll=rememberLazyListState()
    LaunchedEffect(Unit) { inputFocus.requestFocus() }
    LaunchedEffect(query) { position=0 }
    LaunchedEffect(selectedIndex,hits) { if(hits.isNotEmpty()) scroll.animateScrollToItem(selectedIndex) }
    AlertDialog(onDismissRequest=onDismiss,title={ Text("Find local actions") },
        text={ Column(Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
            if(event.type!=KeyEventType.KeyDown) false else when(event.key) {
                Key.Escape -> { onDismiss(); true }
                Key.DirectionDown -> { if(hits.isNotEmpty()) position=(selectedIndex+1)%hits.size; true }
                Key.DirectionUp -> { if(hits.isNotEmpty()) position=(selectedIndex+hits.size-1)%hits.size; true }
                Key.Enter,Key.NumPadEnter -> { hits.getOrNull(selectedIndex)?.let { onSelect(it.id) }; true }
                else -> false
            }
        }) {
            OutlinedTextField(query,{ if(it.length<=CommandRegistry.MAX_QUERY) query=it },singleLine=true,
                label={ Text("Search modules, open windows and workspaces") },modifier=Modifier.fillMaxWidth().focusRequester(inputFocus).testTag("palette-query"))
            Text("Local literal search • ↑/↓ select, Enter opens, Esc closes • 1024-character limit. No files, contacts, private-app or web index is searched.")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=280.dp),state=scroll) {
                itemsIndexed(hits,key={ _,command -> command.id }) { index,command ->
                    TextButton(onClick={ onSelect(command.id) },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                        .semantics { this.selected=index==selectedIndex }.testTag("palette-hit-${command.id}")) {
                        Column(Modifier.fillMaxWidth()) { Text(command.title); Text(command.hint,style=MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if(hits.isEmpty()) Text("No matching local action. Other sources are not indexed.")
        } },confirmButton={ TextButton(onClick=onDismiss) { Text("Close Find") } })
}

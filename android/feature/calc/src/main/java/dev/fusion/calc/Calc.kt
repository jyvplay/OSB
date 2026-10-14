package dev.fusion.calc

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** PR-09: all calculation modes use the production Kotlin kernels, never a web view or eval. */
@Composable
fun CalcScreen(model: CalculatorViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val request=state.session.request
    val ready=!state.loading && !state.busy && !state.recoveryBlocked
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
        .semantics { paneTitle="Calculator" }, verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Calculator",style=MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            CalculatorMode.entries.forEach { mode ->
                OutlinedButton(enabled=ready && request.mode!=mode,onClick={ model.setRequest(request.copy(mode=mode)) }) {
                    Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
        Text("Mode: ${request.mode.name}")
        if(request.mode==CalculatorMode.SCIENTIFIC) Row(Modifier.horizontalScroll(rememberScrollState())) {
            AngleUnit.entries.forEach { angle -> OutlinedButton(enabled=ready && request.angle!=angle,
                onClick={ model.setRequest(request.copy(angle=angle)) }) { Text(angle.name) } }
        }
        if(request.mode==CalculatorMode.PROGRAMMER) {
            Text("64-bit wrapping arithmetic; output radix: ${request.radix.name}. Input prefixes: 0x / 0b / 0o, otherwise decimal.")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                OutputRadix.entries.forEach { radix -> OutlinedButton(enabled=ready && request.radix!=radix,
                    onClick={ model.setRequest(request.copy(radix=radix)) }) { Text(radix.name) } }
            }
        }
        if(request.mode==CalculatorMode.CONVERT) {
            val dimension=Units.definition(request.fromUnit).dimension
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Dimension.entries.forEach { d -> OutlinedButton(enabled=ready && dimension!=d,onClick={
                    val units=Units.definitions.filter { it.dimension==d }
                    model.setRequest(request.copy(fromUnit=units.first().id,toUnit=units.getOrElse(1) { units.first() }.id))
                }) { Text(d.name) } }
            }
            UnitPicker("From",request.fromUnit,dimension,ready) { model.setRequest(request.copy(fromUnit=it)) }
            UnitPicker("To",request.toUnit,dimension,ready) { model.setRequest(request.copy(toUnit=it)) }
        }
        OutlinedTextField(request.expression,{ model.setRequest(request.copy(expression=it)) },enabled=ready,
            modifier=Modifier.fillMaxWidth().testTag("calculator-input").onPreviewKeyEvent {
                if(it.type==KeyEventType.KeyDown && it.key==Key.Enter && ready) { model.evaluate(); true } else false
            },singleLine=true,
            label={ Text(if(request.mode==CalculatorMode.CONVERT) "Amount / decimal expression" else "Expression") })
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Button(enabled=ready,onClick=model::evaluate,modifier=Modifier.testTag("calculator-evaluate")) { Text("Calculate") }
            TextButton(onClick={ showHelp=!showHelp }) { Text(if(showHelp) "Hide syntax" else "Syntax and limits") }
            OutlinedButton(enabled=ready && state.session.history.isNotEmpty(),onClick={ confirmClear=true }) { Text("Clear history…") }
        }
        Text(state.message,Modifier.semantics { liveRegion=LiveRegionMode.Polite })
        Text(state.checkpoint,style=MaterialTheme.typography.bodySmall)
        if(state.loading || state.busy) CircularProgressIndicator()
        if(state.recoveryBlocked) Button(onClick={ confirmReset=true }) { Text("Retain snapshot and reset…") }
        if(showHelp) {
            Text("Decimal: + - * / % ^ and parentheses; ^ integer powers. Scientific: + - * / % ^ !; pi, e; sin/cos/tan, asin/acos/atan, sinh/cosh/tanh, sqrt/cbrt, ln/log10/exp, abs/floor/ceil/round, percent; pow/min/max/hypot accept two arguments. round uses half-even. No implicit multiplication.")
            Text("Programmer: + - * / % & | ^ ~ << >> >>>, parentheses. ^ is XOR, not power; shift counts 0–63. 64-bit wrapping is deliberate. Decimal division is rounded to 34 significant digits; scientific binary64 is approximate. Maximum 512 input characters, 256 tokens, 32 parser levels including the root. History holds 100 results. No currency or civil-calendar conversions.")
            Text("Local history is not encrypted, synced or backed up. Last in-flight changes may not survive a kill. Module hiding retains memory in the Activity, not a general session guarantee for other modules.")
        }
        state.session.history.firstOrNull()?.let { record ->
            Text("Recorded result — ${record.calculation.request.mode.name}",style=MaterialTheme.typography.titleMedium)
            Text(record.calculation.request.expression)
            SelectionContainer { Text(record.calculation.value,Modifier.testTag("calculator-result"),style=MaterialTheme.typography.headlineSmall) }
            Text(record.calculation.explanation)
        }
        Text("Local history (${state.session.history.size}/${CalculatorHistory.MAX_ENTRIES})",style=MaterialTheme.typography.titleMedium)
        state.session.history.forEach { record -> key(record.id) {
            HorizontalDivider()
            Text("${record.calculation.request.mode.name} · ${record.calculation.request.expression} = ${record.calculation.value}")
            Text(record.calculation.explanation,style=MaterialTheme.typography.bodySmall)
            TextButton(enabled=ready,onClick={ model.recall(record) }) { Text("Recall input (no automatic calculation)") }
        } }
    }
    if(confirmClear) AlertDialog(onDismissRequest={ confirmClear=false },title={ Text("Clear calculator history?") },
        text={ Text("Removes recorded results from the current checkpoint. Your current input stays. This does not securely erase retained snapshots or backups.") },
        confirmButton={ TextButton(onClick={ confirmClear=false; model.clearHistory() }) { Text("Clear history") } },
        dismissButton={ TextButton(onClick={ confirmClear=false }) { Text("Cancel") } })
    if(confirmReset) AlertDialog(onDismissRequest={ confirmReset=false },title={ Text("Reset calculator recovery?") },
        text={ Text("Retain the old snapshot bytes and start an empty calculator session. Editor drafts and window layout are not affected.") },
        confirmButton={ TextButton(onClick={ confirmReset=false; model.resetRecovery() }) { Text("Retain and reset") } },
        dismissButton={ TextButton(onClick={ confirmReset=false }) { Text("Cancel") } })
}

@Composable
private fun UnitPicker(label: String, selected: String, dimension: Dimension, enabled: Boolean, onSelect: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled=enabled,onClick={ expanded=true }) { Text("$label: $selected (${Units.definition(selected).label})") }
        DropdownMenu(expanded=expanded && enabled,onDismissRequest={ expanded=false }) {
            Units.definitions.filter { it.dimension==dimension }.forEach { unit ->
                DropdownMenuItem(text={ Text("${unit.id} — ${unit.label}") },onClick={ expanded=false; onSelect(unit.id) })
            }
        }
    }
}

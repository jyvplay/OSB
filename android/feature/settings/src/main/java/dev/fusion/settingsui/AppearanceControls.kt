package dev.fusion.settingsui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.fusion.settings.*

/** Both entry points edit the SAME graph. No mirrored setting, OS-theme claim or hidden data migration. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceControls(graph:SettingsGraph,prefix:String) {
    val values by graph.snapshots.collectAsState()
    val current=AppearancePreferences.read(values)
    var feedback by remember {mutableStateOf("")}
    fun change(key:String,value:String) {
        feedback=if(graph.setAppearance(mapOf(key to value))) "Appearance updated locally. Normal persistence, not a power-loss durability receipt."
            else "Appearance change refused; no validated patch applied."
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Appearance",style=MaterialTheme.typography.titleMedium)
        Text("Professional palette — independent of the layout")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ProfessionalPalette.entries.forEach {palette ->
                val picked=palette==current.palette
                OutlinedButton(onClick={change(AppearancePreferences.PALETTE,palette.id)},
                    modifier=Modifier.heightIn(min=56.dp).testTag("$prefix-theme-${palette.id}").semantics {
                        selected=picked;stateDescription=if(picked) "Selected" else "Not selected"
                    }) {
                    Column {
                        Text(palette.title)
                        Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                            (if(palette.dark) listOf(0xff151b24,0xff202936,0xff80cbb8) else listOf(0xfff3f6fb,0xffffffff,0xff174ea6)).forEach {color ->
                                Box(Modifier.size(16.dp).background(Color(color)).border(1.dp,MaterialTheme.colorScheme.outline))
                            }
                        }
                    }
                }
            }
        }
        Text("Desktop layout")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            DesktopLayout.entries.forEach {layout ->
                OutlinedButton(onClick={change(AppearancePreferences.LAYOUT,layout.id)},
                    modifier=Modifier.heightIn(min=48.dp).testTag("$prefix-layout-${layout.id}").semantics {
                        selected=current.layout==layout;stateDescription=if(current.layout==layout) "Selected" else "Not selected"
                    }) {Text(layout.title)}
            }
        }
        Text(if(current.layout==DesktopLayout.BUSINESS) "Business: full-width bottom taskbar, own-app running windows and familiar right-side window controls."
            else "Art: centered scrollable dock, running indicators, pointer/keyboard-focus magnification and left-side window controls.")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Switch(checked=current.dockZoom,onCheckedChange={change(AppearancePreferences.ZOOM,it.toString())},
                modifier=Modifier.testTag("$prefix-dock-zoom"))
            Text("Art dock magnification (turn off to reduce motion)",Modifier.weight(1f))
        }
        Text("Only Fusion changes. Not Windows/macOS, not a keyboard preset, privacy mode or permission change.",style=MaterialTheme.typography.bodySmall)
        if(feedback.isNotEmpty()) Text(feedback,modifier=Modifier.testTag("$prefix-appearance-status"),style=MaterialTheme.typography.bodySmall)
    }
}

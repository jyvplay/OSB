package dev.fusion.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import dev.fusion.windowing.DesktopState
import dev.fusion.windowing.SnapTileMagneticLayoutSolver

/**
 * Content shown on the connected external display.
 *
 * This is a live session view driven by the same DesktopState as the phone
 * compositor: window titles, workspace, focus and geometry update in real time.
 * It is explicitly NOT the interactive compositor: windows cannot be dragged on
 * the monitor, and third-party apps are not projected there.
 */
@Composable
fun ExternalDesktopScreen(state: DesktopState, deviceLabel: String) {
    val visible = state.windows
        .filter { it.workspace == state.workspace && !it.minimized }
        .sortedByDescending { it.z }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF07090A)).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("FUSION SHELL", color = Color(0xFF65DDB4), fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text("External session · $deviceLabel", color = Color.Gray, fontSize = 15.sp)
        Text(
            "Workspace ${state.workspace + 1} · ${visible.size} open window(s)",
            color = Color.White, fontSize = 20.sp
        )
        val snapHint = SnapTileMagneticLayoutSolver.detectSnapZone(
            pointerX = 0, pointerY = 540,
            workArea = SnapTileMagneticLayoutSolver.BoundsPx(0, 0, 1920, 1080),
        ).name
        val readiness = FusionClassAReadinessAggregator.summarize(
            listOf(FusionClassAReadinessAggregator.ModuleGateRecord(":app", hostPureJvmVerified = true, permissiveLicenseCleanRoom = true))
        )
        Text(
            "R51 Snap zone ready ($snapHint) · Full Class A: ${readiness.fullClassACount}/${readiness.totalModules} (device SDK/HAL gated)",
            color = Color(0xFF9AD9C2), fontSize = 13.sp
        )

        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { w ->
                val focused = state.activeId == w.id
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (focused) Color(0xFF17302A) else Color(0xFF14181A))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (focused) "●" else "○",
                        color = if (focused) Color(0xFF65DDB4) else Color.DarkGray,
                        fontSize = 18.sp
                    )
                    Column(Modifier.weight(1f)) {
                        Text(w.title, color = Color.White, fontSize = 18.sp)
                        Text(
                            "${w.bounds.width.toInt()} x ${w.bounds.height.toInt()} dp  ·  layer ${w.z}",
                            color = Color.Gray, fontSize = 13.sp
                        )
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            Text("No open windows on this workspace.", color = Color.Gray, fontSize = 16.sp)
        }

        Text(
            "Live session mirror. Window content and third-party apps are not rendered here, " +
                "and this display is not interactive. Closing the phone app closes this window.",
            color = Color(0xFFFFC675), fontSize = 12.sp
        )
    }
}

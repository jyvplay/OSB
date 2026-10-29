package dev.fusion.together

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** S22 remains blocked. No fake peer, room, playing state or randomly invented clock measurement. */
@Composable
fun TogetherScreen() {
    val zeroDrift = TogetherCoWatchSyncClock.planPlayheadCorrection(localPositionMs = 0L, expectedLeaderPositionMs = 0L)
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Together unavailable")
        Text("A bounded authenticated socket exchange exists, but pairing, rooms, real-player integration, multi-device synchronization and Syncplay compatibility are not implemented. S22 has not passed.")
        Text("No room is created and no media is playing or synchronized by this module.")
        Text("Pure-JVM PLL clock kernel baseline: ${zeroDrift.action.name} (${zeroDrift.playbackRate}x)")
    }
}

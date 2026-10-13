package dev.fusion.together

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.random.Random

/**
 * Honest local-only prototype of Together room state.
 *
 * What is real here:
 *  - local room code generation
 *  - shared-clock drift model and budget evaluation
 *  - own-copy mode UI contract (each participant supplies their own media)
 *
 * What is NOT implemented:
 *  - networking, signaling, WebRTC, LAN discovery, relay, chat, remote peers
 *  - Syncplay protocol compatibility
 *  - any claim that two devices are actually synchronised
 */
class TogetherSession {
    data class Peer(val name: String, val offsetMs: Long)
    data class State(
        val code: String,
        val peers: List<Peer>,
        val ownCopyMode: Boolean,
        val maxDriftMs: Long,
        val playing: Boolean,
        val positionSeconds: Double
    ) {
        val inBudget: Boolean get() = maxDriftMs <= 100L
    }

    private val rng = Random(System.currentTimeMillis())
    fun newRoom(ownCopyMode: Boolean = true): State {
        val code = buildString {
            repeat(6) { append(('A'..'Z').random(rng)) }
        }
        return State(code, listOf(Peer("You", 0)), ownCopyMode, 0, false, 0.0)
    }

    fun addSimulatedPeer(state: State, name: String): State {
        val offset = rng.nextLong(-90, 90)
        val peers = state.peers + Peer(name.ifBlank { "Peer ${state.peers.size}" }, offset)
        val drift = peers.maxOfOrNull { abs(it.offsetMs) } ?: 0L
        return state.copy(peers = peers, maxDriftMs = drift)
    }

    fun nudgeDrift(state: State): State {
        val peers = state.peers.mapIndexed { idx, p ->
            if (idx == 0) p else p.copy(offsetMs = (p.offsetMs + rng.nextLong(-35, 35)).coerceIn(-250, 250))
        }
        val drift = peers.maxOfOrNull { abs(it.offsetMs) } ?: 0L
        return state.copy(peers = peers, maxDriftMs = drift)
    }

    fun play(state: State): State = state.copy(playing = true)
    fun pause(state: State): State = state.copy(playing = false)
}

@Composable
fun TogetherScreen(session: TogetherSession = remember { TogetherSession() }) {
    var state by remember { mutableStateOf(session.newRoom()) }
    var nextName by remember { mutableStateOf("") }
    val ownCopy = state.ownCopyMode

    Column(
        Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Together — local room model only", style = MaterialTheme.typography.titleSmall)
        Text(
            if (ownCopy)
                "Own-copy mode: each participant supplies their own media. Networking is not implemented."
            else
                "Hosted media mode is not implemented in this build."
        )
        Text("Room code: ${state.code}")
        Text("Max drift: ${state.maxDriftMs} ms${if (state.inBudget) " (within 100 ms budget)" else " (outside budget)"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { state = session.play(state) }, enabled = !state.playing) { Text("Play") }
            OutlinedButton(onClick = { state = session.pause(state) }, enabled = state.playing) { Text("Pause") }
            OutlinedButton(onClick = { state = session.nudgeDrift(state) }) { Text("Recompute drift") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(nextName, { nextName = it }, label = { Text("Simulated peer name") }, modifier = Modifier.weight(1f))
            Button(onClick = { state = session.addSimulatedPeer(state, nextName); nextName = "" }) { Text("Add") }
        }
        state.peers.forEach { peer ->
            Text("${peer.name} · offset ${peer.offsetMs} ms")
        }
        Text(
            "No LAN, WebRTC, Syncplay or chat here. This window exists so the shell can model the feature honestly without pretending it synchronises real devices.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

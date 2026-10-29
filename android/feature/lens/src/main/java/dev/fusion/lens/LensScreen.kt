package dev.fusion.lens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Camera preview only.
 *
 * Deliberately NOT implemented here: background blur, segmentation, auto-framing,
 * relighting, virtual camera output, WebRTC injection, recording, upload.
 * The two switches record a user preference and change nothing in the pipeline;
 * they are labelled as such so the UI cannot imply an effect that does not exist.
 */
@Composable
fun LensScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var status by remember { mutableStateOf("Camera not started.") }
    var blurRequested by remember { mutableStateOf(false) }
    var autoFrameRequested by remember { mutableStateOf(false) }
    var useFront by remember { mutableStateOf(true) }
    var softwareMuted by remember { mutableStateOf(false) }
    val studioPosture = remember(useFront, softwareMuted) {
        FoldPosturePresenterStudio.resolveStudioPosture(
            hingeAngleDeg = 180.0,
            externalDisplayDocked = false,
            requestedOptic = if (useFront) FoldPosturePresenterStudio.LensOptic.FRONT_WIDE else FoldPosturePresenterStudio.LensOptic.REAR_PRIMARY,
            hardwareCamKillSwitchClosed = false,
            softwareCamMuted = softwareMuted,
            micActive = false,
        )
    }
    val thermalBudget = remember { VirtualWebcamMjpegStream.governCaptureBudget(skinTempCelsius = 37.5, requestedFps = 30, requestedHeightPx = 1080) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        if (!result) status = "Camera permission denied. Nothing is captured."
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Lens — live preview only", style = MaterialTheme.typography.titleSmall)

        if (!granted) {
            Text("Camera permission is not granted. No frames are read until you allow it.")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera permission")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { useFront = !useFront }) {
                    Text(if (useFront) "Front camera" else "Back camera")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Blur (not applied)")
                Switch(checked = blurRequested, onCheckedChange = { blurRequested = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Auto-frame (not applied)")
                Switch(checked = autoFrameRequested, onCheckedChange = { autoFrameRequested = it })
            }
        }

        AndroidView(
            factory = { ctx -> PreviewView(ctx).also { previewView = it } },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Text(status)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { softwareMuted = !softwareMuted }) {
                Text(if (softwareMuted) "Unmute feed" else "Mute feed (black frame)")
            }
            Text(
                "Studio math: ${studioPosture.posture.name} · ${studioPosture.activeOptic.focalEqMm}mm · cap ${thermalBudget.second}p@${thermalBudget.first}fps",
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            "These switches record intent only; no segmentation or framing code runs. " +
                "There is no virtual camera, so other apps cannot receive this feed.",
            style = MaterialTheme.typography.labelSmall
        )
    }

    LaunchedEffect(granted, previewView, useFront, lifecycleOwner) {
        val view = previewView
        if (!granted || view == null) return@LaunchedEffect
        val provider = try {
            withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
        } catch (e: Exception) {
            status = "Camera provider unavailable: ${e.message}"
            return@LaunchedEffect
        }
        val preview = Preview.Builder().build().apply { setSurfaceProvider(view.surfaceProvider) }
        val selector =
            if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        status = try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview)
            "Preview bound to ${if (useFront) "front" else "back"} camera. No effects, no recording."
        } catch (e: Exception) {
            "Could not bind that camera: ${e.message}"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
}

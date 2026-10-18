// SPDX-License-Identifier: Apache-2.0
package dev.fusion.maps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

private fun coordinate(point: GeoPoint) = String.format(Locale.ROOT, "%.5f°, %.5f°", point.latitude, point.longitude)

@Composable
fun MapScreen(model: MapViewModel) {
    var addVisible by remember { mutableStateOf(false) }
    var placesVisible by remember { mutableStateOf(false) }
    val book = model.book
    if (addVisible) {
        var name by remember { mutableStateOf("") }
        var lat by remember { mutableStateOf(String.format(Locale.ROOT, "%.6f", model.cursor.latitude)) }
        var lon by remember { mutableStateOf(String.format(Locale.ROOT, "%.6f", model.cursor.longitude)) }
        var error by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { addVisible = false }, title = { Text("Save a map pin") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Local plaintext bookmark, not a location reading. Up to 256 pins.")
                OutlinedTextField(name, { if (it.length <= 64) name = it }, label = { Text("Place name") }, modifier = Modifier.testTag("map-pin-name"))
                OutlinedTextField(lat, { if (it.length <= 32) lat = it }, label = { Text("Latitude (−90 to 90)") }, modifier = Modifier.testTag("map-latitude"))
                OutlinedTextField(lon, { if (it.length <= 32) lon = it }, label = { Text("Longitude (−180 to 180)") }, modifier = Modifier.testTag("map-longitude"))
                if (error.isNotEmpty()) Text(error)
            }
        }, confirmButton = {
            // These locals belong to the actual Dialog window, not the underlying desktop window.
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            TextButton(onClick = {
                try {
                    val point = GeoPoint(lat.toDouble(), lon.toDouble()); MapPin(1, name.trim(), point)
                    model.add(name.trim(), point)
                    focus.clearFocus(); keyboard?.hide(); addVisible = false
                } catch (_: Exception) { error = "Use a printable 1–64 character name and valid decimal coordinates." }
            }, enabled = !model.busy && book != null && book.pins.size < 256, modifier = Modifier.testTag("map-save-pin")) { Text("Save pin") }
        },
            dismissButton = { TextButton(onClick = { addVisible = false }) { Text("Cancel") } })
    }
    if (placesVisible) AlertDialog(onDismissRequest = { placesVisible = false }, title = { Text("Saved map pins") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (book?.pins.isNullOrEmpty()) Text("No saved pins. Tap the atlas or enter coordinates, then choose Add pin.")
            book?.pins?.forEach { pin ->
                Text(pin.name, style = MaterialTheme.typography.titleMedium)
                Text(coordinate(pin.point))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { model.camera = model.camera.copy(center = pin.point); model.cursor = pin.point; placesVisible = false }, modifier = Modifier.testTag("map-go-${pin.id}")) { Text("Go") }
                    TextButton(onClick = { model.measureFrom = pin.point; model.cursor = pin.point; placesVisible = false }) { Text("Measure from here") }
                    TextButton(onClick = { model.remove(pin.id) }, enabled = !model.busy, modifier = Modifier.testTag("map-delete-${pin.id}")) { Text("Delete pin") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { placesVisible = false }) { Text("Done") } })
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Offline atlas", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { addVisible = true }, enabled = book != null && !model.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("map-add-pin")) { Text("Add pin") }
            TextButton(onClick = { placesVisible = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("map-places")) { Text("Places (${book?.pins?.size ?: 0})") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            val width = maxWidth.value.toDouble().coerceAtLeast(1.0); val height = maxHeight.value.toDouble().coerceAtLeast(1.0)
            val density = LocalDensity.current.density
            val camera = model.camera
            val colors = MaterialTheme.colorScheme
            Canvas(Modifier.fillMaxSize().testTag("offline-map-canvas").semantics { contentDescription = "Offline atlas, low-resolution land outlines. Pan to move; tap to select coordinates." }
                .pointerInput(density) { detectDragGestures { change, amount -> change.consume(); model.camera = model.camera.pan((amount.x / density).toDouble(), (amount.y / density).toDouble()) } }
                .pointerInput(density,width,height) { detectTapGestures { point -> model.cursor = model.camera.at((point.x / density).toDouble(), (point.y / density).toDouble(), width, height) } }) {
                val worldPixels = camera.worldSize * density
                // Draw genuine shipped coastline coordinates. Split at wrap seams; never bridge the world with a false coastline.
                for (ring in model.land) {
                    val path = Path(); var previous: Offset? = null
                    for (point in ring) {
                        val p = camera.screen(point, width, height); val offset = Offset((p.x * density).toFloat(), (p.y * density).toFloat())
                        val before = previous
                        if (before == null || abs(offset.x - before.x) > worldPixels / 2) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                        previous = offset
                    }
                    drawPath(path, colors.primary, style = Stroke(width = 1.25.dp.toPx()))
                }
                for (latitude in -60..60 step 30) {
                    val y = camera.screen(GeoPoint(latitude.toDouble(), camera.center.longitude), width, height).y * density
                    drawLine(colors.outline.copy(alpha = .28f), Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), .5.dp.toPx())
                }
                for (longitude in -180 until 180 step 30) {
                    val x = camera.screen(GeoPoint(0.0, longitude.toDouble()), width, height).x * density
                    drawLine(colors.outline.copy(alpha = .28f), Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), .5.dp.toPx())
                }
                val selected = camera.screen(model.cursor, width, height); val center = Offset((selected.x * density).toFloat(), (selected.y * density).toFloat())
                drawCircle(colors.primary, 6.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                drawLine(colors.primary, center - Offset(12.dp.toPx(), 0f), center + Offset(12.dp.toPx(), 0f), 1.dp.toPx())
                drawLine(colors.primary, center - Offset(0f, 12.dp.toPx()), center + Offset(0f, 12.dp.toPx()), 1.dp.toPx())
            }
            book?.pins?.forEach { pin ->
                val p = camera.screen(pin.point, width, height)
                if (p.x in 24.0..(width - 24).coerceAtLeast(24.0) && p.y in 24.0..(height - 24).coerceAtLeast(24.0) && width >= 48 && height >= 48) {
                    TextButton(onClick = { model.cursor = pin.point }, modifier = Modifier.offset((p.x - 24).dp, (p.y - 24).dp).size(48.dp)
                        .testTag("map-pin-${pin.id}").semantics { contentDescription = "Map pin: ${pin.name}" }, contentPadding = PaddingValues(0.dp)) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) { Text("•", color = colors.onPrimary) }
                    }
                }
            }
            Text("Made with Natural Earth · 1:110m land", Modifier.align(Alignment.BottomStart).background(colors.surface).padding(4.dp), style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapAction("−", "Zoom out", "map-zoom-out", model.camera.zoom > 0) { model.camera = model.camera.copy(zoom = model.camera.zoom - 1) }
            MapAction("+", "Zoom in", "map-zoom-in", model.camera.zoom < 18) { model.camera = model.camera.copy(zoom = model.camera.zoom + 1) }
            MapAction("←", "Pan west", "map-west") { model.camera = model.camera.pan(96.0, 0.0) }
            MapAction("→", "Pan east", "map-east") { model.camera = model.camera.pan(-96.0, 0.0) }
            MapAction("↑", "Pan north", "map-north") { model.camera = model.camera.pan(0.0, 96.0) }
            MapAction("↓", "Pan south", "map-south") { model.camera = model.camera.pan(0.0, -96.0) }
            TextButton(onClick = { model.camera = MapCamera(); model.cursor = model.camera.center }, modifier = Modifier.heightIn(min = 48.dp)) { Text("World") }
        }
        Column(Modifier.heightIn(max = 130.dp).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            Text("Selected: ${coordinate(model.cursor)} · zoom ${model.camera.zoom}", Modifier.testTag("map-coordinate"), style = MaterialTheme.typography.bodySmall)
            val from = model.measureFrom
            if (from != null) Text("Great-circle distance: " + String.format(Locale.ROOT, "%.3f km", EarthGeometry.meters(from, model.cursor) / 1000) + " · not a road route", Modifier.testTag("map-distance"))
            TextButton(onClick = { if (from == null) model.measureFrom = model.cursor else model.measureFrom = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("map-measure")) { Text(if (from == null) "Measure from selected point" else "Clear measurement") }
            Text(model.status, Modifier.testTag("map-status"), style = MaterialTheme.typography.bodySmall)
            Text("No streets, search, GPS, traffic or navigation. Polar latitudes are clamped for drawing, not changed in saved pins.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MapAction(label: String, description: String, tag: String, enabled: Boolean = true, action: () -> Unit) {
    OutlinedButton(onClick = action, enabled = enabled, modifier = Modifier.size(48.dp).testTag(tag).semantics { contentDescription = description }, contentPadding = PaddingValues(2.dp)) { Text(label) }
}

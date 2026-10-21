// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fusion.launcher.*
import dev.fusion.model.PilotPolicy

private data class VisibleDeskItem(val id: Long, val title: String, val plane: DeskPlane, val point: DeskPoint, val target: String?, val count: Int)

/** Real native scene, not a WebView/illustration. Wall overview marks are decorative; focus has full-size targets. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabletopDesktop(model: TabletopSessionModel, flat: Boolean, enabled: Boolean, onOpen: (String) -> Unit) {
    val doc = model.document
    if (doc == null) {
        Column(Modifier.padding(16.dp)) {
            Text(model.status)
            OutlinedButton(onClick = model::reload, enabled = !model.busy) { Text("Reload arrangement") }
        }
        return
    }
    val scene = doc.scene
    val active = if (flat) DeskPlane.TABLE else scene.focused ?: DeskPlane.TABLE
    val focused = flat || scene.focused != null
    val camera = scene.cameras[active.ordinal]
    val ready = enabled && !model.busy
    var selected by remember { mutableStateOf<Long?>(null) }
    var pinsDialog by remember { mutableStateOf(false) }
    var viewsDialog by remember { mutableStateOf(false) }
    var arrangeDialog by remember { mutableStateOf(false) }
    var pendingView by remember { mutableStateOf<Long?>(null) }
    fun change(action: DeskAction) = model.change(doc.revision, action)
    val items = scene.pins.filter { it.pile == null }.map { VisibleDeskItem(it.id, PilotPolicy.module(it.target).title, it.plane, it.point, it.target, 1) } +
        scene.piles.map { pile -> VisibleDeskItem(pile.id, pile.name, pile.plane, pile.point, null, scene.pins.count { it.pile == pile.id }) }
    val selection = items.find { it.id == selected }
    LaunchedEffect(doc.revision) { if (items.none { it.id == selected }) selected = null }
    fun move(item: VisibleDeskItem, plane: DeskPlane, point: DeskPoint) = change(
        if (item.target == null) DeskAction.MovePile(item.id, plane, point) else DeskAction.MovePin(item.id, plane, point))
    fun pan(dx: Double, dy: Double) = change(DeskAction.Camera(active, camera.copy(center = DeskPoint(
        (camera.center.x + dx / camera.zoom).coerceIn(-DeskPoint.LIMIT, DeskPoint.LIMIT),
        (camera.center.y + dy / camera.zoom).coerceIn(-DeskPoint.LIMIT, DeskPoint.LIMIT)))))

    if (pinsDialog) AlertDialog(onDismissRequest = { pinsDialog = false }, title = { Text("Pin a Fusion app") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pins do not grant permissions or enable blocked apps. Up to 512 pins, not infinite storage.")
            PilotPolicy.modules.forEach { app ->
                OutlinedButton(onClick = { change(DeskAction.Add(app.id, active, camera.center)); pinsDialog = false }, enabled = ready && scene.pins.size < 512,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pin-add-${app.id}")) { Text(app.title) }
            }
        }
    }, confirmButton = { TextButton(onClick = { pinsDialog = false }) { Text("Done") } })

    if (arrangeDialog) {
        var chosen by remember { mutableStateOf(emptySet<Long>()) }
        var name by remember { mutableStateOf("New pile") }
        AlertDialog(onDismissRequest = { arrangeDialog = false }, title = { Text("Pile pins on ${active.title.lowercase()}") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Select at least two pins. Moving a pile moves all its members. Unpile restores individual draggable icons.")
                OutlinedTextField(name, { if (it.length <= 48) name = it }, label = { Text("Pile name") }, modifier = Modifier.testTag("pile-name"))
                scene.pins.filter { it.plane == active && it.pile == null }.forEach { pin ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pin.id in chosen, onCheckedChange = { checked -> chosen = if (checked) chosen + pin.id else chosen - pin.id },
                            modifier = Modifier.size(48.dp).testTag("pile-select-${pin.id}").semantics { contentDescription = "Select ${PilotPolicy.module(pin.target).title} for pile" })
                        Text(PilotPolicy.module(pin.target).title, Modifier.weight(1f))
                    }
                }
            }
        }, confirmButton = {
            TextButton(onClick = { change(DeskAction.Pile(chosen, name.trim())); arrangeDialog = false }, enabled = ready && chosen.size >= 2 && name.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp).testTag("pile-create")) { Text("Create pile") }
        }, dismissButton = { TextButton(onClick = { arrangeDialog = false }) { Text("Cancel") } })
    }
    if (viewsDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { viewsDialog = false }, title = { Text("Saved tabletop views") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A view saves all pins, piles, surface cameras and focus. It does not save documents, windows, permissions or media. 16 views maximum; same names are never overwritten.")
                OutlinedTextField(name, { if (it.length <= 48) name = it }, label = { Text("View name") }, modifier = Modifier.testTag("view-name"))
                Button(onClick = { change(DeskAction.SaveView(name.trim())); name = "" }, enabled = ready && name.isNotBlank() && doc.views.size < 16,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("view-save")) { Text("Save current view") }
                Text(model.status, Modifier.testTag("view-save-status"))
                doc.views.forEach { view ->
                    Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)).padding(8.dp)) {
                        Text(view.name, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            OutlinedButton(onClick = { pendingView = view.id }, enabled = ready, modifier = Modifier.testTag("view-load-${view.id}")) { Text("Load") }
                            TextButton(onClick = { change(DeskAction.DeleteView(view.id)) }, enabled = ready, modifier = Modifier.testTag("view-delete-${view.id}")) { Text("Delete saved view") }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { viewsDialog = false }, modifier = Modifier.testTag("views-done")) { Text("Done") } })
    }
    pendingView?.let { id ->
        AlertDialog(onDismissRequest = { pendingView = null }, title = { Text("Load saved arrangement?") },
            text = { Text("This replaces the current pins, piles and camera positions. Save the current view first if you want to keep it. Documents are not changed.") },
            confirmButton = { TextButton(onClick = { change(DeskAction.LoadView(id)); selected = null; pendingView = null; viewsDialog = false },
                modifier = Modifier.testTag("view-confirm-load"), enabled = ready) { Text("Load arrangement") } },
            dismissButton = { TextButton(onClick = { pendingView = null }) { Text("Cancel") } })
    }

    Column(Modifier.fillMaxSize().testTag("tabletop-desktop")) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (flat) "DESKTOP" else "TABLETOP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { pinsDialog = true }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag("tabletop-pins")) { Text("Pin app") }
            TextButton(onClick = { arrangeDialog = true }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag("tabletop-piles")) { Text("Piles") }
            TextButton(onClick = { viewsDialog = true }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag("tabletop-views")) { Text("Views (${doc.views.size})") }
            doc.views.forEach { view ->
                OutlinedButton(onClick = { pendingView = view.id }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag("quick-view-${view.id}")) {
                    Text("Load: ${view.name}")
                }
            }
            if (!flat) {
                FilterChip(selected = scene.focused == null, onClick = { change(DeskAction.Focus(null)); selected = null }, enabled = ready,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("surface-overview"), label = { Text("Overview") })
                DeskPlane.entries.forEach { plane ->
                    FilterChip(selected = scene.focused == plane, onClick = { change(DeskAction.Focus(plane)); selected = null }, enabled = ready,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("surface-${plane.name.lowercase()}"), label = { Text(plane.title) })
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).testTag("tabletop-canvas")
            .semantics { contentDescription = if (focused) "${active.title} workspace" else "Slanted table overview"; stateDescription = "Table area 83.16 percent of overview scene; ${scene.pins.size} pins" }) {
            val width = maxWidth.value.toDouble().coerceAtLeast(1.0); val height = maxHeight.value.toDouble().coerceAtLeast(1.0)
            val density = LocalDensity.current.density
            val fontScale = LocalDensity.current.fontScale
            val cardWidth = if (fontScale > 1.5f) 96.0 else 80.0
            val cardHeight = if (fontScale > 1.5f) 96.0 else 80.0
            var dragId by remember { mutableStateOf<Long?>(null) }
            var drag by remember { mutableStateOf(Offset.Zero) }
            var panDrag by remember { mutableStateOf(Offset.Zero) }
            val displayCamera = camera.copy(center = DeskPoint(
                (camera.center.x - panDrag.x / density / camera.zoom).coerceIn(-DeskPoint.LIMIT, DeskPoint.LIMIT),
                (camera.center.y - panDrag.y / density / camera.zoom).coerceIn(-DeskPoint.LIMIT, DeskPoint.LIMIT)))
            val colors = MaterialTheme.colorScheme
            Canvas(Modifier.fillMaxSize().pointerInput(ready, doc.revision, active, focused) {
                if (ready) detectTapGestures { point ->
                    val plane = TabletopProjection.planeAt((point.x / size.width).toDouble().coerceIn(0.0, 1.0), (point.y / size.height).toDouble().coerceIn(0.0, 1.0))
                    if (!focused && plane != DeskPlane.TABLE) change(DeskAction.Focus(plane)) else selected = null
                }
            }.pointerInput(ready, doc.revision, active) {
                if (ready) detectDragGestures(onDragStart = { panDrag = Offset.Zero }, onDragCancel = { panDrag = Offset.Zero }, onDragEnd = {
                    val delta = panDrag; panDrag = Offset.Zero; pan((-delta.x / density).toDouble(), (-delta.y / density).toDouble())
                }) { change, amount -> change.consume(); panDrag += amount }
            }) {
                fun point(plane: DeskPlane, u: Double, v: Double): Offset {
                    val p = TabletopProjection.project(plane, u, v, focused); return Offset((p.x * size.width).toFloat(), (p.y * size.height).toFloat())
                }
                fun polygon(points: List<Offset>) = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                if (focused) drawRect(Brush.verticalGradient(listOf(colors.background, colors.surfaceVariant))) else {
                    drawRect(colors.surfaceVariant)
                    val floor = polygon(listOf(point(DeskPlane.TABLE, 0.0, 0.0), point(DeskPlane.TABLE, 1.0, 0.0), point(DeskPlane.TABLE, 1.0, 1.0), point(DeskPlane.TABLE, 0.0, 1.0)))
                    drawPath(floor, Brush.verticalGradient(listOf(colors.surfaceVariant, colors.surface)))
                    drawLine(colors.outline, point(DeskPlane.TABLE, 0.0, 0.0), point(DeskPlane.TABLE, 1.0, 0.0), 1.dp.toPx())
                    drawLine(colors.outline, Offset.Zero, point(DeskPlane.TABLE, 0.0, 0.0), 1.dp.toPx())
                    drawLine(colors.outline, Offset(size.width, 0f), point(DeskPlane.TABLE, 1.0, 0.0), 1.dp.toPx())
                    // Decorative wall pin markers. Select/focus the wall to manipulate full-size icons.
                    items.filter { it.plane != DeskPlane.TABLE }.forEach { item ->
                        val c = scene.cameras[item.plane.ordinal]
                        val u = .5 + (item.point.x - c.center.x) * c.zoom / width
                        val v = .5 + (item.point.y - c.center.y) * c.zoom / height
                        if (u in 0.0..1.0 && v in 0.0..1.0) drawCircle(colors.primary, 3.dp.toPx(), point(item.plane, u, v))
                    }
                }
                // Fixed perspective guides make the slant legible without shrinking clickable icons.
                for (i in 1 until 12) {
                    val t = i / 12.0
                    drawLine(colors.outline.copy(alpha = .16f), point(active, t, 0.0), point(active, t, 1.0), .5.dp.toPx())
                    drawLine(colors.outline.copy(alpha = .16f), point(active, 0.0, t), point(active, 1.0, t), .5.dp.toPx())
                }
            }
            items.filter { it.plane == active }.forEach { item ->
                val projected = runCatching { TabletopProjection.toScreen(item.point, active, displayCamera, width, height, focused) }.getOrNull()
                if (projected != null) {
                    val extra = if (dragId == item.id) drag / density else Offset.Zero
                    val x = projected.x + extra.x; val y = projected.y + extra.y
                    // Cull, never clamp distinct offscreen pins to the same screen edge.
                    if (dragId == item.id || (x - cardWidth / 2 >= 0 && x + cardWidth / 2 <= width && y - cardHeight / 2 >= 0 && y + cardHeight / 2 <= height)) {
                        key(item.id) {
                            Column(Modifier.offset((x - cardWidth / 2).dp, (y - cardHeight / 2).dp).size(cardWidth.dp, cardHeight.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected == item.id) colors.primaryContainer else colors.surface.copy(alpha = .95f))
                                .border(if (selected == item.id) 2.dp else 1.25.dp, if (selected == item.id) colors.primary else colors.outline, RoundedCornerShape(12.dp))
                                .testTag(if (item.target == null) "desk-pile-${item.id}" else "desk-pin-${item.id}")
                                .combinedClickable(enabled = ready, role = Role.Button, onClick = { selected = item.id }, onDoubleClick = {
                                    if (item.target != null) onOpen(item.target) else selected = item.id
                                })
                                .semantics {
                                    contentDescription = item.title + if (item.target == null) ", pile of ${item.count} pins" else ", desktop pin"
                                    stateDescription = "${item.plane.title}; x ${item.point.x.toInt()}; y ${item.point.y.toInt()}"
                                    customActions = listOf(
                                        CustomAccessibilityAction("Move left") { if (ready) { move(item, active, DeskPoint((item.point.x - 32).coerceAtLeast(-DeskPoint.LIMIT), item.point.y)); true } else false },
                                        CustomAccessibilityAction("Move right") { if (ready) { move(item, active, DeskPoint((item.point.x + 32).coerceAtMost(DeskPoint.LIMIT), item.point.y)); true } else false })
                                }
                                .pointerInput(item.id, doc.revision, width, height, ready) {
                                    if (ready) detectDragGestures(onDragStart = { selected = item.id; dragId = item.id; drag = Offset.Zero },
                                        onDragCancel = { dragId = null; drag = Offset.Zero }, onDragEnd = {
                                            val delta = drag; dragId = null; drag = Offset.Zero
                                            val dropX = (projected.x + delta.x / density).coerceIn(0.0, width)
                                            val dropY = (projected.y + delta.y / density).coerceIn(0.0, height)
                                            val dropPlane = if (focused) active else TabletopProjection.planeAt((dropX / width).coerceIn(0.0, 1.0), (dropY / height).coerceIn(0.0, 1.0))
                                            val targetCam = scene.cameras[dropPlane.ordinal]
                                            val point = runCatching {
                                                TabletopProjection.toWorld(dropX, dropY, dropPlane, if (dropPlane == active) camera else targetCam, width, height, focused)
                                            }.getOrDefault(targetCam.center)
                                            move(item, dropPlane, point)
                                        }) { change, amount -> change.consume(); drag += amount }
                                }.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                if (item.target == null) Box(Modifier.size(32.dp).background(colors.primary, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    Text(item.count.toString(), color = colors.onPrimary, fontWeight = FontWeight.Bold)
                                } else AppGlyph(item.target, Modifier.size(32.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurface, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            if (items.none { it.plane == active }) Text("${active.title} · Pin an app here", Modifier.align(Alignment.Center).padding(16.dp), color = colors.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selection == null) {
                Text(if (focused) active.title else "83% table", style = MaterialTheme.typography.labelMedium)
                DeskButton("−", "Zoom out", ready && camera.zoom > .5, "tabletop-zoom-out") { change(DeskAction.Camera(active, camera.copy(zoom = (camera.zoom / 1.25).coerceAtLeast(.5)))) }
                DeskButton("+", "Zoom in", ready && camera.zoom < 3, "tabletop-zoom-in") { change(DeskAction.Camera(active, camera.copy(zoom = (camera.zoom * 1.25).coerceAtMost(3.0)))) }
                DeskButton("←", "Pan left", ready, "tabletop-pan-left") { pan(-96.0, 0.0) }
                DeskButton("→", "Pan right", ready, "tabletop-pan-right") { pan(96.0, 0.0) }
                DeskButton("↑", "Pan up", ready, "tabletop-pan-up") { pan(0.0, -96.0) }
                DeskButton("↓", "Pan down", ready, "tabletop-pan-down") { pan(0.0, 96.0) }
                TextButton(onClick = { change(DeskAction.Camera(active, DeskCamera())) }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag("tabletop-home")) { Text("Center") }
            } else {
                Text(selection.title, style = MaterialTheme.typography.labelMedium)
                if (selection.target != null) {
                    TextButton(onClick = { onOpen(selection.target) }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag("desk-open")) { Text("Open") }
                    TextButton(onClick = { change(DeskAction.RemovePin(selection.id)); selected = null }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp).testTag("desk-remove")) { Text("Unpin") }
                } else TextButton(onClick = { change(DeskAction.Unpile(selection.id)); selected = null }, enabled = ready,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("desk-unpile")) { Text("Unpile (${selection.count})") }
                DeskButton("←", "Move pin left", ready, "desk-left") { move(selection, active, DeskPoint((selection.point.x - 32).coerceAtLeast(-DeskPoint.LIMIT), selection.point.y)) }
                DeskButton("→", "Move pin right", ready, "desk-right") { move(selection, active, DeskPoint((selection.point.x + 32).coerceAtMost(DeskPoint.LIMIT), selection.point.y)) }
                DeskButton("↑", "Move pin up", ready, "desk-up") { move(selection, active, DeskPoint(selection.point.x, (selection.point.y - 32).coerceAtLeast(-DeskPoint.LIMIT))) }
                DeskButton("↓", "Move pin down", ready, "desk-down") { move(selection, active, DeskPoint(selection.point.x, (selection.point.y + 32).coerceAtMost(DeskPoint.LIMIT))) }
                DeskPlane.entries.filter { it != active }.forEach { plane ->
                    TextButton(onClick = { move(selection, plane, scene.cameras[plane.ordinal].center); selected = null }, enabled = ready,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("desk-to-${plane.name.lowercase()}")) { Text("To ${plane.title.lowercase()}") }
                }
                TextButton(onClick = { selected = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("desk-deselect")) { Text("Done") }
            }
        }
        Text(model.status, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).testTag("tabletop-status").semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DeskButton(label: String, description: String, enabled: Boolean, tag: String, action: () -> Unit) {
    OutlinedButton(onClick = action, enabled = enabled, modifier = Modifier.size(48.dp).testTag(tag).semantics { contentDescription = description },
        contentPadding = PaddingValues(2.dp)) { Text(label) }
}

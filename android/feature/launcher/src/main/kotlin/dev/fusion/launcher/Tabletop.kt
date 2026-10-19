// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

import java.util.Collections
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

enum class DeskPlane(val title: String) { TABLE("Table"), BACK("Back wall"), LEFT("Left wall"), RIGHT("Right wall") }
data class DeskPoint(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite() && abs(x) <= LIMIT && abs(y) <= LIMIT) { "Position exceeds the finite workspace" } }
    companion object { const val LIMIT = 1_000_000.0 }
}
data class DeskCamera(val center: DeskPoint = DeskPoint(0.0, 0.0), val zoom: Double = 1.0) {
    init { require(zoom.isFinite() && zoom in .5..3.0) }
}
data class DeskPin(val id: Long, val target: String, val plane: DeskPlane, val point: DeskPoint, val pile: Long? = null) {
    init { require(id > 0 && (pile == null || pile > 0)); require(target.matches(Regex("[a-z][a-z0-9-]{0,63}"))) }
}
data class DeskPile(val id: Long, val name: String, val plane: DeskPlane, val point: DeskPoint) {
    init { require(id > 0); deskName(name) }
}
internal fun deskName(value: String) {
    require(value.length in 1..48 && value == value.trim() && value.none { it.isISOControl() || it.isSurrogate() }) { "Use 1–48 printable characters, without outer spaces" }
}
private fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

/** Immutable snapshots: caller-owned lists cannot mutate live or saved arrangements. */
class TabletopScene(pins: List<DeskPin>, piles: List<DeskPile> = emptyList(),
                    cameras: List<DeskCamera> = List(4) { DeskCamera() }, val focused: DeskPlane? = null) {
    val pins = frozen(pins)
    val piles = frozen(piles)
    val cameras = frozen(cameras)
    init {
        require(pins.size <= MAX_PINS && piles.size <= MAX_PILES && cameras.size == DeskPlane.entries.size)
        require((pins.map { it.id } + piles.map { it.id }).let { it.size == it.distinct().size }) { "Duplicate object ID" }
        val byId = piles.associateBy { it.id }
        pins.forEach { pin -> pin.pile?.let { id ->
            val pile = requireNotNull(byId[id]) { "Missing pile" }
            require(pin.plane == pile.plane && pin.point == pile.point) { "Pile members must share their anchor" }
        } }
        piles.forEach { pile -> require(pins.count { it.pile == pile.id } >= 2) { "A pile needs two members" } }
    }
    fun copy(pins: List<DeskPin> = this.pins, piles: List<DeskPile> = this.piles,
             cameras: List<DeskCamera> = this.cameras, focused: DeskPlane? = this.focused) = TabletopScene(pins, piles, cameras, focused)
    override fun equals(other: Any?) = other is TabletopScene && pins == other.pins && piles == other.piles && cameras == other.cameras && focused == other.focused
    override fun hashCode() = listOf(pins, piles, cameras, focused).hashCode()
    companion object { const val MAX_PINS = 512; const val MAX_PILES = 128 }
}
data class DeskView(val id: Long, val name: String, val scene: TabletopScene) {
    init { require(id > 0); deskName(name) }
}
class TabletopDocument(val revision: Long, val nextId: Long, val scene: TabletopScene, views: List<DeskView> = emptyList()) {
    val views = frozen(views)
    init {
        require(revision in 0 until Long.MAX_VALUE && nextId in 1 until Long.MAX_VALUE && views.size <= MAX_VIEWS)
        require(views.map { it.id }.distinct().size == views.size && views.map { it.name.lowercase(java.util.Locale.ROOT) }.distinct().size == views.size)
        val allScenes = listOf(scene) + views.map { it.scene }
        require(allScenes.all { s -> (s.pins.map { it.id } + s.piles.map { it.id }).all { it < nextId } } && views.all { it.id < nextId })
    }
    override fun equals(other: Any?) = other is TabletopDocument && revision == other.revision && nextId == other.nextId && scene == other.scene && views == other.views
    override fun hashCode() = listOf(revision, nextId, scene, views).hashCode()
    companion object {
        const val MAX_VIEWS = 16
        fun initial(): TabletopDocument {
            val targets = listOf("files", "editor", "notes", "calculator", "media", "maps", "automation", "settings")
            return TabletopDocument(0, 9, TabletopScene(targets.mapIndexed { i, target ->
                DeskPin(i + 1L, target, DeskPlane.TABLE, DeskPoint(((i % 3) - 1) * 104.0, ((i / 3) - 1) * 100.0))
            }))
        }
    }
}
sealed interface DeskAction {
    data class Add(val target: String, val plane: DeskPlane, val point: DeskPoint) : DeskAction
    data class MovePin(val id: Long, val plane: DeskPlane, val point: DeskPoint) : DeskAction
    data class MovePile(val id: Long, val plane: DeskPlane, val point: DeskPoint) : DeskAction
    data class RemovePin(val id: Long) : DeskAction
    data class Pile(val ids: Set<Long>, val name: String) : DeskAction
    data class Unpile(val id: Long) : DeskAction
    data class Camera(val plane: DeskPlane, val camera: DeskCamera) : DeskAction
    data class Focus(val plane: DeskPlane?) : DeskAction
    data class SaveView(val name: String) : DeskAction
    data class LoadView(val id: Long) : DeskAction
    data class DeleteView(val id: Long) : DeskAction
}

/** Every accepted change advances one revision; errors leave the supplied immutable document intact. */
object TabletopEngine {
    fun apply(before: TabletopDocument, action: DeskAction): TabletopDocument {
        var scene = before.scene; var views = before.views; var next = before.nextId
        fun allocate(): Long { require(next < Long.MAX_VALUE - 1) { "ID capacity reached" }; return next++ }
        when (action) {
            is DeskAction.Add -> {
                require(scene.pins.size < TabletopScene.MAX_PINS) { "512-pin capacity reached; nothing added" }
                scene = scene.copy(pins = scene.pins + DeskPin(allocate(), action.target, action.plane, action.point))
            }
            is DeskAction.MovePin -> {
                val pin = scene.pins.single { it.id == action.id }; require(pin.pile == null) { "Move the pile or unpile first" }
                scene = scene.copy(pins = scene.pins.map { if (it.id == pin.id) it.copy(plane = action.plane, point = action.point) else it })
            }
            is DeskAction.MovePile -> {
                require(scene.piles.any { it.id == action.id })
                scene = scene.copy(pins = scene.pins.map { if (it.pile == action.id) it.copy(plane = action.plane, point = action.point) else it },
                    piles = scene.piles.map { if (it.id == action.id) it.copy(plane = action.plane, point = action.point) else it })
            }
            is DeskAction.RemovePin -> {
                require(scene.pins.any { it.id == action.id })
                val pins = scene.pins.filterNot { it.id == action.id }
                val kept = scene.piles.filter { pile -> pins.count { it.pile == pile.id } >= 2 }
                val keptIds = kept.map { it.id }.toSet()
                scene = scene.copy(pins = pins.map { if (it.pile != null && it.pile !in keptIds) it.copy(pile = null) else it }, piles = kept)
            }
            is DeskAction.Pile -> {
                deskName(action.name)
                require(scene.piles.size < TabletopScene.MAX_PILES && action.ids.size in 2..TabletopScene.MAX_PINS)
                val selected = scene.pins.filter { it.id in action.ids }
                require(selected.size == action.ids.size && selected.all { it.pile == null } && selected.map { it.plane }.distinct().size == 1) { "Select two or more unpiled pins on one surface" }
                val anchor = DeskPoint(selected.sumOf { it.point.x } / selected.size, selected.sumOf { it.point.y } / selected.size)
                val pile = DeskPile(allocate(), action.name, selected.first().plane, anchor)
                scene = scene.copy(pins = scene.pins.map { if (it.id in action.ids) it.copy(point = anchor, pile = pile.id) else it }, piles = scene.piles + pile)
            }
            is DeskAction.Unpile -> {
                val pile = scene.piles.single { it.id == action.id }
                val members = scene.pins.filter { it.pile == pile.id }; val columns = ceil(sqrt(members.size.toDouble())).toInt()
                val positions = members.mapIndexed { i, pin -> pin.id to DeskPoint(
                    (pile.point.x + ((i % columns) - (columns - 1) / 2.0) * 88).coerceIn(-DeskPoint.LIMIT, DeskPoint.LIMIT),
                    (pile.point.y + (i / columns) * 88).coerceIn(-DeskPoint.LIMIT, DeskPoint.LIMIT)) }.toMap()
                scene = scene.copy(pins = scene.pins.map { if (it.pile == pile.id) it.copy(pile = null, point = positions.getValue(it.id)) else it }, piles = scene.piles.filterNot { it.id == pile.id })
            }
            is DeskAction.Camera -> scene = scene.copy(cameras = scene.cameras.mapIndexed { i, camera -> if (i == action.plane.ordinal) action.camera else camera })
            is DeskAction.Focus -> scene = scene.copy(focused = action.plane)
            is DeskAction.SaveView -> {
                deskName(action.name); require(views.size < TabletopDocument.MAX_VIEWS) { "16 saved views maximum; delete one first" }
                require(views.none { it.name.equals(action.name, ignoreCase = true) }) { "A view with that name exists; nothing overwritten" }
                views = views + DeskView(allocate(), action.name, scene)
            }
            is DeskAction.LoadView -> scene = views.single { it.id == action.id }.scene
            is DeskAction.DeleteView -> { require(views.any { it.id == action.id }); views = views.filterNot { it.id == action.id } }
        }
        if (scene == before.scene && views == before.views && next == before.nextId) return before
        require(before.revision < Long.MAX_VALUE - 1) { "Revision capacity reached" }
        return TabletopDocument(before.revision + 1, next, scene, views)
    }
}

/** Scene-normalized projection. The table is a true projective trapezoid, not a tilted hitbox. */
object TabletopProjection {
    const val INSET = .055
    const val HORIZON = .12
    const val TABLE_AREA = (1.0 - HORIZON) * (1.0 - INSET) // 0.8316 of scene, excluding chrome.
    data class Point(val x: Double, val y: Double)
    fun project(plane: DeskPlane, u: Double, v: Double, focused: Boolean = false): Point {
        require(u.isFinite() && v.isFinite())
        if (focused) return Point(u, v)
        return when (plane) {
            DeskPlane.TABLE -> {
                val back = 1 - 2 * INSET; val divisor = 1 + (1 / back - 1) * (1 - v)
                require(divisor > .01) { "Point is behind the projective camera" }
                Point(.5 + (u - .5) / divisor, (HORIZON * (1 - v) / back + v) / divisor)
            }
            DeskPlane.BACK -> Point(INSET * v + u * (1 - 2 * INSET * v), HORIZON * v)
            DeskPlane.LEFT -> Point(INSET * u * (1 - v), HORIZON * u * (1 - v) + v)
            DeskPlane.RIGHT -> Point(1 - INSET * (1 - u) * (1 - v), HORIZON * (1 - u) * (1 - v) + v)
        }
    }
    fun inverse(plane: DeskPlane, x: Double, y: Double, focused: Boolean = false): Point {
        require(x.isFinite() && y.isFinite())
        if (focused) return Point(x, y)
        return when (plane) {
            DeskPlane.TABLE -> {
                val back = 1 - 2 * INSET; val denominator = back - HORIZON + y * (1 - back)
                require(denominator > .01)
                val v = (y - HORIZON) / denominator; val divisor = 1 + (1 / back - 1) * (1 - v)
                Point(.5 + (x - .5) * divisor, v)
            }
            DeskPlane.BACK -> { val v = y / HORIZON; val width = 1 - 2 * INSET * v; require(width > .01); Point((x - INSET * v) / width, v) }
            DeskPlane.LEFT, DeskPlane.RIGHT -> {
                val t = (if (plane == DeskPlane.LEFT) x else 1 - x) / INSET
                val v = y - HORIZON * t; require(abs(1 - v) > 1e-9) { "Wall apex has no unique inverse" }
                val u = t / (1 - v); Point(if (plane == DeskPlane.LEFT) u else 1 - u, v)
            }
        }
    }
    fun planeAt(x: Double, y: Double): DeskPlane {
        require(x in 0.0..1.0 && y in 0.0..1.0)
        if (y < HORIZON) {
            val inset = INSET * y / HORIZON
            return if (x < inset) DeskPlane.LEFT else if (x > 1 - inset) DeskPlane.RIGHT else DeskPlane.BACK
        }
        val inset = INSET * (1 - y) / (1 - HORIZON)
        return if (x < inset) DeskPlane.LEFT else if (x > 1 - inset) DeskPlane.RIGHT else DeskPlane.TABLE
    }
    fun toScreen(point: DeskPoint, plane: DeskPlane, camera: DeskCamera, width: Double, height: Double, focused: Boolean): Point {
        require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
        val p = project(plane, .5 + (point.x - camera.center.x) * camera.zoom / width,
            .5 + (point.y - camera.center.y) * camera.zoom / height, focused)
        return Point(p.x * width, p.y * height)
    }
    fun toWorld(x: Double, y: Double, plane: DeskPlane, camera: DeskCamera, width: Double, height: Double, focused: Boolean): DeskPoint {
        require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
        val p = inverse(plane, x / width, y / height, focused)
        return DeskPoint((camera.center.x + (p.x - .5) * width / camera.zoom).coerceIn(-DeskPoint.LIMIT, DeskPoint.LIMIT),
            (camera.center.y + (p.y - .5) * height / camera.zoom).coerceIn(-DeskPoint.LIMIT, DeskPoint.LIMIT))
    }
}

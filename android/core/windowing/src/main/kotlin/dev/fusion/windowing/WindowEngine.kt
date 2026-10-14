package dev.fusion.windowing

import kotlin.math.min
import java.util.Collections

enum class WindowDensity(val minWidth: Float, val minHeight: Float) {
    COMPACT(240f, 180f), BALANCED(240f, 180f), TOUCH(320f, 240f)
}

data class Bounds(val x: Float, val y: Float, val width: Float, val height: Float) {
    val finite: Boolean get() = listOf(x, y, width, height).all { it.isFinite() }
    fun constrained(screenWidth: Float, screenHeight: Float, density: WindowDensity = WindowDensity.COMPACT): Bounds {
        val sw = screenWidth.takeIf { it.isFinite() && it > 0 } ?: 800f
        val sh = screenHeight.takeIf { it.isFinite() && it > 0 } ?: 600f
        val w = (width.takeIf { it.isFinite() } ?: density.minWidth).coerceIn(density.minWidth, maxOf(sw, density.minWidth))
        val h = (height.takeIf { it.isFinite() } ?: density.minHeight).coerceIn(density.minHeight, maxOf(sh, density.minHeight))
        val reachable = min(48f, sw)
        return Bounds(
            (x.takeIf { it.isFinite() } ?: 0f).coerceIn(-w + reachable, sw - reachable),
            (y.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, maxOf(0f, sh - min(48f, sh))), w, h
        )
    }
}

data class ShellWindow(
    val id: Long, val module: String, val title: String, val bounds: Bounds, val z: Int,
    val minimized: Boolean = false, val workspace: Int = 0,
    val openKey: String? = null,
    val restoreBounds: Bounds? = null,
    val tile: Tile? = null,
)
data class DesktopState(
    val windows: List<ShellWindow> = emptyList(), val workspace: Int = 0,
    val activeId: Long? = null, val nextId: Long = 1,
    val screenWidth: Float = 800f, val screenHeight: Float = 600f,
    val density: WindowDensity = WindowDensity.COMPACT,
    val commandEpoch: String? = null, val lastSequence: Long = 0,
)
enum class Tile { LEFT, RIGHT, MAXIMIZE }

sealed interface WindowCommand {
    /** Key deduplicates LIVE windows only. For retries across Close, use DesktopCommandStream. */
    data class Open(val module: String, val title: String, val bounds: Bounds, val key: String? = null) : WindowCommand
    data class Focus(val id: Long) : WindowCommand
    data class FocusNext(val reverse: Boolean = false) : WindowCommand
    data class SetBounds(val id: Long, val bounds: Bounds) : WindowCommand
    data class SetViewport(val width: Float, val height: Float) : WindowCommand
    data class SetDensity(val density: WindowDensity) : WindowCommand
    // Relative pointer/keyboard input events are intentionally not retryable state setters.
    data class Move(val id: Long, val dx: Float, val dy: Float, val sw: Float, val sh: Float) : WindowCommand
    data class Resize(val id: Long, val dw: Float, val dh: Float, val sw: Float, val sh: Float) : WindowCommand
    data class TileWindow(val id: Long, val tile: Tile, val sw: Float, val sh: Float) : WindowCommand
    data class RestoreBounds(val id: Long) : WindowCommand
    data class Minimize(val id: Long) : WindowCommand
    data class Close(val id: Long) : WindowCommand
    data class SwitchWorkspace(val workspace: Int) : WindowCommand
    data class MoveToWorkspace(val id: Long, val workspace: Int) : WindowCommand
}

/** WM-01/02/05/09, SH-08. No Android, time, IO or random state in this reducer. */
object WindowEngine {
    const val MAX_WINDOWS = 48
    const val WORKSPACES = 3

    fun restore(state: DesktopState): DesktopState {
        DesktopCommandStream.validateMetadata(state)
        require(state.windows.size <= MAX_WINDOWS)
        require(state.windows.map { it.id }.distinct().size == state.windows.size)
        require(state.windows.all { it.id in 1 until Long.MAX_VALUE - 1 && it.bounds.finite })
        require(state.windows.all { it.module.matches(Regex("[a-z][a-z0-9_-]{0,63}")) && it.title.length <= 256 })
        require(state.windows.all { (it.tile == null) == (it.restoreBounds == null) && it.restoreBounds?.finite != false })
        val keys = state.windows.mapNotNull { it.openKey }
        require(keys.distinct().size == keys.size && keys.all { it.length in 1..80 })
        require(validViewport(state.screenWidth, state.screenHeight))
        val ws = state.workspace.coerceIn(0, WORKSPACES - 1)
        val windows = state.windows.sortedWith(compareBy<ShellWindow> { it.z }.thenBy { it.id }).mapIndexed { z, w ->
            reflowWindow(w.copy(z = z, workspace = w.workspace.coerceIn(0, WORKSPACES - 1)), state)
        }
        val active = windows.find { it.id == state.activeId && !it.minimized && it.workspace == ws }?.id
            ?: windows.lastOrNull { !it.minimized && it.workspace == ws }?.id
        return immutable(state.copy(windows = windows, workspace = ws, activeId = active,
            nextId = maxOf(state.nextId, (windows.maxOfOrNull { it.id } ?: 0L) + 1).coerceAtMost(Long.MAX_VALUE - 1)))
    }

    private fun validViewport(w: Float, h: Float) = w.isFinite() && h.isFinite() && w > 0 && h > 0 && w <= 100_000 && h <= 100_000
    fun canTile(s: DesktopState, tile: Tile): Boolean = validViewport(s.screenWidth,s.screenHeight) &&
        s.screenHeight >= s.density.minHeight && s.screenWidth >= s.density.minWidth * if(tile == Tile.MAXIMIZE) 1 else 2
    private fun tileBounds(tile: Tile, sw: Float, sh: Float): Bounds = when(tile) {
        Tile.LEFT -> Bounds(0f,0f,sw/2f,sh)
        Tile.RIGHT -> Bounds(sw/2f,0f,sw/2f,sh)
        Tile.MAXIMIZE -> Bounds(0f,0f,sw,sh)
    }
    private fun reflowWindow(w: ShellWindow, s: DesktopState): ShellWindow {
        val tile=w.tile
        val normal=w.restoreBounds?.constrained(s.screenWidth,s.screenHeight,s.density)
        // If a tile no longer fits, return to reachable normal geometry, never overlap fake halves.
        return if(tile!=null && normal!=null && canTile(s,tile)) w.copy(
            bounds=tileBounds(tile,s.screenWidth,s.screenHeight),restoreBounds=normal)
        else w.copy(bounds=(normal ?: w.bounds).constrained(s.screenWidth,s.screenHeight,s.density),restoreBounds=null,tile=null)
    }
    private fun reflow(s: DesktopState) = s.copy(windows=s.windows.map { reflowWindow(it,s) })
    private fun top(s: DesktopState) = s.windows.lastOrNull { it.workspace == s.workspace && !it.minimized }?.id
    private fun focus(s: DesktopState, id: Long): DesktopState {
        val target = s.windows.find { it.id == id } ?: return s
        if (s.activeId == id && s.windows.lastOrNull { it.workspace == s.workspace && !it.minimized }?.id == id && !target.minimized) return s
        val ordered = s.windows.filterNot { it.id == id } + target.copy(minimized = false)
        return s.copy(windows = ordered.mapIndexed { z, w -> w.copy(z = z) }, activeId = id, workspace = target.workspace)
    }
    private fun changeBounds(s: DesktopState, id: Long, bounds: Bounds): DesktopState {
        if (!bounds.finite) return s
        return s.copy(windows = s.windows.map { if (it.id == id) it.copy(bounds = bounds.constrained(s.screenWidth, s.screenHeight, s.density), restoreBounds = null, tile = null) else it })
    }

    private fun immutable(state:DesktopState)=state.copy(windows=Collections.unmodifiableList(ArrayList(state.windows)))
    // Every published result owns its window-list snapshot, including no-op commands.
    // Callers must not mutate input collections concurrently during admission/reduction.
    fun reduce(state: DesktopState, command: WindowCommand): DesktopState = immutable(reduceStep(state,command))
    private fun reduceStep(state: DesktopState, command: WindowCommand): DesktopState = when (command) {
        is WindowCommand.Open -> {
            val existing = command.key?.let { key -> state.windows.find { it.openKey == key } }
            when {
                existing != null -> state // Replay does not steal focus or move a window.
                state.windows.size >= MAX_WINDOWS || state.nextId >= Long.MAX_VALUE - 1 -> state
                !command.bounds.finite || !command.module.matches(Regex("[a-z][a-z0-9_-]{0,63}")) -> state
                command.key != null && command.key.length !in 1..80 -> state
                else -> {
                    val w = ShellWindow(state.nextId, command.module, command.title.take(256),
                        command.bounds.constrained(state.screenWidth, state.screenHeight, state.density),
                        state.windows.size, workspace = state.workspace, openKey = command.key)
                    state.copy(windows = state.windows + w, activeId = w.id, nextId = w.id + 1)
                }
            }
        }
        is WindowCommand.Focus -> focus(state, command.id)
        is WindowCommand.FocusNext -> {
            // Stable ID order; rotating by changing z-order would skip windows.
            val candidates = state.windows.filter { it.workspace == state.workspace && !it.minimized }.sortedBy { it.id }
            if (candidates.isEmpty()) state else {
                val index = candidates.indexOfFirst { it.id == state.activeId }
                val next = if (index < 0) 0 else Math.floorMod(index + if (command.reverse) -1 else 1, candidates.size)
                focus(state, candidates[next].id)
            }
        }
        is WindowCommand.SetBounds -> changeBounds(state, command.id, command.bounds)
        is WindowCommand.SetViewport -> if (!validViewport(command.width, command.height)) state
            else reflow(state.copy(screenWidth = command.width, screenHeight = command.height))
        is WindowCommand.SetDensity -> reflow(state.copy(density = command.density))
        is WindowCommand.Move -> {
            val w = state.windows.find { it.id == command.id }
            if (w == null || !listOf(command.dx, command.dy).all { it.isFinite() } || (!validViewport(command.sw, command.sh) || command.sw != state.screenWidth || command.sh != state.screenHeight)) state else changeBounds(state, w.id,
                w.bounds.copy(x = w.bounds.x + command.dx, y = w.bounds.y + command.dy).constrained(command.sw, command.sh, state.density))
        }
        is WindowCommand.Resize -> {
            val w = state.windows.find { it.id == command.id }
            if (w == null || !listOf(command.dw, command.dh).all { it.isFinite() } || (!validViewport(command.sw, command.sh) || command.sw != state.screenWidth || command.sh != state.screenHeight)) state
            else changeBounds(state, w.id, w.bounds.copy(width = w.bounds.width + command.dw, height = w.bounds.height + command.dh).constrained(command.sw, command.sh, state.density))
        }
        is WindowCommand.TileWindow -> {
            // Delayed geometry commands from an old viewport must not lay out a new display.
            if(command.sw != state.screenWidth || command.sh != state.screenHeight || !canTile(state,command.tile)) state
            else state.copy(windows=state.windows.map { w ->
                if(w.id != command.id) w else w.copy(bounds=tileBounds(command.tile,state.screenWidth,state.screenHeight),
                    restoreBounds=w.restoreBounds ?: w.bounds, tile=command.tile)
            })
        }
        is WindowCommand.RestoreBounds -> state.copy(windows=state.windows.map { w ->
            val normal=w.restoreBounds
            if(w.id != command.id || normal==null) w else w.copy(
                bounds=normal.constrained(state.screenWidth,state.screenHeight,state.density),restoreBounds=null,tile=null)
        })
        is WindowCommand.Minimize -> {
            val updated = state.copy(windows = state.windows.map { if (it.id == command.id) it.copy(minimized = true) else it })
            if (state.activeId == command.id) updated.copy(activeId = top(updated)) else updated
        }
        is WindowCommand.Close -> {
            val updated = state.copy(windows = state.windows.filterNot { it.id == command.id }.mapIndexed { z, w -> w.copy(z = z) })
            if (state.activeId == command.id) updated.copy(activeId = top(updated)) else updated
        }
        is WindowCommand.SwitchWorkspace -> {
            val next = state.copy(workspace = command.workspace.coerceIn(0, WORKSPACES - 1))
            if (next.workspace == state.workspace) state else next.copy(activeId = top(next))
        }
        is WindowCommand.MoveToWorkspace -> {
            val next = state.copy(windows = state.windows.map { if (it.id == command.id) it.copy(workspace = command.workspace.coerceIn(0, WORKSPACES - 1)) else it })
            if (next.windows.none { it.id == next.activeId && it.workspace == next.workspace && !it.minimized }) next.copy(activeId = top(next)) else next
        }
    }
}

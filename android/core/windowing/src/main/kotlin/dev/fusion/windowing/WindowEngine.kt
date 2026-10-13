package dev.fusion.windowing

data class Bounds(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun constrained(screenWidth: Float, screenHeight: Float): Bounds {
        val w = width.coerceAtLeast(240f).coerceAtMost(screenWidth.coerceAtLeast(240f))
        val h = height.coerceAtLeast(180f).coerceAtMost(screenHeight.coerceAtLeast(180f))
        return Bounds(
            x.coerceIn(-w + 48f, (screenWidth - 48f).coerceAtLeast(-w + 48f)),
            y.coerceIn(0f, (screenHeight - 48f).coerceAtLeast(0f)),
            w,
            h
        )
    }
}

data class ShellWindow(
    val id: Long,
    val module: String,
    val title: String,
    val bounds: Bounds,
    val z: Int,
    val minimized: Boolean = false,
    val workspace: Int = 0
)

data class DesktopState(
    val windows: List<ShellWindow> = emptyList(),
    val workspace: Int = 0,
    val activeId: Long? = null,
    val nextId: Long = 1
)

enum class Tile { LEFT, RIGHT, MAXIMIZE }

sealed interface WindowCommand {
    data class Open(val module: String, val title: String, val bounds: Bounds) : WindowCommand
    data class Focus(val id: Long) : WindowCommand
    data class Move(val id: Long, val dx: Float, val dy: Float, val sw: Float, val sh: Float) : WindowCommand
    data class Resize(val id: Long, val dw: Float, val dh: Float, val sw: Float, val sh: Float) : WindowCommand
    data class TileWindow(val id: Long, val tile: Tile, val sw: Float, val sh: Float) : WindowCommand
    data class Minimize(val id: Long) : WindowCommand
    data class Close(val id: Long) : WindowCommand
    data class SwitchWorkspace(val workspace: Int) : WindowCommand
}

/** Pure reducer. Rendering, persistence and Android APIs remain outside this module. */
object WindowEngine {
    fun reduce(state: DesktopState, command: WindowCommand): DesktopState {
        val target = when (command) {
            is WindowCommand.Open, is WindowCommand.SwitchWorkspace -> null
            is WindowCommand.Focus -> command.id
            is WindowCommand.Move -> command.id
            is WindowCommand.Resize -> command.id
            is WindowCommand.TileWindow -> command.id
            is WindowCommand.Minimize -> command.id
            is WindowCommand.Close -> command.id
        }
        if (target != null && state.windows.none { it.id == target }) return state

        return when (command) {
            is WindowCommand.Open -> {
                val next = ShellWindow(
                    id = state.nextId,
                    module = command.module,
                    title = command.title,
                    bounds = command.bounds,
                    z = (state.windows.maxOfOrNull { it.z } ?: 0) + 1,
                    workspace = state.workspace
                )
                state.copy(windows = state.windows + next, activeId = next.id, nextId = next.id + 1)
            }
            is WindowCommand.Focus -> {
                val top = (state.windows.maxOfOrNull { it.z } ?: 0) + 1
                state.copy(
                    windows = state.windows.map { if (it.id == command.id) it.copy(z = top, minimized = false) else it },
                    activeId = command.id
                )
            }
            is WindowCommand.Move -> state.copy(windows = state.windows.map { w ->
                if (w.id == command.id) w.copy(bounds = w.bounds.copy(
                    x = w.bounds.x + command.dx, y = w.bounds.y + command.dy
                ).constrained(command.sw, command.sh)) else w
            })
            is WindowCommand.Resize -> state.copy(windows = state.windows.map { w ->
                if (w.id == command.id) w.copy(bounds = w.bounds.copy(
                    width = w.bounds.width + command.dw, height = w.bounds.height + command.dh
                ).constrained(command.sw, command.sh)) else w
            })
            is WindowCommand.TileWindow -> state.copy(windows = state.windows.map { w ->
                if (w.id != command.id) w else w.copy(bounds = when (command.tile) {
                    Tile.LEFT -> Bounds(0f, 0f, command.sw / 2f, command.sh)
                    Tile.RIGHT -> Bounds(command.sw / 2f, 0f, command.sw / 2f, command.sh)
                    Tile.MAXIMIZE -> Bounds(0f, 0f, command.sw, command.sh)
                })
            })
            is WindowCommand.Minimize -> {
                val windows = state.windows.map { if (it.id == command.id) it.copy(minimized = true) else it }
                state.copy(windows = windows, activeId = windows.filter {
                    it.workspace == state.workspace && !it.minimized
                }.maxByOrNull { it.z }?.id)
            }
            is WindowCommand.Close -> {
                val remaining = state.windows.filterNot { it.id == command.id }
                state.copy(windows = remaining, activeId = remaining.filter {
                    it.workspace == state.workspace && !it.minimized
                }.maxByOrNull { it.z }?.id)
            }
            is WindowCommand.SwitchWorkspace -> {
                val workspace = command.workspace.coerceIn(0, 2)
                state.copy(workspace = workspace, activeId = state.windows.filter {
                    it.workspace == workspace && !it.minimized
                }.maxByOrNull { it.z }?.id)
            }
        }
    }
}
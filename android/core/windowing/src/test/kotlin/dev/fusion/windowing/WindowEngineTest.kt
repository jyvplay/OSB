package dev.fusion.windowing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowEngineTest {
    @Test fun openFocusCloseAreDeterministic() {
        val initial = DesktopState()
        val a = WindowEngine.reduce(initial, WindowCommand.Open("files", "Files", Bounds(5f, 5f, 500f, 400f)))
        val b = WindowEngine.reduce(a, WindowCommand.Open("editor", "Editor", Bounds(20f, 20f, 500f, 400f)))
        assertEquals(2, b.windows.size)
        assertEquals(2L, b.activeId)
        val focused = WindowEngine.reduce(b, WindowCommand.Focus(1L))
        assertEquals(1L, focused.activeId)
        assertEquals(focused, WindowEngine.reduce(focused, WindowCommand.Close(999L)))
        assertEquals(2L, WindowEngine.reduce(focused, WindowCommand.Close(1L)).activeId)
    }

    @Test fun geometryRetainsReachableTitleBar() {
        val bounds = Bounds(100000f, 100000f, 30f, 10f).constrained(800f, 600f)
        assertTrue(bounds.width >= 240f && bounds.height >= 180f)
        assertTrue(bounds.x <= 752f && bounds.y <= 552f)
        val left = WindowEngine.reduce(
            WindowEngine.reduce(DesktopState(), WindowCommand.Open("files", "Files", bounds)),
            WindowCommand.TileWindow(1L, Tile.LEFT, 800f, 600f)
        )
        assertEquals(Bounds(0f, 0f, 400f, 600f), left.windows.single().bounds)
    }

    @Test fun workspacesIsolateActiveWindow() {
        val a = WindowEngine.reduce(DesktopState(), WindowCommand.Open("files", "Files", Bounds(0f, 0f, 300f, 200f)))
        val b = WindowEngine.reduce(a, WindowCommand.SwitchWorkspace(1))
        assertEquals(null, b.activeId)
        assertEquals(1L, WindowEngine.reduce(b, WindowCommand.SwitchWorkspace(0)).activeId)
    }
}
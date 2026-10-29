// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/**
 * Clean-room Per-Context Phone / Tabletop / External-Desktop Profile Engine (`ST-09`) and
 * Multi-Window Workspace Preset Restorer (`AU-02`).
 */
object WorkspaceContextProfileEngine {
    const val SCOPE =
        "Pure-JVM phone/tabletop/desktop per-context profile selector (ST-09) and named multi-window workspace preset restorer (AU-02)."

    enum class ContextPosture { COMPACT_PHONE, FOLD_TABLETOP, EXTERNAL_DESKTOP }

    data class ContextProfile(
        val posture: ContextPosture,
        val dockAutoHide: Boolean,
        val uiScalePercent: Int,
        val maxSimultaneousTiledWindows: Int,
        val defaultInputPointerMode: Boolean,
    )

    data class WindowSlotPreset(
        val moduleRoute: String,
        val normalizedX: Double,
        val normalizedY: Double,
        val normalizedW: Double,
        val normalizedH: Double,
    )

    data class WorkspacePreset(
        val presetId: String,
        val title: String,
        val slots: List<WindowSlotPreset>,
    )

    fun selectContextProfile(
        smallestWidthDp: Int,
        externalDisplayConnected: Boolean,
        hingeHalfOpened: Boolean,
    ): ContextProfile = when {
        externalDisplayConnected || smallestWidthDp >= 840 -> ContextProfile(
            posture = ContextPosture.EXTERNAL_DESKTOP,
            dockAutoHide = false,
            uiScalePercent = 100,
            maxSimultaneousTiledWindows = 4,
            defaultInputPointerMode = true,
        )
        hingeHalfOpened || smallestWidthDp >= 600 -> ContextProfile(
            posture = ContextPosture.FOLD_TABLETOP,
            dockAutoHide = true,
            uiScalePercent = 105,
            maxSimultaneousTiledWindows = 2,
            defaultInputPointerMode = false,
        )
        else -> ContextProfile(
            posture = ContextPosture.COMPACT_PHONE,
            dockAutoHide = false,
            uiScalePercent = 100,
            maxSimultaneousTiledWindows = 1,
            defaultInputPointerMode = false,
        )
    }

    fun builtInPresets(): List<WorkspacePreset> = listOf(
        WorkspacePreset(
            "writer_research",
            "Writer + Browser Research Split",
            listOf(
                WindowSlotPreset("editor", 0.0, 0.0, 0.52, 1.0),
                WindowSlotPreset("browser", 0.52, 0.0, 0.48, 1.0),
            ),
        ),
        WorkspacePreset(
            "sheet_terminal_dev",
            "Spreadsheet + Terminal + Files",
            listOf(
                WindowSlotPreset("calc", 0.0, 0.0, 0.55, 1.0),
                WindowSlotPreset("terminal", 0.55, 0.0, 0.45, 0.55),
                WindowSlotPreset("files", 0.55, 0.55, 0.45, 0.45),
            ),
        ),
    )

    fun materializeBoundsPx(preset: WorkspacePreset, displayW: Int, displayH: Int): List<Pair<String, IntArray>> {
        require(displayW >= 320 && displayH >= 320) { "display dimensions < 320px" }
        return preset.slots.map { s ->
            s.moduleRoute to intArrayOf(
                (s.normalizedX * displayW).toInt(),
                (s.normalizedY * displayH).toInt(),
                (s.normalizedW * displayW).toInt().coerceAtLeast(160),
                (s.normalizedH * displayH).toInt().coerceAtLeast(160),
            )
        }
    }
}

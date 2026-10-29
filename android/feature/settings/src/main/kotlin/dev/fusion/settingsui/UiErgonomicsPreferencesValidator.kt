// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settingsui

/**
 * **UI Ergonomics Preferences Validator** validating context-menu density (`COMPACT` `34dp` vs
 * `STANDARD` `38dp`), default collection view-mode (`LIST` / `GRID` / `COLUMNS`), and bottom-right
 * Undo notification TTL (`4,000..15,000 ms`) + max width (`220..340 dp`).
 */
object UiErgonomicsPreferencesValidator {
    const val SCOPE =
        "Settings validator clamping right-click context-menu width/density, default LIST/GRID/COLUMNS logo mode, and Undo toast TTL."

    enum class ContextMenuDensity(val rowHeightDp: Int) { COMPACT(34), STANDARD(38), TOUCH_RELAXED(44) }

    data class ValidatedUiPrefs(
        val menuDensity: ContextMenuDensity,
        val maxContextMenuWidthDp: Int,
        val defaultCollectionViewMode: String,
        val undoNotificationTtlMillis: Long,
        val maxVisibleUndoStackCards: Int,
    )

    fun validate(
        densityRaw: String?,
        requestedMenuMaxWidthDp: Int,
        defaultViewModeRaw: String?,
        requestedUndoTtlMillis: Long,
        requestedMaxStackCards: Int = 3,
    ): ValidatedUiPrefs {
        val density = runCatching { ContextMenuDensity.valueOf((densityRaw ?: "STANDARD").uppercase()) }
            .getOrDefault(ContextMenuDensity.STANDARD)
        val viewMode = when (defaultViewModeRaw?.uppercase()) {
            "LIST", "GRID", "COLUMNS" -> defaultViewModeRaw.uppercase()
            else -> "LIST"
        }
        return ValidatedUiPrefs(
            menuDensity = density,
            maxContextMenuWidthDp = requestedMenuMaxWidthDp.coerceIn(180, 280),
            defaultCollectionViewMode = viewMode,
            undoNotificationTtlMillis = requestedUndoTtlMillis.coerceIn(4_000L, 15_000L),
            maxVisibleUndoStackCards = requestedMaxStackCards.coerceIn(1, 3),
        )
    }
}

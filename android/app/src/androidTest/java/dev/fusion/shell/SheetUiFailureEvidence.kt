// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.fusion.calc.SheetViewModel

/** Failure-only observation on a disposable emulator; never retries or mutates a test action. */
object SheetUiFailureEvidence {
    fun once(scenario: ActivityScenario<MainActivity>, phase: String, action: () -> Unit) {
        try {
            action()
        } catch (failure: Throwable) {
            var observed = "activity unavailable"
            runCatching {
                scenario.onActivity { activity ->
                    // These tests have already opened the sheet and therefore created this model.
                    val state = ViewModelProvider(activity)[SheetViewModel::class.java].state.value
                    observed = "focus=${activity.hasWindowFocus()} loading=${state.loading} busy=${state.busy} " +
                        "blocked=${state.recoveryBlocked} selected=${state.selected} kind=${state.inputKind} " +
                        "inputLength=${state.input.length} unapplied=${state.unapplied} " +
                        "gridPreview=${state.textGridPreview != null} gridError=${state.textGridError.isNotEmpty()} " +
                        "rangePreview=${state.rangePreview != null} revision=${state.data.draft.revision}"
                }
            }
            val foreground = runCatching { UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).currentPackageName }
                .getOrNull() ?: "unavailable"
            throw AssertionError("Sheet UI $phase failed after one action; foreground=$foreground; $observed", failure)
        }
    }
}

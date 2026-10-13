package dev.fusion.shell

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test for the shell host.
 *
 * Scope is deliberately narrow: the Activity composes, the shelf is present,
 * and opening a module creates a window whose content renders. Nothing here
 * asserts casting, capture, or external-display behaviour.
 *
 * The shell persists its window layout, so saved state is cleared first;
 * otherwise a window opened by one test leaks into the next and duplicate
 * labels make the matchers ambiguous.
 */
@RunWith(AndroidJUnit4::class)
class ShellSmokeTest {

    @Before
    fun clearPersistedDesktop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("fusion", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun shellRendersWithShelf() {
        composeRule.onNodeWithText("Fusion Shell").assertIsDisplayed()
        composeRule.onAllNodesWithText("Calculator").onFirst().assertIsDisplayed()
    }

    @Test
    fun openingCalculatorRendersItsContent() {
        composeRule.onAllNodesWithText("Calculator").onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Calculator — basic arithmetic only").assertIsDisplayed()
    }

    @Test
    fun filesWindowDeclaresMissingAccessHonestly() {
        composeRule.onAllNodesWithText("Files").onFirst().performClick()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithText("No folder access. Only folders you choose are visible.")
            .assertIsDisplayed()
    }
}

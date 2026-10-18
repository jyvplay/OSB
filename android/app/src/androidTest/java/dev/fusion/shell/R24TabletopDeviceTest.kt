// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import dev.fusion.launcher.*
import dev.fusion.settings.SettingsGraph
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class R24TabletopDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(DisposableEmulatorRule()).around(compose)
    private fun model(): TabletopSessionModel { lateinit var result: TabletopSessionModel; compose.activityRule.scenario.onActivity { result = ViewModelProvider(it)[TabletopSessionModel::class.java] }; return result }
    private fun ready() { compose.waitUntil(10000) { model().document != null && !model().busy }; compose.waitForIdle() }
    private fun click(tag: String) = NativeVisualEvidence.revealAndClick(compose, tag)
    private fun changed(old: Long) { compose.waitUntil(10000) { model().document!!.revision > old && !model().busy }; compose.waitForIdle() }
    private fun doc() = model().document!!

    @Test fun actualDragPublishesCoordinatesAndSurvivesActivityRecreation() {
        ready(); val before = doc(); val first = before.scene.pins.first()
        compose.onNodeWithTag("desk-pin-1").assertIsDisplayed().performTouchInput { swipe(center, center + Offset(72f, 36f), 500) }
        changed(before.revision)
        val actual = doc().scene.pins.first()
        assertTrue(actual.point.x > first.point.x); assertTrue(actual.point.y > first.point.y)
        assertEquals(doc(), TabletopStore(compose.activity.filesDir.toPath().resolve("tabletop")).load())
        compose.activityRule.scenario.recreate(); ready()
        assertEquals(actual, doc().scene.pins.first())
        compose.onNodeWithTag("desk-pin-1").assertIsDisplayed()
    }
    @Test fun wallFocusMovePileUnpileAndNamedSnapshotLoadUseRealStore() {
        ready(); var rev = doc().revision
        click("desk-pin-1")
        // combinedClickable legitimately waits for the double-tap window before its single-tap action.
        compose.waitUntil(3000) { compose.onAllNodesWithTag("desk-to-back").fetchSemanticsNodes().size == 1 }
        click("desk-to-back"); changed(rev)
        rev = doc().revision; click("surface-back"); changed(rev)
        assertEquals(DeskPlane.BACK, doc().scene.focused)
        rev = doc().revision
        compose.onNodeWithTag("desk-pin-1").assertIsDisplayed().performTouchInput { swipe(center, center + Offset(60f, 30f), 400) }
        changed(rev)
        click("desk-deselect")
        rev = doc().revision; click("tabletop-pins"); click("pin-add-editor"); changed(rev)
        val second = doc().scene.pins.last().id
        click("tabletop-piles"); click("pile-select-1"); click("pile-select-$second")
        rev = doc().revision; click("pile-create"); changed(rev)
        val pile = doc().scene.piles.single()
        assertEquals(2, doc().scene.pins.count { it.pile == pile.id })
        compose.onNodeWithTag("desk-pile-${pile.id}").assertIsDisplayed()
        click("tabletop-views"); compose.onNodeWithTag("view-name").performTextInput("Wall workspace")
        rev = doc().revision; click("view-save"); changed(rev)
        val snapshot = doc().views.single(); click("views-done")
        click("desk-pile-${pile.id}")
        compose.waitUntil(3000) { compose.onAllNodesWithTag("desk-unpile").fetchSemanticsNodes().size == 1 }
        rev = doc().revision; click("desk-unpile"); changed(rev)
        assertTrue(doc().scene.piles.isEmpty())
        click("tabletop-views"); click("view-load-${snapshot.id}")
        rev = doc().revision; click("view-confirm-load"); changed(rev)
        assertEquals(snapshot.scene, doc().scene)
        compose.activityRule.scenario.recreate(); ready()
        assertEquals(snapshot, doc().views.single()); assertEquals(snapshot.scene, doc().scene)
        NativeVisualEvidence.capture(compose, compose.activity, "r24c-wall", listOf("desk-pile-${pile.id}", "tabletop-pins", "tabletop-piles", "tabletop-views"))
    }
    @Test fun silverAndLightSceneHaveRealTargetsAndNativeCaptures() {
        ready()
        NativeVisualEvidence.assertUnclippedLabel(compose, "dock-label-editor")
        NativeVisualEvidence.capture(compose, compose.activity, "r24c-tabletop", listOf("desk-pin-1", "desk-pin-2", "tabletop-pins", "tabletop-piles", "tabletop-views", "desktop-find", "control-panel"))
        compose.activityRule.scenario.onActivity { activity -> SettingsGraph(activity).use { assertTrue(it.setAppearance(mapOf("theme" to "studio_silver", "layout" to "art"))) } }
        compose.waitForIdle(); compose.onNodeWithTag("art-dock").assertIsDisplayed()
        NativeVisualEvidence.assertUnclippedLabel(compose, "dock-label-editor")
        NativeVisualEvidence.capture(compose, compose.activity, "r24c-silver", listOf("desk-pin-1", "desk-pin-2", "tabletop-pins", "tabletop-piles", "tabletop-views", "desktop-find", "control-panel"))
        assertTrue(TabletopProjection.TABLE_AREA > .8)
        assertEquals("studio_silver", compose.activity.getSharedPreferences("fusion_settings", Context.MODE_PRIVATE).getString("theme", null))
    }
    @Test fun measuredTargetsAndSavedViewRemainOperableAtActualTwoHundredPercentFont() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val before = android.provider.Settings.System.getString(compose.activity.contentResolver, "font_scale")
        require(before == null || before.toFloatOrNull()?.let { it in .5f..5f } == true)
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { fd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() } }
        try {
            shell("settings put system font_scale 2.0"); compose.activityRule.scenario.recreate(); ready()
            compose.waitUntil(10000) { kotlin.math.abs(compose.activity.resources.configuration.fontScale - 2f) < .01f }
            for (tag in listOf("desktop-find", "control-panel", "tabletop-pins", "tabletop-piles", "tabletop-views")) {
                val node = compose.onNodeWithTag(tag); node.performScrollTo().assertIsDisplayed()
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue((bounds.right - bounds.left).value >= 47.99f)
                assertTrue((bounds.bottom - bounds.top).value >= 47.99f)
            }
            NativeVisualEvidence.assertUnclippedLabel(compose, "dock-label-editor")
            click("tabletop-views"); compose.onNodeWithTag("view-name").performScrollTo().performTextInput("Large text workspace")
            val revision = doc().revision; click("view-save"); changed(revision)
            assertEquals("Large text workspace", doc().views.single().name)
            click("views-done")
        } finally { shell(if (before == null) "settings delete system font_scale" else "settings put system font_scale $before") }
    }
    @Test fun corruptTabletopIsNotSilentlyReplacedWithDefaults() {
        ready(); val directory = compose.activity.filesDir.toPath().resolve("tabletop")
        java.nio.file.Files.createDirectories(directory)
        val file = directory.resolve("tabletop-v1.bin"); val original = byteArrayOf(1, 3, 7, 9)
        java.nio.file.Files.write(file, original)
        compose.activityRule.scenario.onActivity { ViewModelProvider(it)[TabletopSessionModel::class.java].reload() }
        compose.waitUntil(10000) { !model().busy && model().document == null }
        compose.onNodeWithText("Arrangement unavailable", substring = true).assertExists()
        assertArrayEquals(original, java.nio.file.Files.readAllBytes(file))
    }
}

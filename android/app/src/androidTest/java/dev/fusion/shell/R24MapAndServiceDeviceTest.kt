// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import dev.fusion.maps.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class R24MapAndServiceDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(DisposableEmulatorRule()).around(compose)
    private fun click(tag: String) = NativeVisualEvidence.revealAndClick(compose, tag)
    private fun open(id: String) {
        compose.waitUntil(10000) { compose.onAllNodes(hasTestTag("shelf-$id") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        click("shelf-$id"); compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
    }
    private fun model(): MapViewModel { lateinit var result: MapViewModel; compose.activityRule.scenario.onActivity { result = ViewModelProvider(it)[MapViewModel::class.java] }; return result }
    @Test fun realOfflineAtlasSavesPlacePansMeasuresAndReloadsWithoutLocationPermission() {
        open("maps"); compose.waitUntil(10000) { model().book != null && !model().busy }
        assertEquals(128, model().land.size)
        fun mapBounds(): dev.fusion.windowing.Bounds {
            lateinit var bounds: dev.fusion.windowing.Bounds
            compose.activityRule.scenario.onActivity { bounds = ViewModelProvider(it)[DesktopSessionModel::class.java].desktop.windows.single { w -> w.module == "maps" }.bounds }
            return bounds
        }
        val beforeDialog = mapBounds()
        assertTrue("Portrait default map window must not already be collapsed: $beforeDialog", beforeDialog.height >= 400)
        click("map-add-pin")
        compose.onNodeWithTag("map-pin-name").performScrollTo().performTextInput("Raleigh")
        compose.onNodeWithTag("map-latitude").performScrollTo().performTextReplacement("35.7796")
        compose.onNodeWithTag("map-longitude").performScrollTo().performTextReplacement("-78.6382")
        val saveBounds = compose.onNodeWithTag("map-save-pin").getUnclippedBoundsInRoot()
        assertTrue((saveBounds.bottom - saveBounds.top).value >= 47.99f)
        click("map-save-pin"); compose.waitUntil(10000) { model().book?.pins?.size == 1 && !model().busy }
        compose.waitUntil(10000) {
            var hidden = false
            compose.activityRule.scenario.onActivity { hidden = it.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == false }
            hidden
        }
        compose.onNodeWithTag("map-add-pin").performScrollTo()
        NativeVisualEvidence.capture(compose, compose.activity, "r24c-atlas", listOf("map-add-pin", "map-places", "map-zoom-out", "map-zoom-in"))
        assertEquals("A Dialog-owned IME must not persist a shrunken normal window", beforeDialog, mapBounds())
        assertEquals(GeoPoint(35.7796, -78.6382), model().book!!.pins.single().point)
        click("map-places"); click("map-go-1")
        assertEquals(GeoPoint(35.7796, -78.6382), model().camera.center)
        click("map-measure")
        compose.onNodeWithTag("map-add-pin").performScrollTo() // Restore the real overflow toolbar's first controls for this finite frame.
        NativeVisualEvidence.capture(compose, compose.activity, "r24c-atlas", listOf("map-add-pin", "map-places", "map-zoom-out", "map-zoom-in"))
        val canvas = compose.onNodeWithTag("offline-map-canvas").fetchSemanticsNode().boundsInRoot
        assertTrue("Actual map canvas must not collapse under controls/IME: $canvas", canvas.height / compose.density.density >= 96)
        compose.waitUntil(10000) { compose.onAllNodesWithTag("map-pin-1").fetchSemanticsNodes().size == 1 }
        click("map-pin-1") // Positive control: a tap on the 48dp pin selects that same saved point.
        assertEquals(model().measureFrom, model().cursor)
        // Keep a background target far outside the 48dp pin after proving the canvas is actually visible.
        compose.onNodeWithTag("offline-map-canvas").performTouchInput { click(Offset(width * .8f, height * .35f)) }
        assertTrue("Background tap must select a different coordinate: ${model().cursor}", EarthGeometry.meters(model().measureFrom!!, model().cursor) > 100)
        compose.onNodeWithTag("map-distance").assertIsDisplayed().assertTextContains("km", substring = true)
        val old = model().camera.center; click("map-east"); assertNotEquals(old, model().camera.center)
        val beforeZoom = model().camera.zoom; click("map-zoom-in"); assertEquals(beforeZoom + 1, model().camera.zoom)
        compose.onNodeWithTag("map-add-pin").performScrollTo() // Restore the real overflow toolbar's first controls for this finite frame.
        NativeVisualEvidence.capture(compose, compose.activity, "r24c-atlas", listOf("map-add-pin", "map-places", "map-zoom-out", "map-zoom-in"))
        compose.activityRule.scenario.recreate()
        // Session consent intentionally does not survive recreation.
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.waitUntil(10000) { model().book != null && !model().busy }
        assertEquals("Raleigh", model().book!!.pins.single().name)
        compose.activityRule.scenario.onActivity { model().reload() }
        compose.waitUntil(10000) { !model().busy }
        assertEquals("Raleigh", model().book!!.pins.single().name)
        val permissions = compose.activity.packageManager.getPackageInfo(compose.activity.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION)); assertFalse(permissions.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    @Test fun providerLinksRequireExplicitDisclosureAndDoNotMasqueradeAsPlayback() {
        open("media"); click("media-tab-services")
        compose.onNodeWithTag("service-url").performScrollTo().performClick().performTextInput("https://youtu.be/abcdefghijk?si=private-marker")
        // The actual IME is part of this case, not an inferred or mocked precondition.
        compose.waitUntil(10_000) {
            var visible = false
            compose.activityRule.scenario.onActivity { visible = it.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true }
            visible
        }
        click("service-preview")
        compose.waitUntil(10_000) {
            var hidden = false
            compose.activityRule.scenario.onActivity { hidden = it.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == false }
            hidden
        }
        compose.onNodeWithText("Open YouTube externally?").assertIsDisplayed()
        compose.onNodeWithText("https://www.youtube.com/watch?v=abcdefghijk").assertExists()
        compose.onNodeWithTag("service-confirm-open").assertIsDisplayed()
        compose.onNodeWithTag("service-cancel").assertIsDisplayed()
        NativeVisualEvidence.captureDialogDisplay(compose, compose.activity, "r58-service-review", "service-confirm-open")
        click("service-cancel")
        compose.onNodeWithTag("service-url").performScrollTo().performTextReplacement("https://youtube.com.evil.test/watch?v=abcdefghijk")
        click("service-preview")
        compose.onNodeWithTag("service-status").assertTextContains("Link refused", substring = true)
        compose.onAllNodesWithTag("service-confirm-open").assertCountEquals(0)
    }
}

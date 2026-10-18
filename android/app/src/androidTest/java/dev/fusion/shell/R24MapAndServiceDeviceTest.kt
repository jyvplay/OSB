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
    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun open(id: String) {
        compose.waitUntil(10000) { compose.onAllNodes(hasTestTag("shelf-$id") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        click("shelf-$id"); compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
    }
    private fun model(): MapViewModel { lateinit var result: MapViewModel; compose.activityRule.scenario.onActivity { result = ViewModelProvider(it)[MapViewModel::class.java] }; return result }
    @Test fun realOfflineAtlasSavesPlacePansMeasuresAndReloadsWithoutLocationPermission() {
        open("maps"); compose.waitUntil(10000) { model().book != null && !model().busy }
        assertEquals(128, model().land.size)
        click("map-add-pin")
        compose.onNodeWithTag("map-pin-name").performScrollTo().performTextInput("Raleigh")
        compose.onNodeWithTag("map-latitude").performScrollTo().performTextReplacement("35.7796")
        compose.onNodeWithTag("map-longitude").performScrollTo().performTextReplacement("-78.6382")
        click("map-save-pin"); compose.waitUntil(10000) { model().book?.pins?.size == 1 && !model().busy }
        assertEquals(GeoPoint(35.7796, -78.6382), model().book!!.pins.single().point)
        click("map-places"); click("map-go-1")
        assertEquals(GeoPoint(35.7796, -78.6382), model().camera.center)
        click("map-measure")
        compose.onNodeWithTag("offline-map-canvas").performTouchInput { click(center + Offset(45f, 15f)) }
        assertTrue(EarthGeometry.meters(model().measureFrom!!, model().cursor) > 100)
        val old = model().camera.center; click("map-east"); assertNotEquals(old, model().camera.center)
        val beforeZoom = model().camera.zoom; click("map-zoom-in"); assertEquals(beforeZoom + 1, model().camera.zoom)
        NativeVisualEvidence.capture(compose, "r24c-atlas", listOf("map-add-pin", "map-places", "map-zoom-out", "map-zoom-in"))
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
        compose.onNodeWithTag("service-url").performScrollTo().performTextInput("https://youtu.be/abcdefghijk?si=private-marker")
        click("service-preview")
        compose.onNodeWithText("Open YouTube externally?").assertIsDisplayed()
        compose.onNodeWithText("https://www.youtube.com/watch?v=abcdefghijk").assertExists()
        compose.onNodeWithTag("service-confirm-open").assertExists()
        click("service-cancel")
        compose.onNodeWithTag("service-url").performScrollTo().performTextReplacement("https://youtube.com.evil.test/watch?v=abcdefghijk")
        click("service-preview")
        compose.onNodeWithTag("service-status").assertTextContains("Link refused", substring = true)
        compose.onAllNodesWithTag("service-confirm-open").assertCountEquals(0)
    }
}

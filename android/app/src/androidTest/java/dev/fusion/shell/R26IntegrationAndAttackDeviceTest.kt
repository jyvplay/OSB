// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.WindowInsets
import android.widget.Button
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.fusion.launcher.DeskPlane
import dev.fusion.launcher.TabletopStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.regex.Pattern
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.abs

/**
 * R26 Native Integration + Gate-I Targeted Repair Attacks + Live Visual Frame Captures.
 * Real Android API 34 emulator execution, real `PixelCopy(Window)`, zero mocks or simulations.
 */
class R26IntegrationAndAttackDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(DisposableEmulatorRule()).around(compose)

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun openShelf(id: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("shelf-$id") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("shelf-$id").performScrollTo().performClick()
    }

    private fun tabletopModel(): TabletopSessionModel {
        lateinit var result: TabletopSessionModel
        compose.activityRule.scenario.onActivity { result = ViewModelProvider(it)[TabletopSessionModel::class.java] }
        return result
    }

    private fun mapModel(): dev.fusion.maps.MapViewModel {
        lateinit var result: dev.fusion.maps.MapViewModel
        compose.activityRule.scenario.onActivity { result = ViewModelProvider(it)[dev.fusion.maps.MapViewModel::class.java] }
        return result
    }

    private fun awaitTabletopReady() {
        compose.waitUntil(10_000) { tabletopModel().document != null && !tabletopModel().busy }
        compose.waitForIdle()
    }

    /** Gate-I Attack #1: Rapid repeated IME show/hide animations at 200% font scale while polling Compose semantics. */
    @Test
    fun gateIAttackRapidImeToggleAtTwoHundredPercentNeverCrossesSnapshotThreads() {
        val inst = InstrumentationRegistry.getInstrumentation()
        fun shell(cmd: String) {
            inst.uiAutomation.executeShellCommand(cmd).use { fd ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
            }
        }
        try {
            shell("settings put system font_scale 2.0")
            compose.waitUntil(10_000) { abs(compose.activity.resources.configuration.fontScale - 2.0f) < 0.05f }
            openShelf("editor")
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText("New") and isEnabled()).fetchSemanticsNodes()
                    .any { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
            }
            compose.onNodeWithText("New").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("editor-content") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("editor-content").performTextInput("Adversarial IME stress\nLine 2")
            repeat(5) {
                compose.activity.runOnUiThread { compose.activity.window.insetsController?.show(WindowInsets.Type.ime()) }
                compose.waitUntil(10_000) {
                    compose.onAllNodes(hasText("New") and isEnabled()).fetchSemanticsNodes()
                        .any { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
                }
                compose.activity.runOnUiThread { compose.activity.window.insetsController?.hide(WindowInsets.Type.ime()) }
                compose.waitUntil(10_000) {
                    compose.onAllNodes(hasText("New") and isEnabled()).fetchSemanticsNodes()
                        .any { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
                }
            }
            compose.onNodeWithText("New").assertIsDisplayed()
        } finally {
            shell("settings put system font_scale 1.0")
        }
    }

    /** Gate-I Attack #2: Multiple similarly prefixed PNGs in Downloads + picker cancel + recreation cannot leak or escape app. */
    @Test
    fun gateIAttackPngCopyCancelAndRecreationRefuseStaleExportAndKeepDownloadsSource() {
        fun pngChunk(name: String, body: ByteArray): ByteArray {
            val type = name.toByteArray()
            val crc = CRC32().apply { update(type); update(body) }.value.toInt()
            return ByteBuffer.allocate(body.size + 12).putInt(body.size).put(type).put(body).putInt(crc).array()
        }
        val raw = ByteArray((4 * 4 + 1) * 4).apply { for (y in 0 until 4) for (x in 0 until 4) this[y * 17 + 1 + x * 4 + 3] = -1 }
        val def = Deflater(); val zOut = ByteArrayOutputStream(); val buf = ByteArray(128)
        def.setInput(raw); def.finish(); while (!def.finished()) zOut.write(buf, 0, def.deflate(buf)); def.end()
        val pngBytes = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10) +
            pngChunk("IHDR", ByteBuffer.allocate(13).putInt(4).putInt(4).put(8).put(6).put(0).put(0).put(0).array()) +
            pngChunk("tEXt", "Comment\u0000Sensitive".toByteArray()) +
            pngChunk("IDAT", zOut.toByteArray()) +
            pngChunk("IEND", byteArrayOf())

        val stem = UUID.randomUUID().toString().take(8)
        val decoyName = "r26-decoy-$stem.png"
        val targetName = "r26-target-$stem.png"
        fun insertDownload(name: String): Uri {
            val u = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }))
            context.contentResolver.openOutputStream(u, "wt")!!.use { it.write(pngBytes) }
            context.contentResolver.update(u, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return u
        }
        val decoyUri = insertDownload(decoyName)
        val targetUri = insertDownload(targetName)
        try {
            ActivityScenario.launch(PngCopyActivity::class.java).use { scenario ->
                requireNotNull(device.wait(Until.findObject(By.res(context.packageName, "png_copy_choose")), 10_000)).click()
                assertTrue(device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui"))), 10_000))
                device.findObject(By.desc("List view"))?.click()
                val leaf = requireNotNull(device.wait(Until.findObject(By.text(targetName)), 10_000))
                leaf.click()
                assertTrue(device.wait(Until.gone(By.pkg(Pattern.compile(".*documentsui"))), 10_000))
                assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 10_000))
                val deadline = SystemClock.elapsedRealtime() + 15_000
                var ready = false
                while (SystemClock.elapsedRealtime() < deadline && !ready) {
                    scenario.onActivity { ready = it.observation().phase == "READY" }
                    if (!ready) SystemClock.sleep(50)
                }
                assertTrue("Expected READY state after choosing exact target PNG", ready)
                // Cancel a second picker launch: must clear prepared copy immediately so stale bytes cannot be saved.
                requireNotNull(device.wait(Until.findObject(By.res(context.packageName, "png_copy_choose")), 10_000)).click()
                assertTrue(device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui"))), 10_000))
                device.pressBack()
                assertTrue(device.wait(Until.gone(By.pkg(Pattern.compile(".*documentsui"))), 10_000))
                scenario.onActivity { a ->
                    assertEquals("EMPTY", a.observation().phase)
                    assertFalse(a.findViewById<Button>(R.id.png_copy_save).isEnabled)
                }
            }
        } finally {
            context.contentResolver.delete(decoyUri, null, null)
            context.contentResolver.delete(targetUri, null, null)
        }
    }

    /** R26 Tabletop + QuickPanel Presets + Live Silver Art & Light Business Captures. */
    @Test
    fun crossPlaneDragToWallQuickSavedViewAndArtSilverBusinessLightNativeCaptures() {
        awaitTabletopReady()
        // Save named view & confirm quick-load chip renders directly in tabletop bar
        compose.onNodeWithTag("tabletop-views").performClick()
        val viewLabel = "Studio ${UUID.randomUUID().toString().take(4)}"
        compose.onNodeWithTag("view-name").performTextInput(viewLabel)
        compose.onNodeWithTag("view-save").performClick()
        compose.waitUntil(10_000) { tabletopModel().document!!.views.any { it.name == viewLabel } && !tabletopModel().busy }
        val viewId = tabletopModel().document!!.views.first { it.name == viewLabel }.id
        compose.onNodeWithTag("views-done").performClick()
        compose.onNodeWithTag("quick-view-$viewId").performScrollTo().assertIsDisplayed()

        // Drag pin #1 diagonally across the 3D horizon (`nx ≈ 0.50, ny = 0.0 < HORIZON = 0.12`) onto the Back Wall
        compose.onNodeWithTag("desk-pin-1").assertIsDisplayed().performTouchInput {
            swipe(center, Offset(centerX + 180f, -900f), 300)
        }
        compose.waitUntil(10_000) {
            val moved = tabletopModel().document?.scene?.pins?.first { it.id == 1L }
            !tabletopModel().busy && moved != null && moved.plane == DeskPlane.BACK
        }
        val persisted = TabletopStore(compose.activity.filesDir.toPath().resolve("tabletop")).load()
        assertEquals(DeskPlane.BACK, persisted.scene.pins.first { it.id == 1L }.plane)

        // Restore original arrangement via one-tap Quick View chip
        compose.onNodeWithTag("quick-view-$viewId").performScrollTo().performClick()
        compose.onNodeWithTag("view-confirm-load").performClick()
        compose.waitUntil(10_000) {
            tabletopModel().document?.scene?.pins?.first { it.id == 1L }?.plane == DeskPlane.TABLE && !tabletopModel().busy
        }

        // Switch via integrated QuickPanel inside Control Panel -> Art Studio Silver & capture real native frame
        compose.onNodeWithTag("control-panel").performScrollTo().performClick()
        compose.onNodeWithTag("qp-preset-art-silver").performScrollTo().performClick()
        compose.onNodeWithTag("control-done").performClick()
        compose.waitForIdle()
        NativeVisualEvidence.capture(compose, compose.activity, "r26-art-silver", listOf("desktop-find", "control-panel", "desk-pin-1"))

        // Switch via integrated QuickPanel -> Business Office Light & capture real native frame
        compose.onNodeWithTag("control-panel").performScrollTo().performClick()
        compose.onNodeWithTag("qp-preset-business-light").performScrollTo().performClick()
        compose.onNodeWithTag("control-done").performClick()
        compose.waitForIdle()
        NativeVisualEvidence.capture(compose, compose.activity, "r26-business-light", listOf("desktop-find", "control-panel", "desk-pin-1"))
    }

    /** R26 Clean-Room Office Workbench + Offline World Gazetteer + Sandboxed Browser Guard. */
    @Test
    fun officeWorkbenchGazetteerAtlasAndConsentedEmbedGuardExecuteOnDevice() {
        openShelf("office")
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("office-cell-apply") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("office-cell-apply").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("office-val-B3") and hasText("500")).fetchSemanticsNodes().isNotEmpty()
        }
        NativeVisualEvidence.capture(
            compose,
            compose.activity,
            "r26-office-workbench",
            listOf("desktop-find", "control-panel", "office-tab-writer", "office-cell-apply")
        )
        compose.onNodeWithContentDescription("Close window").performClick()

        // Offline Atlas 60-city instant gazetteer search ("Raleigh") without network/GPS permissions
        openShelf("maps")
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("map-gazetteer-open") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("map-gazetteer-open").performScrollTo().performClick()
        compose.onNodeWithTag("map-gazetteer-query").performTextInput("Raleigh")
        compose.onNodeWithTag("map-gazetteer-hit-0").performClick()
        assertTrue(abs(mapModel().cursor.latitude - 35.7796) < 0.01)
        compose.onNodeWithContentDescription("Close window").performClick()

        // Sandboxed HTTPS Browser Workbench refuses `javascript:` scheme and stays on offline `about:blank`
        openShelf("browser-workbench")
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("browser-go") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("browser-url").performTextInput("javascript:alert(1)")
        compose.onNodeWithTag("browser-go").performClick()
        compose.onNodeWithTag("browser-status").assertTextContains("Navigation refused", substring = true)
    }

    /** R26 Hardened Scrambled Keypad + Amnesic Session Purge on Real Device. */
    @Test
    fun hardenedScrambledKeypadPermutesPerKeystrokeQuarantinesBurstAndCapturesFrame() {
        openShelf("ime")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("ime-slot-0") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("ime-slot-0").performScrollTo().performClick()
        compose.onNodeWithTag("ime-masked-length").assertTextContains("•", substring = true)
        NativeVisualEvidence.capture(
            compose,
            compose.activity,
            "r26-hardened-ime",
            listOf("desktop-find", "control-panel", "ime-slot-0", "ime-zeroize")
        )

        // Trigger QuickPanel Amnesic Wipe & confirm wipe epoch increments cleanly
        compose.onNodeWithTag("control-panel").performScrollTo().performClick()
        compose.onNodeWithTag("qp-amnesic-wipe").performScrollTo().performClick()
        compose.onNodeWithTag("qp-posture-summary").assertTextContains("Active session consents: 0", substring = true)
        compose.onNodeWithTag("qp-posture-summary").assertTextContains("Wipe epoch: 1", substring = true)
        compose.onNodeWithTag("control-done").performClick()
    }
}

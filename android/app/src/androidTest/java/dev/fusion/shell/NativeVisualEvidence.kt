// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Paint
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File

/** Evidence from actual Android pixels/semantics. No generated images or universal beauty score. */
object NativeVisualEvidence {
    /** Fixed scene pins/dialog actions need no scroll ancestor; overflow controls do. Never swallow a failed scroll. */
    fun revealAndClick(compose: ComposeContentTestRule, tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())) node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }
    fun assertUnclippedLabel(compose: ComposeContentTestRule, tag: String) {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        val full = node.getUnclippedBoundsInRoot()
        val visible = node.fetchSemanticsNode().boundsInRoot
        assertTrue("$tag must retain its full text-line height", visible.height / compose.density.density + .01f >= (full.bottom - full.top).value)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertEquals(1, layouts.size); assertEquals(1, layouts.single().lineCount)
        assertFalse("$tag has vertical text overflow", layouts.single().didOverflowHeight)
    }
    fun capture(compose: ComposeContentTestRule, name: String, requiredTargets: List<String>) {
        require(name.matches(Regex("r24c-[a-z-]{1,40}")))
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val args = InstrumentationRegistry.getArguments()
        val folder = args.getString("additionalTestOutputDir")?.let(::File)
            ?: File(instrumentation.targetContext.getExternalFilesDir(null), "r24c-native-captures")
        check(folder.exists() || folder.mkdirs())
        // A failed-frame PNG is diagnostic evidence, NEVER a passed geometry/test claim.
        val raw = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val originalWidth = raw.width; val originalHeight = raw.height
        val width = minOf(360, raw.width); val height = (raw.height.toLong() * width / raw.width).toInt()
        val scaled = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            assertTrue(raw.width >= 100 && raw.height >= 100)
            File(folder, "$name-full.png").outputStream().use { assertTrue(raw.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            Canvas(scaled).drawBitmap(raw, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            File(folder, "$name.png").outputStream().use { assertTrue(scaled.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { scaled.recycle(); raw.recycle() }
        val required = JSONArray()
        for (tag in requiredTargets) {
            val node = compose.onNodeWithTag(tag)
            node.assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("$tag width ${bounds.right - bounds.left}", (bounds.right - bounds.left).value >= 47.99f)
            assertTrue("$tag height ${bounds.bottom - bounds.top}", (bounds.bottom - bounds.top).value >= 47.99f)
            val visible = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$tag clipped visible width", visible.width / density >= 47.99f)
            assertTrue("$tag clipped visible height", visible.height / density >= 47.99f)
            required.put(JSONObject().put("tag", tag).put("width_dp", (bounds.right - bounds.left).value).put("height_dp", (bounds.bottom - bounds.top).value)
                .put("visible_width_dp", visible.width / density).put("visible_height_dp", visible.height / density))
        }
        val all = JSONArray()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            val rect = node.boundsInRoot
            if (rect.width > 0 && rect.height > 0) all.put(JSONObject()
                .put("tag", node.config.getOrNull(SemanticsProperties.TestTag) ?: "untagged")
                .put("visible_bounds_px", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
                .put("note", "Clipped semantics bounds; a partial scroll item is not its full hitbox"))
        }
        val report = JSONObject().put("schema", 1).put("origin", "android-instrumentation")
            .put("api", android.os.Build.VERSION.SDK_INT).put("density", density)
            .put("font_scale", instrumentation.targetContext.resources.configuration.fontScale)
            .put("required_targets", required).put("visible_click_actions", all)
            .put("native_image_width", originalWidth).put("native_image_height", originalHeight)
            .put("preview_width", width).put("preview_height", height).put("preview_filter", "bilinear downsample; full original PNG retained in CI artifact")
            .put("scope", "Finite actual frame; not all pages, accessibility certification or subjective beauty")
        File(folder, "$name.json").writeText(report.toString(2))
    }
}

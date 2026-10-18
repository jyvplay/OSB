// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
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
    fun capture(compose: ComposeContentTestRule, name: String, requiredTargets: List<String>) {
        require(name.matches(Regex("r24c-[a-z-]{1,40}")))
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val required = JSONArray()
        for (tag in requiredTargets) {
            val node = compose.onNodeWithTag(tag)
            node.assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("$tag width ${bounds.width}", bounds.width.value >= 47.99f)
            assertTrue("$tag height ${bounds.height}", bounds.height.value >= 47.99f)
            required.put(JSONObject().put("tag", tag).put("width_dp", bounds.width.value).put("height_dp", bounds.height.value))
        }
        val all = JSONArray()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            val rect = node.boundsInRoot
            if (rect.width > 0 && rect.height > 0) all.put(JSONObject()
                .put("tag", node.config.getOrNull(SemanticsProperties.TestTag) ?: "untagged")
                .put("visible_bounds_px", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
                .put("note", "Clipped semantics bounds; a partial scroll item is not its full hitbox"))
        }
        val args = InstrumentationRegistry.getArguments()
        val folder = args.getString("additionalTestOutputDir")?.let(::File)
            ?: File(instrumentation.targetContext.getExternalFilesDir(null), "r24c-native-captures")
        check(folder.exists() || folder.mkdirs())
        val report = JSONObject().put("schema", 1).put("origin", "android-instrumentation")
            .put("api", android.os.Build.VERSION.SDK_INT).put("density", density)
            .put("font_scale", instrumentation.targetContext.resources.configuration.fontScale)
            .put("required_targets", required).put("visible_click_actions", all)
            .put("scope", "Finite actual frame; not all pages, accessibility certification or subjective beauty")
        File(folder, "$name.json").writeText(report.toString(2))
        val raw = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val width = minOf(280, raw.width); val height = (raw.height.toLong() * width / raw.width).toInt()
        val scaled = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            assertTrue(raw.width >= 100 && raw.height >= 100)
            Canvas(scaled).drawBitmap(raw, null, Rect(0, 0, width, height), null)
            File(folder, "$name.png").outputStream().use { assertTrue(scaled.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { scaled.recycle(); raw.recycle() }
    }
}

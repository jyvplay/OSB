// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    private fun committedWindow(activity: Activity): Bitmap {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val committed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val root = activity.window.decorView
            assertTrue("Real accelerated window frame required", root.isHardwareAccelerated && root.isAttachedToWindow)
            root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            root.invalidate()
        }
        assertTrue("No actual renderer frame commit", committed.await(10, TimeUnit.SECONDS))
        val copied = CountDownLatch(1)
        lateinit var image: Bitmap
        var result = PixelCopy.ERROR_UNKNOWN
        instrumentation.runOnMainSync {
            val root = activity.window.decorView
            assertTrue(root.width >= 100 && root.height >= 100)
            image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            // Copy the latest queued APP WINDOW buffer, not a possibly stale whole-display compositor frame.
            // This is NOT the capture API/security claim used by the separate privacy screenshot tests.
            PixelCopy.request(activity.window, image, { status -> result = status; copied.countDown() }, Handler(Looper.getMainLooper()))
        }
        if (!copied.await(10, TimeUnit.SECONDS)) {
            // Do not recycle a bitmap while the platform may still be copying into it.
            throw AssertionError("Actual committed-window PixelCopy did not finish; no image acceptance")
        }
        if (result != PixelCopy.SUCCESS) {
            image.recycle(); throw AssertionError("Actual committed-window PixelCopy failed: $result")
        }
        return image
    }
    fun captureViews(activity: Activity, name: String, requiredIds: List<Int>) {
        require(name.matches(Regex("r25-[a-z-]{1,40}")))
        val instrumentation=InstrumentationRegistry.getInstrumentation();instrumentation.waitForIdleSync()
        val folder=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: File(instrumentation.targetContext.getExternalFilesDir(null),"r25-native-captures")
        check(folder.exists() || folder.mkdirs());val audit=File(folder,"$name.json");check(!audit.exists() || audit.delete())
        val density=activity.resources.displayMetrics.density;val required=JSONArray()
        instrumentation.runOnMainSync {
            for(id in requiredIds) {
                val view=requireNotNull(activity.findViewById<android.view.View>(id));val visible=Rect()
                assertTrue(view.isShown && view.isAttachedToWindow && view.getGlobalVisibleRect(visible))
                val w=view.width/density;val h=view.height/density;val vw=visible.width()/density;val vh=visible.height()/density
                assertTrue(w>=47.99f && h>=47.99f && vw>=47.99f && vh>=47.99f)
                required.put(JSONObject().put("tag",activity.resources.getResourceEntryName(id)).put("width_dp",w).put("height_dp",h).put("visible_width_dp",vw).put("visible_height_dp",vh))
            }
        }
        val raw=committedWindow(activity);val rawWidth=raw.width;val rawHeight=raw.height
        val width=minOf(360,raw.width);val height=(raw.height.toLong()*width/raw.width).toInt()
        val preview=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try {
            File(folder,"$name-full.png").outputStream().use {assertTrue(raw.compress(Bitmap.CompressFormat.PNG,100,it))}
            Canvas(preview).drawBitmap(raw,null,Rect(0,0,width,height),Paint(Paint.FILTER_BITMAP_FLAG))
            File(folder,"$name.png").outputStream().use {assertTrue(preview.compress(Bitmap.CompressFormat.PNG,100,it))}
        } finally {preview.recycle();raw.recycle()}
        audit.writeText(JSONObject().put("schema",1).put("origin","android-instrumentation").put("api",android.os.Build.VERSION.SDK_INT)
            .put("density",density).put("font_scale",activity.resources.configuration.fontScale).put("required_targets",required)
            .put("native_image_width",rawWidth).put("native_image_height",rawHeight).put("preview_width",width).put("preview_height",height)
            .put("capture_api","PixelCopy(Window) after real native target measurements and renderer commit; not whole-display capture")
            .put("preview_filter","bilinear downsample; original app-window PNG retained")
            .put("scope","One actual native page/frame, not all pages/states or beauty/AX acceptance").toString(2))
    }
    fun capture(compose: ComposeContentTestRule, activity: Activity, name: String, requiredTargets: List<String>) {
        require(name.matches(Regex("r2(?:4c|6)-[a-z-]{1,40}")))
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val args = InstrumentationRegistry.getArguments()
        val folder = args.getString("additionalTestOutputDir")?.let(::File)
            ?: File(instrumentation.targetContext.getExternalFilesDir(null), "r24c-native-captures")
        check(folder.exists() || folder.mkdirs())
        val auditFile = File(folder, "$name.json")
        check(!auditFile.exists() || auditFile.delete()) // A later failed capture cannot borrow an older passing audit.
        val required = JSONArray()
        val geometry = runCatching { for (tag in requiredTargets) {
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
        }
        // A failed-frame PNG is diagnostic evidence, NEVER a passed geometry/test claim.
        val raw = committedWindow(activity)
        val originalWidth = raw.width; val originalHeight = raw.height
        val width = minOf(360, raw.width); val height = (raw.height.toLong() * width / raw.width).toInt()
        val scaled = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            assertTrue(raw.width >= 100 && raw.height >= 100)
            File(folder, "$name-full.png").outputStream().use { assertTrue(raw.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            Canvas(scaled).drawBitmap(raw, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            File(folder, "$name.png").outputStream().use { assertTrue(scaled.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { scaled.recycle(); raw.recycle() }
        geometry.getOrThrow()
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
            .put("capture_api", "PixelCopy(Window) after renderer frame commit; application surface, not whole-display capture")
            .put("preview_width", width).put("preview_height", height).put("preview_filter", "bilinear downsample; full original PNG retained in CI artifact")
            .put("scope", "Finite actual frame; not all pages, accessibility certification or subjective beauty")
        auditFile.writeText(report.toString(2))
    }
}

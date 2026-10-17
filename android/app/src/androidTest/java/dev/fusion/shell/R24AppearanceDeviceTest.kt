package dev.fusion.shell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.fusion.designsystem.ProfessionalPalettes
import dev.fusion.editor.EditorViewModel
import dev.fusion.settings.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

/** Actual shared preferences, composition, mouse-source input and screenshot pixels; not reference art. */
class R24AppearanceDeviceTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(DisposableEmulatorRule()).around(compose)
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun open(id:String) {compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("shelf-$id") and isEnabled()).fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithTag("shelf-$id").performScrollTo().performClick()}
    private fun control() {compose.onNodeWithTag("control-panel").performScrollTo().performClick()}
    private fun done() {compose.onNodeWithTag("control-done").performClick();compose.waitForIdle()}
    private fun pixelCount(rgb:Int):Int {
        val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {var n=0;for(y in 0 until image.height step 8) for(x in 0 until image.width step 8) if(image.getPixel(x,y) and 0xffffff==rgb) n++;return n}
        finally {image.recycle()}
    }
    private fun capture(name:String) {
        val raw=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val width=minOf(280,raw.width);val height=(raw.height.toLong()*width/raw.width).toInt()
        val resized=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try {
            Canvas(resized).drawBitmap(raw,null,android.graphics.Rect(0,0,width,height),null)
            val args=InstrumentationRegistry.getArguments()
            val directory=args.getString("additionalTestOutputDir")?.let(::File)
                ?: File(instrumentation.targetContext.getExternalFilesDir(null),"r24-native-captures")
            check(directory.exists() || directory.mkdirs())
            File(directory,"r24-$name.png").outputStream().use {check(resized.compress(Bitmap.CompressFormat.PNG,100,it))}
            println("R24 actual downsampled native screenshot: $directory/r24-$name.png")
        } finally {raw.recycle();resized.recycle()}
    }
    @Test fun bothPalettesAndLayoutsRenderPixelsAndPersistThroughRecreation() {
        for(palette in ProfessionalPalette.entries) for(layout in DesktopLayout.entries) {
            control()
            compose.onNodeWithTag("control-theme-${palette.id}").performScrollTo().performClick()
            compose.onNodeWithTag("control-layout-${layout.id}").performScrollTo().performClick()
            done()
            compose.onNodeWithTag(if(layout==DesktopLayout.ART) "art-dock" else "business-taskbar").assertIsDisplayed()
            val surface=ProfessionalPalettes.colors(palette).surface
            compose.waitUntil(10000) {pixelCount(surface)>200}
            capture("${layout.id}-${palette.id}")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag(if(layout==DesktopLayout.ART) "art-dock" else "business-taskbar").assertExists()
            val values=compose.activity.getSharedPreferences("fusion_settings",Context.MODE_PRIVATE)
            assertEquals(palette.id,values.getString("theme",null));assertEquals(layout.id,values.getString("layout",null))
        }
    }
    @Test fun settingsAndControlPanelShareOneStoreWithoutLosingDirtyEditorOrGates() {
        open("editor");compose.onNodeWithText("New").performScrollTo().performClick()
        compose.onNodeWithTag("editor-content").performScrollTo().performTextInput("R24 unsaved appearance fixture")
        var editorId=0L
        compose.activityRule.scenario.onActivity {a->editorId=ViewModelProvider(a)[DesktopSessionModel::class.java].desktop.windows.single {it.module=="editor"}.id}
        open("settings")
        compose.onNodeWithTag("settings-layout-art").performScrollTo().performClick()
        compose.onNodeWithTag("settings-theme-studio_graphite").performScrollTo().performClick()
        compose.onNodeWithTag("art-dock").assertExists()
        control();compose.onNodeWithTag("control-layout-art").assertIsSelected();compose.onNodeWithTag("control-theme-studio_graphite").assertIsSelected()
        compose.onNodeWithTag("control-layout-business").performScrollTo().performClick()
        compose.onNodeWithTag("control-theme-office_blue").performScrollTo().performClick();done()
        compose.onNodeWithTag("settings-layout-business").assertIsSelected()
        compose.onNodeWithTag("settings-theme-office_blue").assertIsSelected()
        compose.activityRule.scenario.onActivity {a->
            assertEquals(editorId,ViewModelProvider(a)[DesktopSessionModel::class.java].desktop.windows.single {it.module=="editor"}.id)
            val state=ViewModelProvider(a)[EditorViewModel::class.java].state.value
            assertTrue(state.session.documents.any {it.dirty})
            assertTrue(state.buffers.values.any {it.content.contains("R24 unsaved appearance fixture")})
        }
        open("beam");compose.onNodeWithText("Unavailable in this pilot").assertExists()
        compose.onAllNodesWithText("Enable for this session with test data").assertCountEquals(0)
    }
    private fun mouse(x:Float,y:Float,action:Int=MotionEvent.ACTION_HOVER_MOVE) {
        val pp=MotionEvent.PointerProperties().apply {id=0;toolType=MotionEvent.TOOL_TYPE_MOUSE}
        val pc=MotionEvent.PointerCoords().apply {this.x=x;this.y=y;pressure=0f;size=0f}
        val now=SystemClock.uptimeMillis()
        val event=MotionEvent.obtain(now,now,action,1,arrayOf(pp),arrayOf(pc),0,0,1f,1f,0,0,InputDevice.SOURCE_MOUSE,0)
        try {assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true))} finally {event.recycle()}
    }
    private fun screenRect(tag:String):android.graphics.Rect {
        val bounds=compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val offset=IntArray(2)
        compose.activityRule.scenario.onActivity {it.findViewById<ViewGroup>(android.R.id.content).getLocationOnScreen(offset)}
        return android.graphics.Rect((bounds.left+offset[0]).toInt(),(bounds.top+offset[1]).toInt(),(bounds.right+offset[0]).toInt(),(bounds.bottom+offset[1]).toInt())
    }
    private fun accentWidth(rect:android.graphics.Rect,rgb:Int):Int {
        val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            var left=image.width;var right=-1
            for(y in maxOf(0,rect.top) until minOf(image.height,rect.bottom)) for(x in maxOf(0,rect.left) until minOf(image.width,rect.right)) {
                val p=image.getPixel(x,y);val distance=listOf(0,8,16).sumOf {shift->kotlin.math.abs((p ushr shift and 255)-(rgb ushr shift and 255))}
                if(distance<64) {left=minOf(left,x);right=maxOf(right,x)}
            }
            return if(right<left) 0 else right-left+1
        } finally {image.recycle()}
    }
    @Test fun realMouseMagnifiesDrawingNotHitboxAndMotionOffRestoresBaseSize() {
        val expectedFont=android.provider.Settings.System.getFloat(compose.activity.contentResolver,"font_scale",1f)
        compose.waitUntil(10000) {kotlin.math.abs(compose.activity.resources.configuration.fontScale-expectedFont)<.01f && compose.activity.hasWindowFocus()}
        control();compose.onNodeWithTag("control-layout-art").performClick();done()
        compose.onNodeWithTag("shelf-editor").performScrollTo()
        mouse(5f,200f);mouse(5f,200f)
        compose.onNodeWithTag("desktop-find").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) {it()}
        compose.waitUntil(10000) {compose.onNodeWithTag("shelf-editor").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription].contains("normal")}
        val hitBefore=screenRect("shelf-editor");val glyph=screenRect("dock-glyph-editor")
        val roi=android.graphics.Rect(glyph).apply {inset(-24,-24)}
        val accent=ProfessionalPalettes.colors(ProfessionalPalette.OFFICE_BLUE).primary
        val expectedBase=dev.fusion.designsystem.DockMagnification.GLYPH_DP*compose.activity.resources.displayMetrics.density
        // A 'normal' target label is not proof an old120ms animation has reached base raster size.
        compose.waitUntil(10000) {accentWidth(roi,accent).toFloat() in expectedBase*.9f..expectedBase*1.08f}
        val before=accentWidth(roi,accent);assertTrue("Missing actual base icon pixels",before>20)
        mouse(hitBefore.exactCenterX(),hitBefore.exactCenterY())
        mouse(hitBefore.exactCenterX(),hitBefore.exactCenterY())
        compose.waitUntil(10000) {accentWidth(roi,accent)>before*1.15}
        compose.onNodeWithTag("shelf-editor").assert(SemanticsMatcher("actual hover state") {it.config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription].contains("enlarged")})
        assertEquals(hitBefore,screenRect("shelf-editor"))
        mouse(5f,200f);mouse(5f,200f)
        // Patch-specific independent attack: focus alone must magnify with the pointer outside the dock.
        compose.onNodeWithTag("shelf-editor").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) {it()}
        compose.waitUntil(10000) {accentWidth(roi,accent)>before*1.15}
        compose.onNodeWithTag("desktop-find").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) {it()}
        control();compose.onNodeWithTag("control-dock-zoom").performScrollTo().performClick();done()
        mouse(hitBefore.exactCenterX(),hitBefore.exactCenterY());mouse(hitBefore.exactCenterX(),hitBefore.exactCenterY())
        compose.waitUntil(10000) {accentWidth(roi,accent)<=before*1.08}
        compose.onNodeWithTag("shelf-editor").assert(SemanticsMatcher("motion off") {it.config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription].contains("normal")})
        mouse(5f,200f)
    }
    @Test fun invalidAppearancePatchAndCorruptPreferenceTypeCannotPartiallyApplyOrResetData() {
        compose.activityRule.scenario.onActivity {a->
            SettingsGraph(a).use {graph->
                val before=graph.exportSnapshot()
                assertFalse(graph.setAppearance(mapOf("theme" to "studio_graphite","layout" to "wrong")))
                assertArrayEquals(before,graph.exportSnapshot())
                val prefs=a.getSharedPreferences("fusion_settings",Context.MODE_PRIVATE)
                prefs.edit().putInt("theme",9).apply()
                assertEquals(ProfessionalPalette.OFFICE_BLUE,AppearancePreferences.read(graph.snapshots.value).palette)
                assertEquals(9,prefs.getInt("theme",0)) // read fallback must not erase the original bytes/type.
                assertTrue(graph.setAppearance(mapOf("theme" to "studio_graphite","layout" to "art","dock_zoom" to "false")))
                assertEquals(Appearance(ProfessionalPalette.STUDIO_GRAPHITE,DesktopLayout.ART,false),AppearancePreferences.read(graph.snapshots.value))
            }
        }
        compose.onNodeWithTag("art-dock").assertExists()
    }
    @Test fun densityAndSearchRemainReachableAtActualTwoHundredPercentFont() {
        val resolver=compose.activity.contentResolver
        val before=android.provider.Settings.System.getString(resolver,"font_scale")
        require(before==null || before.toFloatOrNull()?.let {it in .5f..5f}==true)
        fun shell(command:String)=instrumentation.uiAutomation.executeShellCommand(command).use {fd->android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {it.readBytes()}}
        try {
            shell("settings put system font_scale 2.0")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) {kotlin.math.abs(compose.activity.resources.configuration.fontScale-2f)<.01f}
            open("settings")
            compose.onNodeWithText("TOUCH").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("settings-apply-density").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("Window geometry density: TOUCH").assertExists()
            compose.onNodeWithTag("settings-query").performScrollTo().assertIsDisplayed().performTextInput("appearance")
            compose.onNodeWithTag("settings-query").assertTextContains("appearance")
        } finally {shell(if(before==null) "settings delete system font_scale" else "settings put system font_scale $before")}
    }

}

package dev.fusion.shell

import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.fusion.automation.TaskRepository
import dev.fusion.settings.SettingsGraph
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** No Compose input interceptor/rule. Real accessibility text, platform IME, scroll and one actual touch pair. */
class R24TaskInputDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun waitFor(label:String,condition:()->Boolean) {
        val end=SystemClock.uptimeMillis()+15000
        while(SystemClock.uptimeMillis()<end) {if(condition())return;SystemClock.sleep(60)}
        throw AssertionError("R24 native timeout: $label")
    }
    private fun find(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo? {
        val root=instrumentation.uiAutomation.rootInActiveWindow ?: return null
        val q=java.util.ArrayDeque<AccessibilityNodeInfo>();q.add(root);var n=0
        while(q.isNotEmpty()) {val node=q.removeFirst();check(++n<=4000);if(predicate(node))return node
            for(i in 0 until node.childCount) node.getChild(i)?.let(q::add)}
        return null
    }
    private fun tag(id:String)=find {it.viewIdResourceName==id || it.viewIdResourceName?.endsWith(":id/$id")==true}
    private fun text(value:String)=find {it.text?.toString()==value}
    private fun click(node:AccessibilityNodeInfo) {
        val r=Rect();node.getBoundsInScreen(r);check(!r.isEmpty)
        val down=SystemClock.uptimeMillis()
        for((action,t) in listOf(MotionEvent.ACTION_DOWN to down,MotionEvent.ACTION_UP to down+40)) {
            val e=MotionEvent.obtain(down,t,action,r.exactCenterX(),r.exactCenterY(),0).apply {source=InputDevice.SOURCE_TOUCHSCREEN}
            try {assertTrue(instrumentation.uiAutomation.injectInputEvent(e,true))} finally {e.recycle()}
        }
    }
    private fun visible(scenario:ActivityScenario<MainActivity>,node:AccessibilityNodeInfo):Boolean {
        val r=Rect();node.getBoundsInScreen(r);val viewport=Rect()
        scenario.onActivity {it.window.decorView.getWindowVisibleDisplayFrame(viewport)}
        return node.isVisibleToUser && !r.isEmpty && viewport.contains(r.centerX(),r.centerY())
    }
    private fun show(scenario:ActivityScenario<MainActivity>,lookup:()->AccessibilityNodeInfo?):AccessibilityNodeInfo {
        repeat(24) {
            val node=lookup() ?: run {SystemClock.sleep(80);return@repeat}
            if(visible(scenario,node)) return node
            if(!node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)) {
                var p=node.parent
                while(p!=null && !p.isScrollable) p=p.parent
                assertNotNull("No real scroll ancestor for offscreen control",p)
                assertTrue(p!!.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
            }
            instrumentation.waitForIdleSync();SystemClock.sleep(80)
        }
        throw AssertionError("Actual control did not become visible")
    }
    private fun set(node:AccessibilityNodeInfo,value:String) {
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)
        }))
    }
    private fun exercise(layout:String) {
        ActivityScenario.launch(MainActivity::class.java).use {s->
            s.onActivity {a->SettingsGraph(a).use {assertTrue(it.setAppearance(mapOf("layout" to layout)))}}
            waitFor("Find") {text("Find")!=null}
            click(show(s){text("Find")})
            waitFor("actual palette edit") {tag("palette-query")!=null}
            set(requireNotNull(tag("palette-query")),"automation")
            waitFor("automation result") {tag("palette-hit-module:automation")!=null}
            click(show(s){tag("palette-hit-module:automation")})
            click(show(s){text("Enable for this session with test data")})
            click(show(s){tag("tasks-mode")})
            waitFor("loaded Task add") {tag("tasks-add")?.isEnabled==true}
            val input=show(s){tag("tasks-title")};click(input)
            set(requireNotNull(tag("tasks-title")),"R24 $layout one native tap")
            s.onActivity {it.window.insetsController?.show(WindowInsets.Type.ime())}
            var ime=false
            waitFor("actual platform IME") {s.onActivity {ime=it.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true};ime}
            val target=show(s){tag("tasks-add")};val rect=Rect();target.getBoundsInScreen(rect);val viewport=Rect()
            s.onActivity {it.window.decorView.getWindowVisibleDisplayFrame(viewport)}
            assertTrue("Pinned save target outside real IME-visible viewport: $rect / $viewport",viewport.contains(rect))
            val before=TaskRepository(File(instrumentation.targetContext.noBackupFilesDir,"tasks-v1.bin")).load()
            assertEquals(0L,before.revision)
            println("R24 BEFORE SINGLE TAP layout=$layout target=$rect viewport=$viewport title=${tag("tasks-title")?.text} ${TaskUiEvidence.geometry(scenarioActivity(s))}")
            click(target) // EXACTLY ONE touch DOWN/UP pair; no direct callback, hidden keyboard or retry.
            waitFor("single-tap actual revision1") {tag("tasks-status")?.text?.contains("Loaded/saved revision 1.")==true}
            val after=TaskRepository(File(instrumentation.targetContext.noBackupFilesDir,"tasks-v1.bin")).load()
            assertEquals(1L,after.revision);assertEquals("R24 $layout one native tap",after.tasks.single().title)
            assertTrue("Store must have exactly one task after one tap",after.tasks.size==1)
        }
    }
    private fun scenarioActivity(s:ActivityScenario<MainActivity>):MainActivity {lateinit var result:MainActivity;s.onActivity {result=it};return result}
    @Test fun oneActualImeVisibleAddInBusinessWritesExactlyOneRevision()=exercise("business")
    @Test fun oneActualImeVisibleAddInArtWritesExactlyOneRevision()=exercise("art")
}

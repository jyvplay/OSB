package dev.fusion.shell

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.ViewModelProvider
import dev.fusion.editor.EditorViewModel
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** No Compose rule/input interception. Real touch, accessibility edit, real IME/window observations. */
class RealImeDeviceTest {
    @get:Rule val disposable = DisposableEmulatorRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun command(s: String) = instrumentation.uiAutomation.executeShellCommand(s).use { fd ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes().toString(Charsets.UTF_8).trim() }
    }
    private fun waitFor(label: String, condition: () -> Boolean) {
        val end=SystemClock.uptimeMillis()+15000
        while (SystemClock.uptimeMillis()<end) { if(condition()) return; SystemClock.sleep(80) }
        throw AssertionError("Timed out: $label")
    }
    private fun find(predicate: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo? {
        val root=instrumentation.uiAutomation.rootInActiveWindow ?: return null
        val queue=java.util.ArrayDeque<AccessibilityNodeInfo>();queue.add(root);var visited=0
        while(queue.isNotEmpty()) {
            val n=queue.removeFirst();check(++visited<=3000)
            if(n.refresh() && n.isVisibleToUser && predicate(n)) return n
            for(i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
        }
        return null
    }
    private fun editorNode() = find {
        it.isEditable && it.isEnabled && (it.viewIdResourceName == "editor-content" || it.viewIdResourceName?.endsWith(":id/editor-content") == true)
    }
    private fun selectedContent(scenario: ActivityScenario<MainActivity>): String? {
        var result: String? = null
        scenario.onActivity {
            val state = ViewModelProvider(it)[EditorViewModel::class.java].state.value
            result = state.session.documents.find { doc -> doc.id == state.session.selectedId }?.content
        }
        return result
    }
    private fun tap(node: AccessibilityNodeInfo) {
        val rect=Rect();node.getBoundsInScreen(rect);check(!rect.isEmpty)
        val down=SystemClock.uptimeMillis()
        for((action,t) in listOf(MotionEvent.ACTION_DOWN to down,MotionEvent.ACTION_UP to down+40)) {
            val event=MotionEvent.obtain(down,t,action,rect.exactCenterX(),rect.exactCenterY(),0).apply { source=InputDevice.SOURCE_TOUCHSCREEN }
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true)) } finally { event.recycle() }
        }
    }
    @Test fun realImeAndLongEditorTextAtTwoHundredPercentWithoutComposeInputInjection() {
        val context=ApplicationProvider.getApplicationContext<Context>();val resolver=context.contentResolver
        val beforeFont=Settings.System.getString(resolver,"font_scale")
        val beforeIme=Settings.Secure.getString(resolver,"show_ime_with_hard_keyboard")
        check(beforeFont==null || beforeFont.toFloatOrNull()?.let { it in 0.5f..5f }==true)
        check(beforeIme==null || beforeIme in listOf("0","1"))
        val manager=context.getSystemService(InputMethodManager::class.java)
        val enabled=manager.enabledInputMethodList.map { it.id }
        val selected=Settings.Secure.getString(resolver,Settings.Secure.DEFAULT_INPUT_METHOD)
        assertTrue("No enabled real IME; default=$selected",enabled.isNotEmpty())
        try {
            command("settings put system font_scale 2.0");command("settings put secure show_ime_with_hard_keyboard 1")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitFor("Activity font scale2") { var scale=0f;scenario.onActivity { scale=it.resources.configuration.fontScale };kotlin.math.abs(scale-2f)<0.01f }
                waitFor("visible Editor shelf button") { find { it.text?.toString()=="Editor" }!=null }
                tap(requireNotNull(find { it.text?.toString()=="Editor" }))
                waitFor("New enabled") { find { it.text?.toString()=="New" && it.isEnabled }!=null }
                tap(requireNotNull(find { it.text?.toString()=="New" && it.isEnabled }))
                waitFor("real tagged editor node") { editorNode()!=null }
                tap(requireNotNull(editorNode()))
                var observation=""
                waitFor("IME visible default=$selected enabled=$enabled") {
                    var visible=false
                    scenario.onActivity {
                        val view=it.window.decorView;val insets=view.rootWindowInsets
                        visible=insets?.isVisible(WindowInsets.Type.ime())==true
                        observation="ime=$visible bottom=${insets?.getInsets(WindowInsets.Type.ime())?.bottom} height=${view.height} scrollY=${view.scrollY}"
                    }; visible
                }
                val text=(1..60).joinToString("\n") { "Real input line $it" }
                val editor=requireNotNull(editorNode())
                assertTrue(editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text) }))
                try {
                    waitFor("long text entered in real model AND visible tagged editor") {
                        selectedContent(scenario) == text && editorNode()?.text?.contains("Real input line 60") == true
                    }
                } catch (failure: AssertionError) {
                    val content = selectedContent(scenario)
                    val actual = editorNode()
                    val rect = Rect(); actual?.getBoundsInScreen(rect)
                    throw AssertionError("Real IME long-text failure: modelChars=${content?.length}; modelTail=${content?.takeLast(80)}; " +
                        "tag=${actual?.viewIdResourceName}; editable=${actual?.isEditable}; visible=${actual?.isVisibleToUser}; " +
                        "bounds=$rect; axTail=${actual?.text?.takeLast(80)}; $observation; original=${failure.message}", failure)
                }
                waitFor("New visible with real IME; $observation") { find { it.text?.toString()=="New" && it.isEnabled }!=null }
                tap(requireNotNull(find { it.text?.toString()=="New" && it.isEnabled }))
                waitFor("second empty document") {
                    val fresh = editorNode()
                    fresh != null && fresh.text?.contains("Real input line 60") != true && selectedContent(scenario) == ""
                }
                println("R13 actual IME observation: default=$selected enabled=$enabled $observation")
            }
        } finally {
            command(if(beforeFont==null) "settings delete system font_scale" else "settings put system font_scale $beforeFont")
            command(if(beforeIme==null) "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $beforeIme")
        }
    }
}

package dev.fusion.privacy
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test

class PrivacyWorkspaceTest {
    private fun touch(id:String) {
        val d=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val selector=By.res("dev.fusion.privacy",id)
        val initial=d.findObject(selector)
        if(initial==null || initial.visibleBounds.isEmpty) androidx.test.uiautomator.UiScrollable(androidx.test.uiautomator.UiSelector().scrollable(true))
            .scrollIntoView(androidx.test.uiautomator.UiSelector().resourceId("dev.fusion.privacy:id/$id"))
        val node=d.wait(Until.findObject(selector),10000);assertNotNull(node);node.click()
    }
    @Test fun actualTypingDiscardCannotRestoreOldEditorUndoAndRecreationStartsEmpty() {
        ActivityScenario.launch(PrivacyActivity::class.java).use {s ->
            val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            androidx.test.uiautomator.UiScrollable(androidx.test.uiautomator.UiSelector().scrollable(true))
                .scrollIntoView(androidx.test.uiautomator.UiSelector().resourceId("dev.fusion.privacy:id/scratch_text"))
            val input=device.wait(Until.findObject(By.res("dev.fusion.privacy","scratch_text")),10000);assertNotNull(input)
            input.text="private native input"
            var old:EditText?=null
            s.onActivity {a ->old=a.findViewById(R.id.scratch_text);assertEquals("private native input",old!!.text.toString())}
            touch("discard_text")
            s.onActivity {a ->
                val fresh=a.findViewById<EditText>(R.id.scratch_text);assertNotSame(old,fresh)
                fresh.onTextContextMenuItem(android.R.id.undo);assertEquals("",fresh.text.toString())
                fresh.setText("do not restore on recreate")
            }
            s.recreate();s.onActivity {assertEquals("",it.findViewById<EditText>(R.id.scratch_text).text.toString())}
        }
    }
    @Test fun pauseDropsBothFieldsAndDisablesSavedHierarchyAndClipboardActions() {
        ActivityScenario.launch(PrivacyActivity::class.java).use {s ->
            s.onActivity {a ->
                val input=a.findViewById<EditText>(R.id.scratch_text)
                input.setText("temporary");a.findViewById<EditText>(R.id.link_text).setText("https://example.com/name?secret=x")
                assertSame(android.view.textclassifier.TextClassifier.NO_OP,input.textClassifier) // Patch-specific check, not just compilation.
                assertFalse(input.isSaveEnabled);assertFalse(input.isSaveFromParentEnabled)
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,input.importantForAutofill)
                val info=EditorInfo();input.onCreateInputConnection(info)
                assertTrue(info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING!=0)
                for(id in listOf(android.R.id.copy,android.R.id.cut,android.R.id.paste,android.R.id.pasteAsPlainText,android.R.id.shareText)) assertFalse(input.onTextContextMenuItem(id))
            }
            val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            touch("review_keyboard_settings")
            assertTrue("Keyboard Settings should open in the API34 system image",device.wait(Until.hasObject(By.pkg("com.android.settings")),10000))
            device.pressBack()
            assertNotNull("Workspace should return from Android Settings",device.wait(Until.findObject(By.res("dev.fusion.privacy","scratch_text")),10000))
            s.onActivity {a ->assertEquals("",a.findViewById<EditText>(R.id.scratch_text).text.toString());assertEquals("",a.findViewById<EditText>(R.id.link_text).text.toString())}
        }
    }
    @Test fun externalExtrasCannotDisableProtectionOrInjectDraftAndProjectionIsOffline() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        ActivityScenario.launch<PrivacyActivity>(Intent(context,PrivacyActivity::class.java).putExtra("unsecuredPositiveControl",true).putExtra(Intent.EXTRA_TEXT,"injected payload")).use {s ->s.onActivity {a ->
            assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
            val input=a.findViewById<EditText>(R.id.scratch_text);assertEquals("",input.text.toString())
            // Native counterexample/control: ordinary LengthFilter silently keeps a prefix.
            val truncating=EditText(a);truncating.filters=arrayOf(android.text.InputFilter.LengthFilter(32768))
            truncating.setText("x".repeat(32769));assertEquals(32768,truncating.length())
            input.setText("x".repeat(32769));assertEquals(0,input.length());assertTrue(a.findViewById<TextView>(R.id.scratch_status).text.startsWith("Input change refused:"))
            input.setText("x".repeat(32768));assertEquals(32768,input.length());assertEquals("",a.findViewById<TextView>(R.id.scratch_status).text.toString());assertEquals(8,input.maxLines)
            input.setText("y".repeat(32769));assertEquals("x".repeat(32768),input.text.toString());assertTrue(a.findViewById<TextView>(R.id.scratch_status).text.startsWith("Input change refused:"))
            input.append("!");assertEquals("x".repeat(32768),input.text.toString());assertTrue(a.findViewById<TextView>(R.id.scratch_status).text.startsWith("Input change refused:"))
            // New attack after the feedback repair: actual IME connection, then accepted deletion.
            input.requestFocus();input.setSelection(input.length())
            requireNotNull(input.onCreateInputConnection(EditorInfo())).commitText("overflow",1)
            assertEquals("x".repeat(32768),input.text.toString())
            assertTrue(a.findViewById<TextView>(R.id.scratch_status).text.startsWith("Input change refused:"))
            input.text.delete(32767,32768);assertEquals(32767,input.length())
            assertEquals("",a.findViewById<TextView>(R.id.scratch_status).text.toString())
            val url=a.findViewById<EditText>(R.id.link_text);url.setText("https://example.com/original")
            url.setText("https://example.com/"+"x".repeat(4096));assertEquals("https://example.com/original",url.text.toString())
            url.text.replace(0,url.length(),"https://example.com/"+"x".repeat(4096))
            assertEquals("https://example.com/original",url.text.toString());assertTrue(a.findViewById<TextView>(R.id.link_status).text.startsWith("Input change refused:"))
            a.findViewById<EditText>(R.id.link_text).setText("https://example.com/Alice?token=secret#private")
        }
            touch("project_link")
            s.onActivity {a ->
            val result=a.findViewById<TextView>(R.id.link_output).text.toString()
            assertTrue(result.startsWith("https://example.com/Alice\n"));assertFalse(result.contains("secret"));assertFalse(result.contains("#private"))
        }}
    }
    @Test fun richImeContentIsRefusedAndIncomingExecutableUrlSpansAreStripped() {
        ActivityScenario.launch(PrivacyActivity::class.java).use {s ->s.onActivity {a ->
            val payload=android.text.SpannableString("plain words")
            payload.setSpan(android.text.style.URLSpan("https://example.com/identity"),0,payload.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val control=EditText(a);control.setText(payload)
            assertEquals(1,control.text.getSpans(0,control.length(),android.text.style.URLSpan::class.java).size)
            val input=a.findViewById<EditText>(R.id.scratch_text);input.setText(payload)
            assertEquals("plain words",input.text.toString());assertEquals(0,input.text.getSpans(0,input.length(),android.text.style.URLSpan::class.java).size)
            input.requestFocus()
            val connection=requireNotNull(input.onCreateInputConnection(EditorInfo()))
            val content=android.view.inputmethod.InputContentInfo(android.net.Uri.parse("content://r20.invalid/never-opened"),android.content.ClipDescription("refused",arrayOf("image/png")),null)
            assertFalse(connection.commitContent(content,0,null))
            assertEquals("plain words",input.text.toString())
        }}
    }

}

package dev.fusion.shell
import android.widget.*
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowInsets
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.fusion.automation.CsvWorkbenchActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.regex.Pattern
class R21CsvUiDeviceTest {
 @get:Rule val disposable=DisposableEmulatorRule()
 private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
 private val pkg get()=InstrumentationRegistry.getInstrumentation().targetContext.packageName
 private fun node(id:String):UiObject2 {
  val selector=By.res(pkg,id)
  val found=device.findObject(selector)
  if(found==null || found.visibleBounds.isEmpty) UiScrollable(UiSelector().resourceId("$pkg:id/csv_scroll")).scrollIntoView(UiSelector().resourceId("$pkg:id/$id"))
  return requireNotNull(device.wait(Until.findObject(selector),10000))
 }
 private fun ready(s:ActivityScenario<CsvWorkbenchActivity>) {
  val deadline=SystemClock.uptimeMillis()+15000;var details="not observed"
  do {
   var ready=false
   s.onActivity {a->
    val status=a.findViewById<TextView>(dev.fusion.automation.R.id.csv_status).text.toString()
    val save=a.findViewById<Button>(dev.fusion.automation.R.id.csv_save)
    val preview=a.findViewById<TextView>(dev.fusion.automation.R.id.csv_preview).text.toString()
    val ime=a.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true
    ready=status.startsWith("Preview ready.") && save.isEnabled && preview.isNotEmpty() && !ime
    details="status=$status; save=${save.isEnabled}; previewChars=${preview.length}; ime=$ime"
   }
   if(ready)return
   SystemClock.sleep(50)
  } while(SystemClock.uptimeMillis()<deadline)
  fail("Actual current CSV preview was not ready: $details")
 }
 private fun nativeSave(s:ActivityScenario<CsvWorkbenchActivity>):String {
  node("csv_save") // Actual native scroll if needed; no direct click listener or export call.
  InstrumentationRegistry.getInstrumentation().waitForIdleSync()
  val target=Rect();var before="not observed"
  s.onActivity {a->
   val save=a.findViewById<Button>(dev.fusion.automation.R.id.csv_save)
   val viewport=Rect();a.window.decorView.getWindowVisibleDisplayFrame(viewport)
   assertTrue(save.isEnabled && save.isShown && a.hasWindowFocus())
   assertFalse(a.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true)
   val clipped=Rect();assertTrue(save.getGlobalVisibleRect(clipped))
   assertEquals("Full Save height must be visible",save.height,clipped.height())
   assertEquals("Full Save width must be visible",save.width,clipped.width())
   val screen=IntArray(2);save.getLocationOnScreen(screen)
   target.set(screen[0],screen[1],screen[0]+save.width,screen[1]+save.height)
   assertTrue("Save clipped in physical screen coordinates: $target / $viewport",viewport.contains(target))
   before="save=$target; viewport=$viewport; status="+a.findViewById<TextView>(dev.fusion.automation.R.id.csv_status).text
  }
  val down=SystemClock.uptimeMillis()
  for((action,time) in listOf(MotionEvent.ACTION_DOWN to down,MotionEvent.ACTION_UP to down+40)) {
   val event=MotionEvent.obtain(down,time,action,target.exactCenterX(),target.exactCenterY(),0).apply {source=InputDevice.SOURCE_TOUCHSCREEN}
   try {assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event,true))} finally {event.recycle()}
  }
  return before
 }
 @Test fun actualSelectionEditInvalidatesPreviewAndSystemSaveReadsBack() {
  ActivityScenario.launch(CsvWorkbenchActivity::class.java).use {s->
   node("csv_source").text="name,value,secret\nAlice,=1+1,DO_NOT_EXPORT"
   node("csv_columns").text="2"
   s.onActivity {it.window.insetsController?.hide(android.view.WindowInsets.Type.ime())} // Do not use Back: no visible IME would close the Activity.
   node("csv_parse").click();ready(s)
   s.onActivity {a->val text=a.findViewById<TextView>(dev.fusion.automation.R.id.csv_preview).text.toString();assertTrue(text.contains("=1+1"));assertFalse(text.contains("DO_NOT_EXPORT"));assertFalse(text.contains("Alice"))}
   node("csv_columns").text="1"
   s.onActivity {a->assertFalse(a.findViewById<Button>(dev.fusion.automation.R.id.csv_save).isEnabled);assertEquals("",a.findViewById<TextView>(dev.fusion.automation.R.id.csv_preview).text.toString());assertFalse(a.findViewById<TextView>(dev.fusion.automation.R.id.csv_status).text.startsWith("Preview ready."))}
   node("csv_columns").text="2";s.onActivity {it.window.insetsController?.hide(android.view.WindowInsets.Type.ime())};node("csv_parse").click();ready(s)
   val beforeSave=nativeSave(s)
   val pickerOpened=device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui"))),10000)
   var afterSave=""
   if(!pickerOpened) s.onActivity {a->afterSave="status="+a.findViewById<TextView>(dev.fusion.automation.R.id.csv_status).text+"; saveEnabled="+a.findViewById<Button>(dev.fusion.automation.R.id.csv_save).isEnabled+"; focus="+a.hasWindowFocus()}
   assertTrue("One real Save touch did not open actual DocumentsUI: $beforeSave; after=$afterSave; foreground=${device.currentPackageName}",pickerOpened)
   val name=device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000);assertNotNull(name);name.text="r21-columns-${UUID.randomUUID()}.xlsx"
   val save=device.wait(Until.findObject(By.text(Pattern.compile("(?i)save"))),10000);assertNotNull(save);save.click()
   assertTrue(device.wait(Until.gone(By.pkg(Pattern.compile(".*documentsui"))),10000))
   val verified=device.wait(Until.hasObject(By.res(pkg,"csv_status").textStartsWith("XLSX readback verified:")),15000)
   var outcome=""
   s.onActivity {a->outcome=a.findViewById<TextView>(dev.fusion.automation.R.id.csv_status).text.toString()}
   assertTrue("Export outcome: $outcome; foreground ${device.currentPackageName}",verified)
   s.onActivity {a->assertTrue(a.findViewById<EditText>(dev.fusion.automation.R.id.csv_source).text.contains("DO_NOT_EXPORT"))}
  }
 }
 @Test fun recreationLosesDraftAndCannotExportOldPreview() {
  ActivityScenario.launch(CsvWorkbenchActivity::class.java).use {s->
   node("csv_source").text="value\n".repeat(256);s.onActivity {it.window.insetsController?.hide(android.view.WindowInsets.Type.ime())};node("csv_parse").click();ready(s)
   s.onActivity {a->assertTrue(a.findViewById<TextView>(dev.fusion.automation.R.id.csv_preview).text.contains("Row 256:"))}
   s.recreate()
   s.onActivity {a->assertEquals("",a.findViewById<EditText>(dev.fusion.automation.R.id.csv_source).text.toString());assertFalse(a.findViewById<Button>(dev.fusion.automation.R.id.csv_save).isEnabled)}
  }
 }
 @Test fun inFlightEditorMutationDiscardsStaleWorkerResult() {
  ActivityScenario.launch(CsvWorkbenchActivity::class.java).use {s->
   s.onActivity {a->
    val input=a.findViewById<EditText>(dev.fusion.automation.R.id.csv_source)
    input.setText("old,value")
    a.findViewById<Button>(dev.fusion.automation.R.id.csv_parse).performClick()
    assertFalse(input.isEnabled)
    // Real main-thread mutation while a real child request is in flight; callback cannot run until this returns.
    input.setText("new,value")
   }
   assertTrue(device.wait(Until.hasObject(By.res(pkg,"csv_status").textStartsWith("Input changed while parsing;")),15000))
   s.onActivity {a->assertFalse(a.findViewById<Button>(dev.fusion.automation.R.id.csv_save).isEnabled);assertTrue(a.findViewById<Button>(dev.fusion.automation.R.id.csv_parse).isEnabled)}
  }
 }

}

package dev.fusion.shell
import android.widget.*
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
 private fun ready() {assertTrue(device.wait(Until.hasObject(By.res(pkg,"csv_status").textStartsWith("Preview ready.")),15000))}
 @Test fun actualSelectionEditInvalidatesPreviewAndSystemSaveReadsBack() {
  ActivityScenario.launch(CsvWorkbenchActivity::class.java).use {s->
   node("csv_source").text="name,value,secret\nAlice,=1+1,DO_NOT_EXPORT"
   node("csv_columns").text="2"
   s.onActivity {it.window.insetsController?.hide(android.view.WindowInsets.Type.ime())} // Do not use Back: no visible IME would close the Activity.
   node("csv_parse").click();ready()
   s.onActivity {a->val text=a.findViewById<TextView>(dev.fusion.automation.R.id.csv_preview).text.toString();assertTrue(text.contains("=1+1"));assertFalse(text.contains("DO_NOT_EXPORT"));assertFalse(text.contains("Alice"))}
   node("csv_columns").text="1"
   s.onActivity {a->assertFalse(a.findViewById<Button>(dev.fusion.automation.R.id.csv_save).isEnabled);assertEquals("",a.findViewById<TextView>(dev.fusion.automation.R.id.csv_preview).text.toString())}
   node("csv_columns").text="2";s.onActivity {it.window.insetsController?.hide(android.view.WindowInsets.Type.ime())};node("csv_parse").click();ready();node("csv_save").click()
   assertTrue(device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui"))),10000))
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
   node("csv_source").text="value\n".repeat(256);s.onActivity {it.window.insetsController?.hide(android.view.WindowInsets.Type.ime())};node("csv_parse").click();ready()
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

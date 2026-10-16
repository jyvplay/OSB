package dev.fusion.shell
import android.content.ContentValues
import android.provider.MediaStore
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.fusion.automation.CsvWorkbenchActivity
import dev.fusion.ooxml.CsvTable
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.regex.Pattern
class R22XlsxUiDeviceTest {
 @get:Rule val disposable=DisposableEmulatorRule()
 private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private val pkg get()=context.packageName
 private fun node(id:String):UiObject2 {
  UiScrollable(UiSelector().resourceId("$pkg:id/csv_scroll")).scrollIntoView(UiSelector().resourceId("$pkg:id/$id"))
  return requireNotNull(device.wait(Until.findObject(By.res(pkg,id)),10000))
 }
 private fun status(prefix:String) {assertTrue("Missing status $prefix",device.wait(Until.hasObject(By.res(pkg,"csv_status").textStartsWith(prefix)),15000))}
 @Test fun actualReadOnlyPickerImportProjectionAndNewCopyPreserveSource() {
  val resolver=context.contentResolver;val name="r22-source-${UUID.randomUUID()}.xlsx";val output="r22-copy-${UUID.randomUUID()}.xlsx"
  val original=CsvTable.of(listOf(listOf("name","value","secret"),listOf("Alice","=1+1","DO_NOT_EXPORT"))).workbook().bytes
  val uri=requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
   put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
   put(MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS);put(MediaStore.MediaColumns.IS_PENDING,1)
  }))
  try {
   resolver.openOutputStream(uri,"wt")!!.use {it.write(original)}
   resolver.update(uri,ContentValues().apply {put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
   ActivityScenario.launch(CsvWorkbenchActivity::class.java).use {s->
    node("csv_import").click()
    assertTrue(device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui"))),10000))
    requireNotNull(device.wait(Until.findObject(By.text(name)),10000)).click()
    assertTrue(device.wait(Until.gone(By.pkg(Pattern.compile(".*documentsui"))),10000))
    node("csv_status");status("XLSX imported:")
    node("csv_columns").text="2";s.onActivity {it.window.insetsController?.hide(android.view.WindowInsets.Type.ime())}
    node("csv_parse").click();node("csv_status");status("Preview ready.")
    s.onActivity {a->val text=a.findViewById<TextView>(dev.fusion.automation.R.id.csv_preview).text.toString();assertTrue(text.contains("=1+1"));assertFalse(text.contains("Alice"));assertFalse(text.contains("DO_NOT_EXPORT"))}
    node("csv_save").click()
    assertTrue(device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui"))),10000))
    requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000)).text=output
    requireNotNull(device.wait(Until.findObject(By.text(Pattern.compile("(?i)save"))),10000)).click()
    assertTrue(device.wait(Until.gone(By.pkg(Pattern.compile(".*documentsui"))),10000))
    node("csv_status");status("XLSX readback verified:")
    resolver.openInputStream(uri)!!.use {assertArrayEquals(original,it.readBytes())}
    val copy=resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.MediaColumns._ID),"${MediaStore.MediaColumns.DISPLAY_NAME}=?",arrayOf(output),null)!!.use {c->assertTrue(c.moveToFirst());android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,c.getLong(0))}
    try {
     val bytes=resolver.openInputStream(copy)!!.use {it.readBytes()}
     val table=dev.fusion.format.pdf.sandbox.FormatLease(context).use {it.evaluateOwnedXlsx(bytes)}.table!!
     assertEquals(listOf(listOf("value"),listOf("=1+1")),table.rows)
    } finally {resolver.delete(copy,null,null)}
    s.recreate();s.onActivity {a->assertFalse(a.findViewById<Button>(dev.fusion.automation.R.id.csv_save).isEnabled);assertEquals("",a.findViewById<EditText>(dev.fusion.automation.R.id.csv_source).text.toString())}
   }
  } finally {resolver.delete(uri,null,null)}
 }
 @Test fun cancelledRealImportPickerKeepsDraftButInvalidatesExport() {
  ActivityScenario.launch(CsvWorkbenchActivity::class.java).use {s->
   node("csv_source").text="KEEP";s.onActivity {it.window.insetsController?.hide(android.view.WindowInsets.Type.ime())}
   node("csv_parse").click();node("csv_status");status("Preview ready.")
   node("csv_import").click();assertTrue(device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui"))),10000))
   device.pressBack();assertTrue(device.wait(Until.gone(By.pkg(Pattern.compile(".*documentsui"))),10000))
   node("csv_status");status("Import cancelled;")
   s.onActivity {a->assertEquals("KEEP",a.findViewById<EditText>(dev.fusion.automation.R.id.csv_source).text.toString());assertFalse(a.findViewById<Button>(dev.fusion.automation.R.id.csv_save).isEnabled)}
  }
 }
}

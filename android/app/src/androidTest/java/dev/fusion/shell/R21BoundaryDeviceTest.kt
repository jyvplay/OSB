package dev.fusion.shell
import android.content.Context
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import dev.fusion.format.pdf.sandbox.FormatLease
import dev.fusion.ooxml.CsvTable
import dev.fusion.browser.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class R21BoundaryDeviceTest {
 @get:Rule val disposable=DisposableEmulatorRule()
 private val context get()=ApplicationProvider.getApplicationContext<Context>()
 private fun names()=context.cacheDir.listFiles()!!.filter {it.name.startsWith("fusion-owned-csv-")}.map {it.name}.toSet()
 @Test fun csvGrammarRunsInDistinctUidAndOwnedInputIsUnlinked() {
  val before=names();val raw="name,value,private\r\n\"a,b\",=1+1,secret".toByteArray()
  val result=FormatLease(context).use {it.evaluateOwnedCsv(raw)}
  assertNotEquals(Process.myUid(),result.uid);assertNotEquals(Process.myPid(),result.pid);assertFalse(result.internetGranted)
  assertEquals(listOf("a,b","=1+1","secret"),requireNotNull(result.table).rows[1]);assertEquals(raw.size,result.bytes);assertEquals(before,names())
  val selected=result.table!!.project(listOf(1));assertEquals(listOf(listOf("value"),listOf("=1+1")),selected.rows)
  assertEquals(selected.rows,CsvTable.readFrame(selected.frame()).rows)
 }
 @Test fun malformedCsvRefusesAndCleansUpThenFreshChildSucceeds() {
  val before=names()
  for(raw in listOf("a,b\nc".toByteArray(),"\"unclosed".toByteArray(),byteArrayOf(0xff.toByte()),ByteArray(65537))) {
   assertTrue(runCatching {FormatLease(context).use {it.evaluateOwnedCsv(raw)}}.isFailure);assertEquals(before,names())
  }
  val r=FormatLease(context).use {it.evaluateOwnedCsv("\"\"".toByteArray())}
  assertEquals(listOf(listOf("")),r.table!!.rows);assertEquals(before,names())
 }
 @Test fun consentCannotSurviveSameUrlOrUnsupportedNavigationOrReplay() {
  val s=BrowserConsentSession("native");val id=s.open("https://example.com/a");val kind=PermissionLeases.Kind.CAMERA
  val p=s.request(id,kind);s.navigate(id,"https://example.com/a")
  assertNull(s.decide(p,true,SystemClock.elapsedRealtime(),1000))
  val g=requireNotNull(s.decide(s.request(id,kind),true,SystemClock.elapsedRealtime(),60000))
  assertTrue(runCatching {s.navigate(id,"file:///private")}.isFailure)
  assertFalse(s.consume(g,id,kind,SystemClock.elapsedRealtime()))
  s.navigate(id,"https://example.com/a")
  val valid=requireNotNull(s.decide(s.request(id,kind),true,SystemClock.elapsedRealtime(),60000))
  assertTrue(s.consume(valid,id,kind,SystemClock.elapsedRealtime()));assertFalse(s.consume(valid,id,kind,SystemClock.elapsedRealtime()))
 }
 @Test fun exactByteCapAndDenseEmptyTableCrossRealBinder() {
  val exact=List(15) {"x".repeat(4096)}.plus("x".repeat(4081)).joinToString(",").toByteArray()
  assertEquals(65536,exact.size)
  val a=FormatLease(context).use {it.evaluateOwnedCsv(exact)}
  assertEquals(16,a.table!!.columns);assertEquals(4081,a.table!!.rows[0][15].length)
  val dense=((",".repeat(31)+"\n").repeat(256)).toByteArray()
  val b=FormatLease(context).use {it.evaluateOwnedCsv(dense)}
  assertEquals(256,b.table!!.rows.size);assertEquals(32,b.table!!.columns);assertEquals(32780,b.table!!.frame().size)
 }

 @Test fun literalWorksheetBeyondTaskBudgetWritesAndReopensRealProvider() {
  val bytes=CsvTable.of(List(256) {List(32) {""}}).workbook().bytes
  assertTrue(bytes.size>512*1024)
  val resolver=context.contentResolver
  val uri=requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,android.content.ContentValues().apply {
   put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"r21-large-${java.util.UUID.randomUUID()}.xlsx")
   put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
   put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS)
  }))
  try {
   val receipt=dev.fusion.automation.TaskTransferIO.writeCreatedLiteralWorksheet(resolver,uri,bytes)
   assertEquals(bytes.size,receipt.bytes)
   resolver.openInputStream(uri)!!.use {assertArrayEquals(bytes,it.readBytes())}
   // Patch attack: a matching prefix plus trailing provider bytes must be refused by the comparator.
   resolver.openOutputStream(uri,"wa")!!.use {it.write(42)}
   assertTrue(runCatching {resolver.openInputStream(uri)!!.use {dev.fusion.automation.ExactReadback.verify(it,bytes)}}.isFailure)
  } finally {resolver.delete(uri,null,null)}
 }

}

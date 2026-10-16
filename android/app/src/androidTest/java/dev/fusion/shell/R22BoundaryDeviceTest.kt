package dev.fusion.shell
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import dev.fusion.ooxml.*
import dev.fusion.format.pdf.sandbox.FormatLease
import dev.fusion.browser.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class R22BoundaryDeviceTest {
 @get:Rule val disposable=DisposableEmulatorRule()
 private val context get()=ApplicationProvider.getApplicationContext<Context>()
 private fun names()=context.cacheDir.listFiles()!!.filter {it.name.startsWith("fusion-owned-xlsx-")}.map {it.name}.toSet()
 @Test fun literalAndIndependentSharedWorkbookRunInDistinctUid() {
  val before=names();val bytes=CsvTable.of(listOf(listOf("=1+1","private"))).workbook().bytes
  val r=FormatLease(context).use {it.evaluateOwnedXlsx(bytes)}
  assertNotEquals(Process.myUid(),r.uid);assertNotEquals(Process.myPid(),r.pid);assertFalse(r.internetGranted)
  assertEquals(bytes.size,r.bytes);assertEquals(listOf(listOf("=1+1","private")),r.table!!.rows);assertEquals(before,names())
  val shared=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("r22-shared.xlsx").use {it.readBytes()}
  val s=FormatLease(context).use {it.evaluateOwnedXlsx(shared)}
  assertEquals(listOf(listOf("=1+1","",""),listOf("","",""),listOf("","","shared α 29")),s.table!!.rows)
  assertEquals(before,names())
 }
 @Test fun corruptAndOversizeXlsxRefuseThenFreshLeaseSucceeds() {
  val before=names();val valid=CsvTable.of(listOf(listOf("safe"))).workbook().bytes
  val corrupt=valid.copyOf();corrupt[14]=(corrupt[14].toInt() xor 1).toByte()
  for(raw in listOf(corrupt,valid+0,byteArrayOf(1,2,3),ByteArray(2097153))) {
   assertTrue(runCatching {FormatLease(context).use {it.evaluateOwnedXlsx(raw)}}.isFailure);assertEquals(before,names())
  }
  assertEquals("safe",FormatLease(context).use {it.evaluateOwnedXlsx(valid)}.table!!.rows[0][0])
 }
 @Test fun maximumDenseLiteralRoundTripCrossesBinderWithNewXmlNodeBudget() {
  val rows=List(256) {List(32) {""}};val bytes=CsvTable.of(rows).workbook().bytes
  assertTrue(bytes.size>512*1024)
  val r=FormatLease(context).use {it.evaluateOwnedXlsx(bytes)}
  assertEquals(rows,r.table!!.rows);assertEquals(32780,r.table!!.frame().size)
 }
 @Test fun updatedXmlNodeLimitAndXincludeAreDataOnlyOnAndroid() {
  assertEquals(32767,BoundedXml.parse(("<a>"+"<b/>".repeat(32767)+"</a>").toByteArray()).children.size)
  assertTrue(runCatching {BoundedXml.parse(("<a>"+"<b/>".repeat(32768)+"</a>").toByteArray())}.isFailure)
  val canary=java.io.File(context.cacheDir,"r22-xinclude").apply {writeText("PRIVATE")}
  try {
   val root=BoundedXml.parse("<a xmlns:x='http://www.w3.org/2001/XInclude'><x:include href='${canary.toURI()}' parse='text'/></a>".toByteArray())
   assertEquals("",root.text);assertEquals(BoundedXml.Name("http://www.w3.org/2001/XInclude","include"),root.children.single().name)
   assertEquals("",root.children.single().text);assertEquals("PRIVATE",canary.readText())
  } finally {canary.delete()}
 }
 @Test fun suspensionBurnsPromptAndGrantUntilFreshDocument() {
  val s=BrowserConsentSession("r22");val t=s.open("https://example.com/");val kind=PermissionLeases.Kind.CAMERA
  val p=s.request(t,kind);val g=requireNotNull(s.decide(s.request(t,kind),true,0,1000))
  s.suspendDocument(t);assertNull(s.decide(p,true,0,1000));assertFalse(s.consume(g,t,kind,1))
  assertTrue(runCatching {s.request(t,kind)}.isFailure)
  s.navigate(t,"https://example.com/");assertTrue(s.consume(requireNotNull(s.decide(s.request(t,kind),true,0,1000)),t,kind,1))
 }
}

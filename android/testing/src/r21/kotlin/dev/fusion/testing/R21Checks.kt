package dev.fusion.testing
import dev.fusion.ooxml.*
import dev.fusion.browser.model.*
import java.util.concurrent.*
object R21Checks {
 @JvmStatic fun main(args:Array<String>) {
  var n=0
  fun ok(v:Boolean) {check(v);n++}
  fun bad(f:()->Unit) {var thrown=false;try {f()} catch(_:IllegalArgumentException) {thrown=true};ok(thrown)}
  fun parse(s:String)=CsvTable.parse(CsvTable.utf8(s))
  ok(parse("").rows.isEmpty());ok(parse("\uFEFF").rows.isEmpty());ok(parse("\"\"").rows==listOf(listOf("")))
  ok(parse("a,\"b,c\",\"d\"\"e\"\r\nx,y,z\n").rows==listOf(listOf("a","b,c","d\"e"),listOf("x","y","z")))
  ok(parse("\"a\r\nb\",c").rows[0][0]=="a\r\nb")
  for(s in listOf("a\"b","\"x\"q","\"unterminated","a\rb","a,b\nc","a\u0000", "a\u0001", "x".repeat(4097),",".repeat(32),"a\n".repeat(257))) bad {parse(s)}
  bad {CsvTable.parse(byteArrayOf(0xc3.toByte(),0x28))};bad {CsvTable.parse(ByteArray(65537))};bad {CsvTable.utf8("\uD800")}
  ok(parse("x".repeat(4096)).rows[0][0].length==4096)
  ok(parse("a\n".repeat(256)).rows.size==256);ok(parse(",".repeat(31)).columns==32)
  val exact=List(15) {"x".repeat(4096)}.plus("x".repeat(4081)).joinToString(",")
  ok(exact.toByteArray().size==65536);ok(parse(exact).columns==16);bad {parse(exact+"x")}
  ok(parse((",".repeat(31)+"\n").repeat(256)).rows.size==256)
  bad {CsvTable.readFrame(ByteArray(131073))}
  val badLength=java.nio.ByteBuffer.allocate(16).putInt(0x46435431).putInt(1).putInt(1).putInt(Int.MAX_VALUE).array()
  bad {CsvTable.readFrame(badLength)}
  ok(parse("safe,_x0041_").project(listOf(0)).workbook().bytes.isNotEmpty())
  val display=parse("\"a\n\"\"b\",\u202ehidden")
  ok(display.preview().contains("\\n"));ok(display.preview().contains("\\u202e"));ok(!display.preview().contains('\u202e'))
  ok(display.rows[0][1].startsWith('\u202e'));ok(parse("z\n".repeat(256)).preview().contains("Row 256:"))
  val table=parse("name,amount,secret\nAlice,=1+1,password")
  ok(table.project(listOf(1,0)).rows[1]==listOf("=1+1","Alice"))
  bad {table.project(listOf(1,1))};bad {table.project(emptyList())};bad {table.project(listOf(3))}
  val other=parse("name,amount,PRIVATE\nAlice,=1+1,OTHER")
  ok(table.project(listOf(0,1)).workbook().bytes.contentEquals(other.project(listOf(0,1)).workbook().bytes))
  val frame=table.frame();ok(CsvTable.readFrame(frame).rows==table.rows)
  for(i in frame.indices) bad {CsvTable.readFrame(frame.copyOf(i))}
  bad {CsvTable.readFrame(frame+0)}
  val bytes="a,b\nc,d".toByteArray();val owned=OwnedCsvRequest(bytes);bytes.fill(0)
  ok(owned.bytes().contentEquals("a,b\nc,d".toByteArray()));ok(owned.accept(owned.size,owned.digest,parse("a,b\nc,d").frame()).columns==2)
  bad {owned.accept(owned.size,"0".repeat(64),frame)}
  // Counterexample: correlation is NOT honest computation; correctly bounded wrong cells still pass.
  ok(owned.accept(owned.size,owned.digest,parse("forged").frame()).rows[0][0]=="forged")
  for(k in 0 until 100) {
   val t=CsvTable.of(List(1+k%20) {r->List(1+k%10) {c->"R$r C$c =${k},\"\nα"}})
   ok(CsvTable.readFrame(t.frame()).rows==t.rows)
   ok(t.project(listOf(0)).rows==t.rows.map {listOf(it[0])})
  }
  val kind=PermissionLeases.Kind.CAMERA
  val s=BrowserConsentSession("local");val tab=s.open("https://example.com/a")
  val p=s.request(tab,kind);s.navigate(tab,"https://example.com/a");ok(s.decide(p,true,0,100)==null)
  val p2=s.request(tab,kind);s.navigate(tab,"https://other.example/");s.navigate(tab,"https://example.com/a");ok(s.decide(p2,true,0,100)==null)
  val denied=s.request(tab,kind);ok(s.decide(denied,false,0,100)==null);ok(s.decide(denied,true,0,100)==null)
  val grant=requireNotNull(s.decide(s.request(tab,kind),true,1,100))
  val pool=Executors.newFixedThreadPool(2);val start=CountDownLatch(1)
  try {val jobs=List(2) {pool.submit<Boolean> {start.await();s.consume(grant,tab,kind,2)}};start.countDown();ok(jobs.count {it.get(5,TimeUnit.SECONDS)}==1)} finally {pool.shutdownNow()}
  val stale=requireNotNull(s.decide(s.request(tab,kind),true,3,100));s.navigate(tab,"https://example.com/a");ok(!s.consume(stale,tab,kind,4))
  val wrong=requireNotNull(s.decide(s.request(tab,kind),true,5,100));ok(!s.consume(wrong,tab,PermissionLeases.Kind.MICROPHONE,6));ok(!s.consume(wrong,tab,kind,7))
  val pending=s.request(tab,kind);s.close(tab);ok(s.decide(pending,true,10,100)==null)
  val tab2=s.open("https://example.com");ok(tab2>tab)
  val invalid=requireNotNull(s.decide(s.request(tab2,kind),true,11,100))
  val delayed=s.request(tab2,kind)
  bad {s.navigate(tab2,"file:///private")}
  ok(!s.consume(invalid,tab2,kind,12)) // Patch target: failed/unsupported navigation must also revoke.
  ok(s.decide(delayed,true,12,100)==null);bad {s.request(tab2,kind)}
  s.navigate(tab2,"https://example.com/new");val repaired=requireNotNull(s.decide(s.request(tab2,kind),true,13,100));ok(s.consume(repaired,tab2,kind,14))
  val cap=BrowserConsentSession("capacity");val tid=cap.open("https://example.org")
  val ps=List(64) {cap.request(tid,kind)};bad {cap.request(tid,kind)}
  ok(cap.decide(ps[0],false,0,100)==null);ok(cap.request(tid,kind)!==ps[0])
  val expire=requireNotNull(cap.decide(ps[1],true,100,1));ok(!cap.consume(expire,tid,kind,101))
  cap.shutdown();ok(cap.decide(ps[2],true,102,1)==null)
  s.shutdown();ok(s.snapshot().tabs.size==1)
  println("R21: $n actual public-consumer assertions PASS; no renderer or anonymity proof")
 }
}

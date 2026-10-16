package dev.fusion.shell
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import dev.fusion.ooxml.BoundedXml
import dev.fusion.archive.StrictZip
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class R22PlatformDeviceTest {
 @get:Rule val disposable=DisposableEmulatorRule()
 @Test fun actualAndroidSaxPreservesNamespacesAndRefusesDtdAndBounds() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val canary=java.io.File(context.cacheDir,"r22-xml-canary").apply {writeText("NOT_PUBLIC")}
  try {
   assertEquals("NOT_PUBLIC",canary.readText()) // Accessible-file control; not a claim that Android default SAX is vulnerable.
   val positive=BoundedXml.parse("<x:a xmlns:x='urn:r22'><x:b> α&amp;&lt; </x:b></x:a>".toByteArray())
   assertEquals(BoundedXml.Name("urn:r22","a"),positive.name)
   assertEquals(" α&< ",positive.children.single().text)
   for(raw in listOf("<!DOCTYPE a [<!ENTITY x SYSTEM '${canary.toURI()}'>]><a>&x;</a>","<a><![CDATA[x]]></a>","<a><!--x--></a>","<?evil x?><a/>","<a>&missing;</a>","<a><b></a>","<?xml version='1.1'?><a/>","<?xml version='1.0' encoding='UTF-16'?><a/>")) {
    assertTrue(raw,runCatching {BoundedXml.parse(raw.toByteArray())}.isFailure)
   }
   assertEquals("a",BoundedXml.parse(("<a>".repeat(32)+"</a>".repeat(32)).toByteArray()).name.local)
   assertTrue(runCatching {BoundedXml.parse(("<a>".repeat(33)+"</a>".repeat(33)).toByteArray())}.isFailure)
   assertTrue(runCatching {BoundedXml.parse(byteArrayOf(0xff.toByte()))}.isFailure)
   assertEquals("NOT_PUBLIC",canary.readText())
  } finally {canary.delete()}
 }
 @Test fun actualAndroidInflaterAcceptsPythonZipAndRejectsHeaderDisagreement() {
  val raw=android.util.Base64.decode("UEsDBBQAAAAIAI0kUF3reVE2FwAAABUAAAAFAAAAYS50eHQrSk3MUUhJTctJLElNUUjLrCgpLUoFAFBLAQIUAxQAAAAIAI0kUF3reVE2FwAAABUAAAAFAAAAAAAAAAAAAACAAQAAAABhLnR4dFBLBQYAAAAAAQABADMAAAA6AAAAAAA=",android.util.Base64.DEFAULT)
  val names=setOf("a.txt")
  assertEquals("real deflated fixture",String(StrictZip.read(raw,names).getValue("a.txt")))
  for(index in listOf(6,8,14,18,22,30)) {
   val bad=raw.copyOf();bad[index]=(bad[index].toInt() xor 1).toByte()
   assertTrue("Header offset $index",runCatching {StrictZip.read(bad,names)}.isFailure)
  }
  assertTrue(runCatching {StrictZip.read(raw+0,names)}.isFailure)
  assertTrue(runCatching {StrictZip.read(raw.copyOf(raw.size-1),names)}.isFailure)
 }
}

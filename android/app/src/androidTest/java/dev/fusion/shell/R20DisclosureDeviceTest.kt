package dev.fusion.shell
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import android.os.SystemClock
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.fusion.automation.*
import dev.fusion.browser.model.PermissionLeases
import dev.fusion.security.LinkDisclosure
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.zip.ZipInputStream

class R20DisclosureDeviceTest {
    @Test fun countsProjectionWritesAndReopensRealProviderWithoutIdentifiers() {
        val c=ApplicationProvider.getApplicationContext<android.content.Context>();val r=c.contentResolver
        val book=TaskBook(197,listOf(LocalTask(UUID.randomUUID().toString(),"Sensitive title Alice",java.time.LocalDate.of(2028,2,29))))
        val original=TaskFrame.encode(book);val counts=TaskCounts.from(book)
        val uri=requireNotNull(r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"counts-${UUID.randomUUID()}.xlsx")
            put(MediaStore.MediaColumns.MIME_TYPE,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS)
        }))
        try {
            val receipt=TaskTransferIO.writeCreatedCounts(r,uri,counts)
            val observed=r.openInputStream(uri)!!.use {it.readBytes()}
            assertEquals(observed.size,receipt.bytes);assertArrayEquals(counts.export().bytes,observed)
            val texts=StringBuilder();ZipInputStream(observed.inputStream()).use {zip ->while(zip.nextEntry!=null) {texts.append(String(zip.readBytes()))}}
            for(secret in listOf(book.tasks[0].id,book.tasks[0].title,"2028-02-29","197")) assertFalse(texts.contains(secret))
            assertArrayEquals(original,TaskFrame.encode(book))
        } finally {r.delete(uri,null,null)}
    }
    @Test fun linkProjectionAgreesWithAndroidUriAndRetainsPathIdentityCounterexample() {
        for(input in listOf("https://EXAMPLE.com:443/Alice?token=secret#location","http://[::1]:80/x%2Fy?x=1","https://example.org:444/?#")) {
            val projected=LinkDisclosure.project(input).text;val native=Uri.parse(projected)
            assertNull(native.encodedQuery);assertNull(native.encodedFragment);assertEquals(requireNotNull(Uri.parse(input).encodedPath).ifEmpty {"/"},native.encodedPath)
        }
        assertTrue(LinkDisclosure.project("https://example.com/Alice?token=x").text.contains("Alice"))
    }
    @Test fun permissionLeaseUsesActualElapsedClockAndBurnsWrongContext() {
        val l=PermissionLeases();val s=PermissionLeases.Scope("p",1,"https://example.com/a")
        val t=l.issue(s,PermissionLeases.Kind.CAMERA,SystemClock.elapsedRealtime(),60000)
        assertTrue(l.consume(t,s,PermissionLeases.Kind.CAMERA,SystemClock.elapsedRealtime()))
        assertFalse(l.consume(t,s,PermissionLeases.Kind.CAMERA,SystemClock.elapsedRealtime()))
        val u=l.issue(s,PermissionLeases.Kind.CAMERA,SystemClock.elapsedRealtime(),60000)
        assertFalse(l.consume(u,PermissionLeases.Scope("p",1,"https://other.example"),PermissionLeases.Kind.CAMERA,SystemClock.elapsedRealtime()))
        assertFalse(l.consume(u,s,PermissionLeases.Kind.CAMERA,SystemClock.elapsedRealtime()))
    }
    @Test fun generatedWorkbookMetadataDoesNotDiscloseTestedDefaultTimezones() {
        val original=java.util.TimeZone.getDefault()
        try {
            val c=TaskCounts(1,2,3,4);var baseline:ByteArray?=null
            for(zone in listOf("UTC","America/New_York","Asia/Tokyo","Pacific/Chatham")) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone))
                val bytes=c.export().bytes
                if(baseline==null) baseline=bytes else assertArrayEquals("Timezone $zone influenced package",baseline,bytes)
                ZipInputStream(bytes.inputStream()).use {zip ->var entries=0;while(zip.nextEntry!=null) {assertTrue(zip.readBytes().isNotEmpty());entries++};assertEquals(5,entries)}
            }
        } finally {java.util.TimeZone.setDefault(original)}
    }

}

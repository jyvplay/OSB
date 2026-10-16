package dev.fusion.shell
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import dev.fusion.automation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.zip.ZipInputStream

/** Real platform MediaStore provider; not a fake DocumentsProvider. Picker UX is a separate test. */
class R19TaskTransferDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    private val resolver get()=ApplicationProvider.getApplicationContext<Context>().contentResolver
    private fun created(extension:String,mime:String,body:(Uri)->Unit) {
        val uri=requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"fusion-r19-${UUID.randomUUID()}.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE,mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/FusionR19")
            put(MediaStore.MediaColumns.IS_PENDING,1)
        }))
        try {body(uri)} finally {resolver.delete(uri,null,null)}
    }
    private fun book()=TaskBook(19,listOf(LocalTask(UUID(0,1).toString(),"=2+3 & literal",java.time.LocalDate.of(2028,2,29)),LocalTask(UUID(0,2).toString(),"Archived",done=true,archived=true)))
    @Test fun actualProviderSnapshotWriteReadbackImportAndCorruptionRefusal()=created("fts","application/octet-stream") {uri ->
        val before=book();val receipt=TaskTransferIO.writeCreatedDocument(resolver,uri,before)
        assertEquals(19L,receipt.revision);assertEquals(TaskFrame.encode(before).size,receipt.bytes)
        assertEquals(before.tasks,TaskTransferIO.read(resolver,uri).tasks)
        val plan=TaskMergePreview.inspect(TaskBook(),TaskTransferIO.read(resolver,uri));assertEquals(1L,plan.apply(TaskBook()).revision)
        resolver.openOutputStream(uri,"wt")!!.use {it.write(ByteArray(TaskFrame.MAX_BYTES+1))}
        assertTrue(runCatching {TaskTransferIO.read(resolver,uri)}.isFailure)
        assertTrue(runCatching {TaskTransferIO.read(resolver,Uri.parse("file:///not-a-grant"))}.isFailure)
    }
    @Test fun actualProviderTwoSheetReportIsLiteralAndNotATaskImport()=created("xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") {uri ->
        val receipt=TaskTransferIO.writeCreatedWorkbook(resolver,uri,book());assertTrue(receipt.bytes>0)
        val entries=mutableMapOf<String,String>()
        resolver.openInputStream(uri)!!.use {input->ZipInputStream(input).use {zip->while(true) {
            val entry=zip.nextEntry?:break;entries[entry.name]=zip.readBytes().toString(Charsets.UTF_8)
        }}}
        assertEquals(6,entries.size)
        assertTrue(entries.getValue("xl/workbook.xml").contains("Archived"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("=2+3 &amp; literal"))
        assertFalse(entries.values.any {it.contains("<f>")})
        assertTrue(runCatching {TaskTransferIO.read(resolver,uri)}.isFailure)
    }
}

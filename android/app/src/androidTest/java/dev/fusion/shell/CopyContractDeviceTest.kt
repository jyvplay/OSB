package dev.fusion.shell

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fusion.editor.CopyDestinationActivity
import dev.fusion.editor.CopyDestinationContract
import dev.fusion.editor.CopyPickerOutcome
import dev.fusion.editor.CopyPickerRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Native Intent/manifest checks, UNRUN. Not real picker delivery, URI grants, or provider acceptance. */
@RunWith(AndroidJUnit4::class)
class CopyContractDeviceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val id="12345678-1234-1234-1234-123456789abc-1"
    @Test fun brokerIsExplicitAndNotExported() {
        val contract=CopyDestinationContract()
        val launch=contract.createIntent(context,CopyPickerRequest(id,"copy.txt"))
        assertEquals(ComponentName(context,CopyDestinationActivity::class.java),launch.component)
        val info=context.packageManager.getActivityInfo(requireNotNull(launch.component),PackageManager.ComponentInfoFlags.of(0))
        assertFalse(info.exported)
        assertEquals(setOf("dev.fusion.editor.COPY_REQUEST","dev.fusion.editor.COPY_NAME"),launch.extras!!.keySet())
    }
    @Test fun officeMimeIsExplicitlyAllowlistedWithoutChangingPlainTextContract() {
        val contract=CopyDestinationContract()
        val docx=contract.createIntent(context,CopyPickerRequest(id,"copy.docx",CopyDestinationContract.DOCX_MIME))
        assertEquals(CopyDestinationContract.DOCX_MIME,docx.getStringExtra("dev.fusion.editor.COPY_MIME"))
        val xlsx=contract.createIntent(context,CopyPickerRequest(id,"copy.xlsx",CopyDestinationContract.XLSX_MIME))
        assertEquals(CopyDestinationContract.XLSX_MIME,xlsx.getStringExtra("dev.fusion.editor.COPY_MIME"))
        assertTrue(runCatching { contract.createIntent(context,CopyPickerRequest(id,"copy.bin","application/x-unknown")) }.isFailure)
    }
    @Test fun uncorrelatedResultNeverBecomesCurrentCancellation() {
        val contract=CopyDestinationContract()
        assertNull(contract.parseResult(Activity.RESULT_CANCELED,null))
        assertNull(contract.parseResult(Activity.RESULT_OK,Intent().setData(Uri.parse("content://provider/document/a"))))
        val cancel=Intent().putExtra("dev.fusion.editor.COPY_REQUEST",id).putExtra("dev.fusion.editor.COPY_OUTCOME","CANCELED")
        val parsed=requireNotNull(contract.parseResult(Activity.RESULT_OK,cancel))
        assertEquals(id,parsed.requestId)
        assertEquals(CopyPickerOutcome.CANCELED,parsed.outcome)
        assertNull(parsed.uri)
    }
    @Test fun selectedResultRetainsItsOriginalIdentity() {
        val result=Intent().putExtra("dev.fusion.editor.COPY_REQUEST",id)
            .putExtra("dev.fusion.editor.COPY_OUTCOME","SELECTED").setData(Uri.parse("content://provider/document/new"))
        val parsed=requireNotNull(CopyDestinationContract().parseResult(Activity.RESULT_OK,result))
        assertEquals(id,parsed.requestId)
        assertEquals(result.data,parsed.uri)
        assertNull(CopyDestinationContract().parseResult(Activity.RESULT_OK,result.setData(null)))
    }
}

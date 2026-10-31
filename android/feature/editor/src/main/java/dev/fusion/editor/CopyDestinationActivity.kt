package dev.fusion.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fusion.documents.CopyProtocol
import dev.fusion.documents.TextBytes

data class CopyPickerRequest(val requestId: String, val suggestedName: String, val mimeType: String = "text/plain")
enum class CopyPickerOutcome { SELECTED, CANCELED, FAILED }
data class CopyPickerResult(val requestId: String, val outcome: CopyPickerOutcome, val uri: Uri?)

/** Result identity comes from our non-exported broker, never from the external document provider. */
class CopyDestinationContract : ActivityResultContract<CopyPickerRequest, CopyPickerResult?>() {
    override fun createIntent(context: Context, input: CopyPickerRequest): Intent {
        require(CopyProtocol.validRequestId(input.requestId))
        require(input.suggestedName.isNotBlank() && TextBytes.encodedSize(input.suggestedName)<=1024)
        require(input.mimeType in ALLOWED_MIMES)
        return Intent(context,CopyDestinationActivity::class.java)
            .putExtra(REQUEST,input.requestId).putExtra(NAME,input.suggestedName).apply {
                // Preserve the existing text-copy intent contract; a missing MIME means text/plain.
                if (input.mimeType != "text/plain") putExtra(MIME,input.mimeType)
            }
    }
    override fun parseResult(resultCode: Int, intent: Intent?): CopyPickerResult? {
        if(resultCode!=Activity.RESULT_OK || intent==null) return null // Never attribute an anonymous cancel to the current request.
        val id=intent.getStringExtra(REQUEST)?.takeIf(CopyProtocol::validRequestId) ?: return null
        val outcome=runCatching { CopyPickerOutcome.valueOf(intent.getStringExtra(OUTCOME).orEmpty()) }.getOrNull() ?: return null
        if(outcome==CopyPickerOutcome.SELECTED && intent.data==null) return null
        return CopyPickerResult(id,outcome,if(outcome==CopyPickerOutcome.SELECTED) intent.data else null)
    }
    companion object {
        internal const val REQUEST="dev.fusion.editor.COPY_REQUEST"
        internal const val NAME="dev.fusion.editor.COPY_NAME"
        internal const val OUTCOME="dev.fusion.editor.COPY_OUTCOME"
        internal const val MIME="dev.fusion.editor.COPY_MIME"
        const val DOCX_MIME="application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val XLSX_MIME="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        internal val ALLOWED_MIMES=setOf("text/plain", DOCX_MIME, XLSX_MIME)
    }
}

/**
 * PR-01 / FF-15: one immutable correlation identity per broker Activity instance.
 * No document content, source URI or recovery payload is sent to the broker/provider.
 * [U] verify result/grant propagation and process recreation on API 34/36/37 and OEM pickers.
 */
class CopyDestinationActivity : ComponentActivity() {
    private var launched=false
    private var finished=false
    private val requestId: String? get()=intent.getStringExtra(CopyDestinationContract.REQUEST)?.takeIf(CopyProtocol::validRequestId)
    private val picker=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK && result.data?.data!=null)
            finishResult(CopyPickerOutcome.SELECTED,result.data?.data,result.data?.flags ?: 0)
        else finishResult(CopyPickerOutcome.CANCELED)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(requestId==null) { finish(); return }
        // System Back/finish still returns this attempt's cancellation, not an uncorrelated URI.
        setResult(Activity.RESULT_OK,resultIntent(CopyPickerOutcome.CANCELED))
        onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finishResult(CopyPickerOutcome.CANCELED) }
        })
        launched=savedInstanceState?.getBoolean("copy-picker-launched",false) ?: false
        setContent {
            MaterialTheme(colorScheme=darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("Choose a new copy destination",style=MaterialTheme.typography.titleLarge)
                        Text("Select a new file. Android's Create Document action may return an existing item. The app refuses known aliases and nonempty/unreadable targets before opening output. Emptiness is not an atomic freshness guarantee; provider aliasing and concurrent changes are not verified.")
                        Text("If Android restored this screen without a usable picker, cancel and explicitly retry in the editor. An old result cannot authorize a newer attempt.")
                        Button(onClick={ finishResult(CopyPickerOutcome.CANCELED) }) { Text("Cancel this picker attempt") }
                    }
                }
            }
        }
        if(!launched) {
            launched=true
            try {
                val name=intent.getStringExtra(CopyDestinationContract.NAME).orEmpty()
                require(name.isNotBlank() && TextBytes.encodedSize(name)<=1024)
                // Older editor requests carry no MIME extra after process recreation.
                val mime=intent.getStringExtra(CopyDestinationContract.MIME) ?: "text/plain"
                require(mime in CopyDestinationContract.ALLOWED_MIMES)
                picker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(mime).putExtra(Intent.EXTRA_TITLE,name))
            } catch(_: Exception) { finishResult(CopyPickerOutcome.FAILED) }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("copy-picker-launched",launched)
        super.onSaveInstanceState(outState)
    }
    private fun resultIntent(outcome: CopyPickerOutcome)=Intent()
        .putExtra(CopyDestinationContract.REQUEST,requestId).putExtra(CopyDestinationContract.OUTCOME,outcome.name)
    private fun finishResult(outcome: CopyPickerOutcome, uri: Uri?=null, flags: Int=0) {
        if(finished) return
        finished=true
        val output=resultIntent(outcome)
        if(uri!=null) {
            output.data=uri
            // Forward only the read/write grants actually returned by the provider; no broad prefix grant.
            output.addFlags(flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        }
        setResult(Activity.RESULT_OK,output)
        finish()
    }
}

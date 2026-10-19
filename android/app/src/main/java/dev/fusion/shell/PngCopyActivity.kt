// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.view.View
import android.view.WindowInsets
import android.widget.*
import dev.fusion.format.image.PngMetadata
import dev.fusion.format.image.sandbox.PngLease
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit new-copy metadata workflow outside the no-INTERNET companion. No native image decoder is used here.
 * Provider calls are off UI and limited to one process-level operation. A nonresponsive provider can hold that
 * lane until it returns/process exits; no hard provider cancellation, anonymity or forensic-erasure claim.
 */
class PngCopyActivity : Activity() {
    companion object {
        private val lane=Executors.newSingleThreadExecutor { runnable -> Thread(runnable,"png-copy-provider").apply {isDaemon=true} }
        private val occupied=AtomicBoolean(false)
        private const val PICK=31;private const val SAVE=32
        private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
        private fun read(context:Context,uri:Uri):ByteArray {
            context.contentResolver.openInputStream(uri).use { source ->
                requireNotNull(source) {"No readable document"}
                val out=ByteArrayOutputStream();val chunk=ByteArray(8192)
                while(true) {val n=source.read(chunk);if(n<0)break;if(n==0)throw IllegalArgumentException("No-progress provider")
                    require(out.size().toLong()+n<=PngMetadata.MAX_BYTES) {"Input exceeds2MiB"};out.write(chunk,0,n)}
                return out.toByteArray()
            }
        }
    }
    private data class Prepared(val source:Uri,val bytes:ByteArray,val inputBytes:Int,val width:Int,val height:Int,val removed:Int,val workerUid:Int,val inetDenied:Boolean)
    private var generation=Any()
    private var prepared:Prepared?=null
    private var pendingSave:Prepared?=null
    private var busy=false
    private lateinit var status:TextView
    private lateinit var summary:TextView
    private lateinit var choose:Button
    private lateinit var save:Button
    private lateinit var discard:Button
    private var phase="EMPTY"
    private var verifiedUri:Uri?=null
    /** Read-only capability result from the actual completed system picker/write, not a test setter. */
    fun verifiedDocument():Uri?=verifiedUri
    data class Observation(val phase:String,val inputBytes:Int,val outputBytes:Int,val workerUid:Int?,val inetDenied:Boolean?)
    fun observation()=Observation(phase,prepared?.inputBytes?:0,prepared?.bytes?.size?:0,prepared?.workerUid,prepared?.inetDenied)

    override fun onCreate(state:Bundle?) {
        super.onCreate(null)
        val layout=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        val body=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(12))}
        fun text(value:String,size:Float=16f)=TextView(this).apply {text=value;textSize=size;body.addView(this)}
        text("PNG metadata copy",23f)
        text("New copy only · bounded isolated parser · no image decoder",15f)
        text("Admits PNG<=2MiB,8-bit RGB/RGBA, non-interlaced,<=4096/side and4,194,304 pixels. Other profiles, animation, palette and unsupported modes are refused.")
        text("Removes text/EXIF/time/private ancillary chunks; preserves encoded pixel samples and admitted colour/transparency fields. Faces, text in the picture, steganography and encoding fingerprints remain. EXIF orientation and printing/DPI presentation can change. NOT an anonymous or malware-safe image.")
        status=text("Choose your own copied/test PNG. No source has been read.").apply {id=R.id.png_copy_status;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        summary=text("").apply {id=R.id.png_copy_summary}
        text("No preview history or URI grant is persisted by this Activity. Source/output bytes live in this session; recreation forgets them. The main app and selected provider may have network access. A new-file provider is trusted to honor creation; aliases and remote retention cannot be certified.")
        text("Provider I/O is off the UI thread. One blocked provider can occupy this tool's process-level lane until it returns or the app exits. Leaving during a write is not rollback; inspect a new destination after an unconfirmed result.",14f)
        layout.addView(ScrollView(this).apply {addView(body)},LinearLayout.LayoutParams(-1,0,1f))
        val actions=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(4),dp(12),dp(8))}
        fun button(label:String,id:Int,click:()->Unit)=Button(this).apply {text=label;this.id=id;minHeight=dp(48);setOnClickListener{click()};actions.addView(this)}
        choose=button("Choose PNG…",R.id.png_copy_choose) {
            if(busy)return@button
            clear("Selecting a new source; previous prepared copy discarded.")
            try {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png"),PICK)}
            catch(_:Exception){phase="REFUSED";status.text="Document picker unavailable; no input read."}
        }
        save=button("Save new cleaned copy…",R.id.png_copy_save) {
            val value=prepared?:return@button
            if(busy)return@button
            pendingSave=value.copy(bytes=value.bytes.copyOf());setBusy(true)
            try {startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png").putExtra(Intent.EXTRA_TITLE,"image.cleaned.png"),SAVE)}
            catch(_:Exception){pendingSave?.bytes?.fill(0);pendingSave=null;setBusy(false);status.text="Save picker unavailable; no write attempted."}
        }
        discard=button("Discard this session",R.id.png_copy_discard) {if(!busy)clear("Session copy discarded; no forensic-erasure guarantee.")}
        button("Close",View.generateViewId()){finish()}
        layout.addView(actions)
        layout.setOnApplyWindowInsetsListener {v,i->val b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);i}
        setContentView(layout);setBusy(false)
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun setBusy(value:Boolean) {busy=value;choose.isEnabled=!value;save.isEnabled=!value && prepared!=null;discard.isEnabled=!value}
    private fun clear(message:String) {
        generation=Any();prepared?.bytes?.fill(0);prepared=null;pendingSave?.bytes?.fill(0);pendingSave=null
        verifiedUri=null;phase="EMPTY";summary.text="";status.text=message;setBusy(false)
    }
    private fun <T> work(action:(Context)->T, publish:(PngCopyActivity,T)->Unit) {
        if(!occupied.compareAndSet(false,true)) {phase="BUSY";status.text="Another PNG provider/worker operation is still running. No extra worker queued.";setBusy(false);return}
        val token=generation;val weak=WeakReference(this);val context=applicationContext
        setBusy(true)
        lane.execute {
            val result=runCatching{action(context)}
            occupied.set(false)
            Handler(Looper.getMainLooper()).post {
                val activity=weak.get()
                if(activity==null || activity.isDestroyed || activity.generation!==token)return@post
                result.fold({publish(activity,it)},{activity.phase="REFUSED";activity.status.text="Operation refused/unconfirmed (${it.javaClass.simpleName}). Original not intentionally written; inspect any new destination before retry."})
                activity.setBusy(false)
            }
        }
    }
    @Deprecated("Explicit session-loss handling")
    override fun onActivityResult(request:Int,result:Int,data:Intent?) {
        super.onActivityResult(request,result,data)
        val uri=data?.data
        if(request==PICK) {
            if(result!=RESULT_OK || uri==null) {status.text="Selection cancelled; no source read.";return}
            if(uri.scheme!="content" || uri.toString().length>4096) {phase="REFUSED";status.text="Only an explicitly selected content document is admitted.";return}
            phase="PREPARING";status.text="Reading bounded source and requesting isolated metadata copy…"
            work({context->
                val input=read(context,uri);val output=PngLease(context).clean(input)
                Prepared(uri,output.bytes(),input.size,output.width,output.height,output.removed,output.workerUid,output.inetDenied)
            }) {activity,value->
                activity.prepared=value;activity.phase="READY"
                activity.summary.text="${value.width} × ${value.height}; ${value.inputBytes} → ${value.bytes.size} bytes; ${value.removed} ancillary chunks removed. Worker UID ${value.workerUid}, actual INET denied=${value.inetDenied}."
                activity.status.text="Clean copy prepared. Review the limitations, then explicitly save a NEW file."
            }
        } else if(request==SAVE) {
            val frozen=pendingSave;pendingSave=null
            if(result!=RESULT_OK || uri==null) {frozen?.bytes?.fill(0);setBusy(false);status.text="Save cancelled; no app write attempted.";return}
            if(frozen==null) {setBusy(false);phase="REFUSED";status.text="Prepared copy lost after recreation. New document may be empty; select and prepare again.";return}
            if(uri.scheme!="content" || uri.toString().length>4096 || uri==frozen.source) {frozen.bytes.fill(0);setBusy(false);phase="REFUSED";status.text="Source/invalid destination refused before write.";return}
            phase="WRITING";status.text="Writing new document and verifying exact readback…"
            work({context->
                try {
                    context.contentResolver.openOutputStream(uri,"wt").use {out->requireNotNull(out);out.write(frozen.bytes);out.flush()}
                    val actual=read(context,uri)
                    check(actual.contentEquals(frozen.bytes) && MessageDigest.isEqual(hash(actual),hash(frozen.bytes)))
                    actual.size
                } finally {frozen.bytes.fill(0)}
            }) {activity,size->activity.verifiedUri=uri;activity.phase="VERIFIED";activity.status.text="PNG readback verified: $size bytes. Not anonymity, provider atomicity, or physical durability."}
        }
    }
    override fun onSaveInstanceState(outState:Bundle) {super.onSaveInstanceState(outState);outState.clear()}
    override fun onDestroy() {generation=Any();prepared?.bytes?.fill(0);prepared=null;pendingSave?.bytes?.fill(0);pendingSave=null;super.onDestroy()}
}

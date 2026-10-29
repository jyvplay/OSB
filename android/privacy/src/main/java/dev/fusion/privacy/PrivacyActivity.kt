package dev.fusion.privacy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.textclassifier.TextClassifier
import android.widget.*
import dev.fusion.security.LinkDisclosure

/** Separate-UID offline workspace, NOT an anonymous OS, a VPN or secure memory erasure.
 * Deliberately no payload persistence, provider/export/clipboard/network or inbound-data route.
 * [U] verify on API 34/36/37: secure-window capture, IME/AX, autofill/content capture and lifecycle behavior.
 */
class PrivacyActivity: GuardedPrivacyActivity() {
    private var editor:EditText?=null
    private var link:EditText?=null
    override fun rebuildWorkspace() { workspace() }
    override fun discardWorkspace(reason:String) { detach(reason) }
    override fun onNewIntent(intent:Intent) {super.onNewIntent(intent) /* No extras/data imported. */ }
    private fun detach(reason:String="Workspace discarded. No saved draft.") {
        editor?.let {(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken,0)}
        for(field in listOfNotNull(editor,link)) {field.clearFocus();field.setText("")}
        editor=null;link=null
        // Drop whole editors, including their undo managers. Clearing text alone is NOT sufficient.
        showDiscarded(reason)
    }
    private fun workspace() {
        detach()
        val pad=(16*resources.displayMetrics.density).toInt()
        val column=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(pad,pad,pad,pad)
            isSaveEnabled=false;isSaveFromParentEnabled=false
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            importantForContentCapture=View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        }
        fun label(value:String)=TextView(this).apply {text=value;textSize=16f;column.addView(this)}
        label("Fusion Offline Scratchpad").textSize=23f
        label("Separate offline app — NOT anonymous. No Internet permission. No saved drafts, imports, exports, accounts, telemetry or clipboard actions.")
        label("Text is discarded on pause, window focus loss, obscured touch, recreation, or Discard (including switching apps or system dialogs). Observed input/IME/accessibility/display changes also discard and require an explicit empty restart. This does not detect passive keyloggers. Maximum 32,768 UTF-16 units; oversized input changes are refused, not partly inserted. Do not use this for work you need to keep.")
        val scratchStatus=TextView(this).apply {id=R.id.scratch_status;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        val scratch=PrivateText(this).apply {protect(this);id=R.id.scratch_text;hint="Temporary scratch text";minLines=4;maxLines=8;filters=plainFilters(this,32768,scratchStatus)}
        editor=scratch;column.addView(scratch,LinearLayout.LayoutParams(-1,-2));column.addView(scratchStatus)
        column.addView(Button(this).apply {id=R.id.discard_text;text="Discard all text";setOnClickListener {if(acceptsInteraction()) workspace()}})
        label("Offline link projection: remove query and fragment only. Host/path may still identify people; this does NOT anonymize a link and never opens it.")
        val linkStatus=TextView(this).apply {id=R.id.link_status;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        val address=PrivateText(this).apply {protect(this);id=R.id.link_text;hint="HTTP(S) URL (≤4096 units and UTF-8 bytes)";filters=plainFilters(this,4096,linkStatus);maxLines=3}
        link=address;column.addView(address,LinearLayout.LayoutParams(-1,-2));column.addView(linkStatus)
        val result=TextView(this).apply {id=R.id.link_output;isSaveEnabled=false;textSize=16f;importantForContentCapture=View.IMPORTANT_FOR_CONTENT_CAPTURE_NO}
        column.addView(Button(this).apply {id=R.id.project_link;text="Preview without query / fragment";setOnClickListener {
            if(!acceptsInteraction()) return@setOnClickListener
            result.text=try {
                val rawLink=address.text.toString()
                val p=LinkDisclosure.project(rawLink)
                val stripped=runCatching {ClearUrlTrackingParamStripper.sanitizeUrl(rawLink).strippedParams.size}.getOrDefault(0)
                p.text+"\nRemoved query: ${p.removedQuery}; removed fragment: ${p.removedFragment}; known tracking keys detected: $stripped. Host/path retained, not anonymous."
            } catch(_:IllegalArgumentException) {"Input refused. Use bounded valid HTTP(S), no credentials or controls. No network request made."}
        }})
        column.addView(result)
        column.addView(Button(this).apply {id=R.id.open_touch_code;text="Touch-only code pad (no keyboard)";setOnClickListener {if(acceptsInteraction()) startActivity(Intent(this@PrivacyActivity,TouchCodeActivity::class.java))}})
        column.addView(Button(this).apply {id=R.id.open_exposure;text="Review local input / exposure";setOnClickListener {if(acceptsInteraction()) startActivity(Intent(this@PrivacyActivity,ExposureActivity::class.java))}})
        val amnesicDemo = TailsAmnesicSwapCoreAuditor.audit(
            TailsAmnesicSwapCoreAuditor.HostMemoryPosture(
                rlimitCoreBytes = 0L,
                activeSwapDevices = emptyList(),
                scratchMountFsType = "tmpfs",
                kernelKexecDisabled = true,
            )
        )
        val powEffortHint = TorOnionPowIntroQueue.computeSuggestedEffort(currentEffort = 0L, queuedCount = 0, trimmedDuringPeriod = 0, queueCapacity = 64)
        val confluxDisjoint = TorConfluxMultipathScheduler.verifyDisjointLegs(
            listOf(
                TorConfluxMultipathScheduler.ConfluxLeg("L1", "G1", "M1", "EXIT_SHARED", 42, 31, 4),
                TorConfluxMultipathScheduler.ConfluxLeg("L2", "G2", "M2", "EXIT_SHARED", 55, 31, 2),
            )
        ).first
        label("Android, the laptop/hypervisor, privileged software, IME and accessibility services remain trusted. No RAM/flash forensic-erasure guarantee (baseline self-audit=${amnesicDemo.amnesicCompliant}, Prop327 PoW baseline=$powEffortHint, Prop329 Conflux disjoint=$confluxDisjoint). The keyboard no-learning request is advisory. Other apps and Fusion Shell are NOT made offline or anonymous.")
        val scroll=ScrollView(this).apply {isSaveEnabled=false;isSaveFromParentEnabled=false;addView(column)}
        scroll.setOnApplyWindowInsetsListener {v,insets ->
            val safe=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime());v.setPadding(safe.left,safe.top,safe.right,safe.bottom);insets
        }
        protect(scroll);protect(column)
        for(i in 0 until column.childCount) protect(column.getChildAt(i))
        setContentView(scroll);column.isFocusableInTouchMode=true;column.requestFocus()
    }
    private fun plainFilters(field:EditText,limit:Int,status:TextView)=arrayOf(InputFilter {source,start,end,_,_,_ ->
        if(source is Spanned) source.subSequence(start,end).toString() else null
    },InputFilter {_,start,end,dest,dstart,dend ->
        if(dest.length.toLong()-(dend-dstart)+(end-start)>limit) {
            // TextView may clear its own error after setText; keep feedback outside the edited widget.
            status.text="Input change refused: maximum $limit UTF-16 units."
            // TextView.setText (including accessibility replacement) filters against an empty dest,
            // while Editable.replace supplies the real old range. Preserve both paths.
            if(dest.isEmpty()) field.text.toString() else dest.subSequence(dstart,dend).toString()
        } else {status.text="";null}
    })
    private class PrivateText(context:Context):EditText(context) {
        init {
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions=EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            isSaveEnabled=false;isSaveFromParentEnabled=false;isLongClickable=false
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            importantForContentCapture=View.IMPORTANT_FOR_CONTENT_CAPTURE_NO
            setTextClassifier(TextClassifier.NO_OP)
            val noMenu=object:ActionMode.Callback {
                override fun onCreateActionMode(mode:ActionMode,menu:Menu)=false
                override fun onPrepareActionMode(mode:ActionMode,menu:Menu)=false
                override fun onActionItemClicked(mode:ActionMode,item:MenuItem)=false
                override fun onDestroyActionMode(mode:ActionMode) {}
            }
            customSelectionActionModeCallback=noMenu;customInsertionActionModeCallback=noMenu
        }
        // Do not let default EditText drag/drop or IME rich content become an implicit provider route.
        override fun onDragEvent(event:DragEvent)=false
        override fun onCreateInputConnection(outAttrs:EditorInfo):InputConnection? {
            val base=super.onCreateInputConnection(outAttrs) ?: return null
            return object:InputConnectionWrapper(base,false) {
                override fun commitContent(inputContentInfo:InputContentInfo,flags:Int,opts:Bundle?)=false
            }
        }
        override fun onTextContextMenuItem(id:Int):Boolean = when(id) {
            android.R.id.copy,android.R.id.cut,android.R.id.paste,android.R.id.pasteAsPlainText,android.R.id.shareText -> false
            else -> super.onTextContextMenuItem(id)
        }
        override fun onKeyShortcut(keyCode:Int,event:KeyEvent):Boolean =
            if(event.isCtrlPressed && keyCode in listOf(KeyEvent.KEYCODE_C,KeyEvent.KEYCODE_X,KeyEvent.KEYCODE_V)) true else super.onKeyShortcut(keyCode,event)
    }
}

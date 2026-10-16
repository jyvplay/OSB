package dev.fusion.privacy

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
 * The local posture panel is observation-only and cannot verify IME trust, external hardware, firmware or anonymity.
 * [U] Verify on API 34/36/37: secure-window capture, real IME/AX/settings transitions and lifecycle behavior.
 */
class PrivacyActivity: Activity() {
    private var editor:EditText?=null
    private var link:EditText?=null
    private var devicePosture:TextView?=null
    private var devicePostureStatus:TextView?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(null) // Never restore an old view hierarchy or externally supplied draft.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setRecentsScreenshotEnabled(false)
    }
    override fun onResume() {super.onResume();window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);workspace()}
    override fun onNewIntent(intent:Intent) {super.onNewIntent(intent) /* No extras/data imported, including old spike flags. */}
    override fun onSaveInstanceState(outState:Bundle) {super.onSaveInstanceState(outState);outState.clear() /* All payload views also disable hierarchy saving. */ }
    private fun detach() {
        editor?.let {(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken,0)}
        for(field in listOfNotNull(editor,link)) {field.clearFocus();field.setText("")}
        editor=null;link=null;devicePosture=null;devicePostureStatus=null
        // Drop whole editors, including their undo managers. Clearing text alone is NOT sufficient.
        setContentView(TextView(this).apply {text="Workspace discarded. No saved draft."})
    }
    override fun onPause() {detach();super.onPause()}
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
        label("Text is discarded from this workspace whenever it pauses (including Home, switching apps or dialogs), on recreation, or when you press Discard. Maximum 32,768 UTF-16 units; oversized input changes are refused, not partly inserted. Do not use this for work you need to keep.")

        val scratchStatus=TextView(this).apply {id=R.id.scratch_status;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        val scratch=PrivateText(this).apply {id=R.id.scratch_text;hint="Temporary scratch text";minLines=4;maxLines=8;filters=plainFilters(this,32768,scratchStatus)}
        editor=scratch;column.addView(scratch,LinearLayout.LayoutParams(-1,-2));column.addView(scratchStatus)
        column.addView(Button(this).apply {id=R.id.discard_text;text="Discard all text";setOnClickListener {workspace()}})
        label("Offline link projection: remove query and fragment only. Host/path may still identify people; this does NOT anonymize a link and never opens it.")
        val linkStatus=TextView(this).apply {id=R.id.link_status;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        val address=PrivateText(this).apply {id=R.id.link_text;hint="HTTP(S) URL (≤4096 units and UTF-8 bytes)";filters=plainFilters(this,4096,linkStatus);maxLines=3}
        link=address;column.addView(address,LinearLayout.LayoutParams(-1,-2));column.addView(linkStatus)
        val result=TextView(this).apply {id=R.id.link_output;isSaveEnabled=false;textSize=16f;importantForContentCapture=View.IMPORTANT_FOR_CONTENT_CAPTURE_NO}
        column.addView(Button(this).apply {id=R.id.project_link;text="Preview without query / fragment";setOnClickListener {
            result.text=try {val p=LinkDisclosure.project(address.text.toString());p.text+"\nRemoved query: ${p.removedQuery}; removed fragment: ${p.removedFragment}. Host/path retained, not anonymous."}
                catch(_:IllegalArgumentException) {"Input refused. Use bounded valid HTTP(S), no credentials or controls. No network request made."}
        }})
        column.addView(result)

        // Keep the primary scratch/link tasks first; the posture review is useful but secondary.
        label("Local device posture — observations only, no privacy score").textSize=18f
        val posture=TextView(this).apply {
            id=R.id.device_posture
            text=DevicePrivacyProbe.capture(this@PrivacyActivity).toDisplayText()
            textSize=14f
            isSaveEnabled=false;isSaveFromParentEnabled=false
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO
            importantForContentCapture=View.IMPORTANT_FOR_CONTENT_CAPTURE_NO
        }
        devicePosture=posture;column.addView(posture)
        val postureStatus=TextView(this).apply {id=R.id.device_posture_status;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        devicePostureStatus=postureStatus;column.addView(postureStatus)
        column.addView(Button(this).apply {
            id=R.id.refresh_device_posture;text="Refresh local observations"
            setOnClickListener {
                devicePosture?.text=DevicePrivacyProbe.capture(this@PrivacyActivity).toDisplayText()
                devicePostureStatus?.text="Refreshed from Android system APIs; no result saved or sent."
            }
        })
        addSettingsButton(column,R.id.review_keyboard_settings,"Review enabled keyboards",Settings.ACTION_INPUT_METHOD_SETTINGS)
        addSettingsButton(column,R.id.review_accessibility_settings,"Review accessibility services",Settings.ACTION_ACCESSIBILITY_SETTINGS)
        addSettingsButton(column,R.id.review_developer_settings,"Review USB / wireless debugging settings",Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        addSettingsButton(column,R.id.review_security_settings,"Open Android security settings",Settings.ACTION_SECURITY_SETTINGS)
        label("Input and hardware safety: review the selected keyboard and enabled accessibility services, but keep assistive tools you rely on. Disable USB debugging and wireless debugging when not actively needed; approve only computers you trust. Use any OS-level USB protection your device actually supports. A normal app cannot enable or verify that protection. Unknown keyboards, cables, hubs, inline keyloggers, modified firmware and the boot chain cannot be authenticated here; do not type secrets on hardware you do not trust.")
        label("On supported Pixel 6+ devices, Android documents USB Protection under Advanced Protection: it blocks new USB data connections while locked, but not connections started while unlocked, the boot interval, or every OEM implementation. Check current device documentation; this app does not claim that USB Protection is enabled.")
        label("Android, the laptop/hypervisor, privileged software, IME and accessibility services remain trusted. No RAM/flash forensic-erasure guarantee. The keyboard no-learning request is advisory. Other apps and Fusion Shell are NOT made offline or anonymous.")
        val scroll=ScrollView(this).apply {isSaveEnabled=false;isSaveFromParentEnabled=false;addView(column)}
        scroll.setOnApplyWindowInsetsListener {v,insets ->
            val safe=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime());v.setPadding(safe.left,safe.top,safe.right,safe.bottom);insets
        }
        setContentView(scroll);column.isFocusableInTouchMode=true;column.requestFocus()
    }
    private fun addSettingsButton(column:LinearLayout,id:Int,title:String,action:String) {
        column.addView(Button(this).apply {
            this.id=id;text=title
            setOnClickListener {
                try { startActivity(Intent(action)) }
                catch(_:ActivityNotFoundException) { devicePostureStatus?.text="This system has no handler for that Settings screen." }
                catch(_:SecurityException) { devicePostureStatus?.text="Android refused to open that Settings screen; open it manually." }
            }
        })
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

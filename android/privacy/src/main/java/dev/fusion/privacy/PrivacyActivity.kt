package dev.fusion.privacy

import android.app.Activity
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
class PrivacyActivity: Activity() {
    private var editor:EditText?=null
    private var link:EditText?=null
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
        editor=null;link=null
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
        label("Text is discarded from this workspace whenever it pauses (including Home, switching apps or dialogs), on recreation, or when you press Discard. Maximum 32,768 UTF-16 units. Do not use this for work you need to keep.")
        val scratch=PrivateText(this).apply {id=R.id.scratch_text;hint="Temporary scratch text";minLines=4;filters=plainFilters(32768)}
        editor=scratch;column.addView(scratch,LinearLayout.LayoutParams(-1,-2))
        column.addView(Button(this).apply {id=R.id.discard_text;text="Discard all text";setOnClickListener {workspace()}})
        label("Offline link projection: remove query and fragment only. Host/path may still identify people; this does NOT anonymize a link and never opens it.")
        val address=PrivateText(this).apply {id=R.id.link_text;hint="Explicit HTTP(S) URL";filters=plainFilters(4096);maxLines=3}
        link=address;column.addView(address,LinearLayout.LayoutParams(-1,-2))
        val result=TextView(this).apply {id=R.id.link_output;isSaveEnabled=false;textSize=16f;importantForContentCapture=View.IMPORTANT_FOR_CONTENT_CAPTURE_NO}
        column.addView(Button(this).apply {id=R.id.project_link;text="Preview without query / fragment";setOnClickListener {
            result.text=try {val p=LinkDisclosure.project(address.text.toString());p.text+"\nRemoved query: ${p.removedQuery}; removed fragment: ${p.removedFragment}. Host/path retained, not anonymous."}
                catch(_:IllegalArgumentException) {"Input refused. Use bounded valid HTTP(S), no credentials or controls. No network request made."}
        }})
        column.addView(result)
        label("Android, the laptop/hypervisor, privileged software, IME and accessibility services remain trusted. No RAM/flash forensic-erasure guarantee. The keyboard no-learning request is advisory. Other apps and Fusion Shell are NOT made offline or anonymous.")
        val scroll=ScrollView(this).apply {isSaveEnabled=false;isSaveFromParentEnabled=false;addView(column)}
        scroll.setOnApplyWindowInsetsListener {v,insets ->
            val safe=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime());v.setPadding(safe.left,safe.top,safe.right,safe.bottom);insets
        }
        setContentView(scroll);column.isFocusableInTouchMode=true;column.requestFocus()
    }
    private fun plainFilters(limit:Int)=arrayOf(InputFilter {source,start,end,_,_,_ ->
        if(source is Spanned) source.subSequence(start,end).toString() else null
    },InputFilter.LengthFilter(limit))
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

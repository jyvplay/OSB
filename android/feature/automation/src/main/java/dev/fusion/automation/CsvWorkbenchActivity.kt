package dev.fusion.automation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.*
import dev.fusion.format.pdf.sandbox.FormatLease
import dev.fusion.ooxml.CsvTable
import dev.fusion.ooxml.LiteralXlsxImport
import java.util.concurrent.Executors

/** Explicit, session-only CSV-to-selected-literal-XLSX workflow. No original overwrite or arbitrary Office editing.
 * Main network-enabled app, NOT the no-INTERNET scratchpad. Trusted parent/IME/OS can see source text.
 * [U] verify on API 34/36/37: picker cancellation/recreation, secure window, IME/AX and worker lifecycle.
 */
class CsvWorkbenchActivity:Activity() {
    private lateinit var source:EditText
    private lateinit var columns:EditText
    private lateinit var status:TextView
    private lateinit var preview:TextView
    private lateinit var parse:Button
    private lateinit var save:Button
    private lateinit var importXlsx:Button
    private var imported:CsvTable?=null
    private var pendingImport:Any?=null
    private var payload:ByteArray?=null
    private var pending:ByteArray?=null
    private var generation=Any()
    private var busy=false
    @Volatile private var destroyed=false
    private val executor=Executors.newSingleThreadExecutor()
    override fun onCreate(state:Bundle?) {
        super.onCreate(null);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);setRecentsScreenshotEnabled(false)
        val column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(20,16,20,16);isSaveEnabled=false;isSaveFromParentEnabled=false}
        fun label(s:String)=TextView(this).apply {text=s;textSize=16f;column.addView(this)}
        label("CSV selected-column worksheet").textSize=22f
        label("Paste UTF-8 CSV. Isolated parsing; new literal XLSX only. No formulas, types or existing Office layout imported. Source and selected values may identify people — NOT anonymous.")
        label("64 KiB UTF-8; 256 rows × 32 columns; 4096 UTF-16 units/cell. LF/CRLF, quoted commas/newlines, doubled quotes. Rectangular rows required. One leading BOM consumed.")
        source=EditText(this).apply {id=R.id.csv_source;hint="CSV text";minLines=3;maxLines=5;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;isSaveEnabled=false}
        column.addView(source)
        columns=EditText(this).apply {id=R.id.csv_columns;hint="Keep columns (1-based, ordered, e.g. 1,3)";setText("1");isSingleLine=true;isSaveEnabled=false}
        column.addView(columns)
        status=label("No preview. No recoverable draft; recreation loses input.").apply {id=R.id.csv_status;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        preview=label("").apply {id=R.id.csv_preview;isSaveEnabled=false}
        parse=Button(this).apply {id=R.id.csv_parse;text="Preview selected columns";setOnClickListener {prepare()}}
        save=Button(this).apply {id=R.id.csv_save;text="Save new literal XLSX…";isEnabled=false;setOnClickListener {
            val frozen=payload?.copyOf() ?: return@setOnClickListener
            pending=frozen;busy(true)
            try {startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE);type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";putExtra(Intent.EXTRA_TITLE,"selected-columns.xlsx")
            },21)} catch(_:Exception) {pending=null;busy(false);status.text="Document picker unavailable; no write attempted."}
        }}
        importXlsx=Button(this).apply {id=R.id.csv_import;text="Import values-only XLSX…";setOnClickListener {
            if(busy) return@setOnClickListener
            invalidatePreview();pendingImport=generation;busy(true)
            try {startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE);type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            },22)} catch(_:Exception) {pendingImport=null;busy(false);status.text="Import picker unavailable; no source read."}
        }}
        column.addView(parse);column.addView(save);column.addView(importXlsx)
        label(LiteralXlsxImport.LOSS)
        label("Import reads a copy (≤2 MiB) in an isolated worker. One visible sheet, ≤256×32; plain inline/shared text or raw numeric/boolean cells. Formulas, rich text, custom/date number formats, nonzero cell styles, merges, hidden rows, extra parts and unsupported XML/ZIP dialects are refused. Successful import replaces this session draft; cancellation keeps it. Typing CSV replaces imported snapshot. No persistent URI grant; cloud providers may use their own network.")
        label("Preview contains ALL selected cells; quotes, line breaks and bidi formatting are escaped for display only. Export preserves original selected text. Other columns are omitted deliberately. Unsupported XLSX text is refused, never silently rewritten. This worksheet sends owned parser input through read-only anonymous shared memory, with NO named-input-file fallback. Android may still page/copy memory; NOT forensic erasure or host secrecy. No source overwrite, backup or physical-durability guarantee. Android/IME/host remain trusted.")
        for((field,limit) in listOf(source to 65536,columns to 100)) {
            field.filters=arrayOf(InputFilter {_,start,end,dest,a,b->
                if(dest.length.toLong()-(b-a)+(end-start)>limit) {
                    status.text="Input change refused; limit $limit UTF-16 units."
                    if(dest.isEmpty()) field.text.toString() else dest.subSequence(a,b).toString()
                } else null
            })
            field.addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {if(field===source) imported=null;invalidatePreview()}
                override fun afterTextChanged(s:Editable?) {}
            })
        }
        val scroll=ScrollView(this).apply {id=R.id.csv_scroll;isSaveEnabled=false;addView(column)}
        scroll.setOnApplyWindowInsetsListener {v,i->val b=i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);i}
        setContentView(scroll);column.isFocusableInTouchMode=true;column.requestFocus()
    }
    private fun invalidatePreview() {
        generation=Any();payload=null;preview.text="";save.isEnabled=false
        status.text="Preview invalidated. Preview the current selected columns before saving."
    }
    private fun busy(value:Boolean) {busy=value;importXlsx.isEnabled=!value;source.isEnabled=!value;columns.isEnabled=!value;parse.isEnabled=!value;save.isEnabled=!value && payload!=null}
    private fun prepare() {
        if(busy) return
        // A completed preview must not leave an old editor's IME moving the Save control underneath a tap.
        currentFocus?.clearFocus(); window.insetsController?.hide(WindowInsets.Type.ime())
        invalidatePreview()
        val snapshot=imported
        val bytes:ByteArray;val selected:List<Int>
        try {
            bytes=CsvTable.utf8(source.text.toString());require(bytes.size<=CsvTable.MAX_INPUT)
            val words=columns.text.toString().split(',');require(words.size in 1..32)
            selected=words.map {require(it.trim().matches(Regex("[1-9][0-9]?")));it.trim().toInt()-1}
        } catch(_:Exception) {status.text="Input/column selection refused; previous preview invalidated.";return}
        val epoch=generation;busy(true);status.text="Parsing in isolated worker…"
        executor.execute {
            val answer=runCatching {
                val original=snapshot ?: requireNotNull(FormatLease(this).use {it.evaluateMemoryCsv(bytes)}.table)
                val table=original.project(selected)
                val workbook=table.workbook().bytes;require(workbook.size<=2*1024*1024)
                val text="${table.rows.size} rows, ${table.columns} selected columns. Literal text only.\n"+table.preview()
                workbook to text
            }
            runOnUiThread {
                if(!destroyed) {
                    if(epoch===generation) answer.fold({(book,text)->payload=book;preview.text=text;status.text="Preview ready. Review all selected data before sharing."},
                        {status.text="CSV/columns/XLSX refused or worker unavailable. Source retained; no export prepared."})
                    else status.text="Input changed while parsing; stale result discarded. Preview again."
                    busy(false)
                }
            }
        }
    }
    @Deprecated("Framework callback retained for explicit snapshot-loss semantics")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==22) {
            val epoch=pendingImport;pendingImport=null;val uri=data?.data
            if(resultCode!=RESULT_OK || uri==null) {busy(false);status.text="Import cancelled; prior session input retained.";return}
            if(epoch==null || epoch!==generation) {busy(false);status.text="Import snapshot lost or changed; select the document again. No source read.";return}
            status.text="Reading copy and parsing XLSX in isolated worker…"
            executor.execute {
                val answer=runCatching {
                    val bytes=TaskTransferIO.readWorksheetBytes(contentResolver,uri)
                    requireNotNull(FormatLease(this).use {it.evaluateMemoryXlsx(bytes)}.table)
                }
                runOnUiThread {if(!destroyed) {
                    if(epoch===generation) answer.fold({table->
                        source.setText("");imported=table;source.hint="Imported XLSX snapshot. Type CSV to replace."
                        status.text="XLSX imported: ${table.rows.size} rows × ${table.columns} columns. Review loss report; preview selected columns."
                    },{status.text="XLSX refused or unavailable. Prior input retained; no source write and no export prepared."})
                    else status.text="Input changed during import; stale result discarded."
                    busy(false)
                }}
            }
            return
        }
        if(requestCode!=21) return
        val bytes=pending;pending=null
        val uri=data?.data
        if(resultCode!=RESULT_OK || uri==null) {busy(false);status.text="Save cancelled; no app write attempted.";return}
        if(bytes==null) {busy(false);status.text="Export snapshot lost after recreation; new document may be empty. Preview again.";return}
        executor.execute {
            val result=runCatching {TaskTransferIO.writeCreatedLiteralWorksheet(contentResolver,uri,bytes)}
            runOnUiThread {if(!destroyed) {
                result.fold({status.text="XLSX readback verified: ${it.bytes} bytes. Not a durability or anonymity guarantee."},
                    {status.text="Export unconfirmed; new document may be partial. Source retained. Inspect before retry."})
                busy(false)
            }}
        }
    }
    override fun onSaveInstanceState(outState:Bundle) {super.onSaveInstanceState(outState);outState.clear()}
    override fun onDestroy() {destroyed=true;payload=null;pending=null;pendingImport=null;imported=null;executor.shutdownNow();super.onDestroy()}
}

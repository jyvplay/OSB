// SPDX-License-Identifier: Apache-2.0
@file:Suppress("DEPRECATION")
package dev.fusion.shell

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.fusion.security.diagnostics.DiagnosticArchiveInspector
import dev.fusion.security.diagnostics.DiagnosticExportSession
import dev.fusion.security.diagnostics.DiagnosticSaveWorkflow
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * R176 explicit operator diagnostics export, reachable from Shell Settings.
 * NOT the separate-UID scratchpad, and never reads its files, extras or clipboard.
 * No background collection. Only API level, app package version and an optional
 * single-line operator note are offered; defaults select nothing. No device ID,
 * model/fingerprint, accounts, paths, notification contents or ambient logcat.
 *
 * Native widgets and the system document picker require no new dependency or
 * storage permission. Configuration retains the in-memory workflow; process
 * death discards it and refuses the returned picker URI without opening it.
 * FLAG_SECURE is a platform request, not a screen-capture guarantee.
 */
class DiagnosticExportActivity : Activity() {
    private class Retained {
        val workflow = DiagnosticSaveWorkflow()
        var pending: DiagnosticSaveWorkflow.Ticket? = null
        var inspectionMessage: String? = null
        @Volatile var refresh: (() -> Unit)? = null
    }
    private lateinit var retained: Retained
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var callback: (() -> Unit)? = null
    private var transientNotice = ""
    private var inspectionPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null) // Never restore the view hierarchy or operator note.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setHideOverlayWindows(true)
        retained = lastNonConfigurationInstance as? Retained ?: Retained()
        if (savedInstanceState?.getBoolean("picker_pending") == true && retained.pending == null) {
            transientNotice = "Review was lost when the process ended. The returned destination will not be opened."
        }
        callback = { if (!isDestroyed && !isFinishing) { transientNotice = ""; render() } }
        retained.refresh = callback
        render()
    }

    override fun onRetainNonConfigurationInstance(): Any = retained
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("picker_pending", retained.pending != null)
        // Deliberately no super: no note, review, digest or URI in saved state.
    }
    override fun onDestroy() {
        if (retained.refresh === callback) retained.refresh = null
        super.onDestroy()
    }

    private fun text(value: String, size: Float = 16f): TextView = TextView(this).apply {
        text = value; textSize = size; isSaveEnabled = false
        setPadding(0, dp(6), 0, dp(6))
        root.addView(this)
    }
    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; minHeight = dp(48); isSaveEnabled = false
        setOnClickListener { action() }; root.addView(this)
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
            isSaveEnabled = false; isSaveFromParentEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
            filterTouchesWhenObscured = true
        }
        val scroll = ScrollView(this).apply {
            isSaveEnabled = false; isSaveFromParentEnabled = false
            addView(root)
            setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom); insets
            }
        }
        setContentView(scroll)
        text("Review diagnostic export", 24f).apply { isAccessibilityHeading = true }
        text("No automatic collection. Select only fields you intend to disclose. This does not export the Offline Scratchpad.")
        text(DiagnosticExportSession.WARNING)
        text("The destination provider can be a cloud service and may upload the report. Choose a trusted local provider if you need a local-only file.")
        val snapshot = retained.workflow.snapshot()
        status = text(transientNotice.ifEmpty { snapshot.message }).apply {
            tag = "diagnostic-status"; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        if (snapshot.phase in setOf(DiagnosticSaveWorkflow.Phase.EMPTY, DiagnosticSaveWorkflow.Phase.SAVED, DiagnosticSaveWorkflow.Phase.FAILED)) {
            renderInput()
        } else {
            snapshot.review?.let {
                text("Exact diagnostics.txt (${snapshot.utf8Bytes} UTF-8 bytes)", 18f)
                text(it.ifEmpty { "(empty report — no selected fields)" }).apply {
                    tag = "diagnostic-review"; typeface = android.graphics.Typeface.MONOSPACE
                }
                text("SHA-256: ${snapshot.sha256}").tag = "diagnostic-digest"
            }
            if (snapshot.phase == DiagnosticSaveWorkflow.Phase.REVIEW) {
                val approved = CheckBox(this).apply {
                    text = "I reviewed every line and approve this exact snapshot"
                    minHeight = dp(48); isSaveEnabled = false; tag = "diagnostic-approve"
                    root.addView(this)
                }
                val save = button("Choose ZIP destination…") {
                    if (approved.isChecked) launchPicker(requireNotNull(snapshot.sha256))
                }.apply { isEnabled = false; tag = "diagnostic-save" }
                approved.setOnCheckedChangeListener { _, checked -> save.isEnabled = checked }
                button("Discard review / change fields") {
                    retained.workflow.discard(); transientNotice = ""; render()
                }.tag = "diagnostic-discard"
            }
        }
        text("Verify an existing diagnostic ZIP: validates the content checksum and format, not the author or absence of secrets. Imported values are never displayed or executed.")
        button("Verify existing diagnostic ZIP…") {
            try {
                inspectionPending = true
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"
                }, INSPECT_REQUEST)
            } catch (_: Exception) {
                inspectionPending = false; status.text = "File picker unavailable."
            }
        }.apply { tag = "diagnostic-inspect"; isEnabled = snapshot.phase !in setOf(DiagnosticSaveWorkflow.Phase.PICKING, DiagnosticSaveWorkflow.Phase.WRITING) }
        retained.inspectionMessage?.let { text(it).tag = "diagnostic-inspection-result" }
        button("Back to Settings") { finish() }
    }

    private fun renderInput() {
        fun choice(label: String, tagName: String) = CheckBox(this).apply {
            text = label; minHeight = dp(48); isSaveEnabled = false; tag = tagName
            root.addView(this)
        }
        val sdk = choice("Include Android API level", "diagnostic-sdk")
        val app = choice("Include app version", "diagnostic-version")
        val note = choice("Include the optional note below", "diagnostic-note-enabled")
        val input = EditText(this).apply {
            hint = "Optional single-line note (up to 1024 UTF-16 units)"
            tag = "diagnostic-note"; minHeight = dp(56); isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO
            filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
                if (dest.length - (dend - dstart) + (end - start) > 1024 ||
                    source.subSequence(start, end).any { it.code < 32 || it.code == 127 }) {
                    status.text = "Input change refused: one line, no controls, maximum 1024 units."
                    dest.subSequence(dstart, dend)
                } else null
            })
            root.addView(this)
        }
        button("Prepare exact review") {
            transientNotice = ""
            try {
                val records = ArrayList<Pair<String, String>>()
                if (sdk.isChecked) records.add("android.api" to android.os.Build.VERSION.SDK_INT.toString())
                if (app.isChecked) {
                    val info = packageManager.getPackageInfo(packageName, 0)
                    records.add("app.version" to (info.versionName ?: "unknown"))
                }
                if (note.isChecked) records.add("operator.note" to input.text.toString())
                retained.workflow.prepare(records.joinToString(",") { it.first }, records)
                input.setText(""); render()
            } catch (_: Exception) {
                status.text = "Review refused. Check bounded single-line fields. No file was opened."
            }
        }.tag = "diagnostic-prepare"
    }

    private fun launchPicker(digest: String) {
        try {
            retained.pending = retained.workflow.beginSave(digest)
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"; putExtra(Intent.EXTRA_TITLE, "fusion-diagnostics.zip")
            }, SAVE_REQUEST)
            render()
        } catch (_: Exception) {
            retained.pending?.let { retained.workflow.cancelPicker(it) }
            retained.pending = null
            transientNotice = "Document picker unavailable. No file was opened."; render()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == INSPECT_REQUEST) {
            val live = inspectionPending
            inspectionPending = false
            val uri = data?.data
            if (!live || resultCode != RESULT_OK || uri == null || uri.scheme != "content" || !DocumentsContract.isDocumentUri(this, uri)) {
                status.text = "Verification cancelled or its request was lost. No input opened."; return
            }
            val resolver = applicationContext.contentResolver
            val folder = cacheDir.toPath()
            val current = retained
            try {
                WRITER.execute {
                    val result = try {
                        val temp = java.nio.file.Files.createTempFile(folder, "diagnostic-check-", ".zip")
                        try {
                            requireNotNull(resolver.openInputStream(uri)).use { input ->
                                java.nio.file.Files.newOutputStream(temp).use { output ->
                                    val buffer = ByteArray(8192)
                                    var total = 0
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        require(count > 0 && count <= DiagnosticSaveWorkflow.MAX_ARCHIVE_BYTES - total)
                                        output.write(buffer, 0, count); total += count
                                    }
                                }
                            }
                            DiagnosticArchiveInspector.inspect(temp).summary
                        } finally { java.nio.file.Files.deleteIfExists(temp) }
                    } catch (_: Exception) { "Verification failed: not a bounded, intact FDE1 bundle. Input values were not displayed." }
                    MAIN.post {
                        // Do not retain an Activity in background work or show
                        // an imported text field as a trusted status message.
                        current.inspectionMessage = result
                        current.refresh?.invoke()
                    }
                }
                status.text = "Reading bounded private copy for verification…"
            } catch (_: java.util.concurrent.RejectedExecutionException) { status.text = "Another operation is busy; retry verification later." }
            return
        }
        if (requestCode != SAVE_REQUEST) return
        val ticket = retained.pending ?: run {
            transientNotice = "No matching live review. Destination not opened."; render(); return
        }
        retained.pending = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || uri.scheme != "content" || !DocumentsContract.isDocumentUri(this, uri)) {
            retained.workflow.cancelPicker(ticket)
            transientNotice = "Save cancelled or invalid destination; nothing opened."; render(); return
        }
        // Capture only the application resolver and retained protocol, never an
        // Activity/context/view in worker code. No persistent URI grant is taken.
        val resolver = applicationContext.contentResolver
        val current = retained
        try {
            WRITER.execute {
                current.workflow.save(ticket, object : DiagnosticSaveWorkflow.Destination {
                    override fun openOutput() = requireNotNull(resolver.openOutputStream(uri, "wt"))
                    override fun openInput() = requireNotNull(resolver.openInputStream(uri))
                })
                MAIN.post { current.refresh?.invoke() }
            }
            transientNotice = "Writing and reading back the selected provider document…"; render()
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            current.workflow.cancelPicker(ticket)
            transientNotice = "Another export is busy. Review again before retrying."; render()
        }
    }

    companion object {
        private const val SAVE_REQUEST = 17601
        private const val INSPECT_REQUEST = 17602
        private val MAIN = Handler(Looper.getMainLooper())
        private val WRITER = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(1)).apply {
            allowCoreThreadTimeOut(true)
        }
    }
}

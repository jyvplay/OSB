// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.fusion.security.diagnostics.DiagnosticSaveWorkflow
import dev.fusion.security.diagnostics.DiagnosticArchiveInspector
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R176 authored device tests, NOT executed by host compilation. Run only on a
 * disposable AVD with fusionDisposable=true. The provider test uses the actual
 * MediaStore Downloads provider, not a stand-in. It creates/deletes its own URI;
 * it does not claim system-picker interaction or a third-party provider matrix.
 */
@RunWith(AndroidJUnit4::class)
class R176DiagnosticExportDeviceTest {
    @get:Rule val disposable = DisposableEmulatorRule()
    private fun scenario() = ActivityScenario.launch<DiagnosticExportActivity>(
        Intent(ApplicationProvider.getApplicationContext(), DiagnosticExportActivity::class.java))
    private inline fun <reified T : View> field(activity: DiagnosticExportActivity, tag: String): T =
        activity.findViewById<View>(android.R.id.content).findViewWithTag<T>(tag)

    @Test fun defaultFieldsAreNotCollectedAndEmptyReviewStillRequiresApproval() {
        scenario().use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(field<CheckBox>(activity, "diagnostic-sdk").isChecked)
                assertFalse(field<CheckBox>(activity, "diagnostic-version").isChecked)
                assertFalse(field<CheckBox>(activity, "diagnostic-note-enabled").isChecked)
                field<Button>(activity, "diagnostic-prepare").performClick()
                assertEquals("(empty report — no selected fields)", field<TextView>(activity, "diagnostic-review").text.toString())
                assertFalse(field<Button>(activity, "diagnostic-save").isEnabled)
                field<CheckBox>(activity, "diagnostic-approve").isChecked = true
                assertTrue(field<Button>(activity, "diagnostic-save").isEnabled)
            }
        }
    }

    @Test fun exactRedactedReviewSurvivesConfigurationButApprovalDoesNot() {
        scenario().use { scenario ->
            scenario.onActivity { activity ->
                field<CheckBox>(activity, "diagnostic-note-enabled").isChecked = true
                field<EditText>(activity, "diagnostic-note").setText("dev@example.org")
                field<Button>(activity, "diagnostic-prepare").performClick()
                assertEquals("operator.note=[email]", field<TextView>(activity, "diagnostic-review").text.toString())
                field<CheckBox>(activity, "diagnostic-approve").isChecked = true
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals("operator.note=[email]", field<TextView>(activity, "diagnostic-review").text.toString())
                assertFalse(field<CheckBox>(activity, "diagnostic-approve").isChecked)
                assertFalse(field<Button>(activity, "diagnostic-save").isEnabled)
            }
        }
    }

    @Test fun discardDropsReviewAndNoteRatherThanSavingADraft() {
        scenario().use { scenario ->
            scenario.onActivity { activity ->
                field<CheckBox>(activity, "diagnostic-note-enabled").isChecked = true
                field<EditText>(activity, "diagnostic-note").setText("temporary observation")
                field<Button>(activity, "diagnostic-prepare").performClick()
                field<Button>(activity, "diagnostic-discard").performClick()
                assertEquals("", field<EditText>(activity, "diagnostic-note").text.toString())
                assertFalse(field<CheckBox>(activity, "diagnostic-note-enabled").isChecked)
            }
        }
    }

    @Test fun oversizedEditIsRefusedInsteadOfPartlyInsertingIt() {
        scenario().use { scenario ->
            scenario.onActivity { activity ->
                val note = field<EditText>(activity, "diagnostic-note")
                note.setText("retained")
                note.text.replace(0, note.length(), "x".repeat(1025))
                assertEquals("retained", note.text.toString())
                assertTrue(field<TextView>(activity, "diagnostic-status").text.contains("refused"))
            }
        }
    }

    @Test fun actualDownloadsProviderRoundTripAndIndependentArchiveReadback() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "r176-disposable-${System.nanoTime()}.zip")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }))
        val path = java.nio.file.Files.createTempFile(context.cacheDir.toPath(), "r176-provider-", ".zip")
        try {
            val workflow = DiagnosticSaveWorkflow()
            workflow.prepare("note", listOf("note" to "dev@example.org"))
            val digest = requireNotNull(workflow.snapshot().sha256)
            val ticket = workflow.beginSave(digest)
            assertTrue(workflow.save(ticket, object : DiagnosticSaveWorkflow.Destination {
                override fun openOutput() = requireNotNull(resolver.openOutputStream(uri, "wt"))
                override fun openInput() = requireNotNull(resolver.openInputStream(uri))
            }))
            requireNotNull(resolver.openInputStream(uri)).use { input ->
                java.nio.file.Files.newOutputStream(path).use { output -> input.copyTo(output) }
            }
            val report = DiagnosticArchiveInspector.inspect(path)
            assertEquals(digest, report.sha256)
            assertEquals(1, report.records)
        } finally {
            java.nio.file.Files.deleteIfExists(path)
            assertEquals(1, resolver.delete(uri, null, null))
        }
    }

    @Test fun secureReviewWindowKeepsCaptureExclusionEnabled() {
        scenario().use { scenario ->
            scenario.onActivity { activity ->
                field<CheckBox>(activity, "diagnostic-sdk").isChecked = true
                field<Button>(activity, "diagnostic-prepare").performClick()
                // Secure-window policy intentionally blocks screen capture on
                // compliant devices. Do NOT clear FLAG_SECURE to get a pretty
                // test screenshot or report a blank frame as a UI inspection.
                assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }
}

package dev.fusion.shell

import android.app.Activity
import android.graphics.Rect
import android.view.WindowInsets
import androidx.test.platform.app.InstrumentationRegistry
import dev.fusion.automation.TaskRepository
import java.io.File

/** Test-only bounded diagnostics. No retry, semantic click, altered timeout or production hook. */
object TaskUiEvidence {
    fun failure(activity:Activity,phase:String,cause:Throwable,status:String="not captured",before:String="not captured"):AssertionError {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val disk=runCatching {TaskRepository(File(activity.noBackupFilesDir,"tasks-v1.bin")).load()}
            .fold({"revision=${it.revision}; tasks=${it.tasks}"},{"${it.javaClass.simpleName}: ${it.message}"})
        val geometry=geometry(activity)
        // Do not call Compose printToString: its error path itself threw a cross-thread observer error.
        return AssertionError("Actual Task UI phase=$phase; $geometry; status=${status.take(400)}; before=${before.take(500)}; disk=$disk; original=${cause.javaClass.simpleName}: ${cause.message?.take(300)}",cause)
    }
    fun geometry(activity:Activity):String {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        var geometry="unavailable"
        runCatching {instrumentation.runOnMainSync {
            val view=activity.window.decorView;val insets=view.rootWindowInsets;val visible=Rect()
            view.getWindowVisibleDisplayFrame(visible)
            geometry="decor=${view.width}x${view.height}; visible=$visible; ime=${insets?.isVisible(WindowInsets.Type.ime())}; imeInsets=${insets?.getInsets(WindowInsets.Type.ime())}"
        }}
        return geometry
    }
}

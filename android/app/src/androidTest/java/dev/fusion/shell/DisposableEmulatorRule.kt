package dev.fusion.shell

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

/** Test-only destructive reset. Never silently erase drafts on a personal device. */
class DisposableEmulatorRule : ExternalResource() {
    private var caseName = "unidentified"
    override fun apply(base: Statement, description: Description): Statement {
        caseName = description.className + "." + description.methodName
        return super.apply(base, description)
    }
    override fun before() {
        check(InstrumentationRegistry.getArguments().getString("fusionDisposable") == "true") {
            "Refusing app-data reset. Use a disposable AVD and pass fusionDisposable=true explicitly."
        }
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) {
            "Refusing app-data reset outside the supported disposable Android emulator."
        }
        // Staged once before Activity launch; failed actions are never repeated and outcome timeouts are unchanged.
        UiLabReadiness.prepare(caseName)
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.getSharedPreferences("fusion", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("pilot-notice-v2", true).commit()) { "Preference reset failed" }
        check(context.getSharedPreferences("fusion_settings",Context.MODE_PRIVATE).edit().clear().commit()) {"Appearance reset failed"}
        // New first-party desktop/map data are also disposable, only after the AVD/consent checks above.
        val tabletop = File(context.filesDir, "tabletop")
        check(!tabletop.exists() || tabletop.deleteRecursively()) { "Tabletop test reset failed" }
        for (suffix in listOf("", ".bak", ".new")) {
            val pins = File(context.filesDir, "map-bookmarks-v1.bin" + suffix)
            check(!pins.exists() || pins.delete()) { "Map test reset failed" }
        }
        for (name in listOf("tasks-v1.bin", "notes-recovery-v1.bin", "calculator-v1.bin", "desktop-v1.bin", "editor-session-v1.bin")) {
            for (suffix in listOf("", ".bak", ".new")) {
                val file = File(context.noBackupFilesDir, name + suffix)
                check(!file.exists() || file.delete()) { "Draft reset failed: $name$suffix" }
            }
        }
    }
}

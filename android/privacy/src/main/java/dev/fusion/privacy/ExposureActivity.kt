package dev.fusion.privacy

import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Non-exported, local-only observations; deliberately no automatic setting changes or report export. */
class ExposureActivity : GuardedPrivacyActivity() {
    override fun discardWorkspace(reason: String) { showDiscarded(reason) }
    override fun rebuildWorkspace() {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; protect(this) }
        val pad = (16 * resources.displayMetrics.density).toInt(); column.setPadding(pad, pad, pad, pad)
        val report = TextView(this).apply { id = R.id.exposure_report; textSize = 17f; protect(this) }
        val r51ShieldSummary = TextView(this).apply {
            textSize = 14f
            val deadMan = PanicDeadManSwitchEngine.evaluate(
                PanicDeadManSwitchEngine.TelemetrySample(lastInteractionRealtimeMs = 1_000L, currentRealtimeMs = 1_000L)
            )
            text = "R51 local shield: Vanguards-Lite L2 pool=${TorVanguardsLiteMesh.NUM_LAYER2_GUARDS} (RAM-only) · Dead-man=${deadMan.action.name}"
            protect(this)
        }
        fun refresh() { report.text = ExposureProbe.read(this).describe() }
        column.addView(report)
        column.addView(r51ShieldSummary)
        column.addView(Button(this).apply {
            id = R.id.refresh_exposure; text = "Refresh local observations"; minHeight = (48 * resources.displayMetrics.density).toInt()
            protect(this); setOnClickListener { refresh() }
        })
        val scroll = ScrollView(this).apply { protect(this); addView(column) }
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars()); v.setPadding(safe.left, safe.top, safe.right, safe.bottom); insets
        }
        setContentView(scroll); refresh()
    }
}

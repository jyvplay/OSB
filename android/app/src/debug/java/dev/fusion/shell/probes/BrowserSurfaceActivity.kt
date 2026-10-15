package dev.fusion.shell.probes

import android.app.Activity
import android.os.Bundle
import android.view.SurfaceView
import android.widget.LinearLayout

/** Test-owned embedding host, not a replacement for MainActivity's own compositor or PR07 UI. */
class BrowserSurfaceActivity:Activity() {
    lateinit var left:SurfaceView;private set
    lateinit var right:SurfaceView;private set
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        // AOSP SurfaceView requires above-host z-order for embedded input delivery.
        // [U] verify API34/36/37; this does NOT solve chrome/overlap/IME/AX in the own compositor.
        left=SurfaceView(this).apply { setZOrderOnTop(true) }
        right=SurfaceView(this).apply { setZOrderOnTop(true) }
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            addView(this@BrowserSurfaceActivity.left,LinearLayout.LayoutParams(0,-1,1f));addView(this@BrowserSurfaceActivity.right,LinearLayout.LayoutParams(0,-1,1f))
        }
        setContentView(row)
    }
}

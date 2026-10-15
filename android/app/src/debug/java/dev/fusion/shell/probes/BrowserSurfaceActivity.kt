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
        left=SurfaceView(this);right=SurfaceView(this)
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            addView(this@BrowserSurfaceActivity.left,LinearLayout.LayoutParams(0,-1,1f));addView(this@BrowserSurfaceActivity.right,LinearLayout.LayoutParams(0,-1,1f))
        }
        setContentView(row)
    }
}

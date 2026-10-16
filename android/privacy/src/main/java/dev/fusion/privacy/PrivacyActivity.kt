package dev.fusion.privacy
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
/** Preimplementation platform experiment. Unsecured positive control is removed before any workspace ships. */
class PrivacyActivity: Activity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if(!intent.getBooleanExtra("unsecuredPositiveControl",false)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(TextView(this).apply {text="R20 screenshot marker";setBackgroundColor(Color.rgb(237,19,180))})
    }
}

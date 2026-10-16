package dev.fusion.privacy

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
/** Preimplementation platform experiment only. Replaced by workspace only after actual spike evidence. */
class PrivacyActivity: Activity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text="R20 platform spike, not an anonymous workspace" })
    }
}

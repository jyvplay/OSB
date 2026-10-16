package dev.fusion.privacy
/** Debug-only fixed-color screenshot control. Not editable, not exported and absent from release. */
class ScreenshotControlActivity:android.app.Activity() {
    override fun onCreate(state:android.os.Bundle?) {
        super.onCreate(state)
        setContentView(android.widget.TextView(this).apply {text="R20 screenshot marker";setBackgroundColor(android.graphics.Color.rgb(237,19,180))})
    }
}

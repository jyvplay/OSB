package dev.fusion.shell

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Renders Compose content on a secondary display.
 *
 * A Presentation is a Dialog, so its view tree does not inherit the hosting
 * Activity's lifecycle/ViewModel/SavedState owners. ComposeView requires all
 * three, so they are attached explicitly from the host Activity. Without this
 * the external window crashes on first composition.
 *
 * Honest limit: a Presentation is owned by the Activity. When the Activity
 * stops, this window is dismissed. There is no foreground service keeping a
 * desktop alive on the monitor while the phone UI is elsewhere.
 */
class DesktopPresentation(
    private val host: ComponentActivity,
    display: Display,
    private val content: @Composable () -> Unit
) : Presentation(host as Context, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setContent { content() }
        }
        setContentView(view)
    }
}

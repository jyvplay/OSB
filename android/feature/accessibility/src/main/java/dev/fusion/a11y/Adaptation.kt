package dev.fusion.a11y

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class A11ySettings(
    val fontScale: Float,
    val animationsDisabled: Boolean,
    val highContrastText: Boolean,
    val colorInversion: Boolean,
    val touchExplorationOn: Boolean,
    val activeServices: Int
)

/**
 * Reads the user's real accessibility preferences so the shell can respect them.
 * Read-only: Fusion does not run an accessibility service and cannot change
 * these settings. Honouring reduce-motion / font scale is the caller's job.
 */
object Adaptation {
    fun read(context: Context): A11ySettings {
        val cr = context.contentResolver
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val services = runCatching {
            manager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).size
        }.getOrDefault(0)
        return A11ySettings(
            fontScale = runCatching { Settings.System.getFloat(cr, Settings.System.FONT_SCALE) }.getOrDefault(1f),
            animationsDisabled = runCatching {
                Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE)
            }.getOrDefault(1f) == 0f,
            highContrastText = runCatching {
                Settings.Secure.getInt(cr, "high_text_contrast_enabled")
            }.getOrDefault(0) == 1,
            colorInversion = runCatching {
                Settings.Secure.getInt(cr, "accessibility_display_inversion_enabled")
            }.getOrDefault(0) == 1,
            touchExplorationOn = runCatching { manager.isTouchExplorationEnabled }.getOrDefault(false),
            activeServices = services
        )
    }
}

@Composable
fun AccessibilityScreen(context: Context) {
    val s = remember { Adaptation.read(context) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Accessibility state (read-only)", style = MaterialTheme.typography.titleSmall)
        Text("Font scale: ${s.fontScale}")
        Text("Reduce motion: ${if (s.animationsDisabled) "ON — shell should skip animations" else "off"}")
        Text("High contrast text: ${if (s.highContrastText) "on" else "off"}")
        Text("Colour inversion: ${if (s.colorInversion) "on" else "off"}")
        Text("Touch exploration (TalkBack-style): ${if (s.touchExplorationOn) "on" else "off"}")
        Text("Enabled accessibility services: ${s.activeServices}")
        val focusRing = remember(s.highContrastText) {
            FocusRingDwellClickController.buildFocusRing(highVisibilityEnabled = s.highContrastText, darkTheme = true)
        }
        val simpleScale = remember(s.fontScale) {
            EasySimpleModeLayoutScaler.buildSimpleLauncher(listOf("files" to "Files", "notes" to "Notes"), systemFontScale = s.fontScale)
        }
        val daltonizedRed = remember {
            ColorBlindnessDaltonizeMatrix.daltonize(ColorBlindnessDaltonizeMatrix.CvdMode.DEUTERANOPIA, 0.90, 0.20, 0.20)
        }
        Text(
            "R50 A11y tokens: ring=${focusRing.innerStrokeDp}dp (${focusRing.innerArgbHex}) · EasyMode>=${simpleScale.minTouchTargetDp}dp@${simpleScale.effectiveFontScale}x · DeutanR=${"%.2f".format(daltonizedRed[0])}",
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            "Fusion does not run an accessibility service and never injects input. " +
                "Full TalkBack/Switch Access validation of the window manager has not been done yet.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

package dev.fusion.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class DensityProfile { COMPACT, BALANCED, TOUCH }

@Immutable
data class FusionDensity(val target: Dp, val titleBar: Dp) {
    companion object {
        val Compact = FusionDensity(24.dp, 28.dp)
        val Balanced = FusionDensity(32.dp, 36.dp)
        val Touch = FusionDensity(48.dp, 48.dp)
    }
}
val LocalDensityProfile = staticCompositionLocalOf { FusionDensity.Balanced }

private val Scheme = darkColorScheme(
    primary = Color(0xFF65DDB4),
    // Opaque sRGB foreground calculation; not a rendered accessibility pass.
    onPrimary = Color(0xFF000000L or requireNotNull(Contrast.foreground(0,0x65DDB4)).toLong()),
    background = Color(0xFF0C1011),
    surface = Color(0xFF191E20),
    onBackground = Color(0xFFE4E4E7),
    onSurface = Color(0xFFE4E4E7)
)

@Composable
fun FusionTheme(profile: DensityProfile = DensityProfile.BALANCED, content: @Composable () -> Unit) {
    val d = when (profile) {
        DensityProfile.COMPACT -> FusionDensity.Compact
        DensityProfile.BALANCED -> FusionDensity.Balanced
        DensityProfile.TOUCH -> FusionDensity.Touch
    }
    CompositionLocalProvider(LocalDensityProfile provides d) {
        MaterialTheme(colorScheme = Scheme, content = content)
    }
}

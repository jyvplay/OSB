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

private fun rgb(value:Int)=Color(0xff000000L or value.toLong())

@Composable
fun FusionTheme(profile: DensityProfile = DensityProfile.BALANCED,
                palette: dev.fusion.settings.ProfessionalPalette = dev.fusion.settings.ProfessionalPalette.OFFICE_BLUE,
                content: @Composable () -> Unit) {
    val c=ProfessionalPalettes.colors(palette)
    val base=if(palette.dark) darkColorScheme() else androidx.compose.material3.lightColorScheme()
    val scheme=base.copy(primary=rgb(c.primary),onPrimary=rgb(c.onPrimary),
        primaryContainer=rgb(c.selection),onPrimaryContainer=rgb(c.onSelection),
        secondary=rgb(c.primary),onSecondary=rgb(c.onPrimary),secondaryContainer=rgb(c.selection),onSecondaryContainer=rgb(c.onSelection),
        tertiary=rgb(c.primary),onTertiary=rgb(c.onPrimary),background=rgb(c.background),onBackground=rgb(c.text),
        surface=rgb(c.surface),onSurface=rgb(c.text),surfaceVariant=rgb(c.raised),onSurfaceVariant=rgb(c.muted),
        outline=rgb(c.outline),outlineVariant=rgb(c.outline),surfaceTint=rgb(c.primary),
        surfaceContainerLowest=rgb(c.background),surfaceContainerLow=rgb(c.surface),surfaceContainer=rgb(c.surface),
        surfaceContainerHigh=rgb(c.raised),surfaceContainerHighest=rgb(c.raised))
    val d=when(profile) {DensityProfile.COMPACT->FusionDensity.Compact;DensityProfile.BALANCED->FusionDensity.Balanced;DensityProfile.TOUCH->FusionDensity.Touch}
    CompositionLocalProvider(LocalDensityProfile provides d) {MaterialTheme(colorScheme=scheme,content=content)}
}

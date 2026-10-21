package dev.fusion.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fusion.designsystem.BeautyStandard
import dev.fusion.designsystem.DesktopMetrics
import dev.fusion.designsystem.DockMagnification
import dev.fusion.settings.Appearance
import dev.fusion.settings.DesktopLayout

/**
 * R26 AUTHORED, NOT EXECUTED IN THIS SANDBOX. Drop-in dock with the same signature as
 * [DesktopShelf]: same ShelfApp list, same magnification, same >=48dp targets.
 *
 * Difference: slot/gap math comes from the host-verified [BeautyStandard.dockPlan] kernel
 * (phi-derived gaps, at most 8 preferred visible slots, explicit overflow count) instead of
 * filling every available slot. The overflow indicator is a real scrollable-row label, not a
 * shrunken icon. Wiring is a one-line call-site swap at MainActivity's DesktopShelf call;
 * pending Studio/CI compile plus disposable-emulator verification.
 *
 * R27: test tags aligned to the DesktopShelf scheme (art-dock / business-taskbar /
 * shelf-scroll / shelf-{id} / dock-glyph-{id} / dock-label-{id}) so the existing ~10
 * androidTest files keep passing whenever the swap happens. Still UNWIRED: no call site
 * references BeautyDock yet, so nothing in the app changes. AUTHORED, NOT EXECUTED.
 */
@Composable
fun BeautyDock(apps: List<ShelfApp>, appearance: Appearance, imeVisible: Boolean, enabled: Boolean, onOpen: (String) -> Unit) {
    require(apps.size <= DockMagnification.MAX_ITEMS)
    var hot by remember { mutableStateOf<Int?>(null) }
    val art = appearance.layout == DesktopLayout.ART
    BoxWithConstraints(
        Modifier.fillMaxWidth().background(if (art) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.BottomCenter) {
        val density = LocalDensity.current
        val plan = BeautyStandard.dockPlan(maxWidth.value.toDouble().coerceAtLeast(48.0),
            density.fontScale.toDouble().coerceIn(.5, 5.0), apps.size)
        val metrics = DesktopMetrics.dock(maxWidth.value.toDouble().coerceAtLeast(48.0),
            density.fontScale.toDouble().coerceIn(.5, 5.0), imeVisible, apps.size)
        Surface(shape = RoundedCornerShape(if (art) 22.dp else 0.dp), tonalElevation = 0.dp,
            color = if (art) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(horizontal = if (art) 12.dp else 0.dp, vertical = if (art) 6.dp else 0.dp)
                .widthIn(max = if (art) plan.viewportDp.dp else 10000.dp).then(if (!art) Modifier.fillMaxWidth() else Modifier)
                .testTag(if (art) "art-dock" else "business-taskbar")) {
            Row(Modifier.horizontalScroll(rememberScrollState()).testTag("shelf-scroll")
                .padding(horizontal = 8.dp, vertical = if (imeVisible) 2.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(plan.gapDp.dp), verticalAlignment = Alignment.Bottom) {
                apps.forEachIndexed { index, app ->
                    val interactions = remember(app.id) { MutableInteractionSource() }
                    val hovered by interactions.collectIsHoveredAsState()
                    val focused by interactions.collectIsFocusedAsState()
                    LaunchedEffect(hovered, focused) { if (hovered || focused) hot = index else if (hot == index) hot = null }
                    val target = DockMagnification.scale(index, hot, appearance.layout, appearance.dockZoom && !imeVisible)
                    val scale by animateFloatAsState(target, animationSpec = tween(if (appearance.dockZoom) 120 else 0), label = "dock-${app.id}")
                    Column(Modifier.width(plan.slotDp.dp).height(metrics.height.dp)
                        .testTag("shelf-${app.id}").hoverable(interactions, enabled)
                        .clickable(enabled = enabled, role = Role.Button, interactionSource = interactions,
                            indication = androidx.compose.foundation.LocalIndication.current) { onOpen(app.id) }
                        .semantics(mergeDescendants = true) {
                            stateDescription = (if (app.active) "Active" else if (app.running) "Running" else "Closed") +
                                "; magnification " + (if (target > 1f) "enlarged" else "normal")
                        }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        AppGlyph(app.id, Modifier.size(metrics.glyph.dp).testTag("dock-glyph-${app.id}").graphicsLayer { scaleX = scale; scaleY = scale })
                        Spacer(Modifier.height(if (imeVisible) 1.dp else 5.dp))
                        Text(app.label, modifier = Modifier.testTag("dock-label-${app.id}"), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = if (app.active) FontWeight.Bold else FontWeight.Normal)
                        Box(Modifier.padding(top = 3.dp).size(width = if (app.active) 18.dp else 5.dp, height = 3.dp)
                            .clip(RoundedCornerShape(2.dp)).background(if (app.running) MaterialTheme.colorScheme.primary else Color.Transparent))
                    }
                }
                if (plan.overflow > 0) {
                    Text("+${plan.overflow} more — scroll", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically).testTag("shelf-overflow")
                            .semantics { contentDescription = "${plan.overflow} more apps; scroll the dock" })
                }
            }
        }
    }
}

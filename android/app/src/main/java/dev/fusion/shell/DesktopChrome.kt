package dev.fusion.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fusion.designsystem.DockMagnification
import dev.fusion.designsystem.DesktopMetrics
import androidx.compose.ui.platform.LocalDensity
import dev.fusion.settings.Appearance
import dev.fusion.settings.DesktopLayout
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ShelfApp(val id:String,val label:String,val running:Boolean,val active:Boolean)

/** Original tiny vector glyphs, not copyrighted OS assets or a multi-MB icon dependency. */
@Composable
internal fun AppGlyph(id:String,modifier:Modifier) {
    val ink=MaterialTheme.colorScheme.onPrimary
    val accent=MaterialTheme.colorScheme.primary
    Canvas(modifier.clip(RoundedCornerShape(9.dp)).background(accent).padding(6.dp)) {
        val w=size.width;val h=size.height;val line=Stroke(width=2.dp.toPx())
        when(id) {
            "files" -> {
                val p=Path().apply {moveTo(0f,h*.22f);lineTo(w*.38f,h*.22f);lineTo(w*.5f,h*.38f);lineTo(w,h*.38f);lineTo(w,h*.88f);lineTo(0f,h*.88f);close()}
                drawPath(p,ink,style=line)
            }
            "browser" -> {drawCircle(ink,radius=w*.43f,style=line);drawLine(ink,Offset(w*.3f,h*.7f),Offset(w*.7f,h*.3f),line.width);drawCircle(ink,w*.08f)}
            "calculator" -> {
                drawRoundRect(ink,cornerRadius=androidx.compose.ui.geometry.CornerRadius(2f,2f),style=line)
                drawLine(ink,Offset(w*.18f,h*.25f),Offset(w*.82f,h*.25f),line.width)
                for(x in 0..1) for(y in 0..1) drawCircle(ink,w*.075f,Offset(w*(.3f+x*.4f),h*(.5f+y*.27f)))
            }
            "media" -> {
                drawCircle(ink,radius=w*.44f,style=line)
                drawPath(Path().apply {moveTo(w*.38f,h*.25f);lineTo(w*.75f,h*.5f);lineTo(w*.38f,h*.75f);close()},ink)
            }
            "maps" -> {
                drawCircle(ink,radius=w*.44f,style=line)
                drawOval(ink,Offset(w*.28f,h*.06f),Size(w*.44f,h*.88f),style=line)
                drawLine(ink,Offset(w*.06f,h*.5f),Offset(w*.94f,h*.5f),line.width)
            }
            "settings" -> for(i in 0..2) {val y=h*(.2f+i*.3f);drawLine(ink,Offset(0f,y),Offset(w,y),line.width);drawCircle(ink,w*.12f,Offset(w*(if(i==1) .7f else .3f),y))}
            "apps","pilot" -> for(x in 0..1) for(y in 0..1) drawRect(ink,Offset(x*w*.58f,y*h*.58f),Size(w*.36f,h*.36f))
            else -> {drawRoundRect(ink,cornerRadius=androidx.compose.ui.geometry.CornerRadius(2f,2f),style=line)
                for(i in 0..2) drawLine(ink,Offset(w*.18f,h*(.25f+i*.24f)),Offset(w*.82f,h*(.25f+i*.24f)),line.width)}
        }
    }
}

@Composable
fun DesktopTopBar(appearance:Appearance,activeTitle:String,workspace:Int,onFind:()->Unit,onSafety:()->Unit,onControl:()->Unit) {
    var clock by remember {mutableStateOf(LocalTime.now())}
    LaunchedEffect(Unit) {while(true) {clock=LocalTime.now();delay(15000)}}
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal=8.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("F",fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary,fontSize=24.sp)
            // Primary Find remains before the overflow title, with a real >=48dp layout box.
            TextButton(onClick=onFind,modifier=Modifier.heightIn(min=48.dp).testTag("desktop-find")) {Text("Find")}
            Text(if(appearance.layout==DesktopLayout.ART) activeTitle else "Fusion Shell",fontWeight=FontWeight.SemiBold,maxLines=1)
            TextButton(onClick=onSafety,modifier=Modifier.heightIn(min=48.dp)) {Text("Pilot safety")}
            Text("Workspace ${workspace+1}",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(clock.format(DateTimeFormatter.ofPattern("HH:mm")),color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.widthIn(max=148.dp).horizontalScroll(rememberScrollState())) {
            TextButton(onClick=onControl,modifier=Modifier.heightIn(min=48.dp).testTag("control-panel")) {Text("Control panel")}
        }
    }
}

@Composable
fun DesktopWallpaper(art:Boolean) {
    val colors=MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(if(art) Brush.linearGradient(listOf(colors.background,colors.surfaceVariant,colors.background))
        else Brush.linearGradient(listOf(colors.background,colors.background)))) {
        Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("Fusion Shell",style=MaterialTheme.typography.headlineSmall,color=colors.onSurface,fontWeight=FontWeight.Bold)
            Text(if(art) "Space to create." else "A clear place to work.",style=MaterialTheme.typography.headlineMedium,color=colors.primary,fontWeight=FontWeight.SemiBold)
            Text("Fusion Shell · local first · evaluation build",color=colors.onSurfaceVariant)
            Text("Open an app below, or use Ctrl+K",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
        }
    }
}

@Composable
fun DesktopShelf(apps:List<ShelfApp>,appearance:Appearance,imeVisible:Boolean,enabled:Boolean,onOpen:(String)->Unit) {
    require(apps.size<=DockMagnification.MAX_ITEMS)
    var hot by remember {mutableStateOf<Int?>(null)}
    val art=appearance.layout==DesktopLayout.ART
    BoxWithConstraints(Modifier.fillMaxWidth().background(if(art) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface),
        contentAlignment=Alignment.BottomCenter) {
        val metrics=DesktopMetrics.dock(maxWidth.value.toDouble().coerceAtLeast(48.0),LocalDensity.current.fontScale.toDouble().coerceIn(.5,5.0),imeVisible,apps.size)
        Surface(shape=RoundedCornerShape(if(art) 22.dp else 0.dp),tonalElevation=0.dp,
            color=if(art) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            modifier=Modifier.padding(horizontal=if(art) 12.dp else 0.dp,vertical=if(art) 6.dp else 0.dp)
                .widthIn(max=if(art) metrics.viewport.dp else 10000.dp).then(if(!art) Modifier.fillMaxWidth() else Modifier)
                .testTag(if(art) "art-dock" else "business-taskbar")) {
            Row(Modifier.horizontalScroll(rememberScrollState()).testTag("shelf-scroll").padding(horizontal=8.dp,vertical=if(imeVisible) 2.dp else 8.dp),
                horizontalArrangement=Arrangement.spacedBy(metrics.gap.dp),verticalAlignment=Alignment.Bottom) {
                apps.forEachIndexed {index,app ->
                    val interactions=remember(app.id) {MutableInteractionSource()}
                    val hovered by interactions.collectIsHoveredAsState()
                    val focused by interactions.collectIsFocusedAsState()
                    LaunchedEffect(hovered,focused) {if(hovered || focused) hot=index else if(hot==index) hot=null}
                    val target=DockMagnification.scale(index,hot,appearance.layout,appearance.dockZoom && !imeVisible)
                    val scale by animateFloatAsState(target,animationSpec=tween(if(appearance.dockZoom) 120 else 0),label="dock-${app.id}")
                    Column(Modifier.width(metrics.slot.dp).height(metrics.height.dp)
                        .testTag("shelf-${app.id}").hoverable(interactions,enabled)
                        .clickable(enabled=enabled,role=Role.Button,interactionSource=interactions,indication=androidx.compose.foundation.LocalIndication.current) {onOpen(app.id)}
                        .semantics(mergeDescendants=true) {
                            stateDescription=(if(app.active) "Active" else if(app.running) "Running" else "Closed")+"; magnification "+(if(target>1f) "enlarged" else "normal")
                        },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
                        AppGlyph(app.id,Modifier.size(metrics.glyph.dp).testTag("dock-glyph-${app.id}").graphicsLayer {scaleX=scale;scaleY=scale})
                        Spacer(Modifier.height(if(imeVisible) 1.dp else 5.dp))
                        Text(app.label,modifier=Modifier.testTag("dock-label-${app.id}"),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp,lineHeight=16.sp,
                            color=MaterialTheme.colorScheme.onSurface,fontWeight=if(app.active) FontWeight.Bold else FontWeight.Normal)
                        Box(Modifier.padding(top=3.dp).size(width=if(app.active) 18.dp else 5.dp,height=3.dp)
                            .clip(RoundedCornerShape(2.dp)).background(if(app.running) MaterialTheme.colorScheme.primary else Color.Transparent))
                    }
                }
            }
        }
    }
}

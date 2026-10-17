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
import dev.fusion.settings.Appearance
import dev.fusion.settings.DesktopLayout
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ShelfApp(val id:String,val label:String,val running:Boolean,val active:Boolean)

/** Original tiny vector glyphs, not copyrighted OS assets or a multi-MB icon dependency. */
@Composable
private fun AppGlyph(id:String,modifier:Modifier) {
    val ink=MaterialTheme.colorScheme.onPrimary
    val accent=MaterialTheme.colorScheme.primary
    Canvas(modifier.clip(RoundedCornerShape(9.dp)).background(accent).padding(6.dp)) {
        val w=size.width;val h=size.height;val line=Stroke(width=2.dp.toPx())
        when(id) {
            "files" -> {
                val p=Path().apply {moveTo(0f,h*.22f);lineTo(w*.38f,h*.22f);lineTo(w*.5f,h*.38f);lineTo(w,h*.38f);lineTo(w,h*.88f);lineTo(0f,h*.88f);close()}
                drawPath(p,ink,style=line)
            }
            "browser" -> {drawCircle(ink,radius=w*.43f,style=line);drawLine(ink,Offset(w*.3f,h*.7f),Offset(w*.7f,h*.3f),line.strokeWidth);drawCircle(ink,w*.08f)}
            "calculator" -> {
                drawRoundRect(ink,cornerRadius=androidx.compose.ui.geometry.CornerRadius(2f,2f),style=line)
                drawLine(ink,Offset(w*.18f,h*.25f),Offset(w*.82f,h*.25f),line.strokeWidth)
                for(x in 0..1) for(y in 0..1) drawCircle(ink,w*.075f,Offset(w*(.3f+x*.4f),h*(.5f+y*.27f)))
            }
            "settings" -> for(i in 0..2) {val y=h*(.2f+i*.3f);drawLine(ink,Offset(0f,y),Offset(w,y),line.strokeWidth);drawCircle(ink,w*.12f,Offset(w*(if(i==1) .7f else .3f),y))}
            "apps","pilot" -> for(x in 0..1) for(y in 0..1) drawRect(ink,Offset(x*w*.58f,y*h*.58f),Size(w*.36f,h*.36f))
            else -> {drawRoundRect(ink,cornerRadius=androidx.compose.ui.geometry.CornerRadius(2f,2f),style=line)
                for(i in 0..2) drawLine(ink,Offset(w*.18f,h*(.25f+i*.24f)),Offset(w*.82f,h*(.25f+i*.24f)),line.strokeWidth)}
        }
    }
}

@Composable
fun DesktopTopBar(appearance:Appearance,activeTitle:String,workspace:Int,onFind:()->Unit,onSafety:()->Unit,onControl:()->Unit) {
    var clock by remember {mutableStateOf(LocalTime.now())}
    LaunchedEffect(Unit) {while(true) {clock=LocalTime.now();delay(15000)}}
    Row(Modifier.fillMaxWidth().height(56.dp).background(MaterialTheme.colorScheme.surface).horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("F",fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary,fontSize=24.sp)
        Text(if(appearance.layout==DesktopLayout.ART) activeTitle else "FUSION  /  BUSINESS",fontWeight=FontWeight.SemiBold,maxLines=1)
        TextButton(onClick=onFind,modifier=Modifier.testTag("desktop-find")) {Text("Find")}
        TextButton(onClick=onSafety) {Text("Pilot safety")}
        Text("Workspace ${workspace+1}",color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick=onControl,modifier=Modifier.testTag("control-panel")) {Text("Control panel")}
        Text(clock.format(DateTimeFormatter.ofPattern("HH:mm")),color=MaterialTheme.colorScheme.onSurfaceVariant)
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
    Box(Modifier.fillMaxWidth().height(if(imeVisible) 68.dp else 100.dp).background(if(art) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface),
        contentAlignment=Alignment.BottomCenter) {
        Surface(shape=RoundedCornerShape(if(art) 22.dp else 0.dp),tonalElevation=0.dp,
            color=if(art) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            modifier=Modifier.padding(horizontal=if(art) 12.dp else 0.dp,vertical=if(art) 6.dp else 0.dp)
                .widthIn(max=if(art) 1080.dp else 10000.dp).then(if(!art) Modifier.fillMaxWidth() else Modifier)
                .testTag(if(art) "art-dock" else "business-taskbar")) {
            Row(Modifier.horizontalScroll(rememberScrollState()).testTag("shelf-scroll").padding(horizontal=8.dp,vertical=if(imeVisible) 2.dp else 8.dp),
                horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.Bottom) {
                apps.forEachIndexed {index,app ->
                    val interactions=remember(app.id) {MutableInteractionSource()}
                    val hovered by interactions.collectIsHoveredAsState()
                    var focused by remember(app.id) {mutableStateOf(false)}
                    LaunchedEffect(hovered,focused) {if(hovered || focused) hot=index else if(hot==index) hot=null}
                    val target=DockMagnification.scale(index,hot,appearance.layout,appearance.dockZoom && !imeVisible)
                    val scale by animateFloatAsState(target,animationSpec=tween(if(appearance.dockZoom) 120 else 0),label="dock-${app.id}")
                    Column(Modifier.width(DockMagnification.SLOT_DP.dp).height(if(imeVisible) 60.dp else 76.dp)
                        .testTag("shelf-${app.id}").onFocusChanged {focused=it.hasFocus}.hoverable(interactions,enabled)
                        .clickable(enabled=enabled,role=Role.Button,interactionSource=interactions,indication=androidx.compose.foundation.LocalIndication.current) {onOpen(app.id)}
                        .semantics(mergeDescendants=true) {
                            stateDescription=(if(app.active) "Active" else if(app.running) "Running" else "Closed")+"; magnification "+(if(target>1f) "enlarged" else "normal")
                        },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
                        AppGlyph(app.id,Modifier.size(if(imeVisible) 24.dp else DockMagnification.GLYPH_DP.dp).testTag("dock-glyph-${app.id}").graphicsLayer {scaleX=scale;scaleY=scale})
                        Spacer(Modifier.height(if(imeVisible) 1.dp else 5.dp))
                        Text(app.label,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=if(imeVisible) 9.sp else 11.sp,
                            color=MaterialTheme.colorScheme.onSurface,fontWeight=if(app.active) FontWeight.Bold else FontWeight.Normal)
                        Box(Modifier.padding(top=3.dp).size(width=if(app.active) 18.dp else 5.dp,height=3.dp)
                            .clip(RoundedCornerShape(2.dp)).background(if(app.running) MaterialTheme.colorScheme.primary else Color.Transparent))
                    }
                }
            }
        }
    }
}

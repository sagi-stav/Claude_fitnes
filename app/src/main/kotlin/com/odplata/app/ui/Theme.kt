package com.odplata.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odplata.app.R

/** "Iron": the look of gym hardware. Rubber floor, steel, and the colours of Olympic plates. */
object Iron {
    val FloorTop = Color(0xFF2A2D32)
    val Floor = Color(0xFF17191C)
    val Ink = Color(0xFFEEF0F2)
    val Muted = Color(0xFFA0A7B1)
    val Line = Color(0xFF3A3E45)
    val RaisedTop = Color(0xFF34383F)
    val RaisedBottom = Color(0xFF272A30)
    val Edge = Color(0xFF0E0F11)
    val Well = Color(0xFF101214)
    val SteelLight = Color(0xFFF4F5F7)
    val Steel = Color(0xFFC9CED5)
    val SteelDark = Color(0xFF6F7782)
    val OnSteel = Color(0xFF14171B)

    val Red = Color(0xFFD2362B)
    val Blue = Color(0xFF2460C8)
    val Yellow = Color(0xFFE9AE00)
    val Green = Color(0xFF2A9558)
    val White = Color(0xFFF1F3F5)
    val Signal = Color(0xFFF2BC1B)
}

@OptIn(ExperimentalTextApi::class)
private fun heebo(w: Int) = Font(R.font.heebo, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Heebo = FontFamily(heebo(400), heebo(500), heebo(700), heebo(800))
val Karantina = FontFamily(Font(R.font.karantina_bold, FontWeight.Bold))

object Type {
    val Body = TextStyle(fontFamily = Heebo, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val Small = TextStyle(fontFamily = Heebo, fontSize = 13.sp, lineHeight = 18.sp)
    val Strong = TextStyle(fontFamily = Heebo, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
    val Title = TextStyle(fontFamily = Karantina, fontSize = 40.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
    val Display = TextStyle(fontFamily = Karantina, fontSize = 30.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
}

/** Root of every screen: floor gradient, right-to-left layout and room for the system bars. */
@Composable
fun IronScreen(content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalContentColor provides Iron.Ink,
        LocalTextStyle provides Type.Body,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Iron.FloorTop, Iron.Floor), endY = 900f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** A surface that sits above the floor and sinks when pressed, like a real key. */
@Composable
fun Raised(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    top: Color = Iron.RaisedTop,
    bottom: Color = Iron.RaisedBottom,
    edge: Color = Iron.Edge,
    radius: Dp = 14.dp,
    sunk: Boolean = false,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val down = pressed || sunk
    Box(
        modifier
            .graphicsLayer { translationY = if (down) 3.dp.toPx() else 0f }
            .drawBehind {
                val r = CornerRadius(radius.toPx())
                if (!down) drawRoundRect(edge, Offset(0f, 4.dp.toPx()), size, r)
                drawRoundRect(Brush.verticalGradient(if (down) listOf(bottom, top) else listOf(top, bottom)), cornerRadius = r)
                // A thin highlight along the top lip.
                if (!down) drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(1.dp.toPx(), 0f), Size(size.width - 2.dp.toPx(), 1.5.dp.toPx()), r)
            }
            .clip(RoundedCornerShape(radius))
            .then(if (onClick != null) Modifier.clickable(source, null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** The main action: bright steel. */
@Composable
fun SteelButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Raised(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        onClick = if (enabled) onClick else null,
        top = Iron.SteelLight, bottom = Iron.Steel, edge = Iron.SteelDark,
    ) {
        Text(text, Modifier.align(Alignment.Center), color = Iron.OnSteel, style = Type.Strong.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp))
    }
}

@Composable
fun DarkButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Raised(modifier.defaultMinSize(minHeight = 48.dp), onClick = onClick) {
        Text(text, Modifier.align(Alignment.Center), style = Type.Strong)
    }
}

/** Small square key for steppers. */
@Composable
fun Key(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Raised(modifier.size(52.dp), onClick = onClick, radius = 12.dp, padding = PaddingValues(0.dp)) {
        Text(label, Modifier.align(Alignment.Center), style = Type.Strong.copy(fontSize = 18.sp), textAlign = TextAlign.Center)
    }
}

/** A recessed area for read-outs and verdicts. */
@Composable
fun Well(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val r = CornerRadius(14.dp.toPx())
                drawRoundRect(Color.White.copy(alpha = 0.07f), Offset(0f, 1.dp.toPx()), size, r)
                drawRoundRect(Iron.Well, cornerRadius = r)
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
fun TopBar(left: String, right: String = "", onBack: (() -> Unit)? = null, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onBack != null) {
            Text("חזרה", Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onBack).padding(horizontal = 6.dp, vertical = 10.dp), color = Iron.Muted, style = Type.Small)
        }
        Text(left, Modifier.weight(1f), color = Iron.Muted, style = Type.Small, maxLines = 1)
        if (right.isNotEmpty()) Text(right, color = Iron.Muted, style = Type.Small)
        action?.invoke(this)
    }
}

@Composable
fun TextLink(text: String, modifier: Modifier = Modifier, color: Color = Iron.Muted, onClick: () -> Unit) {
    Text(text, modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp), color = color, style = Type.Small.copy(fontWeight = FontWeight.Medium))
}

/**
 * Keeps a run such as "8–12", "+10" or "60×11" in left-to-right order inside Hebrew text;
 * without the isolate marks the bidi algorithm flips it to "12–8".
 */
fun ltr(s: String): String = "\u2066$s\u2069"

/** 62.5 -> "62.5", 60.0 -> "60". */
fun kg(w: Double): String = if (w % 1.0 == 0.0) w.toInt().toString() else ((w * 100).toInt() / 100.0).toString()

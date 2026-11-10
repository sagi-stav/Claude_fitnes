package com.odplata.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

/** Plates to put on each side of a bar, heaviest first. */
fun platesPerSide(total: Double, bar: Double): List<Double> {
    var left = (total - bar) / 2
    val out = ArrayList<Double>()
    for (p in PLATES) while (left >= p - 1e-9) { out.add(p); left -= p }
    return out
}

fun plateColor(kg: Double): Color = when {
    kg >= 25 -> Iron.Red
    kg >= 20 -> Iron.Blue
    kg >= 15 -> Iron.Yellow
    kg >= 10 -> Iron.Green
    kg >= 5 -> Iron.White
    else -> Color(0xFF8A94A3)
}

/**
 * The signature element: a weight plate seen face-on, with the total load stamped on the hub.
 * [rim] is the small number moulded near the edge (the plate's own weight), if any.
 */
@Composable
fun PlateDisc(center: String, unit: String, color: Color, modifier: Modifier = Modifier, size: Dp = 216.dp, rim: String? = null, describe: String = "") {
    Box(modifier.size(size).semantics { contentDescription = describe }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            val c = Offset(r, r)
            val dark = lerp(color, Color.Black, 0.28f)
            val light = lerp(color, Color.White, 0.18f)
            drawCircle(Color.Black.copy(alpha = 0.45f), r * 0.98f, c + Offset(0f, r * 0.07f))
            drawCircle(Brush.verticalGradient(listOf(light, color, dark)), r * 0.97f, c)
            drawCircle(dark, r * 0.86f, c, style = Stroke(r * 0.03f))
            drawCircle(light.copy(alpha = 0.6f), r * 0.83f, c, style = Stroke(r * 0.012f))
            drawCircle(dark, r * 0.60f, c)
            drawCircle(color, r * 0.57f, c)
            drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.50f, c + Offset(0f, r * 0.02f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF1F3F5), Color(0xFF9AA2AD)), c - Offset(r * 0.15f, r * 0.2f), r * 0.75f), r * 0.48f, c)
        }
        if (rim != null) {
            Text(rim, Modifier.align(Alignment.TopCenter), color = Color.White.copy(alpha = 0.8f), style = Type.Display.copy(fontSize = (size.value * 0.12f).sp, lineHeight = (size.value * 0.22f).sp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, color = Iron.OnSteel, style = Type.Title.copy(fontSize = (size.value * (if (center.length > 3) 0.25f else 0.32f)).sp, lineHeight = (size.value * 0.27f).sp))
            if (unit.isNotEmpty()) Text(unit, color = Iron.OnSteel, style = Type.Small.copy(fontWeight = FontWeight.Bold, fontSize = (size.value * 0.055f).sp, lineHeight = (size.value * 0.06f).sp))
        }
    }
}

/** A small plate used as a coloured marker in lists. */
@Composable
fun PlateDot(color: Color, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = Offset(r, r)
        drawCircle(lerp(color, Color.Black, 0.3f), r, c)
        drawCircle(color, r * 0.9f, c)
        drawCircle(lerp(color, Color.Black, 0.25f), r * 0.62f, c, style = Stroke(r * 0.08f))
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.26f, c)
        drawCircle(Iron.Floor, r * 0.1f, c)
    }
}

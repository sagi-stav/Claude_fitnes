package com.odplata.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.odplata.app.data.Equip

/**
 * Equipment icons, drawn on a 24x24 grid with one stroke weight so they read as a family.
 * Solid parts are the heavy bits (plates, weight stacks); strokes are bars, cables and frames.
 */
@Composable
fun EquipIcon(equip: Equip, modifier: Modifier = Modifier, size: Dp = 28.dp, color: Color = Iron.Ink) {
    Canvas(modifier.size(size).semantics { contentDescription = equip.label }) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), 1.8f * u, StrokeCap.Round)
        fun block(x: Float, y: Float, w: Float, h: Float, r: Float = 1f) = drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u))
        fun ring(x: Float, y: Float, r: Float) = drawCircle(color, r * u, Offset(x * u, y * u), style = stroke)
        fun path(build: Path.() -> Unit) = drawPath(Path().apply(build), color, style = stroke)
        when (equip) {
            Equip.BARBELL -> {
                line(1.5f, 12f, 22.5f, 12f)
                block(4f, 5f, 2.6f, 14f); block(7.2f, 7.5f, 2f, 9f)
                block(17.4f, 5f, 2.6f, 14f); block(14.8f, 7.5f, 2f, 9f)
            }
            Equip.DUMBBELL -> {
                line(8f, 12f, 16f, 12f)
                block(3f, 6.5f, 5f, 11f, 1.6f); block(16f, 6.5f, 5f, 11f, 1.6f)
            }
            Equip.MACHINE -> {
                path { moveTo(5 * u, 21 * u); lineTo(5 * u, 3 * u); lineTo(19 * u, 3 * u); lineTo(19 * u, 21 * u) }
                block(8f, 6.5f, 8f, 2.6f, 0.6f); block(8f, 10.2f, 8f, 2.6f, 0.6f); block(8f, 13.9f, 8f, 2.6f, 0.6f); block(8f, 17.6f, 8f, 2.6f, 0.6f)
            }
            Equip.CABLE -> {
                ring(8f, 5.5f, 3f)
                path { moveTo(11 * u, 5.5f * u); lineTo(16 * u, 5.5f * u); lineTo(16 * u, 15 * u) }
                path { moveTo(12.5f * u, 20.5f * u); lineTo(16 * u, 15 * u); lineTo(19.5f * u, 20.5f * u); close() }
                line(5f, 8.5f, 5f, 21f)
            }
            Equip.SMITH -> {
                line(5f, 2.5f, 5f, 21.5f); line(19f, 2.5f, 19f, 21.5f)
                line(1.5f, 11f, 22.5f, 11f)
                block(7f, 7f, 2.2f, 8f); block(14.8f, 7f, 2.2f, 8f)
            }
            Equip.EZ_BAR -> {
                path {
                    moveTo(1.5f * u, 12 * u); lineTo(7 * u, 12 * u); lineTo(9.5f * u, 9.5f * u); lineTo(12 * u, 12 * u)
                    lineTo(14.5f * u, 9.5f * u); lineTo(17 * u, 12 * u); lineTo(22.5f * u, 12 * u)
                }
                block(3.4f, 6.5f, 2.4f, 11f); block(18.2f, 6.5f, 2.4f, 11f)
            }
            Equip.KETTLEBELL -> {
                drawCircle(color, 6.5f * u, Offset(12 * u, 15 * u))
                path { moveTo(8.5f * u, 10 * u); lineTo(8.5f * u, 5.5f * u); quadraticTo(12 * u, 2 * u, 15.5f * u, 5.5f * u); lineTo(15.5f * u, 10 * u) }
            }
            Equip.BODY -> {
                drawCircle(color, 2.6f * u, Offset(12 * u, 4.6f * u))
                path { moveTo(5 * u, 10 * u); lineTo(19 * u, 10 * u) }
                path { moveTo(12 * u, 8.5f * u); lineTo(12 * u, 15 * u); moveTo(12 * u, 15 * u); lineTo(8 * u, 21.5f * u); moveTo(12 * u, 15 * u); lineTo(16 * u, 21.5f * u) }
            }
            Equip.BAND -> {
                drawOval(color, Offset(3 * u, 8 * u), Size(18 * u, 8 * u), style = stroke)
                line(3f, 12f, 1.5f, 12f); line(21f, 12f, 22.5f, 12f)
            }
            Equip.OTHER -> {
                ring(12f, 12f, 8.5f); ring(12f, 12f, 2.5f)
            }
        }
    }
}

private class Part(val muscle: String, val x: Float, val y: Float, val w: Float, val h: Float, val r: Float = 4f)

// A figure built from blocks, like a wooden artist's mannequin. Units: 100 wide, 200 tall.
private val FRONT = listOf(
    Part("neck", 44f, 25f, 12f, 7f, 2f),
    Part("traps", 34f, 30f, 32f, 5f, 2f),
    Part("shoulders", 19f, 32f, 15f, 15f, 7f), Part("shoulders", 66f, 32f, 15f, 15f, 7f),
    Part("chest", 34f, 36f, 15f, 17f), Part("chest", 51f, 36f, 15f, 17f),
    Part("biceps", 17f, 49f, 13f, 24f, 6f), Part("biceps", 70f, 49f, 13f, 24f, 6f),
    Part("forearms", 13f, 75f, 12f, 27f, 5f), Part("forearms", 75f, 75f, 12f, 27f, 5f),
    Part("abdominals", 37f, 55f, 26f, 34f),
    Part("abductors", 27f, 91f, 6f, 20f, 3f), Part("abductors", 67f, 91f, 6f, 20f, 3f),
    Part("quadriceps", 34f, 93f, 14f, 52f, 6f), Part("quadriceps", 52f, 93f, 14f, 52f, 6f),
    Part("adductors", 47f, 93f, 6f, 24f, 3f),
    Part("calves", 35f, 149f, 12f, 40f, 5f), Part("calves", 53f, 149f, 12f, 40f, 5f),
)
private val BACK = listOf(
    Part("neck", 44f, 25f, 12f, 7f, 2f),
    Part("traps", 37f, 30f, 26f, 15f),
    Part("shoulders", 19f, 32f, 15f, 15f, 7f), Part("shoulders", 66f, 32f, 15f, 15f, 7f),
    Part("middle back", 41f, 47f, 18f, 16f, 3f),
    Part("lats", 31f, 47f, 9f, 30f), Part("lats", 60f, 47f, 9f, 30f),
    Part("lower back", 41f, 65f, 18f, 23f, 3f),
    Part("triceps", 17f, 49f, 13f, 24f, 6f), Part("triceps", 70f, 49f, 13f, 24f, 6f),
    Part("forearms", 13f, 75f, 12f, 27f, 5f), Part("forearms", 75f, 75f, 12f, 27f, 5f),
    Part("glutes", 33f, 90f, 16f, 19f, 6f), Part("glutes", 51f, 90f, 16f, 19f, 6f),
    Part("hamstrings", 34f, 111f, 14f, 34f, 6f), Part("hamstrings", 52f, 111f, 14f, 34f, 6f),
    Part("calves", 35f, 149f, 12f, 40f, 5f), Part("calves", 53f, 149f, 12f, 40f, 5f),
)
private val FRONT_ONLY = setOf("chest", "biceps", "abdominals", "quadriceps", "adductors", "abductors")
private val BACK_ONLY = setOf("lats", "middle back", "lower back", "triceps", "glutes", "hamstrings", "traps")

private fun DrawScope.figure(parts: List<Part>, primary: Set<String>, secondary: Set<String>, ox: Float, s: Float) {
    val base = Color(0xFF454A53)
    drawCircle(base, 10f * s, Offset(ox + 50f * s, 13f * s))
    for (p in parts) {
        val c = when (p.muscle) {
            in primary -> Iron.Signal
            in secondary -> Color(0xFF8C7430)
            else -> base
        }
        drawRoundRect(c, Offset(ox + p.x * s, p.y * s), Size(p.w * s, p.h * s), CornerRadius(p.r * s))
    }
}

/**
 * Muscle map: the working muscle lit up on a block figure. Shows the front or the back view,
 * whichever holds the primary muscle, or both when [both] is set.
 */
@Composable
fun MuscleMap(primary: List<String>, secondary: List<String> = emptyList(), modifier: Modifier = Modifier, height: Dp = 44.dp, both: Boolean = false) {
    val p = primary.toSet()
    val s = secondary.toSet()
    val back = p.any { it in BACK_ONLY } && p.none { it in FRONT_ONLY }
    val views = if (both) listOf(FRONT, BACK) else listOf(if (back) BACK else FRONT)
    Canvas(modifier.size(width = height * 0.5f * views.size, height = height).semantics { contentDescription = "Muscle map" }) {
        val scale = size.height / 200f
        views.forEachIndexed { i, v -> figure(v, p, s, i * 100f * scale, scale) }
    }
}

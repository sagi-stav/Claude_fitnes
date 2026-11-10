package com.odplata.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odplata.app.AppLogic
import com.odplata.app.Phase
import com.odplata.app.Screen
import com.odplata.app.data.ActiveExercise
import com.odplata.app.data.Equip
import com.odplata.app.data.ExerciseDef
import com.odplata.engine.Action
import com.odplata.engine.Decision
import com.odplata.engine.Feel
import kotlinx.coroutines.delay

@Composable
fun WorkoutScreen(app: AppLogic) {
    val now = app.clock
    val active = app.data.active ?: return
    val cur = app.current ?: return
    val def = app.exercise(cur.exerciseId)
    var confirmEnd by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    IronScreen {
        TopBar(active.name, "Exercise ${active.index + 1} of ${active.exercises.size}") {
            if (confirmEnd) {
                TextLink("Finish and save", color = Iron.Signal) { app.finish() }
                TextLink("No") { confirmEnd = false }
            } else TextLink("Finish") { confirmEnd = true }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            active.exercises.forEachIndexed { i, e ->
                val color = when {
                    i == active.index -> Iron.Steel
                    e.done.size >= e.sets -> Iron.Green
                    e.done.isNotEmpty() -> Iron.Yellow
                    else -> Iron.Line
                }
                Canvas(Modifier.weight(1f).height(18.dp).clickable(role = Role.Button, onClickLabel = "Go to exercise ${i + 1}") { app.jumpTo(i) }) {
                    drawRoundRect(color, Offset(0f, 6.dp.toPx()), Size(size.width, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                }
            }
        }
        when (val phase = app.phase) {
            Phase.Lift -> Lift(app, cur, def)
            is Phase.Ask -> Ask(app, phase, def)
            is Phase.Rest -> Rest(app, phase, cur, def, now)
        }
    }
}

private fun discColor(def: ExerciseDef, weight: Double, bar: Double): Color = when {
    def.equip.plateLoaded -> platesPerSide(weight, bar).firstOrNull()?.let(::plateColor) ?: Color(0xFF5A6370)
    def.equip.perHand -> plateColor(weight)
    def.equip == Equip.BODY -> Color(0xFF5A6370)
    else -> Color(0xFF4A5059)
}

private fun loadLine(def: ExerciseDef, weight: Double, bar: Double): String = when {
    def.equip.plateLoaded -> {
        val plates = platesPerSide(weight, bar)
        if (plates.isEmpty()) "Empty bar, ${kg(bar)} kg" else plates.joinToString(" + ") { kg(it) } + " per side · bar ${kg(bar)}"
    }
    def.equip.perHand -> "${kg(weight)} kg in each hand"
    def.equip == Equip.BODY -> "Bodyweight"
    else -> def.equip.label
}

@Composable
private fun ColumnScope.Lift(app: AppLogic, cur: ActiveExercise, def: ExerciseDef) {
    val decision = remember(cur.exerciseId, cur.done.size, app.data.grids[def.id]) { app.decision(cur) }
    val grid = app.grid(def)
    var weight by remember(cur.exerciseId, cur.done.size) { mutableStateOf(decision.load) }
    var reps by remember(cur.exerciseId, cur.done.size) { mutableStateOf(decision.targetReps) }
    val last = remember(cur.exerciseId) { app.lastTime(cur.exerciseId) }
    val body = def.equip == Equip.BODY
    fun move(steps: Int) { weight = (weight + steps * grid.step).coerceIn(grid.min, grid.max) }

    Raised(Modifier.fillMaxWidth(), onClick = { app.go(Screen.Info(def.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExerciseMark(def)
            Column(Modifier.weight(1f)) {
                Text(def.name, style = Type.Strong, maxLines = 2)
                Text("Set ${cur.done.size + 1} of ${cur.sets} · target ${cur.repLo}–${cur.repHi}", color = Iron.Muted, style = Type.Small)
            }
        }
    }
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        if (!body) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Key("+") { move(1) }; Key("−") { move(-1) } } else Spacer(Modifier.width(52.dp))
        PlateDisc(
            center = if (body) "BW" else kg(weight), unit = if (body) "" else "kg",
            color = discColor(def, weight, grid.min),
            rim = if (def.equip.plateLoaded) platesPerSide(weight, grid.min).firstOrNull()?.let(::kg) else null,
            size = 204.dp, describe = if (body) "Bodyweight" else "${kg(weight)} kilograms",
        )
        if (!body) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Key("+" + kg(grid.step * 4)) { move(4) }; Key("−" + kg(grid.step * 4)) { move(-4) }
        } else Spacer(Modifier.width(52.dp))
    }
    Text(loadLine(def, weight, grid.min), Modifier.fillMaxWidth(), color = Iron.Muted, style = Type.Small, textAlign = TextAlign.Center)
    Spacer(Modifier.weight(1f))
    Well {
        Text(verdict(decision, def), style = Type.Strong, color = Iron.Signal)
        Text(reasonText(decision.reason, cur.repLo, decision.repsToUnlock), style = Type.Small)
        if (last != null && cur.done.isEmpty()) Text("Last time: " + last.joinToString("  ") { "${kg(it.weight)}×${it.reps}" }, color = Iron.Muted, style = Type.Small)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Key("−") { reps = maxOf(0, reps - 1) }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(reps.toString(), style = Type.Title.copy(fontSize = 52.sp, lineHeight = 48.sp))
            Text("reps", Modifier.padding(bottom = 6.dp), color = Iron.Muted)
        }
        Key("+") { reps = minOf(50, reps + 1) }
    }
    SteelButton("Set done") { app.setDone(weight, reps) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextLink(if (cur.done.isEmpty()) "Skip this exercise" else "Next exercise") { app.nextExercise() }
    }
}

private fun verdict(d: Decision, def: ExerciseDef): String {
    val what = if (def.equip == Equip.BODY) "${d.targetReps} reps" else "${kg(d.load)} kg × ${d.targetReps}"
    return if (d.action == Action.START) "Pick a starting weight" else "${actionText(d.action)}: $what"
}

@Composable
private fun ColumnScope.Ask(app: AppLogic, ask: Phase.Ask, def: ExerciseDef) {
    Text(if (def.equip == Equip.BODY) "${ask.reps} reps" else "${kg(ask.weight)} kg × ${ask.reps}", color = Iron.Muted, style = Type.Small)
    Text("How was it?", style = Type.Title.copy(fontSize = 52.sp, lineHeight = 48.sp))
    Spacer(Modifier.weight(1f))
    for (feel in Feel.entries) {
        Raised(Modifier.fillMaxWidth().defaultMinSize(minHeight = 68.dp), onClick = { app.answer(feel) }) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlateDot(feel.color, size = 36.dp)
                Column {
                    Text(feel.label, style = Type.Strong.copy(fontSize = 18.sp))
                    Text(feel.sub, color = Iron.Muted, style = Type.Small)
                }
            }
        }
    }
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { TextLink("Back to fix the weight or reps") { app.cancelAsk() } }
}

@Composable
private fun ColumnScope.Rest(app: AppLogic, rest: Phase.Rest, cur: ActiveExercise, def: ExerciseDef, now: () -> Long) {
    var left by remember(rest) { mutableStateOf(maxOf(0L, rest.until - now())) }
    val context = LocalContext.current
    LaunchedEffect(rest) {
        while (left > 0) {
            delay(250)
            left = maxOf(0L, rest.until - now())
        }
        buzz(context)
    }
    val active = app.data.active
    val exerciseDone = cur.done.size >= cur.sets
    val lastExercise = active != null && active.index >= active.exercises.size - 1
    val sec = ((left + 999) / 1000).toInt()

    Text(if (left > 0) "Rest" else "Ready when you are", color = Iron.Muted, style = Type.Small)
    Spacer(Modifier.weight(1f))
    Box(Modifier.align(Alignment.CenterHorizontally).size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 12.dp.toPx()
            val inset = Offset(w / 2, w / 2)
            val arc = Size(size.width - w, size.height - w)
            drawArc(Iron.Well, 0f, 360f, false, inset, arc, style = Stroke(w))
            drawArc(Iron.Steel, -90f, 360f * (left.toFloat() / (rest.total * 1000f)).coerceIn(0f, 1f), false, inset, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("%d:%02d".format(sec / 60, sec % 60), style = Type.Title.copy(fontSize = 76.sp, lineHeight = 70.sp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { TextLink("+30 seconds") { app.addRest(30) } }
    Spacer(Modifier.weight(1f))
    Well {
        if (!exerciseDone) {
            val d = remember(cur.done.size) { app.decision(cur) }
            Text("Next set: " + if (def.equip == Equip.BODY) "${d.targetReps} reps" else "${kg(d.load)} kg × ${d.targetReps}", style = Type.Strong, color = Iron.Signal)
            Text(reasonText(d.reason, cur.repLo, d.repsToUnlock), style = Type.Small)
        } else if (!lastExercise && active != null) {
            Text("Next exercise: " + app.exercise(active.exercises[active.index + 1].exerciseId).name, style = Type.Strong, color = Iron.Signal)
            Text("All sets of ${def.name} are done.", style = Type.Small)
        } else {
            Text("That was the last exercise", style = Type.Strong, color = Iron.Signal)
            Text("The summary shows what changes next time.", style = Type.Small)
        }
    }
    SteelButton(if (!exerciseDone) "Next set" else if (lastExercise) "Finish workout" else "Next exercise") { app.continueAfterRest() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        if (exerciseDone) TextLink("One more set of this exercise") { app.extraSet() }
    }
}

private fun buzz(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
    }
}

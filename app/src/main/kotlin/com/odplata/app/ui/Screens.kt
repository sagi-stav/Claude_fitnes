package com.odplata.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odplata.app.AppLogic
import com.odplata.app.Screen
import com.odplata.app.data.Equip
import com.odplata.app.data.Experience
import com.odplata.app.data.Goal
import com.odplata.app.data.MuscleGroup
import com.odplata.app.data.Profile
import com.odplata.engine.Action

// ------------------------------------------------------------------ profile

@Composable
fun ProfileScreen(app: AppLogic, first: Boolean) {
    val initial = app.data.profile ?: Profile()
    var name by remember { mutableStateOf(initial.name) }
    var age by remember { mutableStateOf(initial.age.toString()) }
    var height by remember { mutableStateOf(initial.heightCm.toString()) }
    var weight by remember { mutableStateOf(kg(initial.weightKg)) }
    var goal by remember { mutableStateOf(initial.goal) }
    var exp by remember { mutableStateOf(initial.experience) }
    var perWeek by remember { mutableStateOf(initial.perWeek) }
    var rest by remember { mutableStateOf(initial.restSec) }
    val valid = age.toIntOrNull() in 10..99 && height.toIntOrNull() in 100..230 && (weight.toDoubleOrNull() ?: 0.0) in 30.0..250.0

    IronScreen {
        if (first) TopBar("One More Plate") else TopBar("Settings", onBack = { app.back() })
        Text(if (first) "First, who are you" else "My details", style = Type.Title)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Name (optional)", name) { name = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Age", age, Modifier.weight(1f), number = true) { age = it }
                Field("Height, cm", height, Modifier.weight(1f), number = true) { height = it }
                Field("Weight, kg", weight, Modifier.weight(1f), number = true) { weight = it }
            }
            Text("Goal", color = Iron.Muted, style = Type.Small)
            Chips(Goal.entries, goal, { it.label }) { goal = it }
            Text("Experience", color = Iron.Muted, style = Type.Small)
            Chips(Experience.entries, exp, { it.label }) { exp = it }
            Text("Workouts per week", color = Iron.Muted, style = Type.Small)
            Chips(listOf(2, 3, 4, 5, 6), perWeek, { it.toString() }) { perWeek = it }
            Text("Rest between sets", color = Iron.Muted, style = Type.Small)
            Chips(listOf(60, 90, 120, 180), rest, { "%d:%02d".format(it / 60, it % 60) }) { rest = it }
            Text("The goal sets the rep range suggested for each new exercise. You can change it per exercise.", color = Iron.Muted, style = Type.Small)
        }
        SteelButton(if (first) "Continue" else "Save", enabled = valid) {
            app.saveProfile(initial.copy(name = name.trim(), age = age.toInt(), heightCm = height.toInt(), weightKg = weight.toDouble(),
                goal = goal, experience = exp, perWeek = perWeek, restSec = rest))
        }
    }
}

// ------------------------------------------------------------------ home

@Composable
fun HomeScreen(app: AppLogic, today: Long) {
    val profile = app.data.profile ?: return
    val done = app.doneThisWeek()
    val next = app.nextUp()
    IronScreen {
        TopBar(dayText(today), "This week $done of ${profile.perWeek}")
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(maxOf(profile.perWeek, done)) { i ->
                Canvas(Modifier.weight(1f).height(8.dp)) {
                    drawRoundRect(if (i < done) Iron.Green else Iron.Line, cornerRadius = CornerRadius(4.dp.toPx()))
                }
            }
        }
        Text(if (profile.name.isNotEmpty()) "${profile.name}, what are we training?" else "What are we training?", style = Type.Title)
        if (app.data.active != null) {
            Raised(Modifier.fillMaxWidth(), onClick = { app.go(Screen.Workout) }) {
                Column {
                    Text("Workout in progress: ${app.data.active?.name}", style = Type.Strong, color = Iron.Signal)
                    Text("Tap to continue where you stopped", color = Iron.Muted, style = Type.Small)
                }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
            if (app.data.workouts.isEmpty()) item {
                Well {
                    Text("No workouts yet", style = Type.Strong)
                    Text("Build a workout once: name it, pick exercises, set the sets and rep range. From then on the app remembers your weights and decides when to go up.", color = Iron.Muted, style = Type.Small)
                }
            }
            itemsIndexed(app.data.workouts, key = { _, w -> w.id }) { i, w ->
                val isNext = w.id == next?.id && app.data.active == null
                Raised(Modifier.fillMaxWidth(), onClick = { app.go(Screen.Editor(w.id)) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(w.name, style = Type.Strong)
                                Text("${w.exercises.size} exercises · " + if (isNext) "next up" else agoText(app.lastDone(w.id)), color = if (isNext) Iron.Signal else Iron.Muted, style = Type.Small)
                            }
                            Text("ABCDEFGH".getOrElse(i) { '·' }.toString(), style = Type.Title, color = if (isNext) Iron.Ink else Iron.Line)
                        }
                        if (isNext) SteelButton("Start workout") { app.start(w.id) }
                    }
                }
            }
        }
        DarkButton("+ New workout", Modifier.fillMaxWidth()) { app.newWorkout() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextLink("History") { app.go(Screen.History) }
            TextLink("Settings") { app.go(Screen.Settings) }
        }
    }
}

// ------------------------------------------------------------------ workout editor

@Composable
fun EditorScreen(app: AppLogic, workoutId: String) {
    val w = app.workout(workoutId) ?: return
    var open by remember { mutableStateOf(-1) }
    var confirmDelete by remember { mutableStateOf(false) }
    IronScreen {
        TopBar("Edit workout", onBack = { app.back() })
        Field("Workout name", w.name, style = Type.Display) { v -> app.editWorkout(w.id) { it.copy(name = v) } }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
            if (w.exercises.isEmpty()) item {
                Well { Text("This workout is empty. Add the first exercise.", color = Iron.Muted) }
            }
            itemsIndexed(w.exercises) { i, e ->
                val def = app.exercise(e.exerciseId)
                fun change(f: (com.odplata.app.data.PlanExercise) -> com.odplata.app.data.PlanExercise) =
                    app.editWorkout(w.id) { it.copy(exercises = it.exercises.mapIndexed { j, x -> if (j == i) f(x) else x }) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExerciseRow(def, "${e.sets} sets · ${e.repLo}–${e.repHi} reps · ${def.equip.label}") { open = if (open == i) -1 else i }
                    if (open == i) Well {
                        Stepper("Sets", e.sets.toString(), onMinus = { change { it.copy(sets = maxOf(1, it.sets - 1)) } }, onPlus = { change { it.copy(sets = minOf(8, it.sets + 1)) } })
                        Stepper("Minimum reps", e.repLo.toString(), onMinus = { change { it.copy(repLo = maxOf(1, it.repLo - 1)) } }, onPlus = { change { it.copy(repLo = minOf(it.repHi - 1, it.repLo + 1)) } })
                        Stepper("Maximum reps", e.repHi.toString(), onMinus = { change { it.copy(repHi = maxOf(it.repLo + 1, it.repHi - 1)) } }, onPlus = { change { it.copy(repHi = minOf(30, it.repHi + 1)) } })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextLink("Details") { app.go(Screen.Info(def.id)) }
                            if (i > 0) TextLink("Move up") {
                                app.editWorkout(w.id) { it.copy(exercises = it.exercises.toMutableList().apply { add(i - 1, removeAt(i)) }) }; open = i - 1
                            }
                            TextLink("Remove", color = Iron.Red) {
                                app.editWorkout(w.id) { it.copy(exercises = it.exercises.filterIndexed { j, _ -> j != i }) }; open = -1
                            }
                        }
                    }
                }
            }
        }
        DarkButton("+ Add exercise", Modifier.fillMaxWidth()) { app.go(Screen.Picker(w.id)) }
        SteelButton("Start workout", enabled = w.exercises.isNotEmpty() && app.data.active == null) { app.start(w.id) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            if (confirmDelete) {
                TextLink("Yes, delete this workout", color = Iron.Red) { app.deleteWorkout(w.id) }
                TextLink("Cancel") { confirmDelete = false }
            } else TextLink("Delete workout") { confirmDelete = true }
        }
    }
}

// ------------------------------------------------------------------ exercise picker

private val GROUP_MUSCLE = mapOf(
    MuscleGroup.CHEST to "chest", MuscleGroup.BACK to "lats", MuscleGroup.SHOULDERS to "shoulders", MuscleGroup.BICEPS to "biceps",
    MuscleGroup.TRICEPS to "triceps", MuscleGroup.LEGS to "quadriceps", MuscleGroup.ABS to "abdominals", MuscleGroup.FOREARMS to "forearms",
)

@Composable
fun PickerScreen(app: AppLogic, workoutId: String) {
    var group by remember { mutableStateOf<MuscleGroup?>(null) }
    var equip by remember { mutableStateOf<Equip?>(null) }
    var query by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customEquip by remember { mutableStateOf(Equip.MACHINE) }
    val results = remember(query) { app.search(query) }
    val g = group

    IronScreen {
        TopBar(if (g == null) "Choose exercise" else g.label, onBack = { if (g != null && query.length < 2) { group = null; equip = null; custom = false } else app.back() })
        Field("Search exercises", query) { query = it }
        when {
            query.trim().length >= 2 -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
                if (results.isEmpty()) item { Text("No exercise matches. You can add your own from inside a muscle group.", color = Iron.Muted) }
                items(results, key = { it.id }) { def ->
                    ExerciseRow(def, def.equip.label) { app.addToWorkout(workoutId, def) }
                }
            }
            g == null -> {
                Text("Which muscle?", style = Type.Title)
                LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(MuscleGroup.entries) { m ->
                        Raised(Modifier.fillMaxWidth(), onClick = { group = m }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MuscleMap(listOf(GROUP_MUSCLE.getValue(m)), height = 56.dp)
                                Text(m.label, style = Type.Strong)
                            }
                        }
                    }
                }
            }
            else -> {
                val all = remember(g, app.data.custom) { app.exercises(g) }
                val kinds = remember(all) { Equip.entries.filter { e -> all.any { it.equip == e } } }
                Chips(kinds, equip, { it.label }, scroll = true) { equip = if (equip == it) null else it }
                val shown = all.filter { equip == null || it.equip == equip }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
                    items(shown, key = { it.id }) { def ->
                        ExerciseRow(def, def.equip.label) { app.addToWorkout(workoutId, def) }
                    }
                }
                if (custom) Well {
                    Field("Exercise name", customName) { customName = it }
                    Chips(listOf(Equip.MACHINE, Equip.CABLE, Equip.BARBELL, Equip.DUMBBELL, Equip.BODY), customEquip, { it.label }, scroll = true) { customEquip = it }
                    SteelButton("Add to workout", enabled = customName.trim().length >= 2) {
                        app.addToWorkout(workoutId, app.addCustom(customName.trim(), customEquip, g))
                    }
                } else DarkButton("+ My own ${g.label.lowercase()} exercise", Modifier.fillMaxWidth()) { custom = true }
            }
        }
    }
}

// ------------------------------------------------------------------ exercise details

@Composable
fun InfoScreen(app: AppLogic, exerciseId: String) {
    val def = app.exercise(exerciseId)
    val grid = app.grid(def)
    val history = remember(app.data.sessions) { app.history(exerciseId) }
    IronScreen {
        TopBar(def.equip.label, onBack = { app.back() })
        Text(def.name, style = Type.Display)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MuscleMap(def.primary, def.secondary, height = 120.dp, both = true)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EquipIcon(def.equip, size = 40.dp)
                    Text(def.equip.label, style = Type.Strong)
                    Text("Yellow: the main muscle", color = Iron.Muted, style = Type.Small)
                }
            }
            if (def.images.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                def.images.forEach { AssetPhoto(it, Modifier.weight(1f).aspectRatio(1.5f)) }
            }
            if (def.equip != Equip.BODY) Well {
                Text("Weights on this equipment", style = Type.Strong)
                Text("The app only suggests weights that really exist. If the step in your gym is different, change it here.", color = Iron.Muted, style = Type.Small)
                Stepper("Step, kg", kg(grid.step), onMinus = { app.setGrid(def.id, grid.copy(step = maxOf(0.5, grid.step - 0.5))) }, onPlus = { app.setGrid(def.id, grid.copy(step = grid.step + 0.5)) })
                Stepper("Lightest weight, kg", kg(grid.min), onMinus = { app.setGrid(def.id, grid.copy(min = maxOf(0.0, grid.min - 0.5))) }, onPlus = { app.setGrid(def.id, grid.copy(min = grid.min + 0.5)) })
            }
            if (history.size >= 2) Well {
                Text("Your estimated strength", style = Type.Strong)
                Text("Estimated one-rep max: ${kg(Math.round(history.first().second).toDouble())} → ${kg(Math.round(history.last().second).toDouble())} kg over ${history.size} sessions", color = Iron.Muted, style = Type.Small)
                Canvas(Modifier.fillMaxWidth().height(90.dp).padding(top = 8.dp)) {
                    val lo = history.minOf { it.second }
                    val hi = maxOf(history.maxOf { it.second }, lo + 1)
                    val path = Path()
                    history.forEachIndexed { i, (_, v) ->
                        val x = i * size.width / (history.size - 1)
                        val y = size.height - ((v - lo) / (hi - lo)).toFloat() * size.height
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        if (i == history.size - 1) drawCircle(Iron.Signal, 5.dp.toPx(), Offset(x, y))
                    }
                    drawPath(path, Iron.Signal, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            if (def.steps.isNotEmpty()) {
                Text("How to do it", style = Type.Strong)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    def.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", color = Iron.Muted, style = Type.Small) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ summary and history

@Composable
fun SummaryScreen(app: AppLogic, sessionId: String) {
    val s = app.session(sessionId) ?: return
    IronScreen {
        TopBar("${s.name} · ${dayText(s.day)}", "${maxOf(1, s.durationSec / 60)} min · ${s.exercises.sumOf { it.sets.size }} sets")
        Text("What changes next time", style = Type.Title)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
            items(s.changes) { c ->
                val def = app.exercise(c.exerciseId)
                val sets = s.exercises.firstOrNull { it.exerciseId == c.exerciseId }?.sets.orEmpty()
                val lo = app.workout(s.workoutId)?.exercises?.firstOrNull { it.exerciseId == c.exerciseId }?.repLo ?: 8
                Raised(Modifier.fillMaxWidth(), onClick = { app.go(Screen.Info(def.id)) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            EquipIcon(def.equip, size = 22.dp)
                            Text(def.name, Modifier.weight(1f), style = Type.Strong, maxLines = 2)
                            val color = when (c.action) { Action.UP -> Iron.Green; Action.DOWN, Action.DELOAD -> Iron.Red; else -> Iron.Ink }
                            Text(if (def.equip == Equip.BODY) "${c.nextReps} reps" else if (c.nextLoad == c.load) kg(c.load) else "${kg(c.load)} → ${kg(c.nextLoad)}", color = color, style = Type.Display)
                        }
                        Text("Today: " + sets.joinToString("  ") { "${kg(it.weight)}×${it.reps}" }, color = Iron.Muted, style = Type.Small)
                        Text(reasonText(c.reason, lo, c.unlock), style = Type.Small)
                    }
                }
            }
        }
        SteelButton("Done") { app.back() }
    }
}

@Composable
fun HistoryScreen(app: AppLogic) {
    IronScreen {
        TopBar("History", "${app.data.sessions.size} workouts", onBack = { app.back() })
        Text("Workouts you did", style = Type.Title)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
            if (app.data.sessions.isEmpty()) item { Well { Text("Your workouts will appear here after you finish the first one.", color = Iron.Muted) } }
            items(app.data.sessions.reversed(), key = { it.id }) { s ->
                Raised(Modifier.fillMaxWidth(), onClick = { app.go(Screen.Summary(s.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = Type.Strong)
                            Text("${dayText(s.day)} · ${maxOf(1, s.durationSec / 60)} min", color = Iron.Muted, style = Type.Small)
                        }
                        Text("${s.exercises.sumOf { it.sets.size }} sets", color = Iron.Muted, style = Type.Small)
                    }
                }
            }
        }
    }
}

package com.odplata.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.odplata.app.data.ActiveExercise
import com.odplata.app.data.ActiveSession
import com.odplata.app.data.AppData
import com.odplata.app.data.Catalog
import com.odplata.app.data.Change
import com.odplata.app.data.Equip
import com.odplata.app.data.ExerciseDef
import com.odplata.app.data.Goal
import com.odplata.app.data.LoggedExercise
import com.odplata.app.data.MuscleGroup
import com.odplata.app.data.PlanExercise
import com.odplata.app.data.Profile
import com.odplata.app.data.Session
import com.odplata.app.data.Store
import com.odplata.app.data.Workout
import com.odplata.engine.Decision
import com.odplata.engine.ExerciseConfig
import com.odplata.engine.Feel
import com.odplata.engine.LoadGrid
import com.odplata.engine.Progression
import com.odplata.engine.SetObs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data class Editor(val workoutId: String) : Screen
    data class Picker(val workoutId: String) : Screen
    data class Info(val exerciseId: String) : Screen
    data object Workout : Screen
    data class Summary(val sessionId: String) : Screen
    data object History : Screen
    data object Settings : Screen
}

/** Where the lifter is inside the current exercise. */
sealed interface Phase {
    data object Lift : Phase
    data class Ask(val weight: Double, val reps: Int) : Phase
    data class Rest(val until: Long, val total: Int) : Phase
}

/** Pure app logic over [AppData]; kept free of Android types so it can be unit-tested. */
class AppLogic(
    val catalog: List<ExerciseDef>,
    initial: AppData,
    private val persist: (AppData) -> Unit = {},
    private val today: () -> Long = { LocalDate.now().toEpochDay() },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /** Wall clock, shared with the rest timer on screen. */
    val clock: () -> Long get() = now
    val todayDay: Long get() = today()
    var data by mutableStateOf(initial)
        private set
    var stack by mutableStateOf(listOf(if (initial.profile == null) Screen.Onboarding else if (initial.active != null) Screen.Workout else Screen.Home))
        private set
    var phase by mutableStateOf<Phase>(Phase.Lift)
        private set

    val screen get() = stack.last()
    private val byId = catalog.associateBy { it.id }

    private fun update(f: (AppData) -> AppData) {
        data = f(data)
        persist(data)
    }

    // ------------------------------------------------------------ navigation

    fun go(s: Screen) { stack = stack + s }
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        return true
    }
    private fun reset(vararg s: Screen) { stack = s.toList() }

    // ------------------------------------------------------------ catalogue

    fun exercise(id: String): ExerciseDef =
        byId[id] ?: data.custom.firstOrNull { it.id == id }
        ?: ExerciseDef(id, "Deleted exercise", equip = Equip.OTHER, primary = listOf("chest"))

    fun exercises(group: MuscleGroup): List<ExerciseDef> = (data.custom + catalog).filter { it.group == group }

    fun search(query: String): List<ExerciseDef> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return (data.custom + catalog).filter { q in it.en.lowercase() }
    }

    fun addCustom(name: String, equip: Equip, group: MuscleGroup): ExerciseDef {
        val def = ExerciseDef("custom-" + UUID.randomUUID(), name, true, equip, listOf(group.muscles.first()), custom = true)
        update { it.copy(custom = it.custom + def) }
        return def
    }

    fun grid(def: ExerciseDef): LoadGrid = data.grids[def.id] ?: def.equip.defaultGrid(data.profile?.weightKg ?: 75.0)

    fun setGrid(id: String, grid: LoadGrid) = update { it.copy(grids = it.grids + (id to grid)) }

    // ------------------------------------------------------------ profile and workouts

    fun saveProfile(p: Profile) {
        val first = data.profile == null
        update { it.copy(profile = p) }
        if (first) reset(Screen.Home) else back()
    }

    fun defaultRange(def: ExerciseDef): Pair<Int, Int> = when (data.profile?.goal ?: Goal.MUSCLE) {
        Goal.MUSCLE -> if (def.compound) 8 to 12 else 10 to 15
        Goal.STRENGTH -> if (def.compound) 4 to 6 else 8 to 12
        Goal.TONE -> if (def.compound) 12 to 15 else 12 to 20
    }

    fun workout(id: String) = data.workouts.firstOrNull { it.id == id }

    fun newWorkout() {
        val w = Workout(UUID.randomUUID().toString(), "Workout " + "ABCDEFGH".getOrElse(data.workouts.size) { 'X' })
        update { it.copy(workouts = it.workouts + w) }
        go(Screen.Editor(w.id))
    }

    fun editWorkout(id: String, f: (Workout) -> Workout) =
        update { d -> d.copy(workouts = d.workouts.map { if (it.id == id) f(it) else it }) }

    fun deleteWorkout(id: String) {
        update { d -> d.copy(workouts = d.workouts.filterNot { it.id == id }) }
        back()
    }

    fun addToWorkout(workoutId: String, def: ExerciseDef) {
        val (lo, hi) = defaultRange(def)
        editWorkout(workoutId) { it.copy(exercises = it.exercises + PlanExercise(def.id, 3, lo, hi)) }
        // Back to the editor, wherever in the picker we were.
        stack = stack.dropLastWhile { it !is Screen.Editor }
    }

    /** The workout that follows the one trained most recently. */
    fun nextUp(): Workout? {
        val list = data.workouts.filter { it.exercises.isNotEmpty() }
        if (list.isEmpty()) return null
        val last = data.sessions.lastOrNull()?.workoutId
        val i = list.indexOfFirst { it.id == last }
        return list[(i + 1) % list.size]
    }

    fun lastDone(workoutId: String): Long? = data.sessions.lastOrNull { it.workoutId == workoutId }?.let { today() - it.day }

    fun doneThisWeek(): Int {
        val start = LocalDate.ofEpochDay(today()).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).toEpochDay()
        return data.sessions.count { it.day >= start }
    }

    // ------------------------------------------------------------ training

    private fun config(a: ActiveExercise) = ExerciseConfig(a.repLo, a.repHi, a.sets, grid(exercise(a.exerciseId)))

    fun start(workoutId: String) {
        val w = workout(workoutId) ?: return
        if (w.exercises.isEmpty()) return
        val active = ActiveSession(w.id, w.name, now(), w.exercises.map { ActiveExercise(it.exerciseId, it.sets, it.repLo, it.repHi) })
        update { it.copy(active = active) }
        phase = Phase.Lift
        go(Screen.Workout)
    }

    val current: ActiveExercise? get() = data.active?.let { it.exercises.getOrNull(it.index) }

    /** What the engine prescribes for the set about to be done. */
    fun decision(a: ActiveExercise): Decision {
        val state = data.states[a.exerciseId]
        val cfg = config(a)
        return if (a.done.isEmpty()) Progression.plan(state, cfg, today()) else Progression.nextSet(state, cfg, a.done, today())
    }


    fun lastTime(exerciseId: String): List<SetObs>? =
        data.sessions.lastOrNull { s -> s.exercises.any { it.exerciseId == exerciseId } }
            ?.exercises?.first { it.exerciseId == exerciseId }?.sets

    fun setDone(weight: Double, reps: Int) { phase = Phase.Ask(weight, reps) }

    fun cancelAsk() { phase = Phase.Lift }

    fun answer(feel: Feel) {
        val ask = phase as? Phase.Ask ?: return
        val active = data.active ?: return
        val obs = SetObs(ask.weight, ask.reps, feel)
        update { d ->
            d.copy(active = active.copy(exercises = active.exercises.mapIndexed { i, e -> if (i == active.index) e.copy(done = e.done + obs) else e }))
        }
        val base = data.profile?.restSec ?: 90
        val rest = base + if (feel == Feel.HARD || feel == Feel.FAIL) 30 else 0
        phase = Phase.Rest(now() + rest * 1000L, rest)
    }

    fun addRest(sec: Int) {
        val r = phase as? Phase.Rest ?: return
        phase = Phase.Rest(r.until + sec * 1000L, r.total + sec)
    }

    /** Leaves the rest screen: either the next set of this exercise or the next exercise. */
    fun continueAfterRest() {
        val active = data.active ?: return
        val cur = active.exercises[active.index]
        if (cur.done.size >= cur.sets) nextExercise() else phase = Phase.Lift
    }

    fun extraSet() {
        val active = data.active ?: return
        update { d -> d.copy(active = active.copy(exercises = active.exercises.mapIndexed { i, e -> if (i == active.index) e.copy(sets = e.sets + 1) else e })) }
        phase = Phase.Lift
    }

    fun nextExercise() {
        val active = data.active ?: return
        if (active.index >= active.exercises.size - 1) finish() else {
            update { it.copy(active = active.copy(index = active.index + 1)) }
            phase = Phase.Lift
        }
    }

    fun jumpTo(index: Int) {
        val active = data.active ?: return
        update { it.copy(active = active.copy(index = index)) }
        phase = Phase.Lift
    }

    fun abandon() {
        update { it.copy(active = null) }
        phase = Phase.Lift
        reset(Screen.Home)
    }

    fun finish() {
        val active = data.active ?: return
        val day = today()
        val states = data.states.toMutableMap()
        val changes = ArrayList<Change>()
        val logged = ArrayList<LoggedExercise>()
        for (a in active.exercises) {
            if (a.done.isEmpty()) continue
            val cfg = config(a)
            val state = Progression.finish(states[a.exerciseId], cfg, a.done, day) ?: continue
            states[a.exerciseId] = state
            val next = Progression.plan(state, cfg, day)
            changes.add(Change(a.exerciseId, a.done.first().weight, next.load, next.targetReps, next.action, next.reason, next.repsToUnlock))
            logged.add(LoggedExercise(a.exerciseId, a.done))
        }
        if (logged.isEmpty()) return abandon()
        val session = Session(UUID.randomUUID().toString(), active.workoutId, active.name, day, active.startedAt,
            ((now() - active.startedAt) / 1000).toInt(), logged, changes)
        update { it.copy(active = null, states = states, sessions = it.sessions + session) }
        phase = Phase.Lift
        reset(Screen.Home, Screen.Summary(session.id))
    }

    fun session(id: String) = data.sessions.firstOrNull { it.id == id }

    /** Best estimated one-rep max of each past session for one exercise, oldest first. */
    fun history(exerciseId: String): List<Pair<Long, Double>> = data.sessions.mapNotNull { s ->
        val sets = s.exercises.firstOrNull { it.exerciseId == exerciseId }?.sets ?: return@mapNotNull null
        s.day to sets.maxOf { Progression.e1rm(it.weight, it.reps + it.feel.rir) }
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store.of(app)
    val logic = AppLogic(Catalog.load(app), store.load(), store::save)
}

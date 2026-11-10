package com.odplata.app.data

import com.odplata.engine.Action
import com.odplata.engine.ExerciseState
import com.odplata.engine.LoadGrid
import com.odplata.engine.Reason
import com.odplata.engine.SetObs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Goal(val label: String) { MUSCLE("Muscle"), STRENGTH("Strength"), TONE("Toning") }
enum class Experience(val label: String) { NEW("Beginner"), MID("1–3 years"), VETERAN("Veteran") }

@Serializable
data class Profile(
    val name: String = "",
    val age: Int = 25,
    val heightCm: Int = 175,
    val weightKg: Double = 75.0,
    val goal: Goal = Goal.MUSCLE,
    val experience: Experience = Experience.MID,
    val perWeek: Int = 3,
    val restSec: Int = 90,
)

enum class Equip(val label: String) {
    BARBELL("Barbell"), DUMBBELL("Dumbbells"), MACHINE("Machine"), CABLE("Cable"), SMITH("Smith machine"),
    EZ_BAR("EZ bar"), KETTLEBELL("Kettlebell"), BODY("Bodyweight"), BAND("Band"), OTHER("Other");

    /** Loads that exist by default on this kind of equipment. Editable per exercise. */
    fun defaultGrid(bodyWeight: Double): LoadGrid = when (this) {
        BARBELL -> LoadGrid.BARBELL
        SMITH -> LoadGrid(10.0, 300.0, 2.5)
        EZ_BAR -> LoadGrid(7.5, 120.0, 2.5)
        DUMBBELL -> LoadGrid(2.0, 60.0, 2.0)
        MACHINE -> LoadGrid.MACHINE
        CABLE -> LoadGrid.CABLE
        KETTLEBELL -> LoadGrid(4.0, 48.0, 4.0)
        BODY -> LoadGrid.fixed(bodyWeight)
        BAND, OTHER -> LoadGrid(1.0, 200.0, 1.0)
    }

    /** Whether plates are loaded on a bar, so the plate breakdown is worth showing. */
    val plateLoaded get() = this == BARBELL || this == SMITH || this == EZ_BAR
    val perHand get() = this == DUMBBELL || this == KETTLEBELL
}

enum class MuscleGroup(val label: String, val muscles: Set<String>) {
    CHEST("Chest", setOf("chest")),
    BACK("Back", setOf("lats", "middle back", "lower back", "traps", "neck")),
    SHOULDERS("Shoulders", setOf("shoulders")),
    BICEPS("Biceps", setOf("biceps")),
    TRICEPS("Triceps", setOf("triceps")),
    LEGS("Legs", setOf("quadriceps", "hamstrings", "glutes", "calves", "adductors", "abductors")),
    ABS("Abs", setOf("abdominals")),
    FOREARMS("Forearms", setOf("forearms"));

    companion object {
        fun of(muscle: String): MuscleGroup = entries.first { muscle in it.muscles }
    }
}

@Serializable
data class ExerciseDef(
    val id: String,
    val en: String,
    val common: Boolean = false,
    val equip: Equip,
    val primary: List<String>,
    val secondary: List<String> = emptyList(),
    val compound: Boolean = false,
    val images: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val custom: Boolean = false,
) {
    val name get() = en
    val group get() = MuscleGroup.of(primary.first())
}

@Serializable
data class PlanExercise(val exerciseId: String, val sets: Int, val repLo: Int, val repHi: Int)

@Serializable
data class Workout(val id: String, val name: String, val exercises: List<PlanExercise> = emptyList())

@Serializable
data class ActiveExercise(
    val exerciseId: String,
    val sets: Int,
    val repLo: Int,
    val repHi: Int,
    val done: List<SetObs> = emptyList(),
)

@Serializable
data class ActiveSession(
    val workoutId: String,
    val name: String,
    val startedAt: Long,
    val exercises: List<ActiveExercise>,
    val index: Int = 0,
)

/** What changes for one exercise next time, with the reason, as shown on the summary screen. */
@Serializable
data class Change(
    val exerciseId: String,
    val load: Double,
    val nextLoad: Double,
    val nextReps: Int,
    val action: Action,
    val reason: Reason,
    val unlock: Int? = null,
)

@Serializable
data class LoggedExercise(val exerciseId: String, val sets: List<SetObs>)

@Serializable
data class Session(
    val id: String,
    val workoutId: String,
    val name: String,
    val day: Long,
    val startedAt: Long,
    val durationSec: Int,
    val exercises: List<LoggedExercise>,
    val changes: List<Change> = emptyList(),
)

@Serializable
data class AppData(
    val profile: Profile? = null,
    val workouts: List<Workout> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val states: Map<String, ExerciseState> = emptyMap(),
    val grids: Map<String, LoadGrid> = emptyMap(),
    val custom: List<ExerciseDef> = emptyList(),
    val active: ActiveSession? = null,
    @SerialName("v") val version: Int = 1,
)

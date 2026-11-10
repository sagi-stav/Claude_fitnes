package com.odplata.engine

import kotlinx.serialization.Serializable

/** How a set felt. Each answer maps to an estimate of reps left in reserve (RIR). */
@Serializable
enum class Feel(val rir: Double, val sigmaReps: Double) {
    /** "Could have done 4 or more": only a lower bound on capacity, so it is treated as censored. */
    EASY(4.0, 2.5),
    GOOD(2.5, 1.2),
    HARD(1.0, 0.9),
    /** Hit failure: the most precise observation we ever get. */
    FAIL(0.0, 0.5),
}

@Serializable
data class SetObs(val weight: Double, val reps: Int, val feel: Feel)

/** The loads that physically exist for an exercise, in kg. */
@Serializable
data class LoadGrid(val min: Double, val max: Double, val step: Double) {
    fun snap(w: Double): Double {
        val k = Math.round((w - min) / step)
        return (min + k * step).coerceIn(min, max)
    }

    fun floor(w: Double): Double {
        val k = Math.floor((w - min) / step + 1e-9)
        return (min + k * step).coerceIn(min, max)
    }

    fun next(w: Double): Double? = snap(w).let { if (it + step <= max + 1e-9) it + step else null }
    fun prev(w: Double): Double? = snap(w).let { if (it - step >= min - 1e-9) it - step else null }

    companion object {
        val BARBELL = LoadGrid(20.0, 400.0, 2.5)
        val DUMBBELL = LoadGrid(1.0, 60.0, 1.0)
        val MACHINE = LoadGrid(5.0, 300.0, 5.0)
        val CABLE = LoadGrid(2.5, 150.0, 2.5)
        /** Body-weight work: the load cannot change, only the reps. */
        fun fixed(w: Double) = LoadGrid(w, w, 1.0)
    }
}

@Serializable
data class ExerciseConfig(
    val repLo: Int,
    val repHi: Int,
    val sets: Int,
    val grid: LoadGrid,
    val targetRir: Double = 2.0,
)

@Serializable
data class SessionSummary(
    val day: Long,
    val load: Double,
    val strength: Double,
    val meanRir: Double,
    val failed: Boolean,
)

/**
 * Everything the engine remembers about one exercise.
 * [strength] is the estimated one-rep max in kg; [relVar] is its relative variance.
 */
@Serializable
data class ExerciseState(
    val strength: Double,
    val relVar: Double,
    val trend: Double = 0.0,
    val fatigue: List<Double> = DEFAULT_FATIGUE,
    val lastLoad: Double,
    val lastDay: Long,
    val recent: List<SessionSummary> = emptyList(),
) {
    fun fatigueAt(setIndex: Int): Double =
        fatigue.getOrElse(setIndex) { fatigue.last() * Math.pow(0.985, (setIndex - fatigue.size + 1).toDouble()) }

    companion object {
        val DEFAULT_FATIGUE = listOf(1.0, 0.97, 0.945, 0.925, 0.91)
    }
}

@kotlinx.serialization.Serializable
enum class Action { START, HOLD, UP, DOWN, DELOAD }

@kotlinx.serialization.Serializable
enum class Reason {
    FIRST_TIME,
    /** Conservative estimate clears the bottom of the range at the next load. */
    READY_FOR_MORE,
    /** In range, keep adding reps. */
    BUILD_REPS,
    /** Expected reps fall under the range at the current load. */
    BELOW_RANGE,
    /** Last session included a failed set: never go up right after that. */
    AFTER_FAIL,
    LONG_BREAK,
    STALLED,
    /** In-session: the set was easy and above the range. */
    EASY_NOW,
    /** In-session: today is a weak day, lighten the remaining sets. */
    WEAK_DAY,
    /** No heavier or lighter load exists on this equipment. */
    GRID_LIMIT,
}

data class Decision(
    val load: Double,
    val targetReps: Int,
    val action: Action,
    val reason: Reason,
    /** Reps on the last set at the current load that would unlock the next load. */
    val repsToUnlock: Int? = null,
)

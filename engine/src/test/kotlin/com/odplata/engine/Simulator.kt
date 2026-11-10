package com.odplata.engine

import java.util.Random
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A synthetic lifter whose true strength is known, used to check that the engine tracks it.
 * The lifter has good and bad days, tires across sets and reports effort imprecisely.
 */
class Lifter(
    seed: Long,
    var strength: Double,
    private val gain: Double = 0.006,
    private val gainDecay: Double = 0.985,
    private val dayNoise: Double = 0.03,
    private val fatigue: List<Double> = listOf(1.0, 0.965, 0.94, 0.92, 0.905),
    private val rirBias: Double = 0.0,
    private val rirNoise: Double = 0.7,
) {
    private val rnd = Random(seed)
    private var currentGain = gain
    var forcedForm: Double? = null

    class SetTrace(val obs: SetObs, val trueRir: Int, val target: Int, val top: Int)

    fun train(state: ExerciseState?, cfg: ExerciseConfig, day: Long, startLoad: Double? = null): Pair<List<SetTrace>, Decision> {
        val form = forcedForm ?: (1 + rnd.nextGaussian() * dayNoise)
        val first = Progression.plan(state, cfg, day, startLoad)
        var d = first
        val trace = ArrayList<SetTrace>()
        for (i in 0 until cfg.sets) {
            val f = fatigue.getOrElse(i) { 0.9 }
            val toFail = max(0, floor(Progression.repsToFailure(strength * form * f, d.load) + rnd.nextGaussian() * 0.4).toInt())
            val top = max(cfg.repHi, d.repsToUnlock ?: cfg.repHi)
            val reps = min(toFail, (toFail - 2).coerceIn(min(d.targetReps, top), top))
            val trueRir = toFail - reps
            val perceived = trueRir + rirBias + rnd.nextGaussian() * rirNoise
            val feel = when {
                reps < d.targetReps -> Feel.FAIL
                perceived >= 3.5 -> Feel.EASY
                perceived >= 1.75 -> Feel.GOOD
                else -> Feel.HARD
            }
            trace.add(SetTrace(SetObs(d.load, reps, feel), trueRir, d.targetReps, top))
            if (i < cfg.sets - 1) d = Progression.nextSet(state, cfg, trace.map { it.obs }, day)
        }
        if (trace.count { it.trueRir <= 4 } >= 2) {
            strength *= 1 + currentGain
            currentGain *= gainDecay
        }
        return trace to first
    }
}

class Report(
    val states: List<ExerciseState>,
    val truth: List<Double>,
    val plans: List<Decision>,
    val sets: List<Lifter.SetTrace>,
    val cfg: ExerciseConfig,
) {
    /** Mean absolute relative error of the strength estimate, skipping the first [skip] sessions. */
    fun trackingError(skip: Int = 5): Double =
        states.indices.drop(skip).map { abs(states[it].strength / truth[it] - 1) }.average()

    fun inRange(): Double = sets.count { it.obs.reps >= cfg.repLo && it.obs.reps <= it.top }.toDouble() / sets.size
    fun failRate(): Double = sets.count { it.obs.feel == Feel.FAIL }.toDouble() / sets.size
    fun count(a: Action): Int = plans.count { it.action == a }
    fun line(name: String) = "%-22s err=%.1f%% inRange=%.0f%% fail=%.1f%% load %.1f -> %.1f  true %.1f -> %.1f  up=%d down=%d deload=%d".format(
        name, trackingError() * 100, inRange() * 100, failRate() * 100,
        plans.first().load, plans.last().load, truth.first(), truth.last(),
        count(Action.UP), count(Action.DOWN), count(Action.DELOAD),
    )
}

fun simulate(
    lifter: Lifter,
    cfg: ExerciseConfig,
    sessions: Int,
    startLoad: Double,
    gapDays: (Int) -> Long = { 4 },
    before: (Int, Lifter) -> Unit = { _, _ -> },
): Report {
    var state: ExerciseState? = null
    var day = 100L
    val states = ArrayList<ExerciseState>()
    val truth = ArrayList<Double>()
    val plans = ArrayList<Decision>()
    val sets = ArrayList<Lifter.SetTrace>()
    for (n in 0 until sessions) {
        before(n, lifter)
        truth.add(lifter.strength)
        val (trace, plan) = lifter.train(state, cfg, day, startLoad)
        state = Progression.finish(state, cfg, trace.map { it.obs }, day)!!
        states.add(state)
        plans.add(plan)
        sets.addAll(trace)
        day += gapDays(n)
    }
    return Report(states, truth, plans, sets, cfg)
}

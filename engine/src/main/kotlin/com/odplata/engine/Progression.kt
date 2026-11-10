package com.odplata.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Load progression as state estimation.
 *
 * Every set is a noisy measurement of the lifter's strength on that exercise (expressed as an
 * estimated one-rep max). A two-level filter separates slow, long-term strength from the form of
 * the day, and the prescribed load is whichever load that actually exists on the equipment is
 * predicted to land the reps inside the target range at the target reps-in-reserve.
 *
 * All functions are pure: state in, state or decision out.
 */
object Progression {
    /** Epley: e1RM = w * (1 + repsToFailure / 30). */
    private const val EPLEY = 30.0

    /** Day-to-day swing in performance (relative sd). */
    private const val SIGMA_DAY = 0.03
    /** Measurement noise floor per set (relative sd). */
    private const val SIGMA_BASE = 0.015
    /** How much true strength can drift between two sessions (relative sd). */
    private const val SIGMA_DRIFT = 0.012
    /** Uncertainty assumed for an exercise seen for the first time. */
    private const val SIGMA_NEW = 0.10
    /** Number of standard deviations subtracted before deciding to add load. */
    private const val CAUTION = 1.0
    private const val TREND_MEMORY = 0.7
    private const val TREND_USE = 0.5
    private const val BREAK_DAYS = 10
    private const val STALL_SESSIONS = 4
    /** Spare reps required above the bottom of the range before adding load. */
    private const val UP_MARGIN = 1.0
    /** While an exercise is still being calibrated, jumps of up to this size are allowed. */
    private const val CALIBRATION_JUMP = 0.15
    private const val CALIBRATION_SESSIONS = 3
    /** Session results further than this many sd from the belief are pulled in (one odd day is not a trend). */
    private const val OUTLIER_SD = 1.5

    fun e1rm(weight: Double, repsToFailure: Double): Double = weight * (1 + repsToFailure / EPLEY)

    /** Reps to failure a lifter of strength [strength] gets at [weight]. */
    fun repsToFailure(strength: Double, weight: Double): Double = EPLEY * (strength / weight - 1)

    // ---------------------------------------------------------------- session-level estimate

    private class Session(val rel: Double, val relVar: Double, val prior: Double)

    /**
     * Sequentially folds the sets of one session into an estimate of today's strength relative to
     * [base]. `rel` is the posterior mean of (todayStrength / base - 1).
     */
    private fun estimate(base: Double, baseRelVar: Double, fatigue: (Int) -> Double, sets: List<SetObs>): Session {
        val prior = baseRelVar + SIGMA_DAY * SIGMA_DAY
        var d = 0.0
        var v = prior
        sets.forEachIndexed { i, s ->
            if (s.weight <= 0.0) return@forEachIndexed
            val z = e1rm(s.weight, s.reps + s.feel.rir) / fatigue(i)
            val rel = z / base - 1
            // EASY only tells us "at least this strong": ignore it unless it contradicts the belief.
            if (s.feel == Feel.EASY && rel <= d) return@forEachIndexed
            val r = (s.weight * s.feel.sigmaReps / EPLEY / base).pow(2) + SIGMA_BASE * SIGMA_BASE
            val k = v / (v + r)
            d += k * (rel - d)
            v *= (1 - k)
        }
        return Session(d, v, prior)
    }

    // ---------------------------------------------------------------- time away from the bar

    private fun detrained(state: ExerciseState, today: Long): ExerciseState {
        val gap = (today - state.lastDay - BREAK_DAYS).toDouble()
        if (gap <= 0) return state
        val keep = max(0.85, 1 - 0.004 * gap)
        val extra = min(0.08, 0.004 * gap)
        return state.copy(strength = state.strength * keep, relVar = state.relVar + extra * extra, trend = 0.0)
    }

    // ---------------------------------------------------------------- predictions

    /** Reps expected on set [setIndex] at [weight] while leaving the target reserve. */
    private fun predicted(strength: Double, state: ExerciseState?, cfg: ExerciseConfig, weight: Double, setIndex: Int): Double {
        val f = state?.fatigueAt(setIndex) ?: ExerciseState.DEFAULT_FATIGUE.getOrElse(setIndex) { 0.9 }
        return repsToFailure(strength * f, weight) - cfg.targetRir
    }

    /** A coarse jump (think 8 kg to 10 kg dumbbells) may land a little under the range. */
    private fun jumpFloor(cfg: ExerciseConfig, from: Double, to: Double): Double =
        if (to / from - 1 > 0.10) max(5.0, cfg.repLo - 2.0) else cfg.repLo.toDouble()

    private fun targetReps(pred: Double, cfg: ExerciseConfig, unlock: Int?): Int {
        val top = max(cfg.repHi, unlock ?: cfg.repHi)
        return floor(pred + 0.3).toInt().coerceIn(cfg.repLo, top)
    }

    /** Whether [strength] is enough to move from [from] to [to] and stay in range on every set. */
    private fun ready(cautious: Double, expected: Double, state: ExerciseState?, cfg: ExerciseConfig, from: Double, to: Double): Boolean {
        val floor = jumpFloor(cfg, from, to)
        val f = state?.fatigueAt(cfg.sets - 1) ?: ExerciseState.DEFAULT_FATIGUE.getOrElse(cfg.sets - 1) { 0.9 }
        // First set clears the bottom of the range with a rep to spare, at the target reserve,
        // and the last set can still be completed without reaching failure.
        return predicted(cautious, state, cfg, to, 0) >= floor + UP_MARGIN &&
            repsToFailure(expected * f, to) >= floor + 0.5
    }

    /** Reps on the first set at [load] that would unlock the next load. */
    private fun repsToUnlock(state: ExerciseState, cfg: ExerciseConfig, load: Double): Int? {
        val up = cfg.grid.next(load) ?: return null
        val need = jumpFloor(cfg, load, up) + UP_MARGIN
        // Solve predicted(first set, up) >= need for reps at the current load.
        val reps = (need + cfg.targetRir + EPLEY) * up / load - EPLEY - cfg.targetRir
        return kotlin.math.ceil(reps - 1e-9).toInt().coerceAtMost(30)
    }

    // ---------------------------------------------------------------- public API

    /** What to load for the first set of a session. `state == null` means a brand-new exercise. */
    fun plan(state: ExerciseState?, cfg: ExerciseConfig, today: Long, startLoad: Double? = null): Decision {
        if (state == null) {
            val w = cfg.grid.snap(startLoad ?: cfg.grid.min)
            return Decision(w, cfg.repLo, Action.START, Reason.FIRST_TIME)
        }
        val s = detrained(state, today)
        val longBreak = today - state.lastDay > BREAK_DAYS + 4
        val w0 = cfg.grid.snap(state.lastLoad)
        val expected = s.strength
        val cautious = expected * (1 - CAUTION * sqrt(s.relVar))

        fun decide(load: Double, action: Action, reason: Reason): Decision {
            val unlock = repsToUnlock(s, cfg, load)
            return Decision(load, targetReps(predicted(expected, s, cfg, load, 0), cfg, unlock), action, reason, unlock)
        }

        if (stalled(s)) {
            val w = cfg.grid.floor(w0 * 0.9)
            if (w < w0) return decide(w, Action.DELOAD, Reason.STALLED)
        }

        // Down: the first set no longer reaches the bottom of the range. After a long break the
        // pessimistic estimate is used, so the comeback starts on the safe side.
        val downBy = if (longBreak) cautious else expected
        if (predicted(downBy, s, cfg, w0, 0) < cfg.repLo - 0.5) {
            var w = w0
            while (predicted(downBy, s, cfg, w, 0) < cfg.repLo) {
                val p = cfg.grid.prev(w) ?: break
                if (p < w0 * 0.8) break
                w = p
            }
            if (w < w0) return decide(w, Action.DOWN, if (longBreak) Reason.LONG_BREAK else Reason.BELOW_RANGE)
            return decide(w0, Action.HOLD, Reason.GRID_LIMIT)
        }

        // Up: one step, and only when even the pessimistic estimate keeps the last set in range.
        val up = cfg.grid.next(w0)
        val failedLast = s.recent.lastOrNull()?.failed == true
        if (up != null && !longBreak && ready(cautious, expected, s, cfg, w0, up)) {
            if (failedLast) return decide(w0, Action.HOLD, Reason.AFTER_FAIL)
            // One step at a time, except while the exercise is still being calibrated.
            var w: Double = up
            if (s.recent.size < CALIBRATION_SESSIONS) {
                while (true) {
                    val n = cfg.grid.next(w) ?: break
                    if (n > w0 * (1 + CALIBRATION_JUMP) || !ready(cautious, expected, s, cfg, w0, n)) break
                    w = n
                }
            }
            return decide(w, Action.UP, Reason.READY_FOR_MORE)
        }
        return decide(w0, Action.HOLD, if (up == null) Reason.GRID_LIMIT else Reason.BUILD_REPS)
    }

    /** What to do on the next set, given the sets already done in this session. */
    fun nextSet(state: ExerciseState?, cfg: ExerciseConfig, done: List<SetObs>, today: Long): Decision {
        require(done.isNotEmpty())
        val lastSet = done.last()
        val w0 = cfg.grid.snap(lastSet.weight)
        val i = done.size
        val base = state?.let { detrained(it, today) }
        val todayStrength = if (base == null) {
            firstEstimate(done).strength
        } else {
            val est = estimate(base.strength, base.relVar, base::fatigueAt, done)
            base.strength * (1 + est.rel)
        }
        val unlock = base?.let { repsToUnlock(it, cfg, w0) }
        fun decide(load: Double, action: Action, reason: Reason) =
            Decision(load, targetReps(predicted(todayStrength, base, cfg, load, i), cfg, unlock), action, reason, unlock)

        val anyFail = done.any { it.feel == Feel.FAIL }
        val up = cfg.grid.next(w0)
        if (lastSet.feel == Feel.EASY && lastSet.reps >= cfg.repHi && !anyFail && up != null &&
            predicted(todayStrength, base, cfg, up, i) >= jumpFloor(cfg, w0, up)
        ) {
            var w: Double = up
            if ((base?.recent?.size ?: 0) < CALIBRATION_SESSIONS) {
                while (true) {
                    val n = cfg.grid.next(w) ?: break
                    if (n > w0 * (1 + CALIBRATION_JUMP) || predicted(todayStrength, base, cfg, n, i) < cfg.repLo + UP_MARGIN) break
                    w = n
                }
            }
            return decide(w, Action.UP, Reason.EASY_NOW)
        }

        fun toFail(w: Double) = repsToFailure(todayStrength * (base?.fatigueAt(i) ?: ExerciseState.DEFAULT_FATIGUE.getOrElse(i) { 0.9 }), w)
        val missed = lastSet.feel == Feel.FAIL && lastSet.reps < cfg.repLo
        if (toFail(w0) < cfg.repLo + 0.5 || missed) {
            var w = cfg.grid.prev(w0) ?: return decide(w0, Action.HOLD, Reason.GRID_LIMIT)
            while (toFail(w) < cfg.repLo + 1.5) {
                val p = cfg.grid.prev(w) ?: break
                if (p < w0 * 0.85) break
                w = p
            }
            return decide(w, Action.DOWN, Reason.WEAK_DAY)
        }
        return decide(w0, Action.HOLD, Reason.BUILD_REPS)
    }

    /** Folds a finished session into the long-term state. */
    fun finish(state: ExerciseState?, cfg: ExerciseConfig, sets: List<SetObs>, today: Long): ExerciseState? {
        val work = sets.filter { it.weight > 0.0 }
        if (work.isEmpty()) return state
        // The load to continue from: in-session increases are kept, but a load that was only
        // lightened because of a weak day does not become the new baseline.
        val load = if (work.last().weight < work.first().weight) work.first().weight else work.last().weight
        val meanRir = work.map { it.feel.rir }.average()
        val failed = work.any { it.feel == Feel.FAIL }
        if (state == null) {
            val s = firstEstimate(work)
            return s.copy(lastLoad = load, lastDay = today, recent = listOf(SessionSummary(today, load, s.strength, meanRir, failed)))
        }
        val base = detrained(state, today)
        val est = estimate(base.strength, base.relVar, base::fatigueAt, work)

        // Strip the prior back out to get what the session alone says, then treat that as one
        // observation of long-term strength whose noise includes the form of the day.
        val info = 1 / est.relVar - 1 / est.prior
        var strength = base.strength
        var relVar = base.relVar
        var step = 0.0
        if (info > 1e-9) {
            val r = 1 / info + SIGMA_DAY * SIGMA_DAY
            val limit = OUTLIER_SD * sqrt(relVar + r)
            val sessionRel = (est.rel / (est.relVar * info)).coerceIn(-limit, limit)
            val k = relVar / (relVar + r)
            step = k * sessionRel
            strength *= (1 + step)
            relVar *= (1 - k)
        }
        val trend = (TREND_MEMORY * base.trend + (1 - TREND_MEMORY) * step).coerceIn(-0.01, 0.015)
        val recent = (base.recent + SessionSummary(today, load, strength, meanRir, failed)).takeLast(8)
        val deloaded = load <= base.lastLoad * 0.93
        return ExerciseState(
            // Carry part of the trend forward so a steadily improving lifter is not always one step behind.
            strength = strength * (1 + TREND_USE * trend),
            relVar = relVar + SIGMA_DRIFT * SIGMA_DRIFT,
            trend = trend,
            fatigue = learnFatigue(base.fatigue, work),
            lastLoad = load,
            lastDay = today,
            recent = if (deloaded) recent.takeLast(1) else recent,
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun firstEstimate(sets: List<SetObs>): ExerciseState {
        val f = ExerciseState.DEFAULT_FATIGUE
        // Start from the most informative set (closest to failure), then refine with the rest.
        val anchor = sets.withIndex().minBy { it.value.feel.rir }
        val guess = e1rm(anchor.value.weight, anchor.value.reps + anchor.value.feel.rir) / f.getOrElse(anchor.index) { 0.9 }
        val est = estimate(guess, SIGMA_NEW * SIGMA_NEW, { f.getOrElse(it) { 0.9 } }, sets)
        val onlyEasy = sets.all { it.feel == Feel.EASY }
        return ExerciseState(
            strength = guess * (1 + est.rel),
            relVar = if (onlyEasy) 0.15 * 0.15 else max(est.relVar, 0.04 * 0.04),
            lastLoad = sets.last().weight,
            lastDay = 0,
        )
    }

    /** Learns how much each later set drops relative to the first, at equal load. */
    private fun learnFatigue(old: List<Double>, sets: List<SetObs>): List<Double> {
        val first = sets.first()
        if (first.feel == Feel.EASY) return old
        val z0 = e1rm(first.weight, first.reps + first.feel.rir)
        val out = old.toMutableList()
        for (i in 1 until min(sets.size, 8)) {
            val s = sets[i]
            if (s.feel == Feel.EASY) continue
            while (out.size <= i) out.add(out.last() * 0.985)
            val ratio = (e1rm(s.weight, s.reps + s.feel.rir) / z0).coerceIn(0.8, 1.0)
            out[i] = 0.8 * out[i] + 0.2 * ratio
        }
        return out
    }

    /** No progress for several sessions while training close to failure. */
    private fun stalled(state: ExerciseState): Boolean {
        val r = state.recent
        if (r.size < STALL_SESSIONS + 1) return false
        val window = r.takeLast(STALL_SESSIONS)
        val before = r[r.size - STALL_SESSIONS - 1]
        val noGain = window.maxOf { it.strength } <= before.strength * 1.005
        val noLoad = window.maxOf { it.load } <= before.load
        val grinding = window.map { it.meanRir }.average() <= 1.3
        return noGain && noLoad && grinding
    }
}

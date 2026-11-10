package com.odplata.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressionTest {
    private val bench = ExerciseConfig(repLo = 8, repHi = 12, sets = 4, grid = LoadGrid.BARBELL)
    private val raise = ExerciseConfig(repLo = 10, repHi = 15, sets = 3, grid = LoadGrid(2.0, 40.0, 2.0))

    private fun many(n: Int = 30, run: (Long) -> Report): List<Report> = (1L..n).map(run)
    private fun List<Report>.avg(f: (Report) -> Double) = map(f).average()

    @Test
    fun gridMath() {
        val g = LoadGrid.BARBELL
        assertEquals(60.0, g.snap(61.0))
        assertEquals(62.5, g.next(60.0))
        assertEquals(null, g.prev(20.0))
        assertEquals(52.5, g.floor(54.0))
        assertEquals(10.0, Progression.repsToFailure(Progression.e1rm(60.0, 10.0), 60.0), 1e-9)
    }

    @Test
    fun steadyGainerIsTrackedAndKeptInRange() {
        val runs = many { simulate(Lifter(it, strength = 80.0), bench, 60, startLoad = 55.0) }
        println(runs.first().line("steady gainer"))
        println("  avg: err=%.1f%% inRange=%.0f%% fail=%.1f%%".format(runs.avg { it.trackingError() } * 100, runs.avg { it.inRange() } * 100, runs.avg { it.failRate() } * 100))
        assertTrue(runs.avg { it.trackingError() } < 0.04)
        assertTrue(runs.avg { it.inRange() } > 0.90)
        assertTrue(runs.avg { it.failRate() } < 0.06)
        // The load must follow the lifter up. Ideal = heaviest load whose first set still clears
        // the bottom of the range with a rep to spare, computed from the true strength.
        runs.forEach { r ->
            val ideal = LoadGrid.BARBELL.floor(r.truth.last() / (1 + (bench.repLo + 1 + bench.targetRir) / 30.0))
            val got = r.plans.last().load
            assertTrue(got >= ideal - 5.0 && got <= ideal + 2.5, "final load $got, ideal $ideal")
        }
    }

    @Test
    fun plateauedLifterIsNotPushedIntoFailure() {
        val runs = many { simulate(Lifter(it, strength = 80.0, gain = 0.0), bench, 40, startLoad = 55.0) }
        println(runs.first().line("plateau"))
        println("  avg: fail=%.1f%% inRange=%.0f%% up=%.1f down=%.1f".format(runs.avg { it.failRate() } * 100, runs.avg { it.inRange() } * 100, runs.avg { it.count(Action.UP).toDouble() }, runs.avg { it.count(Action.DOWN).toDouble() }))
        assertTrue(runs.avg { it.failRate() } < 0.06)
        assertTrue(runs.avg { it.inRange() } > 0.90)
        // No yo-yo: after settling, the load should rarely move.
        runs.forEach { r -> assertTrue(r.plans.drop(10).count { it.action == Action.UP || it.action == Action.DOWN } <= 6) }
    }

    @Test
    fun oneBadDayDoesNotLowerTheLongTermPlan() {
        val runs = many { seed ->
            simulate(Lifter(seed, strength = 80.0, gain = 0.0, dayNoise = 0.0), bench, 14, startLoad = 55.0,
                before = { n, l -> l.forcedForm = if (n == 10) 0.90 else 1.0 })
        }
        runs.forEach { r ->
            val before = r.states[9].strength
            val after = r.states[10].strength
            assertTrue(after > before * 0.97, "one bad day moved the estimate by ${(after / before - 1) * 100}%")
            assertTrue(r.plans[11].load >= r.plans[10].load - 2.5, "plan dropped more than one step after a bad day")
        }
        // ...but within the bad session itself the load must come down.
        assertTrue(runs.count { r -> r.sets.subList(40, 44).any { it.obs.weight < r.plans[10].load } } > runs.size * 0.7)
    }

    @Test
    fun coarseJumpsWaitUntilTheLifterIsReady() {
        val runs = many { simulate(Lifter(it, strength = 13.0, gain = 0.008), raise, 60, startLoad = 6.0) }
        println(runs.first().line("coarse grid"))
        println("  avg: fail=%.1f%% inRange=%.0f%%".format(runs.avg { it.failRate() } * 100, runs.avg { it.inRange() } * 100))
        assertTrue(runs.avg { it.failRate() } < 0.08)
        runs.forEach { r ->
            // First set after every jump still lands at or above the relaxed floor.
            r.plans.forEachIndexed { n, p ->
                if (p.action == Action.UP) assertTrue(r.sets[n * raise.sets].obs.reps >= raise.repLo - 3, "jumped too early at session $n")
            }
            assertTrue(r.plans.last().load > r.plans[2].load, "never progressed on a coarse grid")
        }
    }

    @Test
    fun comesBackCarefullyAfterALongBreak() {
        val runs = many { seed ->
            simulate(Lifter(seed, strength = 80.0, gain = 0.0), bench, 16, startLoad = 55.0,
                gapDays = { if (it == 11) 35 else 4 },
                before = { n, l -> if (n == 12) l.strength *= 0.92 })
        }
        runs.forEach { r ->
            assertTrue(r.plans[12].load < r.plans[11].load, "did not lighten after a 5-week break")
            assertTrue(r.plans[12].action == Action.DOWN)
        }
        val firstSetFails = runs.count { it.sets[12 * 4].obs.feel == Feel.FAIL }
        assertTrue(firstSetFails <= runs.size / 5, "too many failed comebacks: $firstSetFails")
    }

    @Test
    fun tooLightStartConvergesQuickly() {
        val runs = many { simulate(Lifter(it, strength = 80.0, gain = 0.0), bench, 8, startLoad = 30.0) }
        println(runs.first().line("light start"))
        // Correct working load for this lifter is 55-60 kg.
        runs.forEach { r -> assertTrue(r.plans[5].load >= 50.0, "still at ${r.plans[5].load} after 5 sessions") }
    }

    @Test
    fun tooHeavyStartBacksOffInTheSameSession() {
        val runs = many { simulate(Lifter(it, strength = 80.0, gain = 0.0), bench, 3, startLoad = 72.5) }
        runs.forEach { r ->
            assertTrue(r.sets[1].obs.weight < 72.5, "kept a load the lifter failed on")
            assertTrue(r.plans[1].load <= 62.5)
        }
    }

    @Test
    fun optimisticReporterDoesNotGetBuried() {
        val runs = many { simulate(Lifter(it, strength = 80.0, rirBias = 1.0), bench, 60, startLoad = 55.0) }
        println("optimistic reporter   avg: fail=%.1f%% inRange=%.0f%% err=%.1f%%".format(runs.avg { it.failRate() } * 100, runs.avg { it.inRange() } * 100, runs.avg { it.trackingError() } * 100))
        assertTrue(runs.avg { it.failRate() } < 0.12)
    }

    @Test
    fun neverGoesUpRightAfterAFailedSession() {
        many { simulate(Lifter(it, strength = 80.0, dayNoise = 0.05), bench, 60, startLoad = 55.0) }.forEach { r ->
            for (n in 1 until r.plans.size) {
                val failedBefore = r.sets.subList((n - 1) * 4, n * 4).any { it.obs.feel == Feel.FAIL }
                if (failedBefore) assertTrue(r.plans[n].action != Action.UP, "went up after a failed session")
            }
        }
    }

    @Test
    fun grindingWithoutProgressTriggersADeload() {
        var state: ExerciseState? = null
        var day = 0L
        val grind = List(4) { SetObs(60.0, 9, Feel.HARD) }
        var sawDeload = false
        repeat(9) {
            val plan = Progression.plan(state, bench, day, 60.0)
            if (plan.action == Action.DELOAD) {
                sawDeload = true
                assertTrue(plan.load <= 55.0)
            }
            state = Progression.finish(state, bench, grind, day)
            day += 4
        }
        assertTrue(sawDeload)
    }

    @Test
    fun bodyweightExerciseOnlyChangesReps() {
        val pullUp = ExerciseConfig(repLo = 5, repHi = 12, sets = 3, grid = LoadGrid.fixed(76.0))
        val r = simulate(Lifter(1, strength = 100.0), pullUp, 30, startLoad = 76.0)
        assertTrue(r.plans.all { it.load == 76.0 })
        assertTrue(r.plans.last().targetReps >= r.plans[1].targetReps)
    }
}

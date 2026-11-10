package com.odplata.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.odplata.app.data.AppData
import com.odplata.app.data.Catalog
import com.odplata.app.data.Equip
import com.odplata.app.data.MuscleGroup
import com.odplata.app.data.Profile
import com.odplata.engine.Action
import com.odplata.engine.Feel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app logic through whole workouts and renders every screen to
 * app/build/shots, so the flow and the look can both be checked without a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xhdpi")
class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private var day = 20000L
    private var clock = 1_000_000L
    private var saved: AppData? = null

    private fun newApp(data: AppData = AppData()) =
        AppLogic(Catalog.load(RuntimeEnvironment.getApplication()), data, { saved = it }, { day }, { clock })

    private fun shot(name: String, app: AppLogic) {
        rule.mainClock.autoAdvance = false
        rule.setContent { App(app) }
        rule.mainClock.advanceTimeBy(500)
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    /** Builds three workouts and trains the first one [sessions] times. */
    private fun seeded(sessions: Int): AppLogic {
        val app = newApp()
        app.saveProfile(Profile(name = "", age = 27, heightCm = 178, weightKg = 76.0))
        fun build(name: String, vararg ids: String) {
            app.newWorkout()
            val id = (app.screen as Screen.Editor).workoutId
            app.editWorkout(id) { it.copy(name = name) }
            ids.forEach { app.addToWorkout(id, app.exercise(it)) }
            app.back()
        }
        build("חזה + יד אחורית", "Barbell_Bench_Press_-_Medium_Grip", "Incline_Dumbbell_Press", "Butterfly", "Triceps_Pushdown")
        build("גב + יד קדמית", "Wide-Grip_Lat_Pulldown", "Seated_Cable_Rows", "Pullups", "Barbell_Curl")
        build("רגליים + כתפיים", "Barbell_Squat", "Leg_Press", "Lying_Leg_Curls", "Side_Lateral_Raise")
        val first = app.data.workouts.first().id
        val starts = listOf(60.0, 22.0, 40.0, 25.0)
        repeat(sessions) { n ->
            app.start(first)
            while (app.data.active != null) {
                val cur = app.current!!
                val d = app.decision(cur)
                val w = if (d.action == Action.START) starts[app.data.active!!.index] else d.load
                app.setDone(w, minOf(cur.repHi, d.targetReps + 1))
                app.answer(if (n % 3 == 2) Feel.HARD else Feel.GOOD)
                clock += 120_000
                app.continueAfterRest()
            }
            app.back()
            day += 4
        }
        return app
    }

    @Test
    fun wholeFlowWorksAndPersists() {
        val app = seeded(3)
        assertEquals(3, app.data.sessions.size)
        assertEquals(4, app.data.states.size)
        assertEquals(Screen.Home, app.screen)
        assertTrue(app.data.sessions.last().changes.size == 4)
        // What was saved is what a fresh start would load.
        assertEquals(app.data, saved)
        val bench = app.data.states.getValue("Barbell_Bench_Press_-_Medium_Grip")
        assertTrue("bench estimate ${bench.strength}", bench.strength in 75.0..95.0)
        // The next workout in rotation is offered.
        assertEquals("גב + יד קדמית", app.nextUp()?.name)
        // A custom exercise can be added and used.
        val mine = app.addCustom("מכונת חזה כתומה", Equip.MACHINE, MuscleGroup.CHEST)
        assertNotNull(app.exercises(MuscleGroup.CHEST).firstOrNull { it.id == mine.id })
        // An unfinished workout survives a restart.
        app.start(app.data.workouts[1].id)
        app.setDone(40.0, 10); app.answer(Feel.GOOD)
        val restarted = newApp(saved!!)
        assertEquals(Screen.Workout, restarted.screen)
        assertEquals(1, restarted.current!!.done.size)
    }

    @Test fun onboarding() = shot("01-onboarding", newApp())
    @Test fun homeEmpty() = shot("02-home-empty", newApp().also { it.saveProfile(Profile()) })
    @Test fun home() = shot("03-home", seeded(3))
    @Test fun editor() = shot("04-editor", seeded(1).also { it.go(Screen.Editor(it.data.workouts.first().id)) })
    @Test fun pickerGroups() = shot("05-picker-groups", seeded(0).also { it.go(Screen.Picker(it.data.workouts.first().id)) })
    @Test fun info() = shot("07-info", seeded(4).also { it.go(Screen.Info("Barbell_Bench_Press_-_Medium_Grip")) })
    @Test fun summary() = shot("11-summary", seeded(4).also { it.go(Screen.Summary(it.data.sessions.last().id)) })
    @Test fun history() = shot("12-history", seeded(3).also { it.go(Screen.History) })

    @Test fun lift() = shot("08-lift", seeded(3).also { it.start(it.data.workouts.first().id) })
    @Test fun liftNew() = shot("08b-lift-new", seeded(0).also { it.start(it.data.workouts[2].id) })
    @Test fun ask() = shot("09-ask", seeded(3).also { it.start(it.data.workouts.first().id); it.setDone(62.5, 10) })
    @Test fun rest() = shot("10-rest", seeded(3).also { it.start(it.data.workouts.first().id); it.setDone(62.5, 10); it.answer(Feel.GOOD); clock += 22_000 })
}

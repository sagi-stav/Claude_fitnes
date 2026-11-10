package com.odplata.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.odplata.app.ui.EditorScreen
import com.odplata.app.ui.HistoryScreen
import com.odplata.app.ui.HomeScreen
import com.odplata.app.ui.InfoScreen
import com.odplata.app.ui.PickerScreen
import com.odplata.app.ui.ProfileScreen
import com.odplata.app.ui.SummaryScreen
import com.odplata.app.ui.WorkoutScreen

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler(enabled = model.logic.stack.size > 1) { model.logic.back() }
            App(model.logic)
        }
    }
}

@Composable
fun App(app: AppLogic) {
    when (val s = app.screen) {
        Screen.Onboarding -> ProfileScreen(app, first = true)
        Screen.Settings -> ProfileScreen(app, first = false)
        Screen.Home -> HomeScreen(app, app.todayDay)
        is Screen.Editor -> EditorScreen(app, s.workoutId)
        is Screen.Picker -> PickerScreen(app, s.workoutId)
        is Screen.Info -> InfoScreen(app, s.exerciseId)
        Screen.Workout -> WorkoutScreen(app)
        is Screen.Summary -> SummaryScreen(app, s.sessionId)
        Screen.History -> HistoryScreen(app)
    }
}

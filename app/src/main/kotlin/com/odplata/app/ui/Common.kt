package com.odplata.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.odplata.app.data.ExerciseDef
import com.odplata.engine.Action
import com.odplata.engine.Feel
import com.odplata.engine.Reason
import java.time.LocalDate

val Feel.label get() = when (this) { Feel.EASY -> "Easy"; Feel.GOOD -> "Just right"; Feel.HARD -> "Hard"; Feel.FAIL -> "Got stuck" }
val Feel.sub get() = when (this) {
    Feel.EASY -> "I had 4 or more reps left"
    Feel.GOOD -> "I had 2–3 reps left"
    Feel.HARD -> "Maybe one rep left"
    Feel.FAIL -> "Could not finish the set"
}
val Feel.color get() = when (this) { Feel.EASY -> Iron.Green; Feel.GOOD -> Iron.Yellow; Feel.HARD -> Iron.Blue; Feel.FAIL -> Iron.Red }

/** One short sentence explaining a decision, so the app never changes a load without saying why. */
fun reasonText(reason: Reason, repLo: Int, unlock: Int?): String = when (reason) {
    Reason.FIRST_TIME -> "New exercise. Pick a weight you think you can lift for $repLo reps and the app will learn from it."
    Reason.READY_FOR_MORE -> "You hit the reps with room to spare. Going up one step."
    Reason.BUILD_REPS -> if (unlock != null) "Stay here and add reps. $unlock reps on the first set unlocks the next weight." else "Stay here and add reps."
    Reason.BELOW_RANGE -> "Reps fell below the range. Going down to get back into it."
    Reason.AFTER_FAIL -> "You got stuck last session. Settle in before going up."
    Reason.LONG_BREAK -> "Back from a break. Start lighter and climb back quickly."
    Reason.STALLED -> "Several sessions near failure with no progress. A light session to recover."
    Reason.EASY_NOW -> "That was easy and above the range. Going up right now."
    Reason.WEAK_DAY -> "A weaker day. Going down to stay in the range."
    Reason.GRID_LIMIT -> "No other weight exists on this equipment. Progress with reps."
}

fun actionText(action: Action): String = when (action) {
    Action.START -> "Start"
    Action.HOLD -> "Stay"
    Action.UP -> "Go up"
    Action.DOWN -> "Go down"
    Action.DELOAD -> "Light session"
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val DAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
fun dayText(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return "${DAYS[d.dayOfWeek.value % 7]} ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
}

fun agoText(days: Long?): String = when (days) {
    null -> "not done yet"
    0L -> "today"
    1L -> "yesterday"
    else -> "$days days ago"
}

@Composable
fun <T> Chips(options: List<T>, selected: T?, label: (T) -> String, modifier: Modifier = Modifier, scroll: Boolean = false, onSelect: (T) -> Unit) {
    Row(if (scroll) modifier.horizontalScroll(rememberScrollState()) else modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            Raised(Modifier.defaultMinSize(minHeight = 48.dp, minWidth = 48.dp), onClick = { onSelect(o) }, sunk = o == selected,
                padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Text(label(o), Modifier.align(Alignment.Center), style = Type.Small, color = if (o == selected) Iron.Signal else Iron.Ink, maxLines = 1)
            }
        }
    }
}

@Composable
fun Field(label: String, value: String, modifier: Modifier = Modifier, number: Boolean = false, style: TextStyle = Type.Strong, onChange: (String) -> Unit) {
    Well(modifier) {
        if (label.isNotEmpty()) Text(label, color = Iron.Muted, style = Type.Small)
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth().defaultMinSize(minHeight = 28.dp),
            textStyle = style.copy(color = Iron.Ink), singleLine = true, cursorBrush = SolidColor(Iron.Signal),
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        )
    }
}

@Composable
fun Stepper(label: String, value: String, modifier: Modifier = Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(1f), color = Iron.Muted, style = Type.Small)
        Key("−", onClick = onMinus)
        Text(value, Modifier.widthIn(min = 56.dp), style = Type.Strong, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Key("+", onClick = onPlus)
    }
}

/** Equipment icon and muscle map side by side: the visual identity of an exercise. */
@Composable
fun ExerciseMark(def: ExerciseDef, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MuscleMap(def.primary, def.secondary, height = 50.dp)
        EquipIcon(def.equip)
    }
}

@Composable
fun ExerciseRow(def: ExerciseDef, sub: String, modifier: Modifier = Modifier, trailing: String = "", onClick: () -> Unit) {
    Raised(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExerciseMark(def)
            Column(Modifier.weight(1f)) {
                Text(def.name, style = Type.Strong, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub.isNotEmpty()) Text(sub, color = Iron.Muted, style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trailing.isNotEmpty()) Text(trailing, style = Type.Display, color = Iron.Ink)
        }
    }
}

@Composable
fun AssetPhoto(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(name) {
        runCatching { context.assets.open("ex/$name").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Iron.Well)) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

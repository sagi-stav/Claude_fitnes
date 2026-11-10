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

val Feel.he get() = when (this) { Feel.EASY -> "קל"; Feel.GOOD -> "בדיוק"; Feel.HARD -> "קשה"; Feel.FAIL -> "נתקעתי" }
val Feel.sub get() = when (this) {
    Feel.EASY -> "יכולתי עוד 4 חזרות ויותר"
    Feel.GOOD -> "נשארו לי ${ltr("2–3")}"
    Feel.HARD -> "נשארה אולי אחת"
    Feel.FAIL -> "לא הצלחתי לסיים"
}
val Feel.color get() = when (this) { Feel.EASY -> Iron.Green; Feel.GOOD -> Iron.Yellow; Feel.HARD -> Iron.Blue; Feel.FAIL -> Iron.Red }

/** One short sentence explaining a decision, so the app never changes a load without saying why. */
fun reasonText(reason: Reason, repLo: Int, unlock: Int?): String = when (reason) {
    Reason.FIRST_TIME -> "תרגיל חדש. בחר משקל שנראה לך מתאים ל־$repLo חזרות, והאפליקציה תלמד ממנו."
    Reason.READY_FOR_MORE -> "עמדת בחזרות עם רזרבה. מעלים צעד."
    Reason.BUILD_REPS -> if (unlock != null) "נשארים ומוסיפים חזרות. $unlock חזרות בסט הראשון יפתחו את המשקל הבא." else "נשארים ומוסיפים חזרות."
    Reason.BELOW_RANGE -> "החזרות ירדו מתחת לטווח. מורידים כדי לחזור אליו."
    Reason.AFTER_FAIL -> "נתקעת באימון הקודם. מבססים לפני שמעלים."
    Reason.LONG_BREAK -> "חזרה מהפסקה. מתחילים קל יותר ועולים מהר."
    Reason.STALLED -> "כמה אימונים בלי התקדמות וקרוב לכשל. אימון קל כדי להתאושש."
    Reason.EASY_NOW -> "היה קל ומעל הטווח. מעלים כבר עכשיו."
    Reason.WEAK_DAY -> "היום קצת פחות חזק. מורידים כדי להישאר בטווח."
    Reason.GRID_LIMIT -> "אין משקל אחר במכשיר הזה. מתקדמים בחזרות."
}

fun actionText(action: Action): String = when (action) {
    Action.START -> "מתחילים"
    Action.HOLD -> "נשארים"
    Action.UP -> "מעלים"
    Action.DOWN -> "מורידים"
    Action.DELOAD -> "אימון קל"
}

private val DAYS = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
fun dayText(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return "יום ${DAYS[d.dayOfWeek.value % 7]}, ${d.dayOfMonth}.${d.monthValue}"
}

fun agoText(days: Long?): String = when (days) {
    null -> "עוד לא בוצע"
    0L -> "היום"
    1L -> "אתמול"
    2L -> "לפני יומיים"
    else -> "לפני $days ימים"
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

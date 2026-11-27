package com.sportassistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sportassistant.R
import com.sportassistant.data.Profile
import com.sportassistant.reminders.ReminderLimits

private val MOVE_CHOICES = listOf(30, 45, 60, 90, 120)
private val WATER_CHOICES = listOf(60, 90, 120, 180)
private const val HOUR = 60
private const val HALF_HOUR = 30

/** All reminder options: what, how often, when during the day, and on which days. */
@Composable
fun ReminderSettings(profile: Profile, onChange: (Profile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow(R.string.rem_move, profile.remMove) { onChange(profile.copy(remMove = it)) }
        if (profile.remMove) {
            IntervalChips(profile.moveEveryMin, MOVE_CHOICES) { onChange(profile.copy(moveEveryMin = it)) }
        }

        SwitchRow(R.string.rem_water, profile.remWater) { onChange(profile.copy(remWater = it)) }
        if (profile.remWater) {
            IntervalChips(profile.waterEveryMin, WATER_CHOICES) { onChange(profile.copy(waterEveryMin = it)) }
        }

        if (profile.remMove || profile.remWater) {
            Text(stringResource(R.string.rem_active_hours), style = MaterialTheme.typography.titleSmall)
            Stepper(
                label = stringResource(R.string.rem_from),
                value = ReminderLimits.format(profile.dayStartMin),
                onMinus = { onChange(profile.copy(dayStartMin = ReminderLimits.shiftStart(profile.dayStartMin, profile.dayEndMin, -HOUR))) },
                onPlus = { onChange(profile.copy(dayStartMin = ReminderLimits.shiftStart(profile.dayStartMin, profile.dayEndMin, HOUR))) },
            )
            Stepper(
                label = stringResource(R.string.rem_to),
                value = ReminderLimits.format(profile.dayEndMin),
                onMinus = { onChange(profile.copy(dayEndMin = ReminderLimits.shiftEnd(profile.dayStartMin, profile.dayEndMin, -HOUR))) },
                onPlus = { onChange(profile.copy(dayEndMin = ReminderLimits.shiftEnd(profile.dayStartMin, profile.dayEndMin, HOUR))) },
            )
            SwitchRow(R.string.rem_weekdays, profile.weekdaysOnly) { onChange(profile.copy(weekdaysOnly = it)) }
        }

        SwitchRow(R.string.rem_teeth, profile.remTeeth) { onChange(profile.copy(remTeeth = it)) }
        if (profile.remTeeth) {
            Stepper(
                label = stringResource(R.string.rem_teeth_morning),
                value = ReminderLimits.format(profile.teethMorningMin),
                onMinus = { onChange(profile.copy(teethMorningMin = ReminderLimits.shiftTime(profile.teethMorningMin, -HALF_HOUR))) },
                onPlus = { onChange(profile.copy(teethMorningMin = ReminderLimits.shiftTime(profile.teethMorningMin, HALF_HOUR))) },
            )
            Stepper(
                label = stringResource(R.string.rem_teeth_evening),
                value = ReminderLimits.format(profile.teethEveningMin),
                onMinus = { onChange(profile.copy(teethEveningMin = ReminderLimits.shiftTime(profile.teethEveningMin, -HALF_HOUR))) },
                onPlus = { onChange(profile.copy(teethEveningMin = ReminderLimits.shiftTime(profile.teethEveningMin, HALF_HOUR))) },
            )
        }

        Text(stringResource(R.string.settings_reminders_hint), style = MaterialTheme.typography.bodySmall)
    }
}

/** Interval choices as chips; a value restored from a backup that is not in the list still gets its chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalChips(current: Int, choices: List<Int>, onSelect: (Int) -> Unit) {
    Text(stringResource(R.string.rem_every), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (choices + current).distinct().sorted().forEach { minutes ->
            FilterChip(
                selected = minutes == current,
                onClick = { onSelect(minutes) },
                label = { Text(stringResource(R.string.rem_minutes_fmt, minutes)) },
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton(onClick = onMinus) { Icon(AppIcons.Remove, contentDescription = stringResource(R.string.rem_earlier)) }
        Text(value, Modifier.width(64.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onPlus) { Icon(AppIcons.Add, contentDescription = stringResource(R.string.rem_later)) }
    }
}

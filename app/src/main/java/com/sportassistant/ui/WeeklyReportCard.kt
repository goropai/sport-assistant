package com.sportassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sportassistant.R
import com.sportassistant.data.WeekSummary
import com.sportassistant.data.WeeklyReport
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

@Composable
fun WeeklyReportCard(s: WeekSummary, today: LocalDate, hasWatchData: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(s.headline.text), style = MaterialTheme.typography.bodyLarge)

            Text(stringResource(R.string.report_active_days, s.activeDays))
            Text(stringResource(R.string.report_active_minutes, s.activeMinutes, WeeklyReport.ACTIVE_MINUTES_GOAL))
            LinearProgressIndicator(
                progress = { (s.activeMinutes.toFloat() / WeeklyReport.ACTIVE_MINUTES_GOAL).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (s.workouts > 0 || s.workoutsPrevWeek > 0) {
                Text(stringResource(R.string.report_workouts_vs, s.workouts, s.workoutsPrevWeek), style = MaterialTheme.typography.bodyMedium)
            }

            s.avgSteps?.let {
                Text(stringResource(R.string.report_avg_steps, it))
                StepBars(s.stepsPerDay, today)
            }
            s.avgSleepMinutes?.let {
                Text(stringResource(R.string.report_avg_sleep, it / 60, it % 60))
            }
            s.avgEatenKcal?.let {
                Text(stringResource(R.string.report_avg_eaten, it))
            }
            if (!hasWatchData) {
                Text(stringResource(R.string.report_no_watch), style = MaterialTheme.typography.bodySmall)
            }

            s.tips.forEach { Text("💡 ${stringResource(it.text)}", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Seven small bars, oldest day first; the tallest is at least the daily step goal so quiet weeks look quiet. */
@Composable
private fun StepBars(steps: List<Long?>, today: LocalDate) {
    val scale = max(STEP_GOAL.toLong(), steps.filterNotNull().maxOrNull() ?: 0L).toFloat()
    val barMaxHeight = 56.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        steps.forEachIndexed { i, value ->
            val day = today.minusDays((steps.lastIndex - i).toLong())
            val fraction = ((value ?: 0L) / scale).coerceIn(0f, 1f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .width(24.dp)
                        .height(max(2f, fraction * barMaxHeight.value).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if ((value ?: 0L) >= STEP_GOAL) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        ),
                )
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

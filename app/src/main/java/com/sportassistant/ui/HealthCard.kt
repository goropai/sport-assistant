package com.sportassistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sportassistant.R
import com.sportassistant.health.HealthSnapshot
import com.sportassistant.health.HealthUiState

/** WHO-aligned, modest daily target for people starting from a sedentary routine. */
const val STEP_GOAL = 7000

@Composable
fun HealthCard(state: HealthUiState, onConnect: () -> Unit, onRefresh: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⌚  ${stringResource(R.string.health_title)}", style = MaterialTheme.typography.titleMedium)
            when (state) {
                HealthUiState.Loading -> Text(stringResource(R.string.health_loading))
                is HealthUiState.Unavailable -> Text(
                    stringResource(if (state.updateRequired) R.string.health_update_required else R.string.health_unavailable),
                )
                HealthUiState.NeedsPermission -> {
                    Text(stringResource(R.string.health_needs_permission), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.health_connect)) }
                }
                HealthUiState.Error -> {
                    Text(stringResource(R.string.health_error))
                    OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.health_refresh)) }
                }
                is HealthUiState.Data -> {
                    if (state.snapshot.isEmpty) Text(stringResource(R.string.health_no_data), style = MaterialTheme.typography.bodyMedium)
                    else SnapshotContent(state.snapshot)
                    OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.health_refresh)) }
                }
            }
        }
    }
}

@Composable
private fun SnapshotContent(s: HealthSnapshot) {
    val steps = s.steps ?: 0L
    Text(stringResource(R.string.health_steps_of, steps, STEP_GOAL), style = MaterialTheme.typography.titleLarge)
    LinearProgressIndicator(progress = { (steps.toFloat() / STEP_GOAL).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        s.activeKcal?.let { Fact(stringResource(R.string.health_active_kcal), it.toString()) }
        s.distanceM?.let { Fact(stringResource(R.string.health_distance), stringResource(R.string.walk_km, it / 1000)) }
        s.sleep?.let { Fact(stringResource(R.string.health_sleep), stringResource(R.string.health_sleep_fmt, it.toHours(), it.toMinutesPart())) }
    }
    s.lastHr?.let { last ->
        val range = if (s.minHr != null && s.maxHr != null) " · ${stringResource(R.string.health_hr_range, s.avgHr ?: 0, s.minHr, s.maxHr)}" else ""
        Text("❤️ ${stringResource(R.string.health_hr_last, last)}$range", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

package com.sportassistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportassistant.BuildConfig
import com.sportassistant.R
import com.sportassistant.UpdateState
import com.sportassistant.update.UpdateInfo
import com.sportassistant.data.Category
import com.sportassistant.data.Exercise
import com.sportassistant.data.Exercises
import com.sportassistant.data.Profile
import com.sportassistant.data.Stats
import com.sportassistant.health.HealthUiState
import com.sportassistant.pose.RepTrackers
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
private fun exerciseDose(ex: Exercise): String = when {
    ex.reps > 0 -> stringResource(R.string.workout_reps, ex.reps)
    ex.seconds >= 120 -> stringResource(R.string.workout_minutes, ex.seconds / 60)
    else -> stringResource(R.string.workout_seconds, ex.seconds)
}

@Composable
private fun ExerciseRow(ex: Exercise, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(ex.emoji, fontSize = 32.sp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(ex.name), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(ex.category.label), style = MaterialTheme.typography.bodySmall)
            }
            Text(exerciseDose(ex), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun TodayScreen(
    plan: List<Exercise>,
    stats: Stats,
    health: HealthUiState,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onStart: () -> Unit,
    onOpen: (Exercise) -> Unit,
) {
    val minutes = (plan.sumOf { it.estimatedSeconds } / 60).coerceAtLeast(1)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.today_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text("${stringResource(R.string.today_streak, stats.currentStreak)}  ·  ${stringResource(R.string.today_points, stats.points)}  ·  ${stringResource(R.string.today_minutes, minutes)}")
        }
        item { HealthCard(health, onConnect, onRefresh) }
        items(plan, key = { it.id }) { ex -> ExerciseRow(ex) { onOpen(ex) } }
        item { Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.today_start)) } }
        item { Text(stringResource(R.string.disclaimer_medical), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun LibraryScreen(onOpen: (Exercise) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Category.entries.forEach { cat ->
            val list = Exercises.all.filter { it.category == cat }
            if (list.isEmpty()) return@forEach
            item(key = "h_${cat.name}") { Text(stringResource(cat.label), style = MaterialTheme.typography.titleLarge) }
            items(list, key = { it.id }) { ex -> ExerciseRow(ex) { onOpen(ex) } }
        }
    }
}

@Composable
fun ExerciseDetailScreen(ex: Exercise, onBack: () -> Unit, onCamera: (Exercise) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(ex.emoji, fontSize = 72.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(stringResource(ex.name), style = MaterialTheme.typography.headlineMedium)
        Text("${stringResource(ex.category.label)} · ${exerciseDose(ex)}", style = MaterialTheme.typography.labelLarge)
        Text(stringResource(ex.steps), style = MaterialTheme.typography.bodyLarge)
        if (RepTrackers.forExercise(ex.id) != null) CameraButton(ex, onCamera)
    }
}

@Composable
private fun CameraButton(ex: Exercise, onCamera: (Exercise) -> Unit) {
    OutlinedButton(onClick = { onCamera(ex) }, modifier = Modifier.fillMaxWidth()) {
        Text("📷  ${stringResource(R.string.camera_count)}")
    }
}

@Composable
fun WorkoutScreen(plan: List<Exercise>, onFinish: (exercises: Int, seconds: Int) -> Int, onClose: () -> Unit, onCamera: (Exercise) -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var awarded by rememberSaveable { mutableIntStateOf(-1) }
    var remaining by rememberSaveable { mutableIntStateOf(0) }

    // The generator always yields at least the mobility block; guard anyway.
    if (plan.isEmpty()) return

    if (awarded >= 0) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉", fontSize = 72.sp)
            Text(stringResource(R.string.workout_complete_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.workout_complete_msg, awarded), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onClose) { Text(stringResource(R.string.workout_close)) }
        }
        return
    }

    val ex = plan[index]
    val last = index == plan.lastIndex

    // Countdown for timed exercises; restarts whenever the exercise changes.
    LaunchedEffect(index) {
        remaining = ex.seconds
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.workout_step, index + 1, plan.size), style = MaterialTheme.typography.labelLarge)
        Text(ex.emoji, fontSize = 72.sp)
        Text(stringResource(ex.name), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (ex.seconds > 0) {
            Text(
                if (ex.seconds >= 120) "%d:%02d".format(remaining / 60, remaining % 60) else stringResource(R.string.workout_seconds, remaining),
                style = MaterialTheme.typography.displayMedium,
            )
        } else {
            Text(exerciseDose(ex), style = MaterialTheme.typography.displaySmall)
        }
        Text(stringResource(ex.steps), modifier = Modifier.fillMaxWidth())
        if (RepTrackers.forExercise(ex.id) != null) CameraButton(ex, onCamera)
        Button(
            onClick = {
                if (last) awarded = onFinish(plan.size, plan.sumOf { it.estimatedSeconds }) else index++
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (last) R.string.workout_finish else R.string.workout_next)) }
    }
}

@Composable
fun ProgressScreen(stats: Stats) {
    val today = LocalDate.now()
    val badges = listOf(
        R.string.badge_first to (stats.workouts >= 1),
        R.string.badge_streak3 to (stats.bestStreak >= 3),
        R.string.badge_streak7 to (stats.bestStreak >= 7),
        R.string.badge_workouts10 to (stats.workouts >= 10),
        R.string.badge_points500 to (stats.points >= 500),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), stats.workouts.toString(), stringResource(R.string.progress_workouts))
            StatCard(Modifier.weight(1f), stats.points.toString(), stringResource(R.string.progress_points))
            StatCard(Modifier.weight(1f), stats.currentStreak.toString(), stringResource(R.string.progress_streak))
        }
        Text(stringResource(R.string.progress_last7), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            stats.last7.forEachIndexed { i, done ->
                val d = today.minusDays((6 - i).toLong())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (done) "✅" else "⚪", fontSize = 24.sp)
                    Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(stringResource(R.string.progress_badges), style = MaterialTheme.typography.titleMedium)
        badges.forEach { (label, earned) ->
            Text("${if (earned) "🏅" else "🔒"}  ${stringResource(label)}", color = if (earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String) {
    Card(modifier) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SettingsScreen(
    profile: Profile,
    update: UpdateState,
    onChange: (Profile) -> Unit,
    onRedo: () -> Unit,
    onCheckUpdate: () -> Unit,
    onInstall: (UpdateInfo) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.settings_reminders), style = MaterialTheme.typography.titleMedium)
        SwitchRow(R.string.rem_move, profile.remMove) { onChange(profile.copy(remMove = it)) }
        SwitchRow(R.string.rem_water, profile.remWater) { onChange(profile.copy(remWater = it)) }
        SwitchRow(R.string.rem_teeth, profile.remTeeth) { onChange(profile.copy(remTeeth = it)) }
        Text(stringResource(R.string.settings_reminders_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onRedo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_redo)) }

        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
        UpdateSection(update, onCheckUpdate, onInstall)
    }
}

@Composable
private fun UpdateSection(update: UpdateState, onCheck: () -> Unit, onInstall: (UpdateInfo) -> Unit) {
    val busy = update is UpdateState.Checking || update is UpdateState.Downloading
    val message = when (update) {
        UpdateState.Idle -> null
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.NotConfigured -> stringResource(R.string.update_not_configured)
        UpdateState.UpToDate -> stringResource(R.string.update_uptodate)
        is UpdateState.Available -> stringResource(R.string.update_available, update.info.versionName)
        UpdateState.Downloading -> stringResource(R.string.update_downloading)
        is UpdateState.NeedsInstallPermission -> stringResource(R.string.update_allow_install)
        UpdateState.HashMismatch -> stringResource(R.string.update_hash_mismatch)
        UpdateState.Error -> stringResource(R.string.update_error)
    }
    val pending = (update as? UpdateState.Available)?.info ?: (update as? UpdateState.NeedsInstallPermission)?.info

    OutlinedButton(onClick = onCheck, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.update_check))
    }
    if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium)
    if (update is UpdateState.Available) update.info.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (pending != null) {
        Button(onClick = { onInstall(pending) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_install)) }
    }
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked, onChecked)
    }
}

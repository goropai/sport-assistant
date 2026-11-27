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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.sportassistant.data.PlanLevel
import com.sportassistant.data.Stats
import com.sportassistant.data.WeekSummary
import com.sportassistant.health.HealthUiState
import com.sportassistant.pose.RepTrackers
import kotlinx.coroutines.delay
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.sportassistant.BackupFailure
import com.sportassistant.BackupState
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
            ExerciseThumb(ex)
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
    level: PlanLevel,
    health: HealthUiState,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onStart: () -> Unit,
    onOpen: (Exercise) -> Unit,
    onLibrary: () -> Unit,
) {
    val minutes = (plan.sumOf { it.estimatedSeconds } / 60).coerceAtLeast(1)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.today_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text("${stringResource(R.string.today_streak, stats.currentStreak)}  ·  ${stringResource(R.string.today_points, stats.points)}  ·  ${stringResource(R.string.today_minutes, minutes)}")
        }
        level.note?.let { note -> item { IconText(AppIcons.Goal, stringResource(note)) } }
        item { HealthCard(health, onConnect, onRefresh) }
        items(plan, key = { it.id }) { ex -> ExerciseRow(ex) { onOpen(ex) } }
        item { Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.today_start)) } }
        item { OutlinedButton(onClick = onLibrary, modifier = Modifier.fillMaxWidth()) { ButtonLabel(AppIcons.Library, stringResource(R.string.tab_library)) } }
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
fun ExerciseDetailScreen(ex: Exercise, onBack: () -> Unit, onCamera: (Exercise) -> Unit, onNose: (Exercise) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        ExerciseVisual(ex)
        Text(stringResource(ex.name), style = MaterialTheme.typography.headlineMedium)
        Text("${stringResource(ex.category.label)} · ${exerciseDose(ex)}", style = MaterialTheme.typography.labelLarge)
        Text(stringResource(ex.steps), style = MaterialTheme.typography.bodyLarge)
        CountButtons(ex, onCamera, onNose)
    }
}

/** Ways to have reps counted automatically: by camera, and for push-ups also by nose touch. */
@Composable
private fun CountButtons(ex: Exercise, onCamera: (Exercise) -> Unit, onNose: (Exercise) -> Unit) {
    if (RepTrackers.forExercise(ex.id) != null) {
        OutlinedButton(onClick = { onCamera(ex) }, modifier = Modifier.fillMaxWidth()) {
            ButtonLabel(AppIcons.Camera, stringResource(R.string.camera_count))
        }
    }
    if (ex.id == NOSE_TOUCH_EXERCISE) {
        OutlinedButton(onClick = { onNose(ex) }, modifier = Modifier.fillMaxWidth()) {
            ButtonLabel(AppIcons.Nose, stringResource(R.string.nose_count))
        }
    }
}

/** The one exercise where the nose can touch a phone lying on the floor. */
const val NOSE_TOUCH_EXERCISE = "pushups"

@Composable
fun WorkoutScreen(
    plan: List<Exercise>,
    onFinish: (exercises: Int, seconds: Int) -> Int,
    onClose: () -> Unit,
    onCamera: (Exercise) -> Unit,
    onNose: (Exercise) -> Unit,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var awarded by rememberSaveable { mutableIntStateOf(-1) }
    var remaining by rememberSaveable { mutableIntStateOf(0) }

    // The generator always yields at least the mobility block; guard anyway.
    if (plan.isEmpty()) return

    if (awarded >= 0) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(AppIcons.Celebrate, 120.dp)
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
        ExerciseVisual(ex)
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
        CountButtons(ex, onCamera, onNose)
        Button(
            onClick = {
                if (last) awarded = onFinish(plan.size, plan.sumOf { it.estimatedSeconds }) else index++
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (last) R.string.workout_finish else R.string.workout_next)) }
    }
}

@Composable
fun ProgressScreen(stats: Stats, report: WeekSummary, hasWatchData: Boolean, onObservations: () -> Unit) {
    val today = LocalDate.now()
    val badges = listOf(
        Triple(R.string.badge_first, AppIcons.BadgeFirst, stats.workouts >= 1),
        Triple(R.string.badge_streak3, AppIcons.BadgeStreak3, stats.bestStreak >= 3),
        Triple(R.string.badge_streak7, AppIcons.BadgeStreak7, stats.bestStreak >= 7),
        Triple(R.string.badge_workouts10, AppIcons.BadgeWorkouts, stats.workouts >= 10),
        Triple(R.string.badge_points500, AppIcons.BadgePoints, stats.points >= 500),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), stats.workouts.toString(), stringResource(R.string.progress_workouts))
            StatCard(Modifier.weight(1f), stats.points.toString(), stringResource(R.string.progress_points))
            StatCard(Modifier.weight(1f), stats.currentStreak.toString(), stringResource(R.string.progress_streak))
        }
        WeeklyReportCard(report, today, hasWatchData)
        Text(stringResource(R.string.progress_last7), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            stats.last7.forEachIndexed { i, done ->
                val d = today.minusDays((6 - i).toLong())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (done) AppIcons.Done else AppIcons.NotDone,
                        contentDescription = null,
                        tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OutlinedButton(onClick = onObservations, modifier = Modifier.fillMaxWidth()) { ButtonLabel(AppIcons.Posture, stringResource(R.string.obs_title)) }
        Text(stringResource(R.string.progress_badges), style = MaterialTheme.typography.titleMedium)
        badges.forEach { (label, icon, earned) ->
            IconText(
                icon = if (earned) icon else AppIcons.Locked,
                text = stringResource(label),
                color = if (earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                tint = if (earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                iconSize = 24.dp,
            )
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
    backup: BackupState,
    onExport: (Uri) -> Unit,
    onReadBackup: (Uri) -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissBackup: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.settings_reminders), style = MaterialTheme.typography.titleMedium)
        ReminderSettings(profile, onChange)
        OutlinedButton(onClick = onRedo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_redo)) }

        Text(stringResource(R.string.settings_ai), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_ai_hint), style = MaterialTheme.typography.bodySmall)
        var key by rememberSaveable(profile.geminiKey) { mutableStateOf(profile.geminiKey) }
        OutlinedTextField(
            key, { key = it.trim() }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_ai_key)) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(
            onClick = { onChange(profile.copy(geminiKey = key)) },
            enabled = key != profile.geminiKey,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_save)) }

        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
        BackupSection(backup, onExport, onReadBackup, onConfirmRestore, onDismissBackup)

        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
        UpdateSection(update, onCheckUpdate, onInstall)
    }
}

@Composable
private fun BackupSection(
    state: BackupState,
    onExport: (Uri) -> Unit,
    onReadBackup: (Uri) -> Unit,
    onConfirmRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(onExport) }
    // Some file providers report .json as octet-stream, so do not filter by type.
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onReadBackup) }
    val busy = state == BackupState.Working

    Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall)
    Button(
        onClick = { export.launch("sport-assistant-${LocalDate.now()}.json") },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.backup_export)) }
    OutlinedButton(onClick = { import.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.backup_restore))
    }

    when (state) {
        BackupState.Working -> Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodyMedium)
        BackupState.Exported -> Text(stringResource(R.string.backup_exported), style = MaterialTheme.typography.bodyMedium)
        BackupState.Restored -> Text(stringResource(R.string.backup_restored), style = MaterialTheme.typography.bodyMedium)
        is BackupState.Failed -> Text(
            stringResource(
                when (state.reason) {
                    BackupFailure.READ -> R.string.backup_err_read
                    BackupFailure.WRITE -> R.string.backup_err_write
                    BackupFailure.NOT_A_BACKUP -> R.string.backup_err_not_backup
                    BackupFailure.NEWER_VERSION -> R.string.backup_err_newer
                    BackupFailure.CORRUPT -> R.string.backup_err_corrupt
                    BackupFailure.TOO_LARGE -> R.string.backup_err_large
                },
            ),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        else -> Unit
    }

    if (state is BackupState.Preview) {
        val d = state.data
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_text, d.logs.size, d.walks.size, d.food.size, d.observations.size)) },
            confirmButton = { TextButton(onClick = onConfirmRestore) { Text(stringResource(R.string.backup_confirm_ok)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.food_cancel)) } },
        )
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
internal fun SwitchRow(label: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked, onChecked)
    }
}

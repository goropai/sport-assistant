package com.sportassistant.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sportassistant.R
import com.sportassistant.data.Complaint
import com.sportassistant.data.Profile
import com.sportassistant.data.Sex

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(initial: Profile, onDone: (Profile) -> Unit) {
    var age by rememberSaveable { mutableStateOf(initial.age.toString()) }
    var height by rememberSaveable { mutableStateOf(initial.heightCm.toString()) }
    var weight by rememberSaveable { mutableStateOf(initial.weightKg.toString()) }
    var sit by rememberSaveable { mutableFloatStateOf(initial.sitHours.toFloat()) }
    var activity by rememberSaveable { mutableIntStateOf(initial.activity) }
    var complaints by rememberSaveable { mutableStateOf(initial.complaints.map { it.name }.toSet()) }
    var sex by rememberSaveable { mutableStateOf(initial.sex.name) }

    fun build() = initial.copy(
        age = age.toIntOrNull()?.coerceIn(10, 100) ?: initial.age,
        heightCm = height.toIntOrNull()?.coerceIn(100, 250) ?: initial.heightCm,
        weightKg = weight.toIntOrNull()?.coerceIn(30, 300) ?: initial.weightKg,
        sitHours = sit.toInt(),
        activity = activity,
        complaints = Complaint.entries.filter { it.name in complaints }.toSet(),
        sex = Sex.entries.firstOrNull { it.name == sex } ?: Sex.UNSPECIFIED,
    )

    // The permission result is irrelevant to saving: reminders simply stay silent if denied.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone(build()) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onb_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onb_subtitle))

        val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text(stringResource(R.string.onb_age)) }, singleLine = true, keyboardOptions = numeric)
            OutlinedTextField(height, { height = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text(stringResource(R.string.onb_height)) }, singleLine = true, keyboardOptions = numeric)
            OutlinedTextField(weight, { weight = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text(stringResource(R.string.onb_weight)) }, singleLine = true, keyboardOptions = numeric)
        }

        Text(stringResource(R.string.onb_sex))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { s -> FilterChip(sex == s.name, { sex = s.name }, label = { Text(stringResource(s.label)) }) }
        }

        Text(stringResource(R.string.onb_sitting, sit.toInt()))
        Slider(sit, { sit = it }, valueRange = 1f..14f, steps = 12)

        Text(stringResource(R.string.onb_activity))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.activity_low, R.string.activity_medium, R.string.activity_high).forEachIndexed { i, label ->
                FilterChip(activity == i, { activity = i }, label = { Text(stringResource(label)) })
            }
        }

        Text(stringResource(R.string.onb_complaints))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Complaint.entries.forEach { c ->
                FilterChip(
                    selected = c.name in complaints,
                    onClick = { complaints = if (c.name in complaints) complaints - c.name else complaints + c.name },
                    label = { Text(stringResource(c.label)) },
                )
            }
        }

        Text(stringResource(R.string.disclaimer_medical), style = MaterialTheme.typography.bodySmall)

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else onDone(build())
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.onb_start)) }
    }
}

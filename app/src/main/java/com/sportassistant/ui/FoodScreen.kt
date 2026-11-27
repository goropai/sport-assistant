package com.sportassistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sportassistant.EstimateState
import com.sportassistant.FoodAnalysisState
import com.sportassistant.R
import com.sportassistant.ai.AnalysisError
import com.sportassistant.ai.FoodItem
import com.sportassistant.data.Energy
import com.sportassistant.data.FoodDb
import com.sportassistant.data.Per100
import com.sportassistant.data.FoodEntry
import com.sportassistant.data.Profile
import com.sportassistant.health.HealthUiState
import java.io.File
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FoodScreen(
    profile: Profile,
    entries: List<FoodEntry>,
    health: HealthUiState,
    walkKcalToday: Int,
    analysis: FoodAnalysisState,
    estimate: EstimateState,
    onPhoto: (Uri) -> Unit,
    onSave: (List<FoodItem>) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit,
    onEstimate: (String) -> Unit,
    onEstimateHandled: () -> Unit,
) {
    val context = LocalContext.current
    var showManual by rememberSaveable { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    val aiConfigured = profile.geminiKey.isNotBlank()

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingPhoto?.let(onPhoto)
    }
    fun launchCamera() {
        val file = File(context.cacheDir, "food/photo.jpg").apply { parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = uri
        takePicture.launch(uri)
    }
    // The manifest declares CAMERA (rep counting), so the system camera app requires it to be granted too.
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) launchCamera() }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(onPhoto) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.food_title), style = MaterialTheme.typography.headlineMedium) }
        item { BalanceCard(profile, entries, health, walkKcalToday) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                        else cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                    enabled = aiConfigured && analysis != FoodAnalysisState.Analyzing,
                    modifier = Modifier.weight(1f),
                ) { ButtonLabel(AppIcons.Camera, stringResource(R.string.food_photo)) }
                Button(
                    onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = aiConfigured && analysis != FoodAnalysisState.Analyzing,
                    modifier = Modifier.weight(1f),
                ) { ButtonLabel(AppIcons.Gallery, stringResource(R.string.food_gallery)) }
                OutlinedButton(onClick = { showManual = !showManual }, modifier = Modifier.weight(1f)) {
                    ButtonLabel(AppIcons.Manual, stringResource(R.string.food_manual))
                }
            }
        }
        if (!aiConfigured) item { Text(stringResource(R.string.food_not_configured), style = MaterialTheme.typography.bodySmall) }

        when (analysis) {
            FoodAnalysisState.Idle -> Unit
            FoodAnalysisState.Analyzing -> item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text(stringResource(R.string.food_analyzing))
                }
            }
            is FoodAnalysisState.Review -> item { ReviewCard(analysis.items, onSave, onDismiss) }
            FoodAnalysisState.NoFood -> item { Notice(stringResource(R.string.food_no_food), onDismiss) }
            is FoodAnalysisState.Failed -> item { Notice(stringResource(errorText(analysis.error)), onDismiss) }
        }

        if (showManual) item {
            ManualCard(
                aiConfigured = aiConfigured,
                estimate = estimate,
                onEstimate = onEstimate,
                onEstimateHandled = onEstimateHandled,
                onAdd = { onSave(listOf(it)); showManual = false },
            )
        }

        item { Text(stringResource(R.string.food_today_entries), style = MaterialTheme.typography.titleMedium) }
        if (entries.isEmpty()) item { Text(stringResource(R.string.food_empty)) }
        items(entries, key = { it.id }) { EntryRow(it, onDelete) }
        item { Text(stringResource(R.string.food_disclaimer), style = MaterialTheme.typography.bodySmall) }
    }
}

internal fun errorText(e: AnalysisError): Int = when (e) {
    AnalysisError.UNAUTHORIZED -> R.string.food_err_auth
    AnalysisError.RATE_LIMITED -> R.string.food_err_rate
    AnalysisError.UNAVAILABLE -> R.string.food_err_unavailable
    AnalysisError.NETWORK -> R.string.food_err_network
    AnalysisError.BAD_RESPONSE -> R.string.food_err_bad
}

@Composable
private fun BalanceCard(profile: Profile, entries: List<FoodEntry>, health: HealthUiState, walkKcalToday: Int) {
    val bmr = Energy.bmr(profile.sex, profile.weightKg, profile.heightCm, profile.age)
    val resting = Energy.restingSoFar(bmr, LocalTime.now().toSecondOfDay())
    val watchKcal = (health as? HealthUiState.Data)?.snapshot?.activeKcal
    val active = watchKcal ?: walkKcalToday
    val eaten = entries.sumOf { it.kcal }
    val spent = resting + active
    val net = eaten - spent

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(stringResource(R.string.food_eaten), stringResource(R.string.food_kcal_fmt, eaten))
                Metric(stringResource(R.string.food_spent), stringResource(R.string.food_kcal_fmt, spent))
                Metric(stringResource(R.string.food_net), (if (net > 0) "+" else "") + stringResource(R.string.food_kcal_fmt, net))
            }
            Text(
                stringResource(R.string.food_spent_breakdown, resting, active) + " · " +
                    stringResource(if (watchKcal != null) R.string.food_source_watch else R.string.food_source_walks),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.food_macros, entries.sumOf { it.proteinG }, entries.sumOf { it.fatG }, entries.sumOf { it.carbsG }),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Notice(text: String, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.food_cancel)) }
        }
    }
}

/** An AI-suggested item the user may correct; changing grams rescales calories and macros proportionally. */
private class Draft(val orig: FoodItem) {
    var name by mutableStateOf(orig.name)
    var grams by mutableStateOf(orig.grams.toString())

    fun toItem(): FoodItem {
        val g = grams.toIntOrNull()?.coerceIn(0, 5000) ?: orig.grams
        val f = if (orig.grams > 0) g.toDouble() / orig.grams else 1.0
        return FoodItem(
            name = name.trim().ifBlank { orig.name },
            grams = g,
            kcal = (orig.kcal * f).toInt(),
            proteinG = (orig.proteinG * f).toInt(),
            fatG = (orig.fatG * f).toInt(),
            carbsG = (orig.carbsG * f).toInt(),
        )
    }
}

@Composable
private fun ReviewCard(items: List<FoodItem>, onSave: (List<FoodItem>) -> Unit, onCancel: () -> Unit) {
    val drafts = remember(items) { items.map { Draft(it) }.toMutableStateList() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.food_review_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.food_review_hint), style = MaterialTheme.typography.bodySmall)
            drafts.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(d.name, { d.name = it.take(80) }, Modifier.weight(2f), label = { Text(stringResource(R.string.food_name)) }, singleLine = true)
                    OutlinedTextField(
                        d.grams, { d.grams = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f),
                        label = { Text(stringResource(R.string.food_grams)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    IconButton(onClick = { drafts.remove(d) }) { Icon(AppIcons.Delete, contentDescription = stringResource(R.string.food_delete)) }
                }
                Text(stringResource(R.string.food_kcal_fmt, d.toItem().kcal), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(drafts.map { it.toItem() }) }, enabled = drafts.isNotEmpty()) { Text(stringResource(R.string.food_save)) }
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.food_cancel)) }
            }
        }
    }
}

private val Per100Saver = listSaver<Per100?, Double>(
    save = { p -> if (p == null) emptyList() else listOf(p.kcal, p.proteinG, p.fatG, p.carbsG) },
    restore = { l -> if (l.size == 4) Per100(l[0], l[1], l[2], l[3]) else null },
)

/**
 * Manual entry with automatic calories: pick a food from the offline table (or ask the AI), enter the
 * weight, and kcal is calculated. The kcal field stays editable; a manual value overrides the calculation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualCard(
    aiConfigured: Boolean,
    estimate: EstimateState,
    onEstimate: (String) -> Unit,
    onEstimateHandled: () -> Unit,
    onAdd: (FoodItem) -> Unit,
) {
    val lang = if (Locale.getDefault().language == "uk") "uk" else "en"
    var name by rememberSaveable { mutableStateOf("") }
    var grams by rememberSaveable { mutableStateOf("") }
    var kcalOverride by rememberSaveable { mutableStateOf<String?>(null) }
    // Per-100 g values come from a table match or an AI lookup.
    var per by rememberSaveable(stateSaver = Per100Saver) { mutableStateOf<Per100?>(null) }

    LaunchedEffect(estimate) {
        if (estimate is EstimateState.Done) {
            per = estimate.per100
            kcalOverride = null
            if (grams.isBlank()) grams = "100"
            onEstimateHandled()
        }
    }

    val g = grams.toIntOrNull() ?: 0
    val auto = per?.takeIf { g > 0 }?.kcalFor(g)
    val kcalText = kcalOverride ?: auto?.toString().orEmpty()
    val kcal = kcalText.toIntOrNull() ?: 0
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                name,
                {
                    name = it.take(80)
                    per = null // a different name means a different food
                    kcalOverride = null
                    if (estimate != EstimateState.Idle) onEstimateHandled()
                },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.food_name)) },
                singleLine = true,
            )

            val suggestions = if (per == null) FoodDb.search(name, lang) else emptyList()
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { food ->
                        SuggestionChip(
                            onClick = {
                                name = food.name(lang)
                                per = food.per100
                                kcalOverride = null
                                if (grams.isBlank()) grams = food.portionG.toString()
                            },
                            label = { Text(stringResource(R.string.food_suggestion, food.name(lang), food.per100.kcal.roundToInt())) },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    grams, { grams = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f),
                    label = { Text(stringResource(R.string.food_grams)) }, singleLine = true, keyboardOptions = numeric,
                )
                OutlinedTextField(
                    kcalText, { kcalOverride = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f),
                    label = { Text(stringResource(R.string.food_kcal_label)) }, singleLine = true, keyboardOptions = numeric,
                )
            }

            per?.let { p ->
                Text(
                    stringResource(R.string.food_per100_info, p.kcal.roundToInt(), p.proteinG.roundToInt(), p.fatG.roundToInt(), p.carbsG.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (kcalOverride != null) {
                    TextButton(onClick = { kcalOverride = null }) { ButtonLabel(AppIcons.Recalculate, stringResource(R.string.food_kcal_reset)) }
                } else if (auto != null) {
                    // Spell the sum out so it is clear the total already uses the entered weight.
                    Text(
                        stringResource(R.string.food_calc_line, g, p.kcal.roundToInt(), auto),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(stringResource(R.string.food_kcal_auto), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (per == null && name.isBlank()) {
                Text(stringResource(R.string.food_manual_hint), style = MaterialTheme.typography.bodySmall)
            }

            if (per == null && aiConfigured && name.trim().length >= 2) {
                when (estimate) {
                    EstimateState.Loading -> Text(stringResource(R.string.food_ai_loading), style = MaterialTheme.typography.bodySmall)
                    EstimateState.NotFound -> Text(stringResource(R.string.food_ai_notfound), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    is EstimateState.Failed -> Text(stringResource(errorText(estimate.error)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else -> Unit
                }
                OutlinedButton(onClick = { onEstimate(name.trim()) }, enabled = estimate != EstimateState.Loading) {
                    ButtonLabel(AppIcons.Ai, stringResource(R.string.food_ai_estimate))
                }
            }

            Button(
                onClick = {
                    val fromTable = kcalOverride == null && g > 0
                    onAdd(
                        FoodItem(
                            name = name.trim(),
                            grams = g,
                            kcal = kcal,
                            // Macros are only known when kcal was not overridden by hand.
                            proteinG = if (fromTable) per?.proteinFor(g) ?: 0 else 0,
                            fatG = if (fromTable) per?.fatFor(g) ?: 0 else 0,
                            carbsG = if (fromTable) per?.carbsFor(g) ?: 0 else 0,
                        ),
                    )
                    name = ""; grams = ""; kcalOverride = null; per = null
                },
                enabled = name.isNotBlank() && kcal > 0,
            ) { Text(stringResource(R.string.food_add)) }
        }
    }
}

@Composable
private fun EntryRow(e: FoodEntry, onDelete: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.titleSmall)
                val grams = if (e.grams > 0) stringResource(R.string.food_grams_fmt, e.grams) + " · " else ""
                Text("$grams${stringResource(R.string.food_kcal_fmt, e.kcal)}", style = MaterialTheme.typography.bodySmall)
                if (e.proteinG + e.fatG + e.carbsG > 0) {
                    Text(stringResource(R.string.food_macros, e.proteinG, e.fatG, e.carbsG), style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = { onDelete(e.id) }) { Icon(AppIcons.Delete, contentDescription = stringResource(R.string.food_delete)) }
        }
    }
}

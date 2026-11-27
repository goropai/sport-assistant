package com.sportassistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import com.sportassistant.data.FoodEntry
import com.sportassistant.data.FoodHistory
import com.sportassistant.data.ManualRow
import com.sportassistant.data.ManualRows
import com.sportassistant.data.Meal
import com.sportassistant.data.MealPicker
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
    frequent: List<FoodEntry>,
    health: HealthUiState,
    walkKcalToday: Int,
    analysis: FoodAnalysisState,
    estimate: EstimateState,
    onPhoto: (Uri) -> Unit,
    onSave: (List<FoodItem>, Meal) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit,
    onEstimate: (String) -> Unit,
    onEstimateHandled: () -> Unit,
    onUpdate: (FoodEntry) -> Unit,
) {
    val context = LocalContext.current
    var showManual by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    val aiConfigured = profile.geminiKey.isNotBlank()
    // Which main meals are already in today's diary: decides whether a new entry defaults to that meal or a snack.
    val loggedMeals = remember(entries) { entries.map { Meal.of(it.meal) }.toSet() }

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
            is FoodAnalysisState.Review -> item { ReviewCard(analysis.items, loggedMeals, onSave, onDismiss) }
            FoodAnalysisState.NoFood -> item { Notice(stringResource(R.string.food_no_food), onDismiss) }
            is FoodAnalysisState.Failed -> item { Notice(stringResource(errorText(analysis.error)), onDismiss) }
        }

        if (showManual) item {
            ManualCard(
                aiConfigured = aiConfigured,
                frequent = frequent,
                loggedMeals = loggedMeals,
                estimate = estimate,
                onEstimate = onEstimate,
                onEstimateHandled = onEstimateHandled,
                onAdd = { items, meal -> onSave(items, meal); showManual = false },
            )
        }

        item { Text(stringResource(R.string.food_today_entries), style = MaterialTheme.typography.titleMedium) }
        if (entries.isEmpty()) item { Text(stringResource(R.string.food_empty)) }
        // The diary is grouped by meal, in the order of the day, each group with its own total.
        Meal.entries.forEach { meal ->
            val inMeal = entries.filter { Meal.of(it.meal) == meal }
            if (inMeal.isNotEmpty()) {
                item(key = "meal_${meal.name}") {
                    Text(
                        stringResource(R.string.food_meal_total, stringResource(meal.label), inMeal.sumOf { it.kcal }),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(inMeal, key = { it.id }) { EntryRow(it, onDelete) { editingId = it.id } }
            }
        }
        if (entries.isNotEmpty()) item { Text(stringResource(R.string.food_tap_to_edit), style = MaterialTheme.typography.bodySmall) }
        item { Text(stringResource(R.string.food_disclaimer), style = MaterialTheme.typography.bodySmall) }
    }

    // The entry may have been deleted while the dialog was open; then there is nothing to edit.
    entries.firstOrNull { it.id == editingId }?.let { entry ->
        EditFoodDialog(entry, onSave = { onUpdate(it); editingId = null }, onDismiss = { editingId = null })
    }
}

/** A drop-down to pick the meal an entry belongs to. */
@Composable
fun MealDropdown(selected: Meal, onSelect: (Meal) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.food_meal_label), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(selected.label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Icon(AppIcons.Dropdown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Meal.entries.forEach { meal ->
                DropdownMenuItem(
                    text = { Text(stringResource(meal.label)) },
                    onClick = {
                        onSelect(meal)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Edit a saved entry. Calories follow the weight until the user types their own number. */
@Composable
private fun EditFoodDialog(entry: FoodEntry, onSave: (FoodEntry) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable(entry.id) { mutableStateOf(entry.name) }
    var grams by rememberSaveable(entry.id) { mutableStateOf(if (entry.grams > 0) entry.grams.toString() else "") }
    var kcal by rememberSaveable(entry.id) { mutableStateOf(entry.kcal.toString()) }
    var kcalEdited by rememberSaveable(entry.id) { mutableStateOf(false) }
    var meal by rememberSaveable(entry.id) { mutableStateOf(entry.meal) }
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)
    val canSave = name.isNotBlank() && kcal.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.food_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MealDropdown(Meal.of(meal), { meal = it.name })
                OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.food_name)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        grams,
                        {
                            grams = it.filter(Char::isDigit).take(4)
                            if (!kcalEdited) kcal = FoodHistory.withGrams(entry, grams.toIntOrNull() ?: 0).kcal.toString()
                        },
                        Modifier.weight(1f),
                        label = { Text(stringResource(R.string.food_grams)) }, singleLine = true, keyboardOptions = numeric,
                    )
                    OutlinedTextField(
                        kcal,
                        { kcal = it.filter(Char::isDigit).take(4); kcalEdited = true },
                        Modifier.weight(1f),
                        label = { Text(stringResource(R.string.food_kcal_label)) }, singleLine = true, keyboardOptions = numeric,
                    )
                }
                if (entry.grams > 0 && !kcalEdited) {
                    Text(stringResource(R.string.food_kcal_auto), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val scaled = FoodHistory.withGrams(entry, grams.toIntOrNull() ?: 0)
                    onSave(scaled.copy(name = name.trim(), kcal = kcal.toIntOrNull() ?: scaled.kcal, meal = meal))
                },
            ) { Text(stringResource(R.string.food_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.food_cancel)) } },
    )
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
private fun ReviewCard(items: List<FoodItem>, loggedMeals: Set<Meal>, onSave: (List<FoodItem>, Meal) -> Unit, onCancel: () -> Unit) {
    val drafts = remember(items) { items.map { Draft(it) }.toMutableStateList() }
    var mealChoice by rememberSaveable { mutableStateOf<String?>(null) }
    val meal = mealChoice?.let(Meal::of) ?: MealPicker.defaultFor(LocalTime.now(), loggedMeals)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.food_review_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.food_review_hint), style = MaterialTheme.typography.bodySmall)
            MealDropdown(meal, { mealChoice = it.name })
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
                Button(onClick = { onSave(drafts.map { it.toItem() }, meal) }, enabled = drafts.isNotEmpty()) { Text(stringResource(R.string.food_save)) }
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.food_cancel)) }
            }
        }
    }
}

private val RowsSaver = listSaver<SnapshotStateList<ManualRow>, ManualRow>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/**
 * Manual entry of one or more foods for a chosen meal. Every section is a food (name, grams, kcal) whose
 * calories are calculated from the weight; "+" adds another section and one tap saves them all.
 */
@Composable
private fun ManualCard(
    aiConfigured: Boolean,
    frequent: List<FoodEntry>,
    loggedMeals: Set<Meal>,
    estimate: EstimateState,
    onEstimate: (String) -> Unit,
    onEstimateHandled: () -> Unit,
    onAdd: (List<FoodItem>, Meal) -> Unit,
) {
    val rows = rememberSaveable(saver = RowsSaver) { mutableStateListOf(ManualRow(id = 1L)) }
    var nextId by rememberSaveable { mutableLongStateOf(2L) }
    var mealChoice by rememberSaveable { mutableStateOf<String?>(null) }
    var estimatingId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Until the user picks one, the meal follows the time of day and what is already in the diary.
    val meal = mealChoice?.let(Meal::of) ?: MealPicker.defaultFor(LocalTime.now(), loggedMeals)
    val validCount = ManualRows.validCount(rows)

    // An AI lookup answers for the row that asked, found by id because rows may have been removed meanwhile.
    LaunchedEffect(estimate) {
        if (estimate is EstimateState.Done) {
            val i = rows.indexOfFirst { it.id == estimatingId }
            if (i >= 0) {
                val row = rows[i]
                rows[i] = row.copy(kcalOverride = null, grams = row.grams.ifBlank { "100" }).withPer100(estimate.per100)
            }
            estimatingId = null
            onEstimateHandled()
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MealDropdown(meal, { mealChoice = it.name })

            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                ManualRowSection(
                    row = row,
                    number = index + 1,
                    canRemove = rows.size > 1,
                    frequent = frequent,
                    aiConfigured = aiConfigured,
                    estimate = estimate,
                    estimatingThis = estimatingId == row.id,
                    onChange = { rows[index] = it },
                    onRemove = {
                        if (estimatingId == row.id) estimatingId = null
                        rows.removeAt(index)
                    },
                    onEstimate = {
                        estimatingId = row.id
                        onEstimate(row.name.trim())
                    },
                    onNameEdited = {
                        if (estimatingId == row.id) {
                            estimatingId = null
                            if (estimate != EstimateState.Idle) onEstimateHandled()
                        }
                    },
                )
            }

            OutlinedButton(
                onClick = {
                    rows += ManualRow(id = nextId)
                    nextId++
                },
                modifier = Modifier.fillMaxWidth(),
            ) { ButtonLabel(AppIcons.Add, stringResource(R.string.food_add_another)) }

            Button(
                onClick = {
                    onAdd(ManualRows.items(rows), meal)
                    rows.clear()
                    rows += ManualRow(id = nextId)
                    nextId++
                    mealChoice = null
                    estimatingId = null
                },
                enabled = validCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.food_add_n, validCount)) }
        }
    }
}

/** One food of the form. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualRowSection(
    row: ManualRow,
    number: Int,
    canRemove: Boolean,
    frequent: List<FoodEntry>,
    aiConfigured: Boolean,
    estimate: EstimateState,
    estimatingThis: Boolean,
    onChange: (ManualRow) -> Unit,
    onRemove: () -> Unit,
    onEstimate: () -> Unit,
    onNameEdited: () -> Unit,
) {
    val lang = if (Locale.getDefault().language == "uk") "uk" else "en"
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)
    val per = row.per100

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.food_product_n, number), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (canRemove) {
                IconButton(onClick = onRemove) { Icon(AppIcons.Delete, contentDescription = stringResource(R.string.food_remove_row)) }
            }
        }

        OutlinedTextField(
            row.name,
            {
                onNameEdited()
                // A different name means a different food: drop the data of the previous one.
                onChange(row.copy(name = it.take(80), kcalOverride = null).withPer100(null))
            },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.food_name)) },
            singleLine = true,
        )

        // With an empty name, offer what the user eats most so a repeat meal is one tap.
        if (per == null && row.name.isBlank() && frequent.isNotEmpty()) {
            Text(stringResource(R.string.food_frequent), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                frequent.forEach { e ->
                    SuggestionChip(
                        onClick = {
                            val p = FoodHistory.per100Of(e)
                            onChange(
                                row.copy(
                                    name = e.name,
                                    grams = if (e.grams > 0) e.grams.toString() else "",
                                    // No weight to derive per-100 g from: keep the calories that were entered.
                                    kcalOverride = if (p == null) e.kcal.toString() else null,
                                ).withPer100(p),
                            )
                        },
                        label = { Text(e.name) },
                    )
                }
            }
        }

        val suggestions = if (per == null) FoodDb.search(row.name, lang) else emptyList()
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { food ->
                    SuggestionChip(
                        onClick = {
                            onChange(
                                row.copy(
                                    name = food.name(lang),
                                    grams = row.grams.ifBlank { food.portionG.toString() },
                                    kcalOverride = null,
                                ).withPer100(food.per100),
                            )
                        },
                        label = { Text(stringResource(R.string.food_suggestion, food.name(lang), food.per100.kcal.roundToInt())) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                row.grams, { onChange(row.copy(grams = it.filter(Char::isDigit).take(4))) }, Modifier.weight(1f),
                label = { Text(stringResource(R.string.food_grams)) }, singleLine = true, keyboardOptions = numeric,
            )
            OutlinedTextField(
                row.kcalText, { onChange(row.copy(kcalOverride = it.filter(Char::isDigit).take(4))) }, Modifier.weight(1f),
                label = { Text(stringResource(R.string.food_kcal_label)) }, singleLine = true, keyboardOptions = numeric,
            )
        }

        per?.let { p ->
            Text(
                stringResource(R.string.food_per100_info, p.kcal.roundToInt(), p.proteinG.roundToInt(), p.fatG.roundToInt(), p.carbsG.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
            )
            val auto = row.autoKcal
            if (row.kcalOverride != null) {
                TextButton(onClick = { onChange(row.copy(kcalOverride = null)) }) {
                    ButtonLabel(AppIcons.Recalculate, stringResource(R.string.food_kcal_reset))
                }
            } else if (auto != null) {
                // Spell the sum out so it is clear the total already uses the entered weight.
                Text(stringResource(R.string.food_calc_line, row.gramsInt, p.kcal.roundToInt(), auto), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.food_kcal_auto), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (per == null && row.name.isBlank() && number == 1) {
            Text(stringResource(R.string.food_manual_hint), style = MaterialTheme.typography.bodySmall)
        }

        if (per == null && aiConfigured && row.name.trim().length >= 2) {
            if (estimatingThis) {
                when (estimate) {
                    EstimateState.Loading -> Text(stringResource(R.string.food_ai_loading), style = MaterialTheme.typography.bodySmall)
                    EstimateState.NotFound -> Text(stringResource(R.string.food_ai_notfound), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    is EstimateState.Failed -> Text(stringResource(errorText(estimate.error)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else -> Unit
                }
            }
            OutlinedButton(onClick = onEstimate, enabled = estimate != EstimateState.Loading) {
                ButtonLabel(AppIcons.Ai, stringResource(R.string.food_ai_estimate))
            }
        }
    }
}

@Composable
private fun EntryRow(e: FoodEntry, onDelete: (Long) -> Unit, onEdit: () -> Unit) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
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

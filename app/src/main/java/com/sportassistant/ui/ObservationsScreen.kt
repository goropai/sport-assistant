package com.sportassistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sportassistant.ObservationState
import com.sportassistant.R
import com.sportassistant.ai.ObservationResult
import com.sportassistant.ai.ObservationType
import com.sportassistant.data.Observation
import com.sportassistant.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun ObservationsScreen(
    profile: Profile,
    items: List<Observation>,
    state: ObservationState,
    onPhoto: (Uri, ObservationType) -> Unit,
    onSave: (keepPhoto: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var typeName by rememberSaveable { mutableStateOf(ObservationType.POSTURE.name) }
    val type = ObservationType.valueOf(typeName)
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    val configured = profile.geminiKey.isNotBlank()
    val busy = state == ObservationState.Analyzing

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingPhoto?.let { onPhoto(it, type) }
    }
    fun launchCamera() {
        val file = File(context.cacheDir, "food/observation.jpg").apply { parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = uri
        takePicture.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) launchCamera() }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onPhoto(it, type) } }

    val ofType = items.filter { it.type == type.name }
    val withPhotos = ofType.filter { it.photoPath != null && File(it.photoPath).exists() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        item { Text(stringResource(R.string.obs_title), style = MaterialTheme.typography.headlineMedium) }
        item { Text(stringResource(R.string.obs_disclaimer), style = MaterialTheme.typography.bodySmall) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ObservationType.entries.forEach { t ->
                    FilterChip(type == t, { typeName = t.name }, label = { Text(stringResource(t.label)) })
                }
            }
        }
        item { Text(stringResource(if (type == ObservationType.POSTURE) R.string.obs_tip_posture else R.string.obs_tip_skin), style = MaterialTheme.typography.bodySmall) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                        else cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                    enabled = configured && !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.food_photo)) }
                Button(
                    onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = configured && !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.food_gallery)) }
            }
        }
        if (!configured) item { Text(stringResource(R.string.obs_not_configured), style = MaterialTheme.typography.bodySmall) }

        when (state) {
            ObservationState.Idle -> Unit
            ObservationState.Analyzing -> item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text(stringResource(R.string.food_analyzing))
                }
            }
            is ObservationState.Result -> item { ResultCard(state.type, state.result, onSave, onDismiss) }
            is ObservationState.Failed -> item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(errorText(state.error)))
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.food_cancel)) }
                    }
                }
            }
        }

        if (withPhotos.size >= 2) item { CompareCard(first = withPhotos.last(), latest = withPhotos.first()) }

        item { Text(stringResource(R.string.obs_history), style = MaterialTheme.typography.titleMedium) }
        if (ofType.isEmpty()) item { Text(stringResource(R.string.obs_empty)) }
        items(ofType, key = { it.id }) { HistoryRow(it, onDelete) }
    }
}

@Composable
private fun ResultCard(type: ObservationType, r: ObservationResult, onSave: (Boolean) -> Unit, onDiscard: () -> Unit) {
    // Skin photos are more sensitive than posture photos, so they are not kept unless the user opts in.
    var keep by remember(type) { mutableStateOf(type == ObservationType.POSTURE) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.obs_result_title), style = MaterialTheme.typography.titleMedium)
            if (r.summary.isNotEmpty()) Text(r.summary)
            r.observations.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            if (r.suggestions.isNotEmpty()) {
                Text(stringResource(R.string.obs_suggestions), style = MaterialTheme.typography.titleSmall)
                r.suggestions.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            if (r.seeDoctor) DoctorBanner(r.doctorReason)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(keep, { keep = it })
                Text(stringResource(R.string.obs_keep_photo), style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(keep) }) { Text(stringResource(R.string.food_save)) }
                OutlinedButton(onClick = onDiscard) { Text(stringResource(R.string.obs_discard)) }
            }
        }
    }
}

@Composable
private fun DoctorBanner(reason: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            IconText(
                AppIcons.Warning,
                stringResource(R.string.obs_see_doctor),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (reason.isNotEmpty()) Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun CompareCard(first: Observation, latest: Observation) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.obs_compare), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(first, latest).forEach { o ->
                    Column(Modifier.weight(1f)) {
                        PhotoThumb(o.photoPath, sampleSize = 2, Modifier.fillMaxWidth().aspectRatio(0.75f).clip(MaterialTheme.shapes.medium))
                        Text(LocalDate.ofEpochDay(o.epochDay).toString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(o: Observation, onDelete: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (o.photoPath != null) PhotoThumb(o.photoPath, sampleSize = 4, Modifier.size(72.dp).clip(MaterialTheme.shapes.small))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(LocalDate.ofEpochDay(o.epochDay).toString(), style = MaterialTheme.typography.labelLarge)
                if (o.summary.isNotEmpty()) Text(o.summary, style = MaterialTheme.typography.bodyMedium)
                o.details.lines().filter { it.isNotBlank() }.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                if (o.seeDoctor) DoctorBanner(o.doctorReason)
            }
            IconButton(onClick = { onDelete(o.id) }) {
                Icon(AppIcons.Delete, contentDescription = stringResource(R.string.food_delete))
            }
        }
    }
}

/** Loads a downsampled copy of an app-private photo off the main thread. */
@Composable
private fun PhotoThumb(path: String?, sampleSize: Int, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })?.asImageBitmap()
        }
    }
    Box(modifier) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

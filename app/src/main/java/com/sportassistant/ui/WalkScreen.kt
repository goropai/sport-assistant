package com.sportassistant.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sportassistant.R
import com.sportassistant.data.Walk
import com.sportassistant.track.TrackState
import com.sportassistant.track.WalkMath
import kotlinx.coroutines.delay
import java.time.LocalDate

fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = sec % 3600 / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatPace(secPerKm: Int?): String =
    if (secPerKm == null || secPerKm > 3600) "--:--" else "%d:%02d".format(secPerKm / 60, secPerKm % 60)

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun WalkScreen(
    track: TrackState,
    walks: List<Walk>,
    weightKg: Int,
    onStart: () -> Unit,
    onFinish: () -> Boolean,
) {
    val context = LocalContext.current
    var gpsOff by remember { mutableStateOf(false) }
    var tooShort by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    fun startIfGpsOn() {
        val lm = context.getSystemService(LocationManager::class.java)
        gpsOff = !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!gpsOff) {
            tooShort = false
            onStart()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocationPermission(context)) startIfGpsOn()
    }

    // Ticks the clock while a walk is in progress.
    LaunchedEffect(track.running) {
        while (track.running) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }

    val elapsed = if (track.running) ((now - track.startMs) / 1000).coerceAtLeast(0) else 0L

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.tab_walk), style = MaterialTheme.typography.headlineMedium) }

        if (track.running) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric(stringResource(R.string.walk_distance), stringResource(R.string.walk_km, track.distanceM / 1000))
                    Metric(stringResource(R.string.walk_time), formatDuration(elapsed))
                    Metric(stringResource(R.string.walk_pace), formatPace(WalkMath.paceSecPerKm(track.distanceM, elapsed)))
                    Metric(stringResource(R.string.walk_kcal), WalkMath.kcal(track.distanceM, elapsed, weightKg).toString())
                }
            }
            if (!track.hasFix) item { Text(stringResource(R.string.walk_waiting), color = MaterialTheme.colorScheme.tertiary) }
            item { Card(Modifier.fillMaxWidth().height(240.dp)) { RouteCanvas(track.points, Modifier.fillMaxSize()) } }
            item {
                Button(onClick = { tooShort = !onFinish() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.walk_finish))
                }
            }
        } else {
            item {
                Button(
                    onClick = {
                        if (hasLocationPermission(context)) startIfGpsOn()
                        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { ButtonLabel(AppIcons.Walk, stringResource(R.string.walk_start)) }
            }
            item { Text(stringResource(R.string.walk_permission_hint), style = MaterialTheme.typography.bodySmall) }
            if (gpsOff) item { Text(stringResource(R.string.walk_gps_off), color = MaterialTheme.colorScheme.error) }
            if (tooShort) item { Text(stringResource(R.string.walk_too_short), color = MaterialTheme.colorScheme.error) }
        }

        if (walks.isNotEmpty()) {
            item { Text(stringResource(R.string.walk_history), style = MaterialTheme.typography.titleMedium) }
            items(walks, key = { it.id }) { WalkRow(it) }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WalkRow(walk: Walk) {
    val points = remember(walk.id) { parsePath(walk.path) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card { RouteCanvas(points, Modifier.size(88.dp)) }
            Column(Modifier.weight(1f)) {
                Text(LocalDate.ofEpochDay(walk.epochDay).toString(), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.walk_km, walk.distanceM / 1000), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatDuration(walk.durationSec)} · ${walk.kcal} ${stringResource(R.string.walk_kcal)} · " +
                        "${formatPace(WalkMath.paceSecPerKm(walk.distanceM, walk.durationSec))} ${stringResource(R.string.walk_pace_unit)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

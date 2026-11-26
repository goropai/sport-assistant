package com.sportassistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sportassistant.R
import com.sportassistant.data.Exercise
import com.sportassistant.pose.PoseAnalyzer
import com.sportassistant.pose.RepCounter
import com.sportassistant.pose.TrackerSpec
import java.util.concurrent.Executors

@Composable
fun CameraScreen(ex: Exercise, spec: TrackerSpec, onDone: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.camera_permission_needed), textAlign = TextAlign.Center)
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.camera_grant)) }
            Button(onClick = onDone) { Text(stringResource(R.string.back)) }
        }
        return
    }

    CountingView(ex, spec, onDone)
}

@Composable
private fun CountingView(ex: Exercise, spec: TrackerSpec, onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    val counter = remember { RepCounter(spec.rest, spec.active) }
    var reps by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context).apply { keepScreenOn = true } }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }

    DisposableEffect(lifecycleOwner, spec) {
        val executor = Executors.newSingleThreadExecutor()
        val analyzer = PoseAnalyzer(spec) { angle ->
            visible = angle != null
            if (angle != null && counter.update(angle, SystemClock.elapsedRealtime())) {
                reps = counter.reps
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val p = providerFuture.get()
            provider = p
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            p.unbindAll()
            // Front camera: the user can watch the screen while exercising.
            p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            executor.shutdown()
            analyzer.close()
            tone?.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        Column(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).safeDrawingPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(ex.name), color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("$reps", color = Color.White, style = MaterialTheme.typography.displayLarge)
            Text(
                if (ex.reps > 0) stringResource(R.string.camera_target, ex.reps) else "",
                color = Color.White,
                modifier = Modifier.alpha(if (ex.reps > 0) 1f else 0f),
            )
            Text(
                when {
                    ex.reps > 0 && reps >= ex.reps -> stringResource(R.string.camera_target_reached)
                    visible -> stringResource(R.string.camera_tracking)
                    else -> stringResource(R.string.camera_body_not_visible)
                },
                color = if (visible) Color(0xFF81C784) else Color(0xFFFFB74D),
                style = MaterialTheme.typography.titleSmall,
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.camera_tip), color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.camera_done)) }
        }
    }
}

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.pose.PoseLandmark
import com.sportassistant.R
import com.sportassistant.data.Exercise
import com.sportassistant.pose.NormPoint
import com.sportassistant.pose.OverlayTransform
import com.sportassistant.pose.PoseAnalyzer
import com.sportassistant.pose.PoseFrame
import com.sportassistant.pose.RepCounter
import com.sportassistant.pose.Sensitivity
import com.sportassistant.pose.TrackerSpec
import com.sportassistant.pose.withSensitivity
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    ex: Exercise,
    spec: TrackerSpec,
    sensitivity: Int,
    onSensitivity: (Int) -> Unit,
    onDone: () -> Unit,
) {
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

    CountingView(ex, spec, sensitivity, onSensitivity, onDone)
}

/** Bones drawn on top of the video, as pairs of ML Kit landmark types. */
private val bones = listOf(
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
    PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
)

private const val DRAW_MIN_LIKELIHOOD = 0.5f

@Composable
private fun CountingView(ex: Exercise, spec: TrackerSpec, sensitivity: Int, onSensitivity: (Int) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    // A new counter per sensitivity level; the rep total lives separately so changing it does not reset it.
    val tuned = remember(spec, sensitivity) { spec.withSensitivity(sensitivity) }
    val counter by rememberUpdatedState(remember(tuned) { RepCounter(tuned.rest, tuned.active) })
    var reps by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf<PoseFrame?>(null) }
    var inActivePhase by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context).apply { keepScreenOn = true } }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }

    DisposableEffect(lifecycleOwner, spec) {
        val executor = Executors.newSingleThreadExecutor()
        val analyzer = PoseAnalyzer(spec) { f ->
            frame = f
            val angle = f.angle
            if (angle != null && counter.update(angle, SystemClock.elapsedRealtime())) {
                reps++
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            inActivePhase = counter.isActive
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

    val visible = frame?.angle != null

    Box(Modifier.fillMaxSize()) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        SkeletonOverlay(frame, inActivePhase, Modifier.fillMaxSize())

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
            frame?.angle?.let { a ->
                Text(
                    stringResource(
                        R.string.camera_angle_fmt,
                        a.roundToInt(),
                        stringResource(if (inActivePhase) R.string.camera_phase_active else R.string.camera_phase_rest),
                    ),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.camera_tip), color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.camera_sensitivity), color = Color.White, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Sensitivity.STRICT to R.string.sens_strict,
                    Sensitivity.NORMAL to R.string.sens_normal,
                    Sensitivity.EASY to R.string.sens_easy,
                ).forEach { (level, label) ->
                    FilterChip(sensitivity == level, { onSensitivity(level) }, label = { Text(stringResource(label)) })
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.camera_done)) }
        }
    }
}

/** Draws the detected skeleton over the preview and highlights the joint being counted. */
@Composable
private fun SkeletonOverlay(frame: PoseFrame?, active: Boolean, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val f = frame ?: return@Canvas

        fun at(p: NormPoint): Offset {
            // The front-camera preview is mirrored, the analysed image is not.
            val (x, y) = OverlayTransform.toView(p.x, p.y, f.imageWidth, f.imageHeight, size.width, size.height, mirror = true)
            return Offset(x, y)
        }
        fun seen(type: Int): NormPoint? = f.points[type]?.takeIf { it.likelihood >= DRAW_MIN_LIKELIHOOD }

        for ((a, b) in bones) {
            val pa = seen(a) ?: continue
            val pb = seen(b) ?: continue
            drawLine(Color.White.copy(alpha = 0.7f), at(pa), at(pb), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }

        val tracked = f.tracked.map { seen(it) }
        if (tracked.size == 3 && tracked.all { it != null }) {
            val (a, b, c) = tracked.map { at(it!!) }
            // Green at rest, orange while in the working position.
            val color = if (active) Color(0xFFFFB74D) else Color(0xFF81C784)
            drawLine(color, a, b, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color, b, c, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color, radius = 10.dp.toPx(), center = b)
            f.angle?.let {
                drawText(
                    measurer,
                    "${it.roundToInt()}°",
                    topLeft = Offset(b.x + 16.dp.toPx(), b.y - 12.dp.toPx()),
                    style = TextStyle(color = color, fontSize = 18.sp),
                )
            }
        }
    }
}

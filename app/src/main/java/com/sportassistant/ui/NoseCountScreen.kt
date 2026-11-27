package com.sportassistant.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sportassistant.R
import com.sportassistant.data.Exercise
import com.sportassistant.pose.TouchRepCounter

/**
 * Push-ups counted by touching the screen with the nose: the phone lies on the floor, screen up,
 * under the face. Every touch in the big area is one repetition (see [TouchRepCounter]).
 */
@Composable
fun NoseCountScreen(ex: Exercise, onDone: () -> Unit) {
    val counter = remember { TouchRepCounter() }
    var reps by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    val view = LocalView.current

    DisposableEffect(view) {
        // The screen must stay on while the user is in the plank position and cannot reach the phone.
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            tone?.release()
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // The finish control sits outside the counting area so that touching it never counts as a rep.
        // It needs a long press, so a stray nose touch cannot end the set.
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(ex.name), style = MaterialTheme.typography.titleMedium)
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = {}, onLongClick = onDone)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text(stringResource(R.string.nose_hold_to_finish), style = MaterialTheme.typography.labelLarge) }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            // Initial pass: see every new contact before anything else can consume it.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed && !it.previousPressed }) {
                                if (counter.onTouch(SystemClock.elapsedRealtime())) {
                                    reps = counter.reps
                                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("$reps", style = MaterialTheme.typography.displayLarge)
                if (ex.reps > 0) {
                    Text(stringResource(R.string.camera_target, ex.reps), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { (reps.toFloat() / ex.reps).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                    if (reps >= ex.reps) {
                        Text(stringResource(R.string.camera_target_reached), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(stringResource(R.string.nose_instructions), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.nose_not_registering), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

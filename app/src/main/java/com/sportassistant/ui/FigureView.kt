package com.sportassistant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportassistant.data.Exercise
import com.sportassistant.figure.FLOOR_Y
import com.sportassistant.figure.FigureDef
import com.sportassistant.figure.Figures
import com.sportassistant.figure.Pt
import com.sportassistant.figure.WORLD_H
import com.sportassistant.figure.WORLD_TOP
import com.sportassistant.figure.WORLD_W
import kotlin.math.max
import kotlin.math.min

/** The exercise's animated stick figure, or its emoji when no illustration exists yet. */
@Composable
fun ExerciseVisual(ex: Exercise, modifier: Modifier = Modifier) {
    val def = Figures.forExercise(ex.id)
    if (def == null) {
        Box(modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
            IconBadge(AppIcons.forCategory(ex.category), 120.dp)
        }
    } else {
        ExerciseFigure(def, stringResource(ex.name), modifier.fillMaxWidth().height(190.dp))
    }
}

/** A small static picture of the exercise for lists: the figure at its first pose, cropped to the figure. */
@Composable
fun ExerciseThumb(ex: Exercise, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val def = Figures.forExercise(ex.id)
    if (def == null) {
        IconBadge(AppIcons.forCategory(ex.category), size, modifier)
        return
    }
    val near = MaterialTheme.colorScheme.primary
    val far = near.copy(alpha = 0.4f)
    val scenery = MaterialTheme.colorScheme.outline
    val background = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    Canvas(modifier.size(size).clip(RoundedCornerShape(14.dp)).background(background)) {
        val sk = def.frame(0f)
        // Fit the figure's own bounding box (plus its props and the floor) into the square.
        val pts = sk.points + def.props.flatMap { listOf(it.first, it.second) } + listOf(Pt(sk.points.minOf { it.x }, FLOOR_Y), Pt(sk.points.maxOf { it.x }, FLOOR_Y))
        val pad = 0.08f
        val minX = pts.minOf { it.x } - pad
        val maxX = pts.maxOf { it.x } + pad
        val minY = pts.minOf { it.y } - sk.headRadius - pad
        val maxY = pts.maxOf { it.y } + pad
        val scale = min(this.size.width / (maxX - minX), this.size.height / (maxY - minY))
        val ox = (this.size.width - (maxX - minX) * scale) / 2
        val oy = (this.size.height - (maxY - minY) * scale) / 2
        fun at(p: Pt) = Offset(ox + (p.x - minX) * scale, oy + (p.y - minY) * scale)

        val thin = max(1.dp.toPx(), 0.006f * scale)
        val thick = max(2.dp.toPx(), 0.03f * scale)
        for ((a, b) in def.props) drawLine(scenery, at(a), at(b), strokeWidth = thin, cap = StrokeCap.Round)
        drawLine(scenery, at(Pt(minX + pad, FLOOR_Y)), at(Pt(maxX - pad, FLOOR_Y)), strokeWidth = thin, cap = StrokeCap.Round)
        for (s in sk.segments) drawLine(if (s.near) near else far, at(s.a), at(s.b), strokeWidth = thick, cap = StrokeCap.Round)
        drawCircle(near, radius = sk.headRadius * scale, center = at(sk.head), style = Stroke(width = thick))
    }
}

@Composable
fun ExerciseFigure(def: FigureDef, description: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "figure")
    // Ping-pong between the two key poses.
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pose",
    )
    val near = MaterialTheme.colorScheme.primary
    val far = near.copy(alpha = 0.4f)
    val scenery = MaterialTheme.colorScheme.outline

    Canvas(modifier.semantics { contentDescription = description }) {
        val scale = min(size.width / WORLD_W, size.height / WORLD_H)
        val ox = (size.width - WORLD_W * scale) / 2
        val oy = (size.height - WORLD_H * scale) / 2
        fun at(p: Pt) = Offset(ox + p.x * scale, oy + (p.y - WORLD_TOP) * scale)

        val thin = max(2.dp.toPx(), 0.008f * scale)
        val thick = max(4.dp.toPx(), 0.02f * scale)

        drawLine(scenery, at(Pt(0.05f, FLOOR_Y)), at(Pt(WORLD_W - 0.05f, FLOOR_Y)), strokeWidth = thin, cap = StrokeCap.Round)
        for ((a, b) in def.props) drawLine(scenery, at(a), at(b), strokeWidth = thin, cap = StrokeCap.Round)

        val sk = def.frame(t)
        for (s in sk.segments) {
            drawLine(if (s.near) near else far, at(s.a), at(s.b), strokeWidth = thick, cap = StrokeCap.Round)
        }
        drawCircle(near, radius = sk.headRadius * scale, center = at(sk.head), style = Stroke(width = thick))
    }
}

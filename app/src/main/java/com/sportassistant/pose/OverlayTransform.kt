package com.sportassistant.pose

import kotlin.math.max

/**
 * Maps a point from the analysed image onto the on-screen preview.
 *
 * The preview uses FILL_CENTER: the camera image is scaled to cover the view and centred, so the
 * overflowing edge is cropped. The front camera preview is mirrored while the analysed image is not.
 */
object OverlayTransform {
    /** @param nx,ny the point as a fraction (0..1) of the analysed image */
    fun toView(
        nx: Float,
        ny: Float,
        imageW: Float,
        imageH: Float,
        viewW: Float,
        viewH: Float,
        mirror: Boolean,
    ): Pair<Float, Float> {
        val scale = max(viewW / imageW, viewH / imageH)
        val shownW = imageW * scale
        val shownH = imageH * scale
        val x = nx * shownW + (viewW - shownW) / 2f
        val y = ny * shownH + (viewH - shownH) / 2f
        return (if (mirror) viewW - x else x) to y
    }
}

/** How strictly a repetition must reach its working position before it counts. */
object Sensitivity {
    const val STRICT = 0
    const val NORMAL = 1
    const val EASY = 2
}

/**
 * Moves the "working position" threshold relative to the rest position: easier counts shallower reps,
 * stricter demands a deeper one. Hysteresis is preserved because the threshold never crosses [rest].
 */
fun TrackerSpec.withSensitivity(level: Int): TrackerSpec {
    val shift = when (level) {
        Sensitivity.STRICT -> -0.15f
        Sensitivity.EASY -> 0.25f
        else -> 0f
    }
    return copy(active = active + (rest - active) * shift)
}

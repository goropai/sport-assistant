package com.sportassistant.pose

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

private const val MIN_LIKELIHOOD = 0.6f

/** A landmark position as a fraction (0..1) of the analysed image, plus ML Kit's confidence. */
data class NormPoint(val x: Float, val y: Float, val likelihood: Float)

/**
 * One analysed camera frame. [imageWidth]/[imageHeight] are the analysed image's size after rotation,
 * needed to map [points] onto the preview. [tracked] lists the three landmark types whose angle is
 * [angle]; both are empty/null when the body is not visible enough.
 */
data class PoseFrame(
    val points: Map<Int, NormPoint>,
    val imageWidth: Float,
    val imageHeight: Float,
    val angle: Float?,
    val tracked: List<Int>,
)

/** Runs on-device pose detection and reports every frame. */
class PoseAnalyzer(
    private val spec: TrackerSpec,
    private val onFrame: (PoseFrame) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return
        }
        val rotation = proxy.imageInfo.rotationDegrees
        // ML Kit reports landmarks in the coordinates of the image *after* rotation.
        val w = (if (rotation == 90 || rotation == 270) proxy.height else proxy.width).toFloat()
        val h = (if (rotation == 90 || rotation == 270) proxy.width else proxy.height).toFloat()
        val image = InputImage.fromMediaImage(media, rotation)
        detector.process(image)
            .addOnSuccessListener { onFrame(frameOf(it, w, h)) }
            .addOnFailureListener { onFrame(PoseFrame(emptyMap(), w, h, null, emptyList())) }
            .addOnCompleteListener { proxy.close() }
    }

    fun close() = detector.close()

    private fun frameOf(pose: Pose, w: Float, h: Float): PoseFrame {
        val points = pose.allPoseLandmarks.associate {
            it.landmarkType to NormPoint(it.position.x / w, it.position.y / h, it.inFrameLikelihood)
        }
        val measured = measure(pose)
        return PoseFrame(points, w, h, measured?.angle, measured?.tracked.orEmpty())
    }

    /** Uses whichever body side (left/right) the camera sees more confidently. */
    private fun measure(pose: Pose): Measured? {
        val (left, right) = when (spec.joint) {
            Joint.KNEE -> listOf(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE) to
                listOf(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
            Joint.ELBOW -> listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST) to
                listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
            Joint.HIP -> listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE) to
                listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE)
        }
        return listOf(left, right).mapNotNull { resolve(pose, it) }.maxByOrNull { it.likelihood }
    }

    private class Measured(val angle: Float, val likelihood: Float, val tracked: List<Int>)

    private fun resolve(pose: Pose, types: List<Int>): Measured? {
        val (pa, pb, pc) = types.map { pose.getPoseLandmark(it) ?: return null }
        val likelihood = minOf(pa.inFrameLikelihood, pb.inFrameLikelihood, pc.inFrameLikelihood)
        if (likelihood < MIN_LIKELIHOOD) return null
        val angle = jointAngle(pa.position.x, pa.position.y, pb.position.x, pb.position.y, pc.position.x, pc.position.y)
        return Measured(angle, likelihood, types)
    }
}

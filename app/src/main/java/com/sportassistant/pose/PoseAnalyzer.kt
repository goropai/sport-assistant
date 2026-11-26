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

/** Runs on-device pose detection; reports the tracked joint angle, or null if the body is not visible. */
class PoseAnalyzer(
    private val spec: TrackerSpec,
    private val onAngle: (Float?) -> Unit,
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
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { onAngle(measure(it)) }
            .addOnFailureListener { onAngle(null) }
            .addOnCompleteListener { proxy.close() }
    }

    fun close() = detector.close()

    /** Uses whichever body side (left/right) the camera sees more confidently. */
    private fun measure(pose: Pose): Float? {
        val (left, right) = when (spec.joint) {
            Joint.KNEE -> triple(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE) to
                triple(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
            Joint.ELBOW -> triple(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST) to
                triple(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
            Joint.HIP -> triple(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE) to
                triple(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE)
        }
        val best = listOf(left, right)
            .mapNotNull { (a, b, c) -> resolve(pose, a, b, c) }
            .maxByOrNull { it.likelihood }
            ?: return null
        return best.angle
    }

    private data class Measured(val angle: Float, val likelihood: Float)

    private fun triple(a: Int, b: Int, c: Int) = Triple(a, b, c)

    private fun resolve(pose: Pose, a: Int, b: Int, c: Int): Measured? {
        val pa = pose.getPoseLandmark(a) ?: return null
        val pb = pose.getPoseLandmark(b) ?: return null
        val pc = pose.getPoseLandmark(c) ?: return null
        val likelihood = minOf(pa.inFrameLikelihood, pb.inFrameLikelihood, pc.inFrameLikelihood)
        if (likelihood < MIN_LIKELIHOOD) return null
        val angle = jointAngle(pa.position.x, pa.position.y, pb.position.x, pb.position.y, pc.position.x, pc.position.y)
        return Measured(angle, likelihood)
    }
}

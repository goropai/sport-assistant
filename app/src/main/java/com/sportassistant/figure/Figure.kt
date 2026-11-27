package com.sportassistant.figure

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * A tiny stick-figure engine for exercise illustrations. Pure Kotlin (no Android types) so poses can be
 * rendered and checked in unit tests.
 *
 * World: x grows right, y grows down, 1.0 tall, 1.6 wide, floor at FLOOR_Y. The figure faces right.
 * Directions are degrees measured from "straight down": 0 = down, 90 = forward (right), 180 = up, -90 = back.
 */

const val FLOOR_Y = 0.95f
const val WORLD_W = 1.6f
/** Scene spans y in WORLD_TOP..WORLD_TOP+WORLD_H; the headroom lets standing figures rise (calf raises). */
const val WORLD_TOP = -0.10f
const val WORLD_H = 1.10f

private const val THIGH = 0.25f
private const val SHIN = 0.25f
private const val FOOT = 0.07f
private const val UPPER = 0.15f
private const val FORE = 0.14f
private const val SPINE_LOW = 0.14f
private const val SPINE_UP = 0.16f
private const val NECK = 0.04f
private const val HEAD_R = 0.055f
private const val FAR_DX = -0.02f

data class Pt(val x: Float, val y: Float) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
}

fun vec(dirDeg: Float, len: Float): Pt {
    val r = Math.toRadians(dirDeg.toDouble())
    return Pt((len * sin(r)).toFloat(), (len * cos(r)).toFloat())
}

private fun dirOf(from: Pt, to: Pt): Float =
    Math.toDegrees(atan2((to.x - from.x).toDouble(), (to.y - from.y).toDouble())).toFloat()

/** A two-bone limb. Either explicit directions, or "reach this point" solved with inverse kinematics. */
sealed interface Limb
/** [a] upper bone, [b] lower bone, [c] foot direction (legs only). */
data class Angles(val a: Float, val b: Float, val c: Float = 90f) : Limb
/** [bend] picks the joint side: +1 / -1. */
data class Reach(val x: Float, val y: Float, val bend: Float = 1f, val c: Float = 90f) : Limb

private fun Limb.toAngles(root: Pt, l1: Float, l2: Float): Angles = when (this) {
    is Angles -> this
    is Reach -> {
        val target = Pt(x, y)
        val d = hypot(target.x - root.x, target.y - root.y).coerceIn(abs(l1 - l2) + 0.001f, l1 + l2 - 0.001f)
        val base = dirOf(root, target)
        val cosA = ((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)).toDouble().coerceIn(-1.0, 1.0)
        val alpha = Math.toDegrees(acos(cosA)).toFloat()
        val d1 = base + bend * alpha
        val joint = root + vec(d1, l1)
        Angles(d1, dirOf(joint, root + vec(base, d)), c)
    }
}

enum class AnchorKind { ANKLE, KNEE, PELVIS }

/** The one point that stays planted; the rest of the body is derived from it. */
data class Anchor(val kind: AnchorKind, val x: Float, val y: Float)

data class SidePose(
    val anchor: Anchor,
    val spineLow: Float = 180f,
    val spineUp: Float = 180f,
    val head: Float = 180f,
    val armNear: Limb = Angles(0f, 0f),
    val armFar: Limb = Angles(0f, 0f),
    val legNear: Limb = Angles(0f, 0f, 90f),
    val legFar: Limb = Angles(0f, 0f, 90f),
)

data class Segment(val a: Pt, val b: Pt, val near: Boolean)

data class Skeleton(val segments: List<Segment>, val head: Pt, val headRadius: Float) {
    val points: List<Pt> get() = segments.flatMap { listOf(it.a, it.b) } + head
}

private fun SidePose.pelvis(): Pt {
    val p = Pt(anchor.x, anchor.y)
    if (anchor.kind == AnchorKind.PELVIS) return p
    val leg = legNear as? Angles ?: error("an ankle/knee-anchored leg must use explicit angles")
    return when (anchor.kind) {
        AnchorKind.ANKLE -> p - vec(leg.a, THIGH) - vec(leg.b, SHIN)
        AnchorKind.KNEE -> p - vec(leg.a, THIGH)
        AnchorKind.PELVIS -> p
    }
}

private fun SidePose.neck(pelvis: Pt) = pelvis + vec(spineLow, SPINE_LOW) + vec(spineUp, SPINE_UP)

/** Replaces every IK [Reach] with explicit [Angles], so poses can be interpolated joint by joint. */
fun SidePose.resolved(): SidePose {
    val p = pelvis()
    val n = neck(p)
    return copy(
        armNear = armNear.toAngles(n, UPPER, FORE),
        armFar = armFar.toAngles(n, UPPER, FORE),
        legNear = legNear.toAngles(p, THIGH, SHIN),
        legFar = legFar.toAngles(p, THIGH, SHIN),
    )
}

private fun SidePose.withPelvisAnchor(): SidePose = copy(anchor = pelvis().let { Anchor(AnchorKind.PELVIS, it.x, it.y) })

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun mix(a: Limb, b: Limb, t: Float): Limb {
    val x = a as Angles
    val y = b as Angles
    return Angles(mix(x.a, y.a, t), mix(x.b, y.b, t), mix(x.c, y.c, t))
}

/** Both poses must already be [resolved]. */
fun lerp(a: SidePose, b: SidePose, t: Float): SidePose {
    // Different anchors cannot be blended; fall back to following the pelvis.
    val (pa, pb) = if (a.anchor.kind == b.anchor.kind) a to b else a.withPelvisAnchor() to b.withPelvisAnchor()
    return SidePose(
        anchor = Anchor(pa.anchor.kind, mix(pa.anchor.x, pb.anchor.x, t), mix(pa.anchor.y, pb.anchor.y, t)),
        spineLow = mix(pa.spineLow, pb.spineLow, t),
        spineUp = mix(pa.spineUp, pb.spineUp, t),
        head = mix(pa.head, pb.head, t),
        armNear = mix(pa.armNear, pb.armNear, t),
        armFar = mix(pa.armFar, pb.armFar, t),
        legNear = mix(pa.legNear, pb.legNear, t),
        legFar = mix(pa.legFar, pb.legFar, t),
    )
}

fun SidePose.skeleton(): Skeleton {
    val r = resolved()
    val pelvis = r.pelvis()
    val mid = pelvis + vec(r.spineLow, SPINE_LOW)
    val neck = mid + vec(r.spineUp, SPINE_UP)
    val headC = neck + vec(r.head, NECK + HEAD_R)

    val segs = mutableListOf<Segment>()
    fun limb(root: Pt, l: Limb, l1: Float, l2: Float, foot: Float?, near: Boolean) {
        val k = l as Angles
        val shift = if (near) Pt(0f, 0f) else Pt(FAR_DX, 0f)
        val mid1 = root + vec(k.a, l1) + shift
        val end = mid1 + vec(k.b, l2)
        segs += Segment(root + shift, mid1, near)
        segs += Segment(mid1, end, near)
        if (foot != null) segs += Segment(end, end + vec(k.c, foot), near)
    }
    // Far limbs first so near ones are drawn on top.
    limb(pelvis, r.legFar, THIGH, SHIN, FOOT, near = false)
    limb(neck, r.armFar, UPPER, FORE, null, near = false)
    segs += Segment(pelvis, mid, true)
    segs += Segment(mid, neck, true)
    segs += Segment(neck, headC, true)
    limb(pelvis, r.legNear, THIGH, SHIN, FOOT, near = true)
    limb(neck, r.armNear, UPPER, FORE, null, near = true)
    return Skeleton(segs, headC, HEAD_R)
}

/** Front view (neck and shoulder exercises). Angles: [headTilt] degrees toward the figure's right (+). */
data class FrontPose(val headTilt: Float = 0f, val shoulderLift: Float = 0f, val armOut: Float = 8f)

fun lerp(a: FrontPose, b: FrontPose, t: Float) =
    FrontPose(mix(a.headTilt, b.headTilt, t), mix(a.shoulderLift, b.shoulderLift, t), mix(a.armOut, b.armOut, t))

fun FrontPose.skeleton(): Skeleton {
    val cx = 0.8f
    val pelvis = Pt(cx, 0.45f)
    val neck = Pt(cx, 0.15f)
    val headC = neck + vec(180f - headTilt, NECK + HEAD_R)
    val segs = mutableListOf<Segment>()
    segs += Segment(pelvis, neck, true)
    segs += Segment(neck, headC, true)
    for (side in listOf(-1f, 1f)) {
        val shoulder = Pt(cx + side * 0.11f, neck.y + 0.015f - shoulderLift)
        val elbow = shoulder + vec(side * armOut, UPPER)
        val wrist = elbow + vec(side * armOut, FORE)
        segs += Segment(neck, shoulder, true)
        segs += Segment(shoulder, elbow, true)
        segs += Segment(elbow, wrist, true)
        val hip = Pt(cx + side * 0.05f, pelvis.y)
        val knee = Pt(cx + side * 0.065f, pelvis.y + THIGH)
        val ankle = Pt(cx + side * 0.075f, FLOOR_Y)
        segs += Segment(pelvis, hip, true)
        segs += Segment(hip, knee, true)
        segs += Segment(knee, ankle, true)
    }
    return Skeleton(segs, headC, HEAD_R)
}

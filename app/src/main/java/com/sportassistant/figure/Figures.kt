package com.sportassistant.figure

/** An animated illustration: props are static scenery, [frame] gives the skeleton at t in 0..1. */
class FigureDef(val props: List<Pair<Pt, Pt>>, private val at: (Float) -> Skeleton) {
    fun frame(t: Float): Skeleton = at(t.coerceIn(0f, 1f))
}

object Figures {
    private val F = FLOOR_Y
    private fun line(x1: Float, y1: Float, x2: Float, y2: Float) = Pt(x1, y1) to Pt(x2, y2)
    private fun ankle(x: Float, y: Float = F) = Anchor(AnchorKind.ANKLE, x, y)
    private fun pelvis(x: Float, y: Float) = Anchor(AnchorKind.PELVIS, x, y)
    private fun knee(x: Float, y: Float) = Anchor(AnchorKind.KNEE, x, y)

    private fun side(a: SidePose, b: SidePose, props: List<Pair<Pt, Pt>> = emptyList()): FigureDef {
        val ra = a.resolved()
        val rb = b.resolved()
        return FigureDef(props) { t -> lerp(ra, rb, t).skeleton() }
    }

    private fun front(a: FrontPose, b: FrontPose) = FigureDef(emptyList()) { t -> lerp(a, b, t).skeleton() }

    private val chair = listOf(
        line(0.48f, 0.68f, 0.74f, 0.68f), line(0.48f, 0.68f, 0.48f, 0.42f),
        line(0.50f, 0.68f, 0.50f, F), line(0.72f, 0.68f, 0.72f, F),
    )

    /** Exercise id -> illustration. Exercises without one fall back to their emoji. */
    val all: Map<String, FigureDef> = mapOf(
        "squats" to side(
            SidePose(ankle(0.85f), armNear = Angles(90f, 90f), armFar = Angles(90f, 90f)),
            SidePose(
                ankle(0.85f), spineLow = 150f, spineUp = 155f, head = 165f,
                armNear = Angles(90f, 90f), armFar = Angles(90f, 90f),
                legNear = Angles(75f, -20f), legFar = Angles(75f, -20f),
            ),
            chair,
        ),
        "wall_pushups" to side(
            SidePose(
                ankle(0.55f), spineLow = 160f, spineUp = 160f, head = 165f,
                armNear = Reach(1.12f, 0.2f, -1f), armFar = Reach(1.12f, 0.2f, -1f),
                legNear = Angles(-20f, -20f), legFar = Angles(-20f, -20f),
            ),
            SidePose(
                ankle(0.55f), spineLow = 140f, spineUp = 140f, head = 150f,
                armNear = Reach(1.12f, 0.2f, -1f), armFar = Reach(1.12f, 0.2f, -1f),
                legNear = Angles(-30f, -30f), legFar = Angles(-30f, -30f),
            ),
            listOf(line(1.12f, 0.02f, 1.12f, F)),
        ),
        "pushups" to side(
            SidePose(
                ankle(0.35f, F - 0.06f), spineLow = 105f, spineUp = 105f, head = 110f,
                armNear = Reach(1.12f, F, -1f), armFar = Reach(1.12f, F, -1f),
                legNear = Angles(-75f, -75f, 30f), legFar = Angles(-75f, -75f, 30f),
            ),
            SidePose(
                ankle(0.35f, F - 0.06f), spineLow = 95f, spineUp = 95f, head = 100f,
                armNear = Reach(1.12f, F, -1f), armFar = Reach(1.12f, F, -1f),
                legNear = Angles(-85f, -85f, 30f), legFar = Angles(-85f, -85f, 30f),
            ),
        ),
        // Isometric hold: one static pose. The slope is chosen so the forearms rest exactly on the floor.
        "plank" to side(
            SidePose(
                ankle(0.30f, F - 0.06f), spineLow = 97f, spineUp = 97f, head = 100f,
                armNear = Angles(0f, 90f), armFar = Angles(0f, 90f),
                legNear = Angles(-83f, -83f, 30f), legFar = Angles(-83f, -83f, 30f),
            ),
            SidePose(
                ankle(0.30f, F - 0.06f), spineLow = 97f, spineUp = 97f, head = 100f,
                armNear = Angles(0f, 90f), armFar = Angles(0f, 90f),
                legNear = Angles(-83f, -83f, 30f), legFar = Angles(-83f, -83f, 30f),
            ),
        ),
        "glute_bridge" to side(
            SidePose(
                pelvis(0.72f, 0.90f), spineLow = -90f, spineUp = -90f, head = -90f,
                armNear = Angles(90f, 90f), armFar = Angles(90f, 90f),
                legNear = Reach(0.95f, F, 1f), legFar = Reach(0.95f, F, 1f),
            ),
            SidePose(
                pelvis(0.72f, 0.76f), spineLow = -60f, spineUp = -60f, head = -75f,
                armNear = Angles(90f, 90f), armFar = Angles(90f, 90f),
                legNear = Reach(0.95f, F, 1f), legFar = Reach(0.95f, F, 1f),
            ),
        ),
        "calf_raises" to side(
            SidePose(ankle(0.8f), armNear = Reach(1.05f, 0.35f, -1f), armFar = Reach(1.05f, 0.35f, -1f)),
            SidePose(
                ankle(0.8f, F - 0.06f),
                armNear = Reach(1.05f, 0.35f, -1f), armFar = Reach(1.05f, 0.35f, -1f),
                legNear = Angles(0f, 0f, 30f), legFar = Angles(0f, 0f, 30f),
            ),
            listOf(line(1.05f, 0.1f, 1.05f, F)),
        ),
        "bird_dog" to side(
            SidePose(
                knee(0.62f, 0.93f), spineLow = 90f, spineUp = 90f, head = 100f,
                armNear = Angles(20f, -20f), armFar = Angles(20f, -20f),
                legNear = Angles(0f, -90f, -90f), legFar = Angles(0f, -90f, -90f),
            ),
            SidePose(
                knee(0.62f, 0.93f), spineLow = 90f, spineUp = 90f, head = 90f,
                armNear = Angles(92f, 92f), armFar = Angles(20f, -20f),
                legNear = Angles(0f, -90f, -90f), legFar = Angles(-95f, -95f, -95f),
            ),
        ),
        "cat_cow" to side(
            SidePose(
                knee(0.55f, 0.93f), spineLow = 75f, spineUp = 110f, head = 135f,
                armNear = Reach(0.84f, F, -1f), armFar = Reach(0.84f, F, -1f),
                legNear = Angles(0f, -90f, -90f), legFar = Angles(0f, -90f, -90f),
            ),
            SidePose(
                knee(0.55f, 0.93f), spineLow = 115f, spineUp = 70f, head = 40f,
                armNear = Reach(0.84f, F, -1f), armFar = Reach(0.84f, F, -1f),
                legNear = Angles(0f, -90f, -90f), legFar = Angles(0f, -90f, -90f),
            ),
        ),
        "march" to side(
            SidePose(
                pelvis(0.8f, 0.45f), spineLow = 178f, spineUp = 178f,
                armNear = Angles(-35f, -15f), armFar = Angles(40f, 70f),
                legNear = Angles(90f, 0f), legFar = Angles(0f, 0f),
            ),
            SidePose(
                pelvis(0.8f, 0.45f), spineLow = 178f, spineUp = 178f,
                armNear = Angles(40f, 70f), armFar = Angles(-35f, -15f),
                legNear = Angles(0f, 0f), legFar = Angles(90f, 0f),
            ),
        ),
        "brisk_walk" to side(
            SidePose(
                pelvis(0.8f, 0.45f), spineLow = 175f, spineUp = 175f, head = 178f,
                armNear = Angles(-35f, -20f), armFar = Angles(35f, 70f),
                legNear = Angles(25f, 10f, 90f), legFar = Angles(-20f, -40f, 45f),
            ),
            SidePose(
                pelvis(0.8f, 0.45f), spineLow = 175f, spineUp = 175f, head = 178f,
                armNear = Angles(35f, 70f), armFar = Angles(-35f, -20f),
                legNear = Angles(-20f, -40f, 45f), legFar = Angles(25f, 10f, 90f),
            ),
        ),
        "hip_flexor" to side(
            SidePose(
                pelvis(0.72f, 0.70f),
                armNear = Angles(20f, -30f), armFar = Angles(20f, -30f),
                legNear = Reach(0.97f, F, 1f), legFar = Angles(0f, -90f, -90f),
            ),
            SidePose(
                pelvis(0.80f, 0.70f),
                armNear = Angles(175f, 175f), armFar = Angles(175f, 175f),
                legNear = Reach(0.97f, F, 1f), legFar = Angles(0f, -90f, -90f),
            ),
        ),
        "chest_opener" to side(
            SidePose(
                pelvis(0.82f, 0.45f),
                armNear = Angles(90f, 180f), armFar = Angles(90f, 180f),
            ),
            SidePose(
                pelvis(0.82f, 0.47f),
                armNear = Angles(90f, 180f), armFar = Angles(90f, 180f),
                legNear = Angles(30f, 0f), legFar = Angles(-25f, -25f, 45f),
            ),
            listOf(line(0.97f, 0.0f, 0.97f, F)),
        ),
        "neck_tilts" to front(FrontPose(headTilt = -35f), FrontPose(headTilt = 35f)),
        "shoulder_rolls" to front(FrontPose(shoulderLift = 0.06f), FrontPose(shoulderLift = -0.015f)),
    )

    fun forExercise(id: String): FigureDef? = all[id]
}

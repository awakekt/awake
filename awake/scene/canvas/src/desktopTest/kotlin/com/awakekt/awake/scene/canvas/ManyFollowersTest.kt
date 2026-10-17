/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasCurve
import com.awakekt.awake.ui.canvas.CanvasCurvePoint
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasFollowLimit
import com.awakekt.awake.ui.canvas.CanvasFollowRules
import com.awakekt.awake.ui.canvas.CanvasProjection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * A crowd: [FOLLOWERS] name plates from a [CanvasFollowerPool], half of them off screen, under a
 * [CanvasFollowLimit] of [LIMIT]. What a frame costs is bounded by what is drawn, not by how many
 * follow: each follower is projected once, at most [LIMIT] are composed, and pooling them in and
 * out makes no entities.
 *
 * The time bound is loose, for a shared CI machine; the counts are the real check. It prints the
 * mean frame so a change's cost shows in the log.
 */
class ManyFollowersTest {

    private val world = World()
    private var projections = 0

    /** World x straight onto the screen, at a distance of |x| / 10. */
    private val projector = object : CanvasProjector {
        override fun project(x: Float, y: Float, z: Float): Vec2 = Vec2(x, y)

        override fun bounds(entity: Entity): Rectangle? = null

        override fun projectFollow(x: Float, y: Float, z: Float): CanvasProjection {
            projections++
            return CanvasProjection(x, y, depth = 1f, distance = kotlin.math.abs(x) / 10f)
        }
    }

    private val plate = SceneCanvasElement(
        kind = CanvasElementKind.Panel,
        anchor = CanvasAnchor.BottomCenter,
        offsetX = 0f,
        offsetY = 0f,
        width = 60f,
        height = 12f,
        background = "#202020C0",
        followRules = CanvasFollowRules(
            maxDistance = 400f,
            scale = CanvasCurve(listOf(CanvasCurvePoint(0f, 1f), CanvasCurvePoint(200f, 0.5f))),
            fade = CanvasCurve(listOf(CanvasCurvePoint(150f, 1f), CanvasCurvePoint(250f, 0.2f))),
        ),
    )

    private fun crowd(): CanvasFollowerPool {
        world.add(world.create(), CanvasFollowLimit(max = LIMIT))
        val pool = CanvasFollowerPool(world, plate)
        for (index in 0 until FOLLOWERS) {
            // Half from -1600 to 0, off the screen's left; half across it.
            val x = (index - FOLLOWERS / 2) * SPACING
            val y = (index % ROWS) * ROW_HEIGHT + ROW_HEIGHT
            world.create().also {
                world.add(it, Name("Mob $index"))
                world.add(it, Transform().apply { worldMatrix = Mat4().translate(x, y, 0f) })
            }
            pool.acquire("Mob $index")
        }
        return pool
    }

    private fun frame(host: ComposeHost): FrameOutput = host.frame(FrameInput(WIDTH, HEIGHT)) { SceneCanvas(world, projector = projector) }

    @Test
    fun aFrameCostsWhatItDrawsNotHowManyFollow() {
        crowd()
        val host = ComposeHost()
        repeat(WARM_UP) { frame(host) }

        projections = 0
        var drawn = 0
        val time: Duration = measureTime {
            repeat(MEASURED) { drawn = frame(host).semantics.countTagged() }
        }

        assertEquals(FOLLOWERS * MEASURED, projections, "each follower projected once a frame")
        assertEquals(LIMIT, drawn, "only the limit's worth composed")
        val mean = time / MEASURED
        println("ManyFollowersTest: $FOLLOWERS followers, $drawn drawn, mean frame $mean")
        assertTrue(mean < FRAME_BUDGET, "mean frame $mean over $FRAME_BUDGET")
    }

    @Test
    fun poolingFollowersInAndOutMakesNoEntities() {
        val pool = crowd()
        val host = ComposeHost()
        val entities = world.family<Name>().size + world.family<com.awakekt.awake.ui.canvas.CanvasElement>().size

        repeat(CHURN_FRAMES) { round ->
            // A tenth of the crowd leaves and comes back each frame, as damage numbers do.
            val leaving = (0 until FOLLOWERS / 10).map { (round * 7 + it * 10) % FOLLOWERS }
            val handedOut = ArrayList<Entity>()
            world.family<com.awakekt.awake.ui.canvas.CanvasElement>().forEach { entity, element ->
                if (element.visible && element.follow.removePrefix("Mob ").toIntOrNull() in leaving) handedOut += entity
            }
            handedOut.forEach(pool::release)
            leaving.forEach { pool.acquire("Mob $it") }
            frame(host)
        }

        assertEquals(entities, world.family<Name>().size + world.family<com.awakekt.awake.ui.canvas.CanvasElement>().size)
        assertEquals(FOLLOWERS, pool.size)
    }

    private companion object {
        const val FOLLOWERS = 1000
        const val LIMIT = 100
        const val SPACING = 3.2f
        const val ROWS = 20
        const val ROW_HEIGHT = 25f
        const val WIDTH = 1600
        const val HEIGHT = 600
        const val WARM_UP = 10
        const val MEASURED = 20
        const val CHURN_FRAMES = 10
        val FRAME_BUDGET = 100.milliseconds
    }
}

private fun List<SemanticsNode>.countTagged(): Int = sumOf { (if (it.testTag?.startsWith("canvas-element-") == true) 1 else 0) + it.children.countTagged() }

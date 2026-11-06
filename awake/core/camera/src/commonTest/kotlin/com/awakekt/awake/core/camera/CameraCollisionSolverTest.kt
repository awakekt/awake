/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.camera

import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraCollisionSolverTest {
    private val pivot = Vec3f(0f, 2f, 0f)
    private val desiredEye = Vec3f(0f, 2f, 10f)
    private val out = Vec3f()

    /** A wall across the line from the pivot to the eye at z = [wallZ]. */
    private fun wallAt(wallZ: Float) = CameraObstruction { from, to, radius ->
        val travel = to.z - from.z
        val reach = (wallZ - radius - from.z) / travel
        if (reach in 0f..1f) reach else 1f
    }

    @Test
    fun anEyeUnderASlopeIsLifted() {
        // Ground rising one unit per unit of z: 10 high under the desired eye, 10.5 at the far sample.
        val slope = CameraGround { _, z -> z }
        val settings = CameraCollision(obstacles = false, groundMargin = 0.5f)
        val eye = CameraCollisionSolver().solve(pivot, desiredEye, settings, slope, null, footprint = 0.5f, delta = 0.016f, out = out)

        assertEquals(11f, eye.y, 1e-4f, "the eye stands groundMargin above the highest sample around it")
        assertEquals(10f, eye.z, 1e-4f)
    }

    @Test
    fun groundOffTheTerrainLeavesTheEyeAlone() {
        val none = CameraGround { _, _ -> Float.NaN }
        val eye = CameraCollisionSolver().solve(pivot, desiredEye, CameraCollision(), none, null, 0.5f, 0.016f, out)

        assertEquals(2f, eye.y)
    }

    @Test
    fun aWallBetweenTheTargetAndTheEyePullsItIn() {
        val settings = CameraCollision(ground = false, radius = 0.2f)
        val eye = CameraCollisionSolver().solve(pivot, desiredEye, settings, null, wallAt(4f), 0f, 0.016f, out)

        assertTrue(eye.z < 4f - 0.2f + 1e-4f, "the eye stops a radius short of the wall, was ${eye.z}")
        assertTrue(eye.z > 3.7f, "but no shorter than that, was ${eye.z}")
    }

    @Test
    fun theEaseOutReturnsTheEyeToTheDesiredDistance() {
        val solver = CameraCollisionSolver()
        val settings = CameraCollision(ground = false, easeOutSeconds = 0.25f)
        solver.solve(pivot, desiredEye, settings, null, wallAt(4f), 0f, 0.016f, out)
        val blocked = solver.distance

        val clear = CameraObstruction { _, _, _ -> 1f }
        solver.solve(pivot, desiredEye, settings, null, clear, 0f, 0.016f, out)
        assertTrue(solver.distance > blocked && solver.distance < 10f, "one frame later it is on its way out, not there: ${solver.distance}")

        repeat(180) { solver.solve(pivot, desiredEye, settings, null, clear, 0f, 1f / 60f, out) }
        assertEquals(10f, solver.distance, 1e-2f, "three seconds later it is back at the desired distance")
    }

    @Test
    fun aNewObstaclePullsInAtOnceWhileEasingOut() {
        val solver = CameraCollisionSolver()
        val settings = CameraCollision(ground = false)
        solver.solve(pivot, desiredEye, settings, null, wallAt(6f), 0f, 0.016f, out)
        solver.solve(pivot, desiredEye, settings, null, wallAt(3f), 0f, 0.016f, out)

        assertTrue(solver.distance < 3f, "a nearer wall is not eased toward: ${solver.distance}")
    }

    @Test
    fun collisionWithNothingToCollideWithIsTheDesiredEye() {
        val eye = CameraCollisionSolver().solve(pivot, desiredEye, CameraCollision(), null, null, 0.5f, 0.016f, out)

        assertEquals(desiredEye, eye)
    }
}

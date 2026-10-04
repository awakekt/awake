/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The "Math" guide includes these samples, so they keep compiling and doing what it says. */
class MathDocsSampleTest {

    @Test
    fun theNoArgumentVectorIsOneNotZero() {
        // --8<-- [start:vector-defaults]
        val ones = Vec3f() // (1, 1, 1)
        val origin = Vec3f.ZERO // (0, 0, 0), a fresh instance each time
        val up = Vec3f.UP // (0, 1, 0); FORWARD is (0, 0, -1)
        // --8<-- [end:vector-defaults]

        assertEquals(Vec3f(1f, 1f, 1f), ones)
        assertEquals(Vec3f(0f, 0f, 0f), origin)
        assertEquals(Vec3f(0f, 1f, 0f), up)
    }

    @Test
    fun aQuarterTurnAboutUpTurnsForwardToLeft() {
        // --8<-- [start:rotate]
        val quarterTurn = Quat.fromAxisAngle(Vec3f.UP, 90.angleRad)
        val turned = quarterTurn.rotate(Vec3f.FORWARD) // (-1, 0, 0): counter-clockwise from above
        // --8<-- [end:rotate]

        assertEquals(-1f, turned.x, TOLERANCE)
        assertEquals(0f, turned.y, TOLERANCE)
        assertEquals(0f, turned.z, TOLERANCE)
    }

    @Test
    fun aLensBuildsItsMatrixForTheRenderersClipSpace() {
        // --8<-- [start:lens]
        val lens = Lens.perspective(eye = Vec3f(0f, 2f, 6f), center = Vec3f.ZERO, fovYDegrees = 60f)
        val viewProjection = lens.viewProjectionMatrix(aspect = 16f / 9f, clipSpace = ClipSpace.WebGpu)
        val pixel = lens.projectToViewport(Vec3f.ZERO, viewProjection, 1280f, 720f, ClipSpace.WebGpu)
        // --8<-- [end:lens]

        assertEquals(60f.angleRad, lens.fovYRadians, TOLERANCE)
        assertNotNull(pixel)
        assertEquals(640f, pixel.x, 0.01f)
        assertEquals(360f, pixel.y, 0.01f)
    }

    @Test
    fun aCharacterBoxThatSinksIntoTheGroundIsPushedBackUp() {
        // --8<-- [start:overlap-2d]
        val ground = Box2(x = 0f, y = -1f, halfWidth = 10f, halfHeight = 1f) // its top is at y = 0
        val player = Box2(x = 0f, y = 0.9f, halfWidth = 0.5f, halfHeight = 1f) // its feet are 0.1 below it
        val coin = Circle2(x = 0.4f, y = 1.2f, radius = 0.3f)

        val picksUpCoin = player.overlaps(coin)

        val push = Overlap2() // kept and reused, so a frame of checks allocates nothing
        if (player.penetration(ground, push)) {
            player.x += push.normalX * push.depth
            player.y += push.normalY * push.depth
        }
        // --8<-- [end:overlap-2d]

        assertEquals(true, picksUpCoin)
        assertEquals(1f, player.y, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-5f
    }
}

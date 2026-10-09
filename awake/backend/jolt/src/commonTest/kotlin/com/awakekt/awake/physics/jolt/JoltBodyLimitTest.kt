/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.SphereShape
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A world holds an open-world region's bodies, past the 5,000 this backend allowed and Jolt.js's 10,240. */
class JoltBodyLimitTest {
    @Test
    fun aWorldHoldsAndStepsMoreBodiesThanTheOldLimits() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            val boxes = List(BODIES) { index ->
                world.createBody(BOX, Vec3f((index % ROW) * SPACING, 0f, (index / ROW) * SPACING), Quat.IDENTITY, MotionType.STATIC)
            }
            world.createBody(SphereShape(0.3f), Vec3f(0f, 5f, 0f), Quat.IDENTITY, MotionType.DYNAMIC)

            repeat(STEPS) { world.step(1f / 60f) }

            val last = BODIES - 1
            val hit = assertNotNull(
                world.raycast(Vec3f((last % ROW) * SPACING, 5f, (last / ROW) * SPACING), Vec3f(0f, -1f, 0f), 10f),
                "the last body is in the world",
            )
            assertEquals(boxes.last(), hit.handle)
        } finally {
            world.destroy()
        }
    }

    private companion object {
        const val BODIES = 10_500
        const val ROW = 120
        const val SPACING = 2f
        const val STEPS = 10
        val BOX = BoxShape(Vec3f(0.4f, 0.4f, 0.4f))
    }
}

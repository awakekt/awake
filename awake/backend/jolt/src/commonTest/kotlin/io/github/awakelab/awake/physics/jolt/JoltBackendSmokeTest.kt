/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics.jolt

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.syncTransforms
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That this target's Jolt binding can build a world, step it, and report the result.
 *
 * In `commonTest` deliberately: it runs on desktop, iOS and wasmJs, and it is the only thing that
 * does. Every other Jolt test in this module runs on desktop alone, which is how the iOS backend
 * reached four features deep while aborting on its first step -- a fixed-size temp allocator
 * sized under what Jolt asks for. Compiling proved nothing.
 */
class JoltBackendSmokeTest {
    @Test
    fun aBoxFalls() = runTest {
        val world = createJoltPhysicsWorld()
        try {
            val box = world.createBody(
                BoxShape(Vec3f(0.5f, 0.5f, 0.5f)),
                Vec3f(0f, 10f, 0f),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
            )
            repeat(30) { world.step(1f / 60f) }

            // Half a second of gravity is about 1.2m. Asserting movement rather than a distance:
            // this exists to prove the binding runs at all, not to re-measure gravity.
            val y = world.syncTransforms().single { it.handle == box }.position.y
            assertTrue(y < 9.5f, "the box did not fall: y=$y")
        } finally {
            world.destroy()
        }
    }
}

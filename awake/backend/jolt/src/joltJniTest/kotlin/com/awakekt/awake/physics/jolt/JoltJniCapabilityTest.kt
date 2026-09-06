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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What jolt-jni can do that the common contract does not require, and so cannot be asserted for
 * every backend.
 *
 * Shared by `desktopTest` and `androidDeviceTest`, which are the same binding. Everything else in
 * this module lives in `commonTest` and runs on every target; what is left here is one thing:
 *
 * **Active-body readback.** `forEachBodyTransform`'s own contract says a backend that cannot
 * enumerate its awake bodies visits everything it tracks, and that a caller "must not treat being
 * visited as evidence of movement". jolt-jni can filter; JoltC has no `GetActiveBodies`. Asserting
 * the filtering in common code would demand an optimisation the API calls optional.
 *
 * Heightfields used to be here too, because JoltC wraps none and iOS threw for one. That is no
 * longer a capability gap -- that backend builds the field as a triangle mesh -- so those tests
 * moved back to `commonTest` where every target has to pass them.
 */
class JoltJniCapabilityTest {

    @Test
    fun aSettledBodyStopsBeingReadBack() = runTest {
        // The point of the active-body readback: a scene spends most of its life at rest, and a
        // crate that has stopped moving costs a JNI crossing per frame to report the pose the
        // caller already wrote.
        val world = createJoltPhysicsWorld()
        try {
            world.createBody(
                BoxShape(Vec3f(10f, 0.5f, 10f)),
                Vec3f(0f, -0.5f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            world.createBody(
                BoxShape(Vec3f(0.5f, 0.5f, 0.5f)),
                Vec3f(0f, 1f, 0f),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
            )

            var whileFalling = 0
            world.step(1f / 60f)
            world.forEachBodyTransform { _, _, _ -> whileFalling++ }
            assertTrue(whileFalling > 0, "a falling body must be visited")

            // Long enough for Jolt to put it to sleep on the floor.
            repeat(600) { world.step(1f / 60f) }
            var whenSettled = 0
            world.forEachBodyTransform { _, _, _ -> whenSettled++ }

            assertEquals(0, whenSettled, "a sleeping body should not be read back at all")
        } finally {
            world.destroy()
        }
    }
}

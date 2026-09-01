/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.syncTransforms
import io.github.awakelab.awake.physics.jolt.JoltPhysicsWorld
import io.github.awakelab.awake.showcase.terrain.TerrainExampleAsset
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The showcase's terrain physics against a real Jolt world -- not a fake.
 *
 * The point is the pairing: the box falls onto the same [TerrainExampleAsset.collisionShape] the
 * terrain mesh is built from, so a disagreement between what is drawn and what is collided with
 * shows up here rather than as a box sinking into a hillside on screen.
 */
class TerrainPhysicsSimulationTest {

    @Test
    fun aBoxFallsAndComesToRestOnTheHeightfield() {
        val world = JoltPhysicsWorld()
        try {
            world.createBody(
                TerrainExampleAsset.collisionShape,
                Vec3f(0f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            val box = world.createBody(
                BoxShape(Vec3f(BOX_HALF_EXTENT, BOX_HALF_EXTENT, BOX_HALF_EXTENT)),
                Vec3f(0f, DROP_HEIGHT, 0f),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
            )

            // The last pose the simulation reported, kept as it arrives. Reading it afterwards
            // would find nothing: the box comes to rest, Jolt puts it to sleep, and a sleeping
            // body is not in the active readback -- which is exactly why a game holds the pose in
            // its own transform rather than asking for it again.
            var resting = Float.NaN
            repeat(STEPS) {
                world.step(FIXED_DELTA)
                world.forEachBodyTransform { handle, position, _ ->
                    if (handle == box) resting = position.y
                }
            }

            assertTrue(
                resting < DROP_HEIGHT - 1f,
                "the box never fell: it is still at $resting, dropped from $DROP_HEIGHT",
            )
            // The terrain's centre sample is 1.8 * 0.65 = 1.17 world units high, and the box rests
            // half its own height above that. Jolt stores heightfield samples quantized, so this is
            // a band rather than an equality -- but a band far tighter than "fell through", which
            // is the failure a wrong collision shape or a wrong origin actually produces.
            assertTrue(
                resting in EXPECTED_REST_LOW..EXPECTED_REST_HIGH,
                "expected the box to rest on the terrain near $EXPECTED_REST_LOW..$EXPECTED_REST_HIGH, got $resting",
            )
        } finally {
            world.destroy()
        }
    }

    private companion object {
        const val BOX_HALF_EXTENT = 0.5f
        const val DROP_HEIGHT = 6f
        const val FIXED_DELTA = 1f / 60f
        const val STEPS = 240
        const val EXPECTED_REST_LOW = 1.2f
        const val EXPECTED_REST_HIGH = 2.2f
    }
}

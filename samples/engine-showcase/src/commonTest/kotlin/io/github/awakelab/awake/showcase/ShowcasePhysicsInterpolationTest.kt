/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.InterpolatedSystem
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.SphereShape
import io.github.awakelab.awake.physics.jolt.createJoltPhysicsWorld
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.physics.PhysicsBody
import io.github.awakelab.awake.showcase.examples.ShowcasePhysics
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That the showcase's registered physics system is actually interpolated.
 *
 * `ShowcasePhysics.system()` wraps [io.github.awakelab.awake.scene.physics.PhysicsSystem] in a
 * lazy delegate, and `SceneSchedule` decides who gets interpolated by testing the system it was
 * *registered* with -- the wrapper, not what is behind it. A wrapper that forgets to declare or
 * forward the interface compiles, runs, and silently draws every body at whatever the last fixed
 * step wrote. Nothing about that is visible in a stack trace, which is why it is pinned here.
 */
class ShowcasePhysicsInterpolationTest {

    @AfterTest
    fun tearDown() {
        // A mutable global, so a leaked world would leak into whatever test ran next.
        ShowcasePhysics.world?.destroy()
        ShowcasePhysics.world = null
    }

    @Test
    fun interpolatingTheRegisteredSystemMovesTheBodyItDrives() = runTest {
        val physics = createJoltPhysicsWorld()
        ShowcasePhysics.world = physics
        val system = ShowcasePhysics.system()
        val world = World()
        val entity = world.create()
        val transform = Transform(position = Vec3f(0f, 20f, 0f))
        world.add(entity, transform)
        world.add(entity, PhysicsBody(shape = SphereShape(radius = 0.5f), motionType = MotionType.DYNAMIC))

        // Two steps, so there are two poses to blend between. One falling body is enough: what is
        // under test is the wiring, and gravity is the cheapest way to make the two poses differ.
        repeat(2) { system.update(world, 1f / 60f) }
        val afterStep = transform.position.y

        system.interpolate(world, 0f)

        // Alpha zero draws the older of the two poses, and a falling body was higher then. If the
        // wrapper drops interpolate, this stays exactly where the step left it.
        assertTrue(
            transform.position.y > afterStep,
            "interpolate did not reach the physics system: the body stayed at $afterStep",
        )
    }

    @Test
    fun theRegisteredSystemDeclaresItselfInterpolated() {
        // The type half of the same claim: SceneSchedule only ever sees this, so a wrapper that
        // forwards correctly but declares plain System is never asked.
        assertTrue(
            ShowcasePhysics.system() is InterpolatedSystem,
            "the showcase's physics system would never be interpolated by SceneSchedule",
        )
    }
}

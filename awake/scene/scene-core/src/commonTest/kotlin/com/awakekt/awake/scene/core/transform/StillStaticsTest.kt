/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.transform

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A still world of [StaticTransform] props visited every static each frame to check it was built.
 * A frame in which no static joined, left, swapped or reset its transform now visits none.
 */
class StillStaticsTest {
    private val world = World()
    private val system = TransformSystem()

    private fun addStatic(x: Float): Transform =
        Transform(position = Vec3f(x, 0f, 0f)).also { world.add(world.create().also { e -> world.add(e, StaticTransform) }, it) }

    @Test
    fun aStillWorldDoesNotVisitItsStatics() {
        repeat(3) { addStatic(it.toFloat()) }
        system.update(world, 0f)
        val walks = system.staticWalks

        repeat(5) { system.update(world, 0f) }

        assertEquals(walks, system.staticWalks)
    }

    @Test
    fun aStaticAddedToAStillWorldIsBuilt() {
        addStatic(1f)
        repeat(3) { system.update(world, 0f) }

        val late = addStatic(7f)
        system.update(world, 0f)

        assertEquals(7f, late.worldMatrix.data[12])
    }

    @Test
    fun aStaticResetInAStillWorldIsBuiltAgain() {
        val prop = addStatic(3f)
        repeat(3) { system.update(world, 0f) }

        prop.reset()
        prop.position.x = 9f
        system.update(world, 0f)

        assertEquals(9f, prop.worldMatrix.data[12])
    }

    @Test
    fun aStaticWhoseTransformIsSwappedIsBuiltAgain() {
        val entity = world.create().also { world.add(it, StaticTransform) }
        world.add(entity, Transform(position = Vec3f(1f, 0f, 0f)))
        repeat(3) { system.update(world, 0f) }

        val swapped = Transform(position = Vec3f(4f, 0f, 0f))
        world.add(entity, swapped)
        system.update(world, 0f)

        assertEquals(4f, swapped.worldMatrix.data[12])
    }

    @Test
    fun aClearedWorldBuildsItsNewStatics() {
        addStatic(1f)
        system.update(world, 0f)
        world.clear()

        val fresh = addStatic(5f)
        system.update(world, 0f)

        assertEquals(5f, fresh.worldMatrix.data[12])
    }
}

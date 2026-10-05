/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.particles

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TransformPlacementTest {
    @Test
    fun aRootNodeIsWhereItsOwnPositionSays() {
        val world = World()
        val root = world.create()
        world.add(root, Transform(position = Vec3f(1f, 2f, 3f)))

        val into = Vec3f(0f, 0f, 0f)
        assertTrue(TransformPlacement.position(world, root, into))

        assertEquals(Vec3f(1f, 2f, 3f), into)
    }

    @Test
    fun aChildNodeIsWhereItsWorldMatrixPutIt() {
        val world = World()
        val parent = world.create()
        val child = world.create()
        world.add(parent, Transform(position = Vec3f(10f, 0f, 0f)))
        world.add(
            child,
            Transform(position = Vec3f(1f, 2f, 3f), parent = parent).apply {
                worldMatrix.m03 = 11f
                worldMatrix.m13 = 2f
                worldMatrix.m23 = 3f
            },
        )

        val into = Vec3f(0f, 0f, 0f)
        assertTrue(TransformPlacement.position(world, child, into))

        assertEquals(Vec3f(11f, 2f, 3f), into, "its place in the world, not its offset from the parent")
    }

    @Test
    fun anEntityWithNoTransformHasNoPlace() {
        val world = World()
        val bare = world.create()

        val into = Vec3f(7f, 8f, 9f)
        assertFalse(TransformPlacement.position(world, bare, into))

        assertEquals(Vec3f(7f, 8f, 9f), into, "left alone")
        assertNull(TransformPlacement.orientation(world, bare))
    }

    @Test
    fun orientationIsTheWorldMatrix() {
        val world = World()
        val entity = world.create()
        val transform = Transform()
        world.add(entity, transform)

        assertSame(transform.worldMatrix, TransformPlacement.orientation(world, entity))
    }
}

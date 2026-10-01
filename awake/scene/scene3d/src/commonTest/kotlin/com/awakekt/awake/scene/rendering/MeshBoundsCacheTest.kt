/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MeshBoundsCacheTest {

    @Test
    fun worldBoundsFollowTheTransformAndStayCachedWhileItHoldsStill() {
        val world = World()
        val system = TransformSystem()
        val transform = Transform(position = Vec3f(10f, 0f, 0f))
        world.add(world.create(), transform)
        val bounds = MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f)))
        system.update(world, 0f)

        val placed = bounds.worldBounds(transform)
        assertEquals(9f, placed.min.x)
        system.update(world, 0f)
        assertSame(placed, bounds.worldBounds(transform), "a transform that held still keeps its cached bounds")

        transform.position.x = 20f
        system.update(world, 0f)
        assertEquals(19f, bounds.worldBounds(transform).min.x, "a moved transform's bounds follow it")
    }
}

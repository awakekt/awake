/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.componentStore
import com.awakekt.awake.ecs.componentType
import com.awakekt.awake.ecs.family
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.SocketAttachmentComponent
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Selecting detail levels stays allocation-free with pins present (#1805): a crowd of groups, a
 * third pinned, a third socket-attached to a pinned one, and followers of both.
 */
class DetailPinAllocationTest {
    @Test
    fun selectionWithPinsAllocatesNothing() {
        val world = World()
        val leaders = ArrayList<Entity>()
        repeat(CROWD) { index ->
            val entity = group(world, x = index.toFloat())
            when (index % 3) {
                0 -> world.add(entity, DetailPin)
                1 -> world.add(entity, SocketAttachmentComponent(targetEntity = leaders.last()))
            }
            leaders += entity
        }
        leaders.forEach { leader -> group(world, x = 0f).also { world.get<LodGroup>(it)!!.follow = LodFollow(leader, levelOffset = 1) } }
        val pass = LodSelectionPass()
        val pins = DetailPins()
        val lens = Lens(eye = Vec3f(0f, 0f, 0f), center = Vec3f(0f, 0f, -1f), fovYRadians = 1f, near = 0.1f, far = 2_000f)
        val family = world.family<Transform, LodGroup>()
        val groups = world.componentStore(componentType<LodGroup>())
        val bounds = world.componentStore(componentType<MeshBounds>())
        fun frame() = pass.select(family, groups, bounds, lens, pins.attach(world))
        repeat(WARM_UP) { frame() }

        assertAllocatesNothing(readAllocation(FRAMES) { repeat(FRAMES) { frame() } }, "a frame's selection with pins")
        assertEquals(2 * CROWD / 3, leaders.count { world.get<LodGroup>(it)!!.activeLevel == 0 }, "the pinned and their attachments")
    }

    private fun group(world: World, x: Float) = world.create().also { entity ->
        world.add(entity, Transform(worldMatrix = Mat4().translate(x, 0f, -1_000f)))
        world.add(entity, MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
        world.add(
            entity,
            LodGroup(List(3) { LodLevel(FakeMesh, FakeMaterial, maxDistance = 0f) }).also {
                it.minScreenCoverage = floatArrayOf(0.25f, 0.1f, 0f)
            },
        )
    }

    private object FakeMesh : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val CROWD = 300
        const val WARM_UP = 50
        const val FRAMES = 200
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EcsStressExampleDriverTest {
    private val world = World()
    private val noop = NoopRenderer()
    private val cube = noop.createMesh(generate { cube(size = 1f, colored = true) })
    private val bounded = object : Mesh by cube {
        override val localBounds: Aabb = Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))
    }
    private val renderers = listOf(MeshRenderer(bounded, noop.createMaterial()))

    @AfterTest
    fun restoreDefaults() {
        EcsStressExampleDriver.detach(world)
        EcsStressExampleDriver.preset(EcsStressExampleDriver.DEFAULT_COUNT)
        EcsStressExampleDriver.moving = true
    }

    @Test
    fun everySpawnedEntityIsAnOrdinaryCullableRenderable() {
        EcsStressExampleDriver.preset(COUNT)
        EcsStressExampleDriver.spawn(world, renderers)

        assertEquals(COUNT, world.family<Transform, MeshRenderer>().size)
        assertEquals(COUNT, world.family<MeshBounds>().size, "without bounds no entity can be culled")
        assertEquals(COUNT, world.family<SwarmMotion>().size)
    }

    @Test
    fun detachDestroysOnlyTheEntitiesItSpawned() {
        val bystander = world.create()
        world.add(bystander, Transform())
        EcsStressExampleDriver.preset(COUNT)
        EcsStressExampleDriver.spawn(world, renderers)

        EcsStressExampleDriver.detach(world)

        assertTrue(world.isAlive(bystander))
        assertEquals(0, world.family<SwarmMotion>().size)
        assertEquals(0, EcsStressExampleDriver.spawnedCount)
    }

    @Test
    fun theSystemMovesEveryEntityAndStopsWhenAskedTo() {
        EcsStressExampleDriver.preset(COUNT)
        EcsStressExampleDriver.spawn(world, renderers)
        val system = SwarmMotionSystem()
        val rest = heights()

        system.update(world, STEP)
        val moved = heights()
        assertTrue(rest.indices.all { rest[it] != moved[it] }, "every entity moves each frame")

        EcsStressExampleDriver.moving = false
        system.update(world, STEP)
        assertEquals(moved, heights())
    }

    @Test
    fun everyPaletteCubeIsOneSolidColour() {
        repeat(EcsStressExampleDriver.paletteSize) { index ->
            val geometry = EcsStressExampleDriver.cubeGeometry(index)
            val colorAt = geometry.format.floatOffsetOf(VertexSemantic.Color)
            val colours = (colorAt until geometry.vertices.size step geometry.format.strideFloats)
                .map { at -> Triple(geometry.vertices[at], geometry.vertices[at + 1], geometry.vertices[at + 2]) }
                .toSet()
            assertEquals(1, colours.size, "cube $index has more than one vertex colour")
        }
    }

    private fun heights(): List<Float> = buildList {
        world.family<Transform, SwarmMotion>().forEach { _, transform, _ -> add(transform.position.y) }
    }

    private companion object {
        const val COUNT = 500
        const val STEP = 0.1f
    }
}

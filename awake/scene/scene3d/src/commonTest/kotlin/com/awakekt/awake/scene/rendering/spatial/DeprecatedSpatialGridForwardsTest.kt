/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("DEPRECATION")

package com.awakekt.awake.scene.rendering.spatial

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.ShadowCasterVolume
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Code written against this package before the move to `:awake:core:spatial` keeps compiling and
 * keeps working: the scene-side grid is a deprecated class forwarding to the core grid.
 */
class DeprecatedSpatialGridForwardsTest {

    @Test
    fun theGridAndConstantForwardToCoreSpatial() {
        val grid: SpatialGrid = SpatialGrid(cellSize = DEFAULT_SPATIAL_CELL_SIZE)
        val moved: com.awakekt.awake.core.spatial.SpatialGrid = grid.core

        assertEquals(com.awakekt.awake.core.spatial.DEFAULT_SPATIAL_CELL_SIZE, moved.cellSize)
        assertEquals(moved.cellSize, grid.cellSize)

        grid.insert(3, Aabb(Vec3f(-1f, 0f, -1f), Vec3f(1f, 2f, 1f)))
        assertEquals(1, moved.size, "An insert through the old class lands in the core grid.")
        assertEquals(setOf(3), grid.ids)
        val box = Aabb(Vec3f(-2f, -2f, -2f), Vec3f(2f, 2f, 2f))
        assertEquals(listOf(3), grid.queryAabb(box, mutableListOf()).toList())
        assertEquals(listOf(3), grid.queryRange(Vec3f(0f, 1f, 0f), 1f, mutableListOf()).toList())

        grid.remove(3)
        assertEquals(0, grid.size)
        moved.insert(4, box)
        grid.clear()
        assertEquals(0, moved.size, "A clear through the old class empties the core grid.")
    }

    @Test
    fun anEntityThatRisesWithinItsColumnIsStillACaster() {
        // A shadowed point light whose cube spans y 45..55 above the entity's column.
        val grid = SpatialGrid(cellSize = 32f)
        grid.insert(1, Aabb(Vec3f(-1f, 0f, -1f), Vec3f(1f, 2f, 1f)))
        val volume = ShadowCasterVolume().apply { addPointShadow(Vec3f(0f, 50f, 0f), range = 5f) }
        assertTrue(grid.queryShadowCasters(volume, mutableListOf()).isEmpty(), "On the ground it is out of the light's reach.")

        val risen = Aabb(Vec3f(-1f, 49f, -1f), Vec3f(1f, 51f, 1f))
        grid.insert(1, risen)

        assertTrue(volume.admits(risen), "The test scene must put the risen entity in the light's reach.")
        assertEquals(listOf(1), grid.queryShadowCasters(volume, mutableListOf()).toList(), "Dropped as a caster: its column's height is stale.")
        assertEquals(listOf(1), grid.core.queryVolume(volume, mutableListOf()).toList())
    }

    @Test
    fun aSpatialIndexOverAnOldGridIsMaintainedThroughIt() {
        val world = World()
        val grid = SpatialGrid(cellSize = 32f)
        world.add(world.create(), SpatialIndex(grid))
        val prop = world.create()
        world.add(prop, Transform(worldMatrix = Mat4().translate(5f, 0f, 5f)))
        world.add(prop, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))

        SpatialIndexSystem().update(world, 0f)

        assertSame(grid, world.findSpatialIndex()?.grid, "The system maintains the index it found, not a new one.")
        assertEquals(listOf(prop.id), grid.queryAabb(Aabb(Vec3f(0f, -2f, 0f), Vec3f(10f, 2f, 10f)), mutableListOf()).toList())
    }

    @Test
    fun aSpatialIndexOverACoreGridExposesItAsTheCore() {
        val core = com.awakekt.awake.core.spatial.SpatialGrid(cellSize = 16f)

        assertSame(core, SpatialIndex(core).grid.core)
        assertEquals(com.awakekt.awake.core.spatial.DEFAULT_SPATIAL_CELL_SIZE, SpatialIndex().grid.cellSize)
    }
}

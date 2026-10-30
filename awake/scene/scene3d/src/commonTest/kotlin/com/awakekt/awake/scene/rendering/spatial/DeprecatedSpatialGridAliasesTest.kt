/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("DEPRECATION")

package com.awakekt.awake.scene.rendering.spatial

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.ShadowCasterVolume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Code written against this package before the move to `:awake:core:spatial` keeps compiling. */
class DeprecatedSpatialGridAliasesTest {

    @Test
    fun theGridAliasAndConstantForwardToCoreSpatial() {
        val grid: SpatialGrid = SpatialGrid(cellSize = DEFAULT_SPATIAL_CELL_SIZE)
        val moved: com.awakekt.awake.core.spatial.SpatialGrid = grid

        assertEquals(com.awakekt.awake.core.spatial.DEFAULT_SPATIAL_CELL_SIZE, moved.cellSize)
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
        assertEquals(listOf(1), grid.queryVolume(volume, mutableListOf()).toList())
    }
}

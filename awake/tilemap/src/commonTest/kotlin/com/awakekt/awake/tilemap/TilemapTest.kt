/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.tilemap

import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.planes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TilemapTest {
    @Test
    fun editsInvalidateOnlyTheirChunkAndCopiesStayIsolated() {
        val source = IntArray(15) { -1 }
        val grid = TilemapGrid(5, 3, source, chunkSize = 2)
        source[0] = 7
        assertEquals(-1, grid[0, 0])
        assertEquals(6, grid.chunks.size)
        assertEquals(1, grid.chunks.last().width)
        assertEquals(1, grid.chunks.last().height)
        grid[4, 2] = 3
        grid[4, 2] = 3
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 1L), grid.chunks.map { it.revision })
        val snapshot = grid.copyTiles()
        grid[4, 2] = -1
        assertEquals(3, snapshot.last())
        assertFailsWith<IllegalArgumentException> { grid[5, 0] = 0 }
        assertFailsWith<IllegalArgumentException> { grid[0, 0] = -2 }
        assertFailsWith<IllegalArgumentException> { TilemapGrid(Int.MAX_VALUE, 2, intArrayOf()) }
    }

    @Test
    fun atlasFramesHaveTopFirstUvsAndEmptyTilesHaveNoVertices() {
        val grid = TilemapGrid(2, 2, intArrayOf(0, -1, 2, 3), chunkSize = 2)
        val atlas = TilemapAtlas(4, 4, 2, 2, pixelsPerUnit = 2f)
        val mesh = requireNotNull(atlas.geometry(grid, grid.chunks.single()))
        assertEquals(60, mesh.vertices.size)
        assertEquals(18, mesh.indices.size)
        assertEquals(listOf(0f, -1f, 0f, 0f, 0.5f), mesh.vertices.take(5))
        assertEquals(listOf(0f, -2f, 0f, 0f, 0f), mesh.vertices.drop(20).take(5))
        grid[0, 0] = -1
        grid[0, 1] = -1
        grid[1, 1] = -1
        assertNull(atlas.geometry(grid, grid.chunks.single()))
        grid[0, 0] = 4
        assertFailsWith<IllegalArgumentException> { atlas.geometry(grid, grid.chunks.single()) }
        assertFailsWith<IllegalArgumentException> { TilemapAtlas(3, 4, 2, 2) }
    }

    @Test
    fun chunkCullingFollowsTheFullNodeTransformAndCameraAspect() {
        val grid = TilemapGrid(8, 2, IntArray(16), chunkSize = 2)
        val atlas = TilemapAtlas(1, 1, 1, 1, pixelsPerUnit = 1f)
        val lens = Lens(eye = Vec3f(1f, -1f, 5f), center = Vec3f(1f, -1f, 0f), fovYRadians = 1f, near = 0.1f, far = 20f).apply {
            projection = Lens.Projection.Orthographic
            orthoHalfHeight = 0.9f
        }
        val planes = Frustum.planes(lens, 1f)
        assertTrue(atlas.isVisible(grid.chunks[0], Mat4(), planes))
        assertFalse(atlas.isVisible(grid.chunks[1], Mat4(), planes))
        assertTrue(atlas.isVisible(grid.chunks[1], Mat4().translate(-2f, 0f, 0f), planes))
        assertTrue(atlas.isVisible(grid.chunks[1], Mat4(), Frustum.planes(lens, 3f)))
        val rotated = Mat4().apply {
            m00 = 0f
            m01 = -1f
            m10 = 1f
            m11 = 0f
        }
        assertFalse(atlas.isVisible(grid.chunks[1], rotated, planes))
        rotated.m13 = -4f
        assertTrue(atlas.isVisible(grid.chunks[1], rotated, planes))
        assertTrue(atlas.isVisible(grid.chunks[1], Mat4().scale(0.1f), planes))
        assertFalse(atlas.isVisible(grid.chunks[0], Mat4().translate(0f, 0f, 10f), planes))
    }
}

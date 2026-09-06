/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain

import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MutableHeightmapTest {
    @Test
    fun appliesOneBatchAndReportsItsSmallestDirtyRegion() {
        val heightmap = MutableHeightmap(FloatArray(9), width = 3, depth = 3, scale = Vec3f(1f, 1f, 1f))

        val change = heightmap.apply(
            listOf(
                HeightmapSampleEdit(x = 2, z = 0, height = 3f),
                HeightmapSampleEdit(x = 0, z = 2, height = 4f),
                HeightmapSampleEdit(x = 2, z = 0, height = 5f),
            ),
        )

        assertEquals(HeightmapChange(1L, HeightmapDirtyRegion(0, 0, 2, 2)), change)
        assertEquals(5f, heightmap.heightAt(2, 0))
        assertEquals(4f, heightmap.heightAt(0, 2))
        assertEquals(1L, heightmap.revision)
    }

    @Test
    fun rejectsAnInvalidBatchWithoutMutatingOrAdvancingRevision() {
        val heightmap = MutableHeightmap(FloatArray(4), width = 2, depth = 2, scale = Vec3f(1f, 1f, 1f))

        assertFailsWith<IllegalArgumentException> {
            heightmap.apply(
                listOf(
                    HeightmapSampleEdit(x = 0, z = 0, height = 2f),
                    HeightmapSampleEdit(x = 2, z = 1, height = 3f),
                ),
            )
        }

        assertEquals(0f, heightmap.heightAt(0, 0))
        assertEquals(0L, heightmap.revision)
    }

    @Test
    fun ignoresNoOpEditsAndSnapshotsStableData() {
        val heightmap = MutableHeightmap(FloatArray(4), width = 2, depth = 2, scale = Vec3f(1f, 1f, 1f))

        assertNull(heightmap.setHeightAt(x = 1, z = 1, height = 0f))
        val change = heightmap.setHeightAt(x = 1, z = 1, height = 3f)
        val snapshot = heightmap.snapshot()
        heightmap.setHeightAt(x = 1, z = 1, height = 5f)

        assertEquals(1L, change?.revision)
        assertEquals(3f, snapshot.heightAt(1, 1))
        assertEquals(5f, heightmap.heightAt(1, 1))
    }
}

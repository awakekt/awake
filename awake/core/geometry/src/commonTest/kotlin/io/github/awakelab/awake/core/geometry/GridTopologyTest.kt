/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That the shared grid winding faces the way every caller assumes.
 *
 * This is the assertion the four hand-written copies of this loop never had. Winding is invisible
 * until something depends on it: a backwards-wound heightfield still draws, and rays still hit it
 * from both sides, and only a body resting on it -- or falling through it -- shows the difference.
 * Checking the normal here means no caller has to discover that.
 */
class GridTopologyTest {

    @Test
    fun everyTriangleFacesUpForAFlatYUpGrid() {
        val size = 4
        // A flat grid at y = 0, laid out the way callers lay one out: vertex (x, z) at z * w + x.
        val positions = Array(size * size) { index ->
            floatArrayOf((index % size).toFloat(), 0f, (index / size).toFloat())
        }

        val indices = gridTriangleIndices(size, size)

        assertEquals((size - 1) * (size - 1) * 6, indices.size)
        for (triangle in indices.indices step 3) {
            val a = positions[indices[triangle]]
            val b = positions[indices[triangle + 1]]
            val c = positions[indices[triangle + 2]]
            // (b - a) x (c - a); only the y component decides up from down for a flat grid.
            val normalY = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2])
            assertTrue(
                normalY > 0f,
                "triangle at index $triangle faces down (normal y = $normalY), so a body would " +
                    "fall through this surface while rays still hit it",
            )
        }
    }

    @Test
    fun aDroppedCellLeavesAHoleAndNoStrayIndices() {
        val size = 4

        // The clipmap ring case: a grid with its middle removed.
        val indices = gridTriangleIndices(size, size) { x, z -> !(x == 1 && z == 1) }

        assertEquals(((size - 1) * (size - 1) - 1) * 6, indices.size, "the array was not trimmed")
        // Vertex 5 is the top-left corner of the dropped cell and belongs to three other cells, so
        // it must still appear -- "dropped" is about cells, not about orphaning vertices.
        assertTrue(indices.contains(5), "neighbouring cells lost a vertex they still use")
    }

    @Test
    fun aGridTooSmallToHaveACellIsRejected() {
        // One vertex across is a line, not a surface. Better to say so than to return nothing and
        // let a caller wonder where its terrain went.
        val failure = runCatching { gridTriangleIndices(1, 4) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "was $failure")
    }
}

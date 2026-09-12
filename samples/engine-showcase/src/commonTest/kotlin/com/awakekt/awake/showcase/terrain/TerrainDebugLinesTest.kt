/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.terrain

import com.awakekt.awake.core.math.Mat4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainDebugLinesTest {
    @Test
    fun diagnosticDrawsEveryVisualAndCollisionSampleInTheSameCentredCoordinates() {
        val heightmap = TerrainExampleAsset.heightmap
        val lines = heightfieldDiagnosticLines(heightmap, TerrainExampleAsset.collisionShape, Mat4())

        val visualGridLines = heightmap.width * (heightmap.depth - 1) +
            heightmap.depth * (heightmap.width - 1)
        val normalLines = ((heightmap.width + 1) / 2) * ((heightmap.depth + 1) / 2)
        val collisionCrossLines = heightmap.width * heightmap.depth * 3
        assertEquals(visualGridLines + normalLines + collisionCrossLines + 3, lines.size)

        // The render grid begins at Heightmap.minX/minZ, rather than at the old corner origin.
        assertTrue(lines.any { it.start.x == heightmap.minX && it.start.z == heightmap.minZ })

        // The magenta X-cross at sample (0, 0) is centred on the same point as the cyan mesh.
        val collisionMarkerHalfSize = 0.08f
        assertTrue(
            lines.any { line ->
                line.start.x == heightmap.minX - collisionMarkerHalfSize &&
                    line.end.x == heightmap.minX + collisionMarkerHalfSize &&
                    line.start.y == heightmap.heightAt(0, 0) * heightmap.scale.y
            },
            "The collision sample at the first corner no longer coincides with the rendered heightmap.",
        )
    }
}

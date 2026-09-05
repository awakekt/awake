/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.terrain

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.geometry.facesUpward
import io.github.awakelab.awake.core.geometry.validate
import io.github.awakelab.awake.core.math.GridOrigin
import io.github.awakelab.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeightmapMeshBuilderTest {
    @Test
    fun buildsRectangularGridWithCentredCoordinatesUpwardNormalsAndDerivedStride() {
        val heightmap = Heightmap(
            samples = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f),
            width = 3,
            depth = 2,
            scale = Vec3f(2f, 3f, 4f),
        )

        val mesh = heightmap.toPositionNormalColorMesh(Color.White)
        val stride = VertexFormat.PositionNormalColor.strideFloats
        val lastOffset = 5 * stride

        assertEquals(6 * stride, mesh.vertices.size)
        // 3x2 samples at (2, _, 4) metres spans 4m x 4m, so the far corner is at +2, +2 rather
        // than at +4, +4: the map is centred on its own origin.
        assertEquals(2f, mesh.vertices[lastOffset])
        assertEquals(0f, mesh.vertices[lastOffset + 1])
        assertEquals(2f, mesh.vertices[lastOffset + 2])
        assertEquals(0f, mesh.vertices[lastOffset + 3], absoluteTolerance = 0.00001f)
        assertEquals(1f, mesh.vertices[lastOffset + 4])
        assertEquals(0f, mesh.vertices[lastOffset + 5], absoluteTolerance = 0.00001f)
        // Each quad is cut on its MAIN diagonal (0-4, 1-5), matching what a Jolt heightfield does
        // -- see gridTriangleIndices. Cut the other way and the drawn ground and the ground things
        // land on are two different surfaces inside every quad.
        assertContentEquals(intArrayOf(0, 3, 4, 0, 4, 1, 1, 4, 5, 1, 5, 2), mesh.indices)
    }

    /**
     * Every heightmap mesh is well-formed, and faces up.
     *
     * The cheap guard on the whole builder. A heightmap wound the wrong way is invisible under
     * back-face culling -- not dark, not inside-out, absent -- which reads as a missing draw call
     * and gets debugged in the renderer. Changing which diagonal a quad is cut on is exactly the
     * kind of edit that can flip it, and one landed recently.
     */
    @Test
    fun everyHeightmapMeshIsWellFormedAndFacesUp() {
        val heightmap = Heightmap(
            samples = FloatArray(SAMPLES * SAMPLES) { index ->
                val x = index % SAMPLES
                val z = index / SAMPLES
                // Not flat: a flat grid has no winding to get wrong and no normals worth checking.
                (x - z).toFloat() * 0.25f + (x % 3) * 0.4f
            },
            width = SAMPLES,
            depth = SAMPLES,
            scale = Vec3f(1.5f, 0.65f, 1.5f),
        )

        val mesh = heightmap.toPositionNormalColorMesh(Color.White)

        assertEquals(emptyList(), mesh.validate(), "the heightmap mesh reported problems")
        assertTrue(mesh.facesUpward(), "a heightmap that does not face up is invisible when culled")
    }

    /**
     * The one assertion that fails if anyone re-introduces a corner anchor.
     *
     * A corner-anchored tile's `Transform.position` is most of a tile away from anything drawn,
     * and LOD distance, culling spheres, origin rebasing and physics placement all read that
     * position -- so the anchor is not cosmetic, and it is not obvious from a screenshot either.
     */
    @Test
    fun boundsAreSymmetricAboutTheOrigin() {
        val heightmap = Heightmap(
            samples = FloatArray(9),
            width = 3,
            depth = 3,
            scale = Vec3f(8f, 1f, 8f),
        )

        val bounds = requireNotNull(heightmap.toPositionNormalColorMesh(Color.White).bounds)

        assertEquals(-8f, bounds.min.x)
        assertEquals(8f, bounds.max.x)
        assertEquals(-8f, bounds.min.z)
        assertEquals(8f, bounds.max.z)
    }

    /**
     * The compat mode, pinned to the numbers it replaced.
     *
     * Content authored against the corner convention keeps working by saying so, and this asserts
     * the OLD expectations verbatim -- the far corner at +4, +4 for a 4m map -- so "corner still
     * means corner" cannot quietly become "corner means something near the corner".
     */
    @Test
    fun aCornerAnchoredMapKeepsTheCoordinatesItAlwaysHad() {
        val heightmap = Heightmap(
            samples = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f),
            width = 3,
            depth = 2,
            scale = Vec3f(2f, 3f, 4f),
            origin = GridOrigin.Corner,
        )

        val mesh = heightmap.toPositionNormalColorMesh(Color.White)
        val lastOffset = 5 * VertexFormat.PositionNormalColor.strideFloats

        assertEquals(4f, mesh.vertices[lastOffset])
        assertEquals(4f, mesh.vertices[lastOffset + 2])
        assertEquals(1f, heightmap.heightAtWorld(4f, 4f) + 1f, "World 4,4 is still the far corner.")
    }

    @Test
    fun usesScaleAwareSlopesAndTheProvidedColourPolicy() {
        val heightmap = Heightmap(
            samples = floatArrayOf(0f, 1f, 2f, 3f),
            width = 2,
            depth = 2,
            scale = Vec3f(2f, 2f, 2f),
        )

        val mesh = heightmap.toPositionNormalColorMesh { x, z, _ -> Color(x.toFloat(), z.toFloat(), 0f) }

        assertEquals(-0.40824828f, mesh.vertices[3], absoluteTolerance = 0.00001f)
        assertEquals(0.40824828f, mesh.vertices[4], absoluteTolerance = 0.00001f)
        assertEquals(-0.81649655f, mesh.vertices[5], absoluteTolerance = 0.00001f)
        assertEquals(1f, mesh.vertices[VertexFormat.PositionNormalColor.strideFloats + 6])
    }

    private companion object {
        const val SAMPLES = 9
    }
}

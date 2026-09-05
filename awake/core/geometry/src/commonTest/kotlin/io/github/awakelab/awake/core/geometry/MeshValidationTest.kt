/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.geometry

import io.github.awakelab.awake.core.geometry.generate.generate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Each check catches the fault it is for, and a good mesh trips none of them.
 *
 * The second half is what makes the first half worth having: a validator that reports problems on
 * meshes the engine actually ships is one nobody will run twice.
 */
class MeshValidationTest {

    private fun grid(width: Int = 4, depth: Int = 4): MeshGeometry {
        val vertices = InterleavedVertices(VertexFormat.PositionNormalColor, width * depth)
        for (z in 0 until depth) {
            for (x in 0 until width) {
                val vertex = z * width + x
                vertices.put(vertex, VertexSemantic.Position, x.toFloat(), 0f, z.toFloat())
                vertices.put(vertex, VertexSemantic.Normal, 0f, 1f, 0f)
                vertices.put(vertex, VertexSemantic.Color, 1f, 1f, 1f)
            }
        }
        return vertices.build(gridTriangleIndices(width, depth))
    }

    private fun MeshGeometry.kinds() = validate().map { it.kind }.toSet()

    @Test
    fun theEnginesOwnMeshesAreClean() {
        // The generators every sample draws with, plus the grid the terrain is built on.
        listOf(
            "grid" to grid(),
            "cube" to generate { cube(size = 1f, colored = true) },
            "plane" to generate { plane(size = 4f, colored = true) },
        ).forEach { (name, mesh) ->
            assertEquals(emptyList(), mesh.validate(), "$name reported problems")
        }
    }

    @Test
    fun anIndexPastTheEndIsCaught() {
        val clean = grid()
        val broken = clean.copy(indices = clean.indices.copyOf().also { it[0] = 9999 })

        assertTrue(MeshProblem.Kind.Indices in broken.kinds(), "an out-of-range index went unreported")
    }

    @Test
    fun aVertexArrayThatIsNotAWholeNumberOfVerticesIsCaught() {
        val clean = grid()
        val broken = clean.copy(vertices = clean.vertices.copyOf(clean.vertices.size - 1))

        assertTrue(MeshProblem.Kind.Stride in broken.kinds(), "a truncated vertex array went unreported")
    }

    @Test
    fun aNormalThatIsNotUnitLengthIsCaught() {
        val clean = grid()
        val vertices = clean.vertices.copyOf()
        val normal = clean.format.floatOffsetOf(VertexSemantic.Normal)
        // Zero, which is what an un-normalised generator produces at a degenerate vertex -- and
        // what lights a surface as though it faced nowhere.
        vertices[normal] = 0f
        vertices[normal + 1] = 0f
        vertices[normal + 2] = 0f

        assertTrue(
            MeshProblem.Kind.NormalLength in clean.copy(vertices = vertices).kinds(),
            "a zero-length normal went unreported",
        )
    }

    @Test
    fun aNonFinitePositionIsCaught() {
        val clean = grid()
        val vertices = clean.vertices.copyOf()
        vertices[0] = Float.NaN

        assertTrue(MeshProblem.Kind.NotFinite in clean.copy(vertices = vertices).kinds(), "a NaN went unreported")
    }

    @Test
    fun aTriangleWithNoAreaIsCaught() {
        val clean = grid()
        val indices = clean.indices.copyOf()
        indices[1] = indices[0]

        assertTrue(
            MeshProblem.Kind.Degenerate in clean.copy(indices = indices).kinds(),
            "a triangle with a repeated corner went unreported",
        )
    }

    @Test
    fun aFlippedTriangleIsCaught() {
        val clean = grid()
        val indices = clean.indices.copyOf()
        // Reverse one triangle, which is exactly what a hole in a back-face-culled surface is.
        val first = indices[0]
        indices[0] = indices[2]
        indices[2] = first

        assertTrue(
            MeshProblem.Kind.Winding in clean.copy(indices = indices).kinds(),
            "a triangle wound against its neighbours went unreported",
        )
    }

    @Test
    fun groundThatFacesUpwardIsDistinguishedFromGroundThatDoesNot() {
        val clean = grid()
        assertTrue(clean.facesUpward(), "the grid the terrain is built on should face up")

        // Every triangle reversed: self-consistent, so `validate` is right to stay silent, and
        // invisible under back-face culling, which is what `facesUpward` is for.
        val flipped = IntArray(clean.indices.size) { at ->
            when (at % 3) {
                0 -> clean.indices[at + 2]
                2 -> clean.indices[at - 2]
                else -> clean.indices[at]
            }
        }
        val inverted = clean.copy(indices = flipped)
        assertEquals(emptyList(), inverted.validate(), "a uniformly reversed mesh is self-consistent")
        assertTrue(!inverted.facesUpward(), "a reversed ground mesh should not read as facing up")
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.gltf

import io.github.awakelab.awake.core.geometry.VertexFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exactly what each interleave layout puts where, including its defaults for absent attributes.
 *
 * Four of these five had no test at all while the showcase's skinned and glTF-viewer drivers
 * depended on them. A packing mistake in one is not a crash: the mesh loads, draws, and renders
 * with a UV read as a colour, which looks like a shader or asset problem anywhere except the
 * interleave loop.
 *
 * The values are deliberately distinguishable per component -- 1,2,3 and 0.1,0.2,0.3 rather than
 * repeated numbers -- so a swapped pair of attributes cannot pass by symmetry.
 */
class GltfInterleaveTest {

    /** Two vertices, every optional attribute present, all distinct. */
    private val full = GltfMesh(
        positions = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f),
        normals = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f),
        colors = floatArrayOf(0.01f, 0.02f, 0.03f, 0.04f, 0.05f, 0.06f),
        uvs = floatArrayOf(0.7f, 0.8f, 0.9f, 1f),
        indices = intArrayOf(0, 1, 0),
        jointIndices = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
        jointWeights = floatArrayOf(0.11f, 0.12f, 0.13f, 0.14f, 0.15f, 0.16f, 0.17f, 0.18f),
    )

    /** The same two vertices with nothing but positions, so every default is exercised. */
    private val bare = GltfMesh(
        positions = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f),
        normals = null,
        colors = null,
        uvs = null,
        indices = intArrayOf(0, 1, 0),
    )

    @Test
    fun positionColorUvPacksInFormatOrder() {
        assertContentEquals(
            floatArrayOf(
                1f, 2f, 3f, 0.01f, 0.02f, 0.03f, 0.7f, 0.8f,
                4f, 5f, 6f, 0.04f, 0.05f, 0.06f, 0.9f, 1f,
            ),
            full.toInterleavedPositionColorUv(),
        )
        assertEquals(
            VertexFormat.PositionColorUv.strideFloats * 2,
            full.toInterleavedPositionColorUv().size,
        )
    }

    @Test
    fun positionColorUvDefaultsToWhiteAndZeroUv() {
        assertContentEquals(
            floatArrayOf(
                1f, 2f, 3f, 1f, 1f, 1f, 0f, 0f,
                4f, 5f, 6f, 1f, 1f, 1f, 0f, 0f,
            ),
            bare.toInterleavedPositionColorUv(),
        )
    }

    @Test
    fun positionNormalColorPacksInFormatOrder() {
        assertContentEquals(
            floatArrayOf(
                1f, 2f, 3f, 0.1f, 0.2f, 0.3f, 0.01f, 0.02f, 0.03f,
                4f, 5f, 6f, 0.4f, 0.5f, 0.6f, 0.04f, 0.05f, 0.06f,
            ),
            full.toInterleavedPositionNormalColor(),
        )
    }

    @Test
    fun positionNormalColorDefaultsToAnUpNormalAndWhite() {
        assertContentEquals(
            floatArrayOf(
                1f, 2f, 3f, 0f, 1f, 0f, 1f, 1f, 1f,
                4f, 5f, 6f, 0f, 1f, 0f, 1f, 1f, 1f,
            ),
            bare.toInterleavedPositionNormalColor(),
        )
    }

    @Test
    fun positionNormalColorUvPacksInFormatOrder() {
        assertContentEquals(
            floatArrayOf(
                1f, 2f, 3f, 0.1f, 0.2f, 0.3f, 0.01f, 0.02f, 0.03f, 0.7f, 0.8f,
                4f, 5f, 6f, 0.4f, 0.5f, 0.6f, 0.04f, 0.05f, 0.06f, 0.9f, 1f,
            ),
            full.toInterleavedPositionNormalColorUv(),
        )
    }

    @Test
    fun positionNormalColorUvDefaultsEveryAbsentAttribute() {
        assertContentEquals(
            floatArrayOf(
                1f, 2f, 3f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f,
                4f, 5f, 6f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f,
            ),
            bare.toInterleavedPositionNormalColorUv(),
        )
    }

    @Test
    fun skinnedPacksJointsAndWeightsAfterTheColour() {
        // Joint indices are integers widened to floats, which is the one place this layout is not
        // simply "more of the same" -- a shader reads them as uint.
        assertContentEquals(
            floatArrayOf(
                1f, 2f, 3f, 0.1f, 0.2f, 0.3f, 0.01f, 0.02f, 0.03f,
                joint(1), joint(2), joint(3), joint(4), 0.11f, 0.12f, 0.13f, 0.14f,
                4f, 5f, 6f, 0.4f, 0.5f, 0.6f, 0.04f, 0.05f, 0.06f,
                joint(5), joint(6), joint(7), joint(8), 0.15f, 0.16f, 0.17f, 0.18f,
            ),
            full.toInterleavedSkinned(),
        )
    }

    @Test
    fun positionNormalColorUvSkinKeepsUvBeforeTheJoints() {
        assertContentEquals(
            floatArrayOf(
                1f, 2f, 3f, 0.1f, 0.2f, 0.3f, 0.01f, 0.02f, 0.03f, 0.7f, 0.8f,
                joint(1), joint(2), joint(3), joint(4), 0.11f, 0.12f, 0.13f, 0.14f,
                4f, 5f, 6f, 0.4f, 0.5f, 0.6f, 0.04f, 0.05f, 0.06f, 0.9f, 1f,
                joint(5), joint(6), joint(7), joint(8), 0.15f, 0.16f, 0.17f, 0.18f,
            ),
            full.toInterleavedPositionNormalColorUvSkin(),
        )
    }

    @Test
    fun anUnskinnedMeshIsRefusedRatherThanGivenEmptyJoints() {
        // Not zero-filled: a mesh with no JOINTS_0 has no business in a skinned layout at all, and
        // silently handing the GPU joint 0 at weight 0 would collapse it onto the root bone
        // instead of saying so.
        val failure = runCatching { bare.toInterleavedSkinned() }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException, "was $failure")
    }

    /**
     * A joint index as it is actually stored: the bit pattern, not the numeric value.
     *
     * The buffer is a `FloatArray` end to end, but the format declares this slot `UInt4`, so the
     * GPU reads those four bytes back as a `uint32`. Writing `1f` here would hand the shader
     * 0x3F800000 as a joint index.
     */
    private fun joint(index: Int): Float = Float.fromBits(index)
}

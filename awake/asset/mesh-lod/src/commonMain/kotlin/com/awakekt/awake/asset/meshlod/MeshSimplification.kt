/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

/**
 * A per-vertex attribute the simplifier keeps intact and weighs when it picks which edges to collapse:
 * a normal, a UV set, a tangent or a vertex colour.
 *
 * @property values [components] floats per vertex, in vertex order.
 * @property components Floats per vertex (2 for a UV set, 3 for a normal, 4 for a tangent or colour).
 * @property weight How much a difference in this attribute costs next to a geometric error measured in
 *   units of the mesh's largest extent. `0` keeps the attribute without weighing it.
 */
class MeshAttribute(
    val values: FloatArray,
    val components: Int,
    val weight: Float = 1f,
) {
    init {
        require(components > 0) { "components is $components; an attribute has at least one" }
        require(values.size % components == 0) { "values has ${values.size} floats, not a multiple of $components" }
        require(weight >= 0f) { "weight is $weight; it can't be negative" }
    }

    /** How many vertices this attribute holds. */
    val vertexCount: Int get() = values.size / components
}

/**
 * Skin influences per vertex: joint indices and their weights, [influences] of each per vertex (4 for a
 * glTF `JOINTS_0`/`WEIGHTS_0` pair). Collapsing between vertices bound to different joints costs
 * [weight] times the squared difference of their per-joint weights, so an edge that crosses a bend
 * collapses last.
 */
class SkinInfluences(
    val joints: IntArray,
    val weights: FloatArray,
    val influences: Int = DEFAULT_INFLUENCES,
    val weight: Float = 1f,
) {
    init {
        require(influences > 0) { "influences is $influences; a skinned vertex has at least one" }
        require(joints.size == weights.size) { "joints has ${joints.size} entries, weights ${weights.size}" }
        require(joints.size % influences == 0) { "joints has ${joints.size} entries, not a multiple of $influences" }
    }

    /** How many vertices these influences cover. */
    val vertexCount: Int get() = joints.size / influences

    private companion object {
        const val DEFAULT_INFLUENCES = 4
    }
}

/**
 * How far to simplify.
 *
 * @property targetRatio The fraction of the input's triangles to keep, in `0..1`.
 * @property maxError Stop before any collapse whose error, relative to the mesh's largest extent, is
 *   above this; the result may then keep more triangles than [targetRatio] asks for.
 * @property lockBorders Keep every vertex on an open border where it is. Turn it on for a mesh made of
 *   separate parts or primitives that meet along open edges, so simplifying each one doesn't open gaps.
 *   Off, a border vertex may still slide along its border, but never off it.
 * @property borderWeight How strongly a border keeps its shape when it does slide.
 */
data class SimplifyOptions(
    val targetRatio: Float,
    val maxError: Float = Float.MAX_VALUE,
    val lockBorders: Boolean = false,
    val borderWeight: Float = DEFAULT_BORDER_WEIGHT,
) {
    init {
        require(targetRatio in 0f..1f) { "targetRatio is $targetRatio; it must be in 0..1" }
        require(maxError >= 0f) { "maxError is $maxError; it can't be negative" }
        require(borderWeight >= 0f) { "borderWeight is $borderWeight; it can't be negative" }
    }

    private companion object {
        const val DEFAULT_BORDER_WEIGHT = 10f
    }
}

/**
 * A simplified index buffer over the **original** vertices: [indices] refer to the same vertex array
 * the input indexed, so every kept vertex keeps all its attributes exactly (UVs, normals, tangents,
 * colours, joints and weights, morph targets) and the vertex buffer can be shared between levels.
 *
 * @property indices The kept triangles, three vertex indices each.
 * @property error The largest collapse error, relative to the mesh's largest extent.
 */
class SimplifiedMesh(val indices: IntArray, val error: Float) {
    /** How many triangles [indices] holds. */
    val triangleCount: Int get() = indices.size / TRIANGLE_CORNERS
}

/**
 * Attribute-aware quadric-error mesh simplification, in the manner of meshoptimizer's
 * `meshopt_simplifyWithAttributes`: edges collapse onto one of their endpoints, cheapest first, by the
 * Garland-Heckbert quadric error of the moved vertex plus the weighted change in its attributes.
 *
 * Vertices that share a position but not attributes (a UV seam, a hard normal edge) collapse together,
 * along the seam only, so a seam never tears and no triangle spans both sides of it. A border vertex
 * slides only along its border, or stays put with [SimplifyOptions.lockBorders]. A collapse that would
 * flip a triangle or change the surface's topology is refused.
 *
 * A clean-room Kotlin implementation of the published technique; no meshoptimizer source is used.
 */
object AttributeMeshSimplifier {
    /**
     * Simplifies the triangle list [indices] over [positions] (`x, y, z` per vertex).
     *
     * @param positions Vertex positions, `x, y, z` per vertex.
     * @param indices A triangle list, three vertex indices per triangle.
     * @param options How far to simplify.
     * @param attributes Per-vertex attributes to keep and weigh; each covers every vertex.
     * @param skin The vertices' joint influences, for a skinned mesh.
     */
    fun simplify(
        positions: FloatArray,
        indices: IntArray,
        options: SimplifyOptions,
        attributes: List<MeshAttribute> = emptyList(),
        skin: SkinInfluences? = null,
    ): SimplifiedMesh {
        require(positions.size % POSITION_COMPONENTS == 0) { "positions has ${positions.size} floats, not x, y, z per vertex" }
        require(indices.size % TRIANGLE_CORNERS == 0) { "indices has ${indices.size} entries, not three per triangle" }
        val vertexCount = positions.size / POSITION_COMPONENTS
        indices.forEachIndexed { i, index ->
            require(index in 0 until vertexCount) { "indices[$i] is $index, outside the $vertexCount vertices" }
        }
        attributes.forEachIndexed { i, attribute ->
            require(attribute.vertexCount == vertexCount) { "attributes[$i] covers ${attribute.vertexCount} vertices, not $vertexCount" }
        }
        require(skin == null || skin.vertexCount == vertexCount) { "skin covers ${skin?.vertexCount} vertices, not $vertexCount" }
        val topology = MeshTopology(positions, indices, options)
        return EdgeCollapse(topology, VertexCost(attributes, skin), options).run()
    }
}

internal const val POSITION_COMPONENTS = 3
internal const val TRIANGLE_CORNERS = 3

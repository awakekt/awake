/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.geometry

import kotlin.math.abs
import kotlin.math.sqrt

/** Something wrong with a mesh, and where. */
data class MeshProblem(
    val kind: Kind,
    /** What was wrong, with the numbers that showed it. */
    val detail: String,
) {
    enum class Kind {
        /** The vertex array is not a whole number of vertices for its declared format. */
        Stride,

        /** An index points past the end of the vertex array, or the index count is not triangles. */
        Indices,

        /** A position or normal is NaN or infinite. Renders as nothing, or as a shard to infinity. */
        NotFinite,

        /** A triangle with two identical corners, or zero area. Draws no pixels and skews normals. */
        Degenerate,

        /** A normal that is not unit length. Lighting scales with it, so the surface reads wrong. */
        NormalLength,

        /** Two triangles sharing an edge traverse it the same way, so one of them faces backwards. */
        Winding,
    }
}

/**
 * Checks a mesh for the faults that render as something other than an error.
 *
 * Every kind here has been paid for at least once in this repo: a surface invisible under
 * back-face culling, a mesh lit as though it had no normals, a terrain that disagreed with its own
 * collider. None of them throws anywhere -- they reach the GPU and come back as a picture that is
 * merely wrong, which is the most expensive kind of wrong to chase.
 *
 * **Asset-time, not per-frame.** This walks every triangle and builds a map of shared edges, so it
 * belongs in a test, a build step or an importer -- never in a frame. It allocates freely on that
 * understanding.
 *
 * Returns every problem it finds rather than the first, because these come in families: a mesh
 * built with the wrong stride reports hundreds of bad indices, and seeing that is what tells you
 * the stride is the cause.
 *
 * What it deliberately does not check:
 * - **Whether the winding is the one you wanted.** It checks that neighbouring triangles agree,
 *   which catches a flipped triangle. A mesh wound consistently inside-out is self-consistent, and
 *   only a camera can tell you which side you meant. [facesUpward] is the small helper for the case
 *   where you do know: a ground surface.
 * - **Whether normals point the way the winding implies.** A mesh may legitimately carry authored
 *   normals that disagree with its faces -- that is what smooth shading across a crease is.
 */
fun MeshGeometry.validate(
    /** How far a normal's length may sit from 1 before it is reported. */
    normalTolerance: Float = DEFAULT_NORMAL_TOLERANCE,
): List<MeshProblem> {
    val stride = format.strideFloats
    // Layout and indices first, and alone: everything after this reads vertices through them, so
    // reporting a bad stride alongside the hundreds of bad reads it causes buries the one cause
    // under its own symptoms.
    val structural = structuralProblems(stride)
    if (structural.isNotEmpty()) return structural

    val problems = mutableListOf<MeshProblem>()
    val positionOffset = format.floatOffsetOf(VertexSemantic.Position)
    problems += vertexProblems(stride, positionOffset, normalTolerance)
    problems += degenerateTriangles(stride, positionOffset)
    problems += inconsistentWinding()
    return problems
}


/** The mesh's shape as a container: whole vertices, whole triangles, indices that exist. */
private fun MeshGeometry.structuralProblems(stride: Int): List<MeshProblem> {
    if (stride <= 0) {
        return listOf(MeshProblem(MeshProblem.Kind.Stride, "the format declares a stride of $stride floats"))
    }
    val problems = mutableListOf<MeshProblem>()
    if (vertices.size % stride != 0) {
        problems += MeshProblem(
            MeshProblem.Kind.Stride,
            "${vertices.size} floats is not a whole number of ${stride}-float vertices",
        )
    }
    if (indices.size % VERTICES_PER_TRIANGLE != 0) {
        problems += MeshProblem(MeshProblem.Kind.Indices, "${indices.size} indices is not a whole number of triangles")
    }
    val vertexCount = vertices.size / stride
    indices.forEachIndexed { at, index ->
        if (index !in 0 until vertexCount) {
            problems += MeshProblem(MeshProblem.Kind.Indices, "index $at points at vertex $index, of $vertexCount")
        }
    }
    return problems
}

/** Positions and normals that are not numbers, and normals that are not unit length. */
private fun MeshGeometry.vertexProblems(
    stride: Int,
    positionOffset: Int,
    normalTolerance: Float,
): List<MeshProblem> {
    val problems = mutableListOf<MeshProblem>()
    val normalOffset = runCatching { format.floatOffsetOf(VertexSemantic.Normal) }.getOrNull()
    for (vertex in 0 until vertices.size / stride) {
        val base = vertex * stride + positionOffset
        if (!vertices[base].isFinite() || !vertices[base + 1].isFinite() || !vertices[base + 2].isFinite()) {
            problems += MeshProblem(MeshProblem.Kind.NotFinite, "vertex $vertex has a non-finite position")
        }
        if (normalOffset != null) {
            problems += normalProblem(vertex * stride + normalOffset, vertex, normalTolerance)
        }
    }
    return problems
}

/** What is wrong with one vertex's normal, if anything. */
private fun MeshGeometry.normalProblem(at: Int, vertex: Int, tolerance: Float): List<MeshProblem> {
    val x = vertices[at]
    val y = vertices[at + 1]
    val z = vertices[at + 2]
    if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
        return listOf(MeshProblem(MeshProblem.Kind.NotFinite, "vertex $vertex has a non-finite normal"))
    }
    val length = sqrt(x * x + y * y + z * z)
    return if (abs(length - 1f) > tolerance) {
        listOf(MeshProblem(MeshProblem.Kind.NormalLength, "vertex $vertex has a normal of length $length"))
    } else {
        emptyList()
    }
}

/** Triangles that draw nothing: a repeated corner, or three points on a line. */
private fun MeshGeometry.degenerateTriangles(stride: Int, positionOffset: Int): List<MeshProblem> {
    val problems = mutableListOf<MeshProblem>()
    fun position(index: Int, component: Int) = vertices[index * stride + positionOffset + component]
    var triangle = 0
    while (triangle < indices.size) {
        val a = indices[triangle]
        val b = indices[triangle + 1]
        val c = indices[triangle + 2]
        if (a == b || b == c || a == c) {
            problems += MeshProblem(
                MeshProblem.Kind.Degenerate,
                "triangle ${triangle / VERTICES_PER_TRIANGLE} repeats a corner ($a, $b, $c)",
            )
        } else {
            val abx = position(b, 0) - position(a, 0)
            val aby = position(b, 1) - position(a, 1)
            val abz = position(b, 2) - position(a, 2)
            val acx = position(c, 0) - position(a, 0)
            val acy = position(c, 1) - position(a, 1)
            val acz = position(c, 2) - position(a, 2)
            val nx = aby * acz - abz * acy
            val ny = abz * acx - abx * acz
            val nz = abx * acy - aby * acx
            if (sqrt(nx * nx + ny * ny + nz * nz) < MINIMUM_TRIANGLE_AREA) {
                problems += MeshProblem(
                    MeshProblem.Kind.Degenerate,
                    "triangle ${triangle / VERTICES_PER_TRIANGLE} has no area",
                )
            }
        }
        triangle += VERTICES_PER_TRIANGLE
    }
    return problems
}

/**
 * Triangles whose neighbours disagree about which way round the shared edge goes.
 *
 * Two correctly-wound neighbours traverse their shared edge in opposite directions -- `a to b` on
 * one, `b to a` on the other. Finding the same direction twice means one of them is inside out,
 * which under back-face culling is a hole you can see through.
 *
 * An edge used by more than two triangles is not reported: that is a legitimately non-manifold
 * mesh, and orientation is not defined for one.
 */
private fun MeshGeometry.inconsistentWinding(): List<MeshProblem> {
    val seen = HashMap<Long, Int>()
    val problems = mutableListOf<MeshProblem>()
    var triangle = 0
    while (triangle < indices.size) {
        val corners = intArrayOf(indices[triangle], indices[triangle + 1], indices[triangle + 2])
        for (corner in 0 until VERTICES_PER_TRIANGLE) {
            val from = corners[corner]
            val to = corners[(corner + 1) % VERTICES_PER_TRIANGLE]
            val directed = from.toLong() * EDGE_KEY_STRIDE + to
            val previous = seen[directed]
            if (previous != null) {
                problems += MeshProblem(
                    MeshProblem.Kind.Winding,
                    "triangles $previous and ${triangle / VERTICES_PER_TRIANGLE} both run the edge " +
                        "$from to $to, so one of them faces backwards",
                )
            } else {
                seen[directed] = triangle / VERTICES_PER_TRIANGLE
            }
        }
        triangle += VERTICES_PER_TRIANGLE
    }
    return problems
}

/**
 * Whether every triangle faces up, for a surface that is meant to be walked on.
 *
 * [validate] can only tell you a mesh agrees with itself; this is the case where the answer is
 * known from the outside. A ground plane or a heightfield wound the wrong way is invisible under
 * back-face culling, and invisible ground looks like a missing draw call rather than a wound mesh.
 */
fun MeshGeometry.facesUpward(): Boolean {
    val stride = format.strideFloats
    val positionOffset = format.floatOffsetOf(VertexSemantic.Position)
    fun position(index: Int, component: Int) = vertices[index * stride + positionOffset + component]
    var triangle = 0
    while (triangle < indices.size) {
        val a = indices[triangle]
        val b = indices[triangle + 1]
        val c = indices[triangle + 2]
        val abx = position(b, 0) - position(a, 0)
        val abz = position(b, 2) - position(a, 2)
        val acx = position(c, 0) - position(a, 0)
        val acz = position(c, 2) - position(a, 2)
        // Only the Y component of the cross product: the sign of it is which way the face points.
        if (abz * acx - abx * acz <= 0f) return false
        triangle += VERTICES_PER_TRIANGLE
    }
    return true
}

private const val VERTICES_PER_TRIANGLE = 3
private const val DEFAULT_NORMAL_TOLERANCE = 0.001f
private const val MINIMUM_TRIANGLE_AREA = 1e-9f

/** Packs a directed edge into one key; large enough that no real mesh's index count collides. */
private const val EDGE_KEY_STRIDE = 1_000_000_007L

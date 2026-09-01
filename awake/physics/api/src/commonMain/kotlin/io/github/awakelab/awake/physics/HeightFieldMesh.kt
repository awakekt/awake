/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics

import io.github.awakelab.awake.core.geometry.gridTriangleIndices
import io.github.awakelab.awake.core.math.GridOrigin

private const val VALUES_PER_VERTEX = 3

/**
 * The same surface as this heightfield, expressed as triangles.
 *
 * For backends with no heightfield of their own. A heightfield *is* a grid of triangles -- Jolt's
 * dedicated shape is a compression of one, storing samples instead of vertices and exploiting the
 * regular grid when it queries -- so a binding that lacks it is missing an optimisation, not a
 * capability. iOS is that binding: JoltC exposes no `JPC_HeightFieldShapeSettings` at all, and
 * this is what lets that target collide with terrain anyway rather than throwing.
 *
 * **The cost is real and scales badly.** A heightfield stores one float per sample; this stores
 * three per vertex plus six indices per cell, and every one of those triangles goes into the
 * broadphase. At the 9x9 fields the samples use that is 128 triangles and irrelevant; at 512x512
 * it is over half a million and would want a per-tile budget instead. That trade is why the
 * dedicated shape is still the one every other backend uses.
 *
 * Vertices are in the shape's own local space, matching [HeightFieldShape]'s own convention
 * including [GridOrigin] -- so a body built from this collides where a body built from the
 * heightfield would, and the caller does not have to know which one it got.
 */
fun HeightFieldShape.toMeshShape(): MeshShape {
    val vertices = FloatArray(sampleCount * sampleCount * VALUES_PER_VERTEX)
    val originX = gridOffset(origin, sampleCount, scale.x)
    val originZ = gridOffset(origin, sampleCount, scale.z)

    for (z in 0 until sampleCount) {
        for (x in 0 until sampleCount) {
            val base = (z * sampleCount + x) * VALUES_PER_VERTEX
            vertices[base] = x * scale.x + originX
            vertices[base + 1] = heightAt(x, z) * scale.y
            vertices[base + 2] = z * scale.z + originZ
        }
    }

    // Shared with every other grid mesh in the engine, because the winding is the dangerous part:
    // wound the wrong way this surface still draws and rays still hit it, and only bodies falling
    // through it show the difference.
    return MeshShape(vertices, gridTriangleIndices(sampleCount, sampleCount))
}

/**
 * Where sample zero sits relative to the body, which is the whole of what [GridOrigin] decides.
 *
 * A centred field has half of itself subtracted; a corner-anchored one has nothing. The backends'
 * own heightfield builders compute exactly this, so a mesh built here lands where they would put
 * the collider.
 */
private fun gridOffset(origin: GridOrigin, sampleCount: Int, scale: Float): Float = when (origin) {
    GridOrigin.Centered -> -(sampleCount - 1) * scale * 0.5f
    GridOrigin.Corner -> 0f
}

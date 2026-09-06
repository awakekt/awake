/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.InterleavedVertices
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.geometry.gridTriangleIndices
import com.awakekt.awake.core.math.Vec3f

/**
 * Builds a counter-clockwise, Y-up grid using [VertexFormat.PositionNormalColor]. Normals use
 * central differences in the interior and one-sided differences on the border.
 *
 * This is asset construction work, not a per-frame renderer path. The caller owns material
 * choice through [colorAt]; the builder only owns geometry packing and topology.
 */
fun Heightmap.toPositionNormalColorMesh(
    colorAt: (x: Int, z: Int, height: Float) -> Color,
): MeshGeometry {
    val scale = scale
    // Wherever the map says its first sample is; centred unless the map opted out.
    val offsetX = minX
    val offsetZ = minZ
    val vertices = InterleavedVertices(VertexFormat.PositionNormalColor, width * depth)
    for (z in 0 until depth) {
        for (x in 0 until width) {
            val vertex = z * width + x
            val height = heightAt(x, z)
            val normal = normalAt(x, z, scale)
            val color = colorAt(x, z, height)
            vertices.put(vertex, VertexSemantic.Position, offsetX + x * scale.x, height * scale.y, offsetZ + z * scale.z)
            vertices.put(vertex, VertexSemantic.Normal, normal.x, normal.y, normal.z)
            vertices.put(vertex, VertexSemantic.Color, color.r, color.g, color.b)
        }
    }

    return vertices.build(gridTriangleIndices(width, depth))
}

/** Builds a uniformly coloured [VertexFormat.PositionNormalColor] mesh. */
fun Heightmap.toPositionNormalColorMesh(color: Color = Color.White): MeshGeometry =
    toPositionNormalColorMesh { _, _, _ -> color }

private fun Heightmap.normalAt(x: Int, z: Int, scale: Vec3f): Vec3f {
    val leftX = (x - 1).coerceAtLeast(0)
    val rightX = (x + 1).coerceAtMost(width - 1)
    val nearZ = (z - 1).coerceAtLeast(0)
    val farZ = (z + 1).coerceAtMost(depth - 1)
    val slopeX = (heightAt(rightX, z) - heightAt(leftX, z)) * scale.y /
        ((rightX - leftX) * scale.x)
    val slopeZ = (heightAt(x, farZ) - heightAt(x, nearZ)) * scale.y /
        ((farZ - nearZ) * scale.z)
    return Vec3f(-slopeX, 1f, -slopeZ).normalize()
}

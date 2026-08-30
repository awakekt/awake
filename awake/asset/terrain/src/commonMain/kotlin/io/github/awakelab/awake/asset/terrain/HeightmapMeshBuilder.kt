/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.terrain

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.math.Vec3f

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
    val format = VertexFormat.PositionNormalColor
    val vertices = FloatArray(width * depth * format.strideFloats)
    var cursor = 0
    for (z in 0 until depth) {
        for (x in 0 until width) {
            val height = heightAt(x, z)
            val normal = normalAt(x, z, scale)
            val color = colorAt(x, z, height)
            vertices[cursor++] = x * scale.x
            vertices[cursor++] = height * scale.y
            vertices[cursor++] = z * scale.z
            vertices[cursor++] = normal.x
            vertices[cursor++] = normal.y
            vertices[cursor++] = normal.z
            vertices[cursor++] = color.r
            vertices[cursor++] = color.g
            vertices[cursor++] = color.b
        }
    }

    val indices = IntArray((width - 1) * (depth - 1) * INDICES_PER_CELL)
    cursor = 0
    for (z in 0 until depth - 1) {
        for (x in 0 until width - 1) {
            val topLeft = z * width + x
            val topRight = topLeft + 1
            val bottomLeft = topLeft + width
            val bottomRight = bottomLeft + 1
            indices[cursor++] = topLeft
            indices[cursor++] = bottomLeft
            indices[cursor++] = topRight
            indices[cursor++] = topRight
            indices[cursor++] = bottomLeft
            indices[cursor++] = bottomRight
        }
    }
    return MeshGeometry(vertices, indices, format)
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

private const val INDICES_PER_CELL = 6

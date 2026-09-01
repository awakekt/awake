/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.geometry

/** Two triangles per cell, three indices each. */
private const val INDICES_PER_CELL = 6

/**
 * Triangle indices for a `width` x `depth` grid of vertices in row-major order.
 *
 * Every grid mesh in the engine needs these six lines and they are the six lines nobody should
 * write twice: **the winding decides which side of the surface is solid**, and getting it backwards
 * produces terrain that renders, that rays hit from both sides, and that bodies fall straight
 * through. That failure looks like a physics bug rather than an index-order one, which is what
 * makes it expensive.
 *
 * The order below puts the face normal along +Y for a Y-up grid, so a heightfield built with it is
 * solid from above. Vertex `(x, z)` is expected at index `z * width + x`, which is the layout
 * `Heightmap` and `HeightFieldShape` both already use.
 *
 * [includeCell] drops cells from the output -- a clipmap ring is a grid with its middle removed.
 * The returned array is trimmed to what was actually written, so a caller can index it directly.
 */
fun gridTriangleIndices(
    width: Int,
    depth: Int,
    includeCell: ((x: Int, z: Int) -> Boolean)? = null,
): IntArray {
    require(width >= 2 && depth >= 2) {
        "a grid needs at least two vertices on each side to have a cell: ${width}x$depth"
    }
    val indices = IntArray((width - 1) * (depth - 1) * INDICES_PER_CELL)
    var cursor = 0
    for (z in 0 until depth - 1) {
        for (x in 0 until width - 1) {
            if (includeCell != null && !includeCell(x, z)) continue
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
    return if (cursor == indices.size) indices else indices.copyOf(cursor)
}

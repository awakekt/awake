/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat

/** A box [half] wide and [height] tall standing on y = 0, its faces lit by their normals: a caster that shades its ground. */
internal fun box(half: Float, height: Float, format: VertexFormat): MeshGeometry {
    val uv = format == VertexFormat.PositionNormalColorUv
    val vertices = ArrayList<Float>()
    val indices = ArrayList<Int>()
    val faces = listOf(
        floatArrayOf(0f, 1f, 0f),
        floatArrayOf(1f, 0f, 0f),
        floatArrayOf(-1f, 0f, 0f),
        floatArrayOf(0f, 0f, 1f),
        floatArrayOf(0f, 0f, -1f),
    )
    for (n in faces) {
        val base = vertices.size / (if (uv) 11 else 9)
        faceCorners(n, half, height).forEach { c ->
            vertices += listOf(c[0], c[1], c[2], n[0], n[1], n[2], 0.8f, 0.7f, 0.6f)
            if (uv) vertices += listOf(0f, 0f)
        }
        indices += listOf(base, base + 1, base + 2, base + 2, base + 3, base)
    }
    return MeshGeometry(vertices.toFloatArray(), indices.toIntArray(), format)
}

private fun faceCorners(n: FloatArray, h: Float, top: Float): List<FloatArray> = when {
    n[1] > 0f -> listOf(floatArrayOf(-h, top, -h), floatArrayOf(-h, top, h), floatArrayOf(h, top, h), floatArrayOf(h, top, -h))
    n[0] > 0f -> listOf(floatArrayOf(h, 0f, -h), floatArrayOf(h, top, -h), floatArrayOf(h, top, h), floatArrayOf(h, 0f, h))
    n[0] < 0f -> listOf(floatArrayOf(-h, 0f, h), floatArrayOf(-h, top, h), floatArrayOf(-h, top, -h), floatArrayOf(-h, 0f, -h))
    n[2] > 0f -> listOf(floatArrayOf(h, 0f, h), floatArrayOf(h, top, h), floatArrayOf(-h, top, h), floatArrayOf(-h, 0f, h))
    else -> listOf(floatArrayOf(-h, 0f, -h), floatArrayOf(-h, top, -h), floatArrayOf(h, top, -h), floatArrayOf(h, 0f, -h))
}

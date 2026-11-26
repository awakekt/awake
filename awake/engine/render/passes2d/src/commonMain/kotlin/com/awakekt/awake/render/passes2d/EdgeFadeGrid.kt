/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawTransform
import com.awakekt.awake.core.graphics2d.EdgeFade
import com.awakekt.awake.core.graphics2d.TextureRegion
import com.awakekt.awake.core.graphics2d.writeTextureVertex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tessellates a [DrawCommand.Texture] with a non-empty [EdgeFade] into a compact per-vertex-alpha
 * textured grid so Vulkan and WebGPU interpolate smooth per-pixel edge feathering in hardware with
 * a single draw call and zero shader changes.
 */
internal class EdgeFadeGrid(
    val xs: FloatArray,
    val ys: FloatArray,
) {
    val vertexCount: Int get() = xs.size * ys.size
    val indexCount: Int get() = (xs.size - 1) * (ys.size - 1) * VertexFormats2D.INDICES_PER_QUAD

    @Suppress("LongParameterList")
    fun writeTo(
        primitive: DrawCommand.Texture,
        region: TextureRegion,
        repeatX: Boolean,
        repeatY: Boolean,
        baseColor: Color,
        transform: DrawTransform?,
        fade: EdgeFade,
        vertices: FloatArray,
        indices: IntArray,
        vertexStart: Int,
        indexStart: Int,
        vertexOffset: Int,
        spanU: Float,
        spanV: Float,
    ) {
        val u0 = if (repeatX) 0f else region.u0
        val v0 = if (repeatY) 0f else region.v0
        val u1 = if (repeatX) primitive.repeatX else region.u1
        val v1 = if (repeatY) primitive.repeatY else region.v1
        val du = u1 - u0
        val dv = v1 - v0
        val rotated = primitive.rotationDegrees != 0f
        val cx = primitive.x + primitive.w * 0.5f
        val cy = primitive.y + primitive.h * 0.5f
        val rad = if (rotated) primitive.rotationDegrees * (PI / 180.0).toFloat() else 0f
        val cosA = if (rotated) cos(rad) else 1f
        val sinA = if (rotated) sin(rad) else 0f
        val stride = VertexFormats2D.TEXTURE_FLOATS_PER_VERTEX
        var vCursor = vertexStart
        for (ry in ys.indices) {
            val nv = ys[ry]
            val localY = primitive.y + primitive.h * nv
            val tv = v0 + dv * nv
            for (cxIdx in xs.indices) {
                val nu = xs[cxIdx]
                val localX = primitive.x + primitive.w * nu
                val tu = u0 + du * nu
                val vx = if (rotated) cx + (localX - cx) * cosA - (localY - cy) * sinA else localX
                val vy = if (rotated) cy + (localX - cx) * sinA + (localY - cy) * cosA else localY
                val factor = fade.alphaAt(nu, nv)
                val vColor = if (factor >= 1f) baseColor else baseColor.withAlpha(baseColor.a * factor)
                writeTextureVertex(vertices, vCursor, vx, vy, tu, tv, region.u0, region.v0, spanU, spanV, vColor, transform)
                vCursor += stride
            }
        }
        writeIndices(indices, indexStart, vertexOffset)
    }

    private fun writeIndices(indices: IntArray, indexStart: Int, vertexOffset: Int) {
        var iCursor = indexStart
        val cols = xs.size
        for (ry in 0 until ys.size - 1) {
            val row0 = vertexOffset + ry * cols
            val row1 = row0 + cols
            for (cxIdx in 0 until cols - 1) {
                val tl = row0 + cxIdx
                val tr = tl + 1
                val bl = row1 + cxIdx
                val br = bl + 1
                indices[iCursor] = tl
                indices[iCursor + 1] = tr
                indices[iCursor + 2] = br
                indices[iCursor + 3] = br
                indices[iCursor + 4] = bl
                indices[iCursor + 5] = tl
                iCursor += VertexFormats2D.INDICES_PER_QUAD
            }
        }
    }

    companion object {
        private const val EDGE_SEGMENTS = 4
        private const val EPSILON = 1e-4f

        fun forFade(fade: EdgeFade): EdgeFadeGrid =
            EdgeFadeGrid(axisStops(fade.left, fade.right), axisStops(fade.top, fade.bottom))

        private fun axisStops(startFade: Float, endFade: Float): FloatArray {
            val raw = FloatArray(2 + EDGE_SEGMENTS * 2)
            var count = 0
            raw[count++] = 0f
            if (startFade > 0f) {
                for (step in 1..EDGE_SEGMENTS) {
                    raw[count++] = startFade * step / EDGE_SEGMENTS
                }
            }
            if (endFade > 0f) {
                val endStart = 1f - endFade
                for (step in 0 until EDGE_SEGMENTS) {
                    raw[count++] = endStart + endFade * step / EDGE_SEGMENTS
                }
            }
            raw[count++] = 1f
            // Insertion sort over at most 10 floats, then deduplicate within EPSILON.
            for (i in 1 until count) {
                val key = raw[i]
                var j = i - 1
                while (j >= 0 && raw[j] > key) {
                    raw[j + 1] = raw[j]
                    j -= 1
                }
                raw[j + 1] = key
            }
            var unique = 1
            for (i in 1 until count) {
                if (raw[i] - raw[unique - 1] > EPSILON) {
                    raw[unique++] = raw[i]
                }
            }
            raw[unique - 1] = 1f
            return raw.copyOf(unique)
        }
    }
}

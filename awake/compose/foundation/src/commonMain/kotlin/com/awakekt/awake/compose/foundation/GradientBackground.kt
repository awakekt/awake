/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.graphics.LinearGradient
import com.awakekt.awake.compose.ui.graphics.Shape
import com.awakekt.awake.compose.ui.graphics.ShapeOutline
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.graphics.drawscope.drawRetainedMesh
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.compose.ui.unit.LayoutDirection
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.ColoredVertex
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.DrawPoint
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.Size2D

internal class GradientBackgroundElement(private val brush: LinearGradient, private val shape: Shape) : ModifierNodeElement<GradientBackgroundNode>() {
    override fun create(): GradientBackgroundNode = GradientBackgroundNode()
    override fun update(node: GradientBackgroundNode) {
        node.brush = brush
        node.shape = shape
    }
}

internal class GradientBackgroundNode :
    Modifier.Node(),
    DrawModifierNode {
    lateinit var brush: LinearGradient
    lateinit var shape: Shape
    private var key: GradientKey? = null
    private var mesh: ColoredTriangleMesh? = null
    private var clip: DrawPath? = null
    private var retentionKey: Any = Any()

    override fun DrawScope.draw(drawContent: () -> Unit) {
        val nextKey = GradientKey(width, height, density, layoutDirection, brush, shape)
        if (key != nextKey) {
            key = nextKey
            mesh = gradientMesh(width.toFloat(), height.toFloat(), brush)
            clip = when (val outline = shape.createOutline(Size2D(width.toFloat(), height.toFloat()), density, layoutDirection)) {
                is ShapeOutline.Rectangle -> null
                is ShapeOutline.Rounded -> DrawShape.RoundedRectangle(Dp(outline.radius)).toPath(outline.bounds)
                is ShapeOutline.RoundedCorners -> outline.path
                is ShapeOutline.Generic -> outline.path
            }
            retentionKey = Any()
        }
        mesh?.let { geometry ->
            val path = clip
            if (path == null) drawRetainedMesh(geometry, retentionKey) else clippedPath(path) { drawRetainedMesh(geometry, retentionKey) }
        }
        drawContent()
    }
}

private data class GradientKey(val width: Int, val height: Int, val density: Float, val direction: LayoutDirection, val brush: LinearGradient, val shape: Shape)

// Shared vertices make adjacent bands meet exactly once, including a midpoint on a half pixel.
// The existing coloured-mesh path handles alpha, transforms and clipping on every renderer.
private fun gradientMesh(width: Float, height: Float, brush: LinearGradient): ColoredTriangleMesh {
    val bands = brush.colors.lastIndex
    val vertices = brush.colors.flatMapIndexed { index, color ->
        val position = index.toFloat() / bands
        if (brush.horizontal) {
            listOf(ColoredVertex(DrawPoint(position * width, 0f), color), ColoredVertex(DrawPoint(position * width, height), color))
        } else {
            listOf(ColoredVertex(DrawPoint(0f, position * height), color), ColoredVertex(DrawPoint(width, position * height), color))
        }
    }
    val indices = IntArray(bands * 6)
    repeat(bands) { band ->
        val vertex = band * 2
        val offset = band * 6
        indices[offset] = vertex
        indices[offset + 1] = vertex + 1
        indices[offset + 2] = vertex + 2
        indices[offset + 3] = vertex + 2
        indices[offset + 4] = vertex + 1
        indices[offset + 5] = vertex + 3
    }
    return ColoredTriangleMesh(vertices, indices)
}

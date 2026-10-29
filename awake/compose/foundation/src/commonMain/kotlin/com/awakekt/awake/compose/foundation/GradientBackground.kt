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
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.ColoredVertex
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.DrawPoint
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.clipToConvexPaths
import com.awakekt.awake.core.graphics2d.merge
import com.awakekt.awake.core.graphics2d.tessellateFillAa
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.Rectangle
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
    private var retentionKey: Any = Any()

    override fun DrawScope.draw(drawContent: () -> Unit) {
        val nextKey = GradientKey(width, height, density, layoutDirection, brush, shape)
        if (key != nextKey) {
            key = nextKey
            val outline = shape.createOutline(Size2D(width.toFloat(), height.toFloat()), density, layoutDirection)
            mesh = gradientMesh(width.toFloat(), height.toFloat(), brush, outline)
            retentionKey = Any()
        }
        mesh?.let { drawRetainedMesh(it, retentionKey) }
        drawContent()
    }
}

private data class GradientKey(val width: Int, val height: Int, val density: Float, val direction: LayoutDirection, val brush: LinearGradient, val shape: Shape)

private fun gradientMesh(width: Float, height: Float, brush: LinearGradient, outline: ShapeOutline): ColoredTriangleMesh {
    if (width <= 0f || height <= 0f) return ColoredTriangleMesh(emptyList(), IntArray(0))
    return when (outline) {
        is ShapeOutline.Rectangle -> rectangularGradientMesh(width, height, brush)
        is ShapeOutline.Rounded -> pathGradientMesh(DrawShape.RoundedRectangle(Dp(outline.radius)).toPath(outline.bounds), width, height, brush)
        is ShapeOutline.RoundedCorners -> pathGradientMesh(outline.path, width, height, brush)
        is ShapeOutline.Generic -> pathGradientMesh(outline.path, width, height, brush)
    }
}

private fun pathGradientMesh(path: DrawPath, width: Float, height: Float, brush: LinearGradient): ColoredTriangleMesh {
    // Rounded UI masks transition across [-1,+1] device pixels. Preserve a soft boundary instead
    // of hard-clipping coloured triangles; both the AA outline and the band splits are cached.
    val shape = path.tessellateFillAa(Color.White, fringePx = GRADIENT_FRINGE_PX)
    val bounds = shape.bounds()
    val extent = if (brush.horizontal) width else height
    val bands = brush.colors.lastIndex
    return (0 until bands).map { band ->
        val first = if (brush.horizontal) bounds.x else bounds.y
        val last = if (brush.horizontal) bounds.x + bounds.width else bounds.y + bounds.height
        val start = if (band == 0) first else extent * band / bands
        val end = if (band == bands - 1) last else extent * (band + 1) / bands
        val clip = if (brush.horizontal) Rectangle(start, bounds.y, end - start, bounds.height) else Rectangle(bounds.x, start, bounds.width, end - start)
        val segment = shape.clipToConvexPaths(listOf(DrawShape.Rectangle.toPath(clip)))
        segment.copy(
            vertices = segment.vertices.map { vertex ->
                val position = if (brush.horizontal) vertex.position.x else vertex.position.y
                val fraction = (position / extent * bands - band).coerceIn(0f, 1f)
                val color = brush.colors[band].lerp(brush.colors[band + 1], fraction)
                vertex.copy(color = color.withAlpha(color.a * vertex.color.a))
            },
        )
    }.merge()
}

private const val GRADIENT_FRINGE_PX = 2f

// Shared vertices make adjacent bands meet exactly once, including a midpoint on a half pixel.
// The existing coloured-mesh path handles alpha, transforms and clipping on every renderer.
private fun rectangularGradientMesh(width: Float, height: Float, brush: LinearGradient): ColoredTriangleMesh {
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

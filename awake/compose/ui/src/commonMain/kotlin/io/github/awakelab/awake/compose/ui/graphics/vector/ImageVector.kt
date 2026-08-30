/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.graphics.vector

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.DrawPath
import io.github.awakelab.awake.core.graphics2d.DrawStroke
import io.github.awakelab.awake.core.graphics2d.FillRule
import io.github.awakelab.awake.core.graphics2d.PathBuilder
import io.github.awakelab.awake.core.graphics2d.transform
import io.github.awakelab.awake.core.graphics2d.uiPath
import io.github.awakelab.awake.core.math2d.Dp
import io.github.awakelab.awake.core.math2d.Rectangle
import io.github.awakelab.awake.core.math2d.dp
import io.github.awakelab.awake.core.math2d.pixelPerfectPixel

data class VectorPath(
    val path: DrawPath,
    val fill: Color? = null,
    val stroke: DrawStroke? = null,
)

data class ImageVector(
    val defaultWidth: Dp,
    val defaultHeight: Dp,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val paths: List<VectorPath>,
) : VectorGraphic

class ImageVectorBuilder internal constructor(
    private val defaultWidth: Dp,
    private val defaultHeight: Dp,
    private val viewportWidth: Float,
    private val viewportHeight: Float,
) {
    private val paths = ArrayList<VectorPath>()

    fun path(
        fill: Color? = null,
        fillRule: FillRule = FillRule.NonZero,
        stroke: DrawStroke? = null,
        block: PathBuilder.() -> Unit,
    ) {
        paths += VectorPath(
            path = uiPath(fillRule = fillRule, block = block),
            fill = fill,
            stroke = stroke,
        )
    }

    internal fun build(): ImageVector = ImageVector(
        defaultWidth = defaultWidth,
        defaultHeight = defaultHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        paths = paths.toList(),
    )
}

fun imageVector(
    defaultWidth: Dp,
    defaultHeight: Dp,
    viewportWidth: Float,
    viewportHeight: Float,
    block: ImageVectorBuilder.() -> Unit,
): ImageVector {
    val builder = ImageVectorBuilder(defaultWidth, defaultHeight, viewportWidth, viewportHeight)
    builder.block()
    return builder.build()
}

fun ImageVector.fitTo(slot: Rectangle): List<VectorPath> {
    // A degenerate viewport or slot has nothing to fit into. Read through named locals rather than
    // one four-clause condition, which trips detekt now that this file lives beside compose's own.
    val hasViewport = viewportWidth > 0f && viewportHeight > 0f
    val hasSlot = slot.width > 0f && slot.height > 0f
    if (!hasViewport || !hasSlot) return emptyList()
    val scale = minOf(slot.width / viewportWidth, slot.height / viewportHeight)
    val scaledWidth = viewportWidth * scale
    val scaledHeight = viewportHeight * scale
    // Snap the centering offset to whole pixels, same as text does via resolveGlyphPx -- an
    // odd (slot - scaled) difference otherwise lands the whole glyph on a half-pixel, blurring
    // every edge instead of just softening it (reported as icons "not pixel perfect").
    val translateX = pixelPerfectPixel(slot.x + (slot.width - scaledWidth) / 2f)
    val translateY = pixelPerfectPixel(slot.y + (slot.height - scaledHeight) / 2f)
    return paths.map { vectorPath ->
        vectorPath.copy(
            path = vectorPath.path.transform(
                scaleX = scale,
                scaleY = scale,
                translateX = translateX,
                translateY = translateY,
            ),
            // stroke-width is authored in the same viewport units as the path coordinates (SVG's
            // default, no vector-effect="non-scaling-stroke") -- scale it the same way, or a big
            // rendered icon gets a proportionally hairline outline.
            stroke = vectorPath.stroke?.let { it.copy(width = (it.width.value * scale).dp) },
        )
    }
}

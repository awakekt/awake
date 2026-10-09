/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics.vector

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.FillRule
import com.awakekt.awake.core.graphics2d.PathBuilder
import com.awakekt.awake.core.graphics2d.transform
import com.awakekt.awake.core.graphics2d.uiPath
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.dp
import com.awakekt.awake.core.math2d.pixelPerfectPixel

/**
 * One path of an [ImageVector], with how it is filled and stroked.
 *
 * With no explicit [fill], a path takes the tint chosen at draw time, unless it has a [stroke], in
 * which case it is outline only. A stroke is always drawn in the tint, and its width is in viewport
 * units so that it scales with the image.
 *
 * @property path The outline, in the vector's viewport coordinates.
 * @property fill An explicit fill colour, or `null` to defer to the tint.
 * @property stroke The stroke style, or `null` for no stroke.
 */
data class VectorPath(
    val path: DrawPath,
    val fill: Color? = null,
    val stroke: DrawStroke? = null,
)

/**
 * A vector image: paths authored in a viewport coordinate space, scaled to fit whatever slot draws them.
 *
 * @property defaultWidth The image's natural width, in dp.
 * @property defaultHeight The image's natural height, in dp.
 * @property viewportWidth Width of the coordinate space the [paths] are authored in.
 * @property viewportHeight Height of the coordinate space the [paths] are authored in.
 * @property paths The paths to draw, in order.
 */
data class ImageVector(
    val defaultWidth: Dp,
    val defaultHeight: Dp,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val paths: List<VectorPath>,
) : VectorGraphic

/**
 * Collects the paths of an [ImageVector] under construction; obtained through [imageVector].
 */
class ImageVectorBuilder internal constructor(
    private val defaultWidth: Dp,
    private val defaultHeight: Dp,
    private val viewportWidth: Float,
    private val viewportHeight: Float,
) {
    private val paths = ArrayList<VectorPath>()

    /**
     * Appends a path built by [block], in viewport coordinates.
     *
     * [fill] and [stroke] behave as on [VectorPath]; [fillRule] decides which regions of a
     * self-overlapping path count as inside.
     */
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

/**
 * Builds an [ImageVector] by running [block] against an [ImageVectorBuilder].
 *
 * Path coordinates in [block] are in the [viewportWidth] by [viewportHeight] space, and the image
 * is scaled from there when drawn.
 */
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

/**
 * Scales and centres this vector's paths into [slot], returning them in the slot's pixel coordinates.
 *
 * The scale is uniform, so the viewport's aspect ratio is kept, and strokes widen by the same factor.
 * The centring offset is snapped to whole pixels. The result is empty when the viewport or [slot]
 * has no area.
 */
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

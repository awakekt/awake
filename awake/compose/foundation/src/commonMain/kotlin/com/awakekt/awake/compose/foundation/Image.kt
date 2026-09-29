/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clipToBounds
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.drawImage
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.Constraints
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws [bitmap] into this node, scaled by [contentScale] and centred.
 *
 * Unconstrained, the node takes the bitmap's pixel size; a smaller bound shrinks it with the
 * aspect ratio kept. [contentDescription] is what assistive technology reads; pass `null` for a
 * purely decorative image.
 */
context(_: Composer)
fun Image(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val described = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics {
            this[SemanticsProperties.Role] = SemanticsRole.Image
            this[SemanticsProperties.Label] = contentDescription
        }
    }
    Layout(
        ImageNodeType,
        modifier = described.clipToBounds().drawBehind {
            val w = bitmap.width * imageScaleX(bitmap.width, bitmap.height, width, height, contentScale)
            val h = bitmap.height * imageScaleY(bitmap.width, bitmap.height, width, height, contentScale)
            drawImage(bitmap, (width - w) / 2f, (height - h) / 2f, w, h)
        },
        measurePolicy = ImageMeasurePolicy(bitmap.width, bitmap.height),
    )
}

private object ImageNodeType

private class ImageMeasurePolicy(private val imageWidth: Int, private val imageHeight: Int) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val fit = imageFit(imageWidth, imageHeight, constraints)
        return layout(
            max((imageWidth * fit).roundToInt(), constraints.minWidth),
            max((imageHeight * fit).roundToInt(), constraints.minHeight),
        ) {}
    }
}

/**
 * How much an [imageWidth]x[imageHeight] image shrinks to fit [constraints]' maximum, never
 * enlarging: the node is the image's size times this, then raised to the minimum.
 */
internal fun imageFit(imageWidth: Int, imageHeight: Int, constraints: Constraints): Float = min(
    if (constraints.maxWidth == Constraints.Infinity) 1f else constraints.maxWidth.toFloat() / imageWidth,
    if (constraints.maxHeight == Constraints.Infinity) 1f else constraints.maxHeight.toFloat() / imageHeight,
).coerceAtMost(1f)

/** Horizontal pixel scale for an image in a [nodeWidth]x[nodeHeight] node; the image is centred. */
internal fun imageScaleX(imageWidth: Int, imageHeight: Int, nodeWidth: Int, nodeHeight: Int, contentScale: ContentScale): Float =
    if (contentScale == ContentScale.FillBounds) {
        nodeWidth.toFloat() / imageWidth
    } else {
        uniformScale(imageWidth, imageHeight, nodeWidth, nodeHeight, contentScale)
    }

/** Vertical pixel scale; see [imageScaleX]. */
internal fun imageScaleY(imageWidth: Int, imageHeight: Int, nodeWidth: Int, nodeHeight: Int, contentScale: ContentScale): Float =
    if (contentScale == ContentScale.FillBounds) {
        nodeHeight.toFloat() / imageHeight
    } else {
        uniformScale(imageWidth, imageHeight, nodeWidth, nodeHeight, contentScale)
    }

private fun uniformScale(imageWidth: Int, imageHeight: Int, nodeWidth: Int, nodeHeight: Int, contentScale: ContentScale): Float {
    val scaleX = nodeWidth.toFloat() / imageWidth
    val scaleY = nodeHeight.toFloat() / imageHeight
    return when (contentScale) {
        ContentScale.Crop -> max(scaleX, scaleY)
        ContentScale.Inside -> min(min(scaleX, scaleY), 1f)
        ContentScale.Fit, ContentScale.FillBounds -> min(scaleX, scaleY)
    }
}

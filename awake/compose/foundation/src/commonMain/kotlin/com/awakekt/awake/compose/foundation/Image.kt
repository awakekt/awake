/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.rememberGroup
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.draw.clipToBounds
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.drawImage
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.FilterQuality
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

context(_: Composer)
/**
 * Draws [bitmap] into this node, scaled by [contentScale] and centred, its colour multiplied by
 * [tint]. [filterQuality] `None` keeps pixel art crisp when it is scaled.
 *
 * Unconstrained, the node takes the bitmap's pixel size; a smaller bound shrinks it with the
 * aspect ratio kept. [contentDescription] is what assistive technology reads; pass `null` for a
 * purely decorative image.
 */
fun Image(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    tint: Color = Color.White,
    filterQuality: FilterQuality = FilterQuality.Low,
) {
    rememberGroup(ImageStateGroup) {
        val state = remember { ImageNodeState() }
        Layout(
            ImageNodeType,
            modifier = state.modifierFor(modifier, contentDescription, bitmap, contentScale, tint, filterQuality),
            measurePolicy = state.policyFor(bitmap.width, bitmap.height),
        )
    }
}

private object ImageNodeType

private object ImageStateGroup

private class ImageNodeState {
    private var policyWidth = -1
    private var policyHeight = -1
    private var policy: ImageMeasurePolicy? = null

    private var lastModifier: Modifier? = null
    private var lastDescription: String? = null
    private var hasDescription = false
    private var descriptionModifier: Modifier = Modifier

    private var lastBitmap: ImageBitmap? = null
    private var lastScale: ContentScale = ContentScale.Fit
    private var lastTint: Color = Color.White
    private var lastFilter: FilterQuality = FilterQuality.Low
    private var drawElement: ImageDrawElement? = null
    private var chain: Modifier = Modifier

    fun policyFor(width: Int, height: Int): ImageMeasurePolicy {
        val cached = policy
        if (cached != null && policyWidth == width && policyHeight == height) return cached
        policyWidth = width
        policyHeight = height
        return ImageMeasurePolicy(width, height).also { policy = it }
    }

    @Suppress("LongParameterList")
    fun modifierFor(
        modifier: Modifier,
        contentDescription: String?,
        bitmap: ImageBitmap,
        contentScale: ContentScale,
        tint: Color,
        filterQuality: FilterQuality,
    ): Modifier {
        val descChanged = !hasDescription || lastDescription != contentDescription
        if (descChanged) {
            hasDescription = true
            lastDescription = contentDescription
            descriptionModifier = if (contentDescription == null) {
                Modifier
            } else {
                Modifier.semantics {
                    this[SemanticsProperties.Role] = SemanticsRole.Image
                    this[SemanticsProperties.Label] = contentDescription
                }
            }
        }
        val prevDraw = drawElement
        val drawChanged = prevDraw == null ||
            lastBitmap !== bitmap ||
            lastScale != contentScale ||
            lastTint != tint ||
            lastFilter != filterQuality
        val currentDraw = if (!drawChanged) {
            prevDraw
        } else {
            lastBitmap = bitmap
            lastScale = contentScale
            lastTint = tint
            lastFilter = filterQuality
            ImageDrawElement(bitmap, contentScale, tint, filterQuality).also { drawElement = it }
        }
        if (descChanged || drawChanged || modifier != lastModifier) {
            lastModifier = modifier
            chain = modifier.then(descriptionModifier).clipToBounds().then(currentDraw)
        }
        return chain
    }
}

private class ImageDrawElement(
    private val bitmap: ImageBitmap,
    private val contentScale: ContentScale,
    private val tint: Color,
    private val filterQuality: FilterQuality,
) : ModifierNodeElement<ImageDrawNode>() {
    override fun create(): ImageDrawNode = ImageDrawNode()

    override fun update(node: ImageDrawNode) {
        node.bitmap = bitmap
        node.contentScale = contentScale
        node.tint = tint
        node.filterQuality = filterQuality
    }

    override fun toString(): String = "imageDraw()"
}

private class ImageDrawNode :
    Modifier.Node(),
    DrawModifierNode {
    lateinit var bitmap: ImageBitmap
    var contentScale: ContentScale = ContentScale.Fit
    var tint: Color = Color.White
    var filterQuality: FilterQuality = FilterQuality.Low

    override fun DrawScope.draw(drawContent: () -> Unit) {
        val w = bitmap.width * imageScaleX(bitmap.width, bitmap.height, width, height, contentScale)
        val h = bitmap.height * imageScaleY(bitmap.width, bitmap.height, width, height, contentScale)
        drawImage(bitmap, (width - w) / 2f, (height - h) / 2f, w, h, tint = tint, filterQuality = filterQuality)
        drawContent()
    }

    override fun toString(): String = "imageDraw()"
}

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

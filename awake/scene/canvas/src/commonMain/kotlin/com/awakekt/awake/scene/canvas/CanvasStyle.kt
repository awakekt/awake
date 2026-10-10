/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.style.StyleScope
import com.awakekt.awake.compose.ui.graphics.Brush
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.shadow.Shadow
import com.awakekt.awake.compose.ui.unit.DpOffset
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable

/**
 * How a [CanvasElement] looks beyond its [CanvasElement.color] and [CanvasElement.background]: the
 * saved form of a Compose `Style`, drawn through the same `Modifier.styleable`. Every property is
 * optional, and one left out keeps the element's own colours. Colours are `#RRGGBB` or
 * `#RRGGBBAA`; lengths are dp.
 *
 * @property background The fill behind the element, in place of [CanvasElement.background].
 * @property gradient A gradient fill, in place of either background colour.
 * @property image An Image's picture, the frame of a Panel, Button or Text over its fill, or a Bar's
 * track.
 * @property fillImage A Bar's fill, cut at its value rather than squeezed into it.
 * @property cornerRadius Rounds the fill, gradient, border and shadow, and a Bar's colour fill.
 * Images stay square.
 * @property borderWidth The border's width, inside the element's edge.
 * @property borderColor The border's colour; a border needs both.
 * @property shadow A shadow cast behind the element, in its shape.
 * @property textColor The colour of a Text's or Button's text, in place of [CanvasElement.color].
 * @property alpha The element's opacity, from 0 to 1, its children included.
 * @property hovered What changes while the pointer is over a Button.
 * @property pressed What changes while a Button is held, over [hovered].
 */
@Serializable
data class CanvasStyle(
    val background: String? = null,
    val gradient: CanvasGradient? = null,
    val image: CanvasImage? = null,
    val fillImage: CanvasImage? = null,
    @PropertyRange(min = 0.0) val cornerRadius: Float? = null,
    @PropertyRange(min = 0.0) val borderWidth: Float? = null,
    val borderColor: String? = null,
    val shadow: CanvasShadow? = null,
    val textColor: String? = null,
    @PropertyRange(min = 0.0, max = 1.0) val alpha: Float? = null,
    val hovered: CanvasStateStyle? = null,
    val pressed: CanvasStateStyle? = null,
) {
    /** What is wrong with this style, as messages naming the field; empty when nothing is. */
    internal fun problems(): List<String> = buildList {
        addAll(colourProblems("background" to background, "borderColor" to borderColor, "textColor" to textColor))
        gradient?.problems()?.forEach { add("gradient.$it") }
        image?.problems()?.forEach { add("image.$it") }
        fillImage?.problems()?.forEach { add("fillImage.$it") }
        if ((cornerRadius ?: 0f) < 0f) add("cornerRadius must not be negative")
        if ((borderWidth ?: 0f) < 0f) add("borderWidth must not be negative")
        shadow?.problems()?.forEach { add("shadow.$it") }
        if (alpha != null && alpha !in 0f..1f) add("alpha must be between 0 and 1")
        hovered?.problems()?.forEach { add("hovered.$it") }
        pressed?.problems()?.forEach { add("pressed.$it") }
    }

    /** Writes this style, and the states [scope] is in, into a Compose style, with [images] for its pictures. */
    internal fun applyTo(scope: StyleScope, images: Map<String, ImageBitmap>) {
        cornerRadius?.let { scope.cornerRadius(it.dp) }
        shadow?.let { scope.dropShadow(it.toShadow()) }
        paint(scope, images, background, gradient, image, textColor, alpha)
        val states = listOfNotNull(hovered.takeIf { scope.state.isHovered }, pressed.takeIf { scope.state.isPressed })
        for (state in states) paint(scope, images, state.background, state.gradient, state.image, state.textColor, state.alpha)
        val border = states.lastOrNull { it.borderColor != null }?.borderColor ?: borderColor
        if (borderWidth != null && borderWidth > 0f && border != null) scope.border(borderWidth.dp, colorOf(border, Color.Transparent))
    }
}

/**
 * What a [CanvasStyle] changes while its Button is hovered or pressed: its paint, but not its shape
 * or shadow. A property left out keeps what the style says.
 *
 * @property background The fill.
 * @property gradient A gradient fill, in place of [background].
 * @property image The picture or frame, such as the next cell of a button's sheet.
 * @property borderColor The border's colour, for a style with a border.
 * @property textColor The text's colour.
 * @property alpha The opacity, from 0 to 1.
 */
@Serializable
data class CanvasStateStyle(
    val background: String? = null,
    val gradient: CanvasGradient? = null,
    val image: CanvasImage? = null,
    val borderColor: String? = null,
    val textColor: String? = null,
    @PropertyRange(min = 0.0, max = 1.0) val alpha: Float? = null,
) {
    internal fun problems(): List<String> = buildList {
        addAll(colourProblems("background" to background, "borderColor" to borderColor, "textColor" to textColor))
        gradient?.problems()?.forEach { add("gradient.$it") }
        image?.problems()?.forEach { add("image.$it") }
        if (alpha != null && alpha !in 0f..1f) add("alpha must be between 0 and 1")
    }
}

/**
 * A gradient from [start] to [end]: top to bottom, or left to right when [horizontal].
 *
 * @property start The colour at the top, or the left.
 * @property end The colour at the bottom, or the right.
 * @property horizontal Whether it runs left to right.
 */
@Serializable
data class CanvasGradient(val start: String, val end: String, val horizontal: Boolean = false) {
    internal fun problems(): List<String> = colourProblems("start" to start, "end" to end)

    internal fun toBrush(): Brush {
        val from = colorOf(start, Color.Transparent)
        val to = colorOf(end, Color.Transparent)
        return if (horizontal) Brush.horizontal(from, to) else Brush.vertical(from, to)
    }
}

/**
 * A shadow behind an element, in its shape.
 *
 * @property color Its colour, usually translucent black.
 * @property offsetX How far right it falls, in dp.
 * @property offsetY How far down it falls, in dp.
 * @property blur How far its edge blurs, in dp.
 * @property spread How far it grows beyond the element before blurring, in dp.
 */
@Serializable
data class CanvasShadow(
    val color: String = "#00000080",
    val offsetX: Float = 0f,
    val offsetY: Float = DEFAULT_OFFSET_Y,
    @PropertyRange(min = 0.0) val blur: Float = DEFAULT_BLUR,
    val spread: Float = 0f,
) {
    internal fun problems(): List<String> = buildList {
        addAll(colourProblems("color" to color))
        if (blur < 0f) add("blur must not be negative")
    }

    internal fun toShadow(): Shadow = Shadow(
        radius = blur.dp,
        color = colorOf(color, Color.Transparent),
        spread = spread.dp,
        offset = DpOffset(offsetX.dp, offsetY.dp),
    )

    private companion object {
        const val DEFAULT_OFFSET_Y = 2f
        const val DEFAULT_BLUR = 4f
    }
}

/** The paint a style and its states share: a gradient replaces the fill colour, and the image draws over both. */
@Suppress("LongParameterList")
private fun paint(
    scope: StyleScope,
    images: Map<String, ImageBitmap>,
    background: String?,
    gradient: CanvasGradient?,
    image: CanvasImage?,
    textColor: String?,
    alpha: Float?,
) {
    background?.let { scope.background(colorOf(it, Color.Transparent)) }
    gradient?.let { scope.background(it.toBrush()) }
    image?.fill(images)?.let(scope::backgroundImage)
    textColor?.let { scope.textColor(colorOf(it, Color.White)) }
    alpha?.let { scope.alpha(it.coerceIn(0f, 1f)) }
}

private fun colourProblems(vararg colours: Pair<String, String?>): List<String> =
    colours.mapNotNull { (name, value) -> if (value != null && !isHexColor(value)) "$name \"$value\" must be #RRGGBB or #RRGGBBAA" else null }

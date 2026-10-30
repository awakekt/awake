/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.style.StyleScope
import com.awakekt.awake.compose.foundation.style.StyleState
import com.awakekt.awake.compose.ui.graphics.Brush
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.shadow.Shadow
import com.awakekt.awake.compose.ui.unit.DpOffset
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.core.text.theme.TextOutline
import com.awakekt.awake.core.text.theme.TextShadow
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
 * @property fillImage A Bar's fill, cut at its value or squeezed into it as [fillImageMode] says.
 * @property fillImageMode How [fillImage] meets the Bar's value: [CanvasFillImageMode.Cut], the default,
 * [CanvasFillImageMode.Squeeze], [CanvasFillImageMode.Capped], or [CanvasFillImageMode.Sweep].
 * @property cornerRadius Rounds the fill, gradient, border and shadow, and a Bar's colour fill.
 * Images stay square.
 * @property borderWidth The border's width, inside the element's edge.
 * @property borderColor The border's colour; a border needs both.
 * @property shadow A shadow cast behind the element, in its shape.
 * @property textColor The colour of a Text's or Button's text, in place of [CanvasElement.color].
 * @property textShadow A hard shadow under a Text's or Button's text.
 * @property textOutline An outline around a Text's or Button's text.
 * @property alpha The element's opacity, from 0 to 1, its children included.
 * @property hovered What changes while the pointer is over a Button.
 * @property pressed What changes while a Button is held, over [hovered].
 * @property disabled What changes while a Button is not [enabled][CanvasElement.enabled], over the
 * rest; null dims it to [DISABLED_ALPHA].
 * @property rotation Degrees clockwise to turn the element's image, background and content about
 * its own centre; null leaves it unrotated. Children turn with it. Hit-testing still uses the
 * element's unrotated layout box, the way a Compose `graphicsLayer` rotation leaves hit-testing on
 * the unrotated box.
 * @property linkColor The colour of a Text's `[[key|shown words]]` spans; null draws them in a
 * default blue.
 * @property transitionSeconds Seconds to interpolate between this style's states ([hovered],
 * [pressed], [disabled]); null falls back to [CanvasControlTheme.transitionSeconds], and 0 changes
 * at once.
 */
@Serializable
data class CanvasStyle(
    val background: String? = null,
    val gradient: CanvasGradient? = null,
    val image: CanvasImage? = null,
    val fillImage: CanvasImage? = null,
    val fillImageMode: CanvasFillImageMode = CanvasFillImageMode.Cut,
    @PropertyRange(min = 0.0) val cornerRadius: Float? = null,
    @PropertyRange(min = 0.0) val borderWidth: Float? = null,
    val borderColor: String? = null,
    val shadow: CanvasShadow? = null,
    val textColor: String? = null,
    val textShadow: CanvasTextShadow? = null,
    val textOutline: CanvasTextOutline? = null,
    @PropertyRange(min = 0.0, max = 1.0) val alpha: Float? = null,
    val hovered: CanvasStateStyle? = null,
    val pressed: CanvasStateStyle? = null,
    val disabled: CanvasStateStyle? = null,
    val rotation: Float? = null,
    val linkColor: String? = null,
    @PropertyRange(min = 0.0) val transitionSeconds: Float? = null,
) {
    /** [CanvasStyle] as it was before `transitionSeconds`: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        background: String? = null,
        gradient: CanvasGradient? = null,
        image: CanvasImage? = null,
        fillImage: CanvasImage? = null,
        fillImageMode: CanvasFillImageMode = CanvasFillImageMode.Cut,
        cornerRadius: Float? = null,
        borderWidth: Float? = null,
        borderColor: String? = null,
        shadow: CanvasShadow? = null,
        textColor: String? = null,
        textShadow: CanvasTextShadow? = null,
        textOutline: CanvasTextOutline? = null,
        alpha: Float? = null,
        hovered: CanvasStateStyle? = null,
        pressed: CanvasStateStyle? = null,
        disabled: CanvasStateStyle? = null,
        rotation: Float? = null,
        linkColor: String? = null,
    ) : this(
        background, gradient, image, fillImage, fillImageMode, cornerRadius, borderWidth, borderColor, shadow,
        textColor, textShadow, textOutline, alpha, hovered, pressed, disabled, rotation, linkColor,
    )

    /** [copy] as it was before `transitionSeconds`: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        background: String? = this.background,
        gradient: CanvasGradient? = this.gradient,
        image: CanvasImage? = this.image,
        fillImage: CanvasImage? = this.fillImage,
        fillImageMode: CanvasFillImageMode = this.fillImageMode,
        cornerRadius: Float? = this.cornerRadius,
        borderWidth: Float? = this.borderWidth,
        borderColor: String? = this.borderColor,
        shadow: CanvasShadow? = this.shadow,
        textColor: String? = this.textColor,
        textShadow: CanvasTextShadow? = this.textShadow,
        textOutline: CanvasTextOutline? = this.textOutline,
        alpha: Float? = this.alpha,
        hovered: CanvasStateStyle? = this.hovered,
        pressed: CanvasStateStyle? = this.pressed,
        disabled: CanvasStateStyle? = this.disabled,
        rotation: Float? = this.rotation,
        linkColor: String? = this.linkColor,
    ): CanvasStyle = copy(
        background, gradient, image, fillImage, fillImageMode, cornerRadius, borderWidth, borderColor, shadow,
        textColor, textShadow, textOutline, alpha, hovered, pressed, disabled, rotation, linkColor,
    )

    /** What is wrong with this style, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        addAll(
            colourProblems(
                "background" to background,
                "borderColor" to borderColor,
                "textColor" to textColor,
                "linkColor" to linkColor,
            ),
        )
        if ((cornerRadius ?: 0f) < 0f) add("cornerRadius must not be negative")
        if ((borderWidth ?: 0f) < 0f) add("borderWidth must not be negative")
        if (alpha != null && alpha !in 0f..1f) add("alpha must be between 0 and 1")
        if (transitionSeconds != null && !(transitionSeconds >= 0f && transitionSeconds.isFinite())) add("transitionSeconds must not be negative")
        addAll(partProblems())
    }

    /** The problems of the objects this style holds, each named by its field. */
    private fun partProblems(): List<String> = listOf(
        "gradient" to gradient?.problems(),
        "image" to image?.problems(),
        "fillImage" to fillImage?.problems(),
        "shadow" to shadow?.problems(),
        "textShadow" to textShadow?.problems(),
        "textOutline" to textOutline?.problems(),
        "hovered" to hovered?.problems(),
        "pressed" to pressed?.problems(),
        "disabled" to disabled?.problems(),
    ).flatMap { (field, problems) -> problems.orEmpty().map { "$field.$it" } }

    /**
     * Writes this style, and the states [scope] is in, into a Compose style, with [images] for its
     * pictures; [isDisabled] adds the [disabled] look over the rest.
     */
    internal fun applyTo(scope: StyleScope, images: Map<String, ImageBitmap>, isDisabled: Boolean = false) {
        cornerRadius?.let { scope.cornerRadius(it.dp) }
        val states = activeStates(scope.state, isDisabled)
        (states.lastOrNull { it.shadow != null }?.shadow ?: shadow)?.let { scope.dropShadow(it.toShadow()) }
        paint(scope, images, background, gradient, image, textColor, alpha)
        for (state in states) paint(scope, images, state.background, state.gradient, state.image, state.textColor, state.alpha)
        val border = states.lastOrNull { it.borderColor != null }?.borderColor ?: borderColor
        if (borderWidth != null && borderWidth > 0f && border != null) scope.border(borderWidth.dp, colorOf(border, Color.Transparent))
    }

    /** The active state overrides on [state], in application order; [isDisabled] adds [disabled] or [DIMMED]. */
    internal fun activeStates(state: StyleState, isDisabled: Boolean = false): List<CanvasStateStyle> {
        val dimmed = if (isDisabled) disabled ?: DIMMED else null
        return listOfNotNull(hovered.takeIf { state.isHovered }, pressed.takeIf { state.isPressed }, dimmed)
    }

    /** The draw-only centre scale active on [state]; 1 when no active state sets one. */
    internal fun stateScale(state: StyleState, isDisabled: Boolean = false): Float {
        val raw = activeStates(state, isDisabled).lastOrNull { it.scale != null }?.scale ?: return 1f
        return if (raw.isFinite()) raw.coerceAtLeast(0f) else 1f
    }

    /** Limits. */
    companion object {
        /** How opaque a disabled Button with no [disabled] look draws. */
        const val DISABLED_ALPHA = 0.45f

        internal val DIMMED = CanvasStateStyle(alpha = DISABLED_ALPHA)
    }
}

/** How a Bar's [CanvasStyle.fillImage] meets the Bar's value. */
@Serializable
enum class CanvasFillImageMode {
    /**
     * The image is laid out at the Bar's full width and cut at the value, so its pattern and its right
     * end cap stay where they are as the value changes, and the cap shows only at 100%.
     */
    Cut,

    /**
     * The image is drawn at the filled width, a nine-slice at the value times the Bar's width: both end
     * caps stay and only the middle stretches. Below the caps' combined width they shrink together
     * rather than overlap.
     */
    Squeeze,

    /**
     * The fill sweeps clockwise from 12 o'clock over the Bar's whole box, like a cooldown overlay
     * darkening an icon as it finishes: a wedge from 0 up to the value's share of a full turn, in
     * the colour fill or, if set, [CanvasStyle.fillImage] cut to the same wedge.
     */
    Sweep,

    /**
     * The image is drawn as a nine-slice whose end caps stay whole at every value above 0: the filled
     * width is both caps plus the value's share of the rest, so at 1% the caps already show and only
     * the middle grows. A Bar narrower than its caps shrinks them, as [Squeeze] does. 0 draws nothing.
     */
    Capped,
}

/**
 * What a [CanvasStyle] changes while its Button is hovered, pressed or disabled: its paint, shadow,
 * scale or opacity. A property left out keeps what the style says.
 *
 * @property background The fill.
 * @property gradient A gradient fill, in place of [background].
 * @property image The picture or frame, such as the next cell of a button's sheet.
 * @property borderColor The border's colour, for a style with a border.
 * @property textColor The text's colour.
 * @property alpha The opacity, from 0 to 1.
 * @property shadow The shadow cast behind the element, in place of [CanvasStyle.shadow].
 * @property scale Multiplier around the element's centre, without moving its layout or hit area.
 */
@Serializable
data class CanvasStateStyle(
    val background: String? = null,
    val gradient: CanvasGradient? = null,
    val image: CanvasImage? = null,
    val borderColor: String? = null,
    val textColor: String? = null,
    @PropertyRange(min = 0.0, max = 1.0) val alpha: Float? = null,
    val shadow: CanvasShadow? = null,
    @PropertyRange(min = 0.0) val scale: Float? = null,
) {
    /** [CanvasStateStyle] as it was before `shadow` and `scale`: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        background: String? = null,
        gradient: CanvasGradient? = null,
        image: CanvasImage? = null,
        borderColor: String? = null,
        textColor: String? = null,
        alpha: Float? = null,
    ) : this(background, gradient, image, borderColor, textColor, alpha)

    /** [copy] as it was before `shadow` and `scale`: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        background: String? = this.background,
        gradient: CanvasGradient? = this.gradient,
        image: CanvasImage? = this.image,
        borderColor: String? = this.borderColor,
        textColor: String? = this.textColor,
        alpha: Float? = this.alpha,
    ): CanvasStateStyle = copy(background, gradient, image, borderColor, textColor, alpha)

    internal fun problems(): List<String> = buildList {
        addAll(colourProblems("background" to background, "borderColor" to borderColor, "textColor" to textColor))
        gradient?.problems()?.forEach { add("gradient.$it") }
        image?.problems()?.forEach { add("image.$it") }
        shadow?.problems()?.forEach { add("shadow.$it") }
        if (alpha != null && alpha !in 0f..1f) add("alpha must be between 0 and 1")
        if (scale != null && !(scale >= 0f && scale.isFinite())) add("scale must not be negative")
    }
}

/**
 * A gradient from [start] to [end]: top to bottom, or left to right when [horizontal].
 *
 * @property start The colour at the top, or the left.
 * @property end The colour at the bottom, or the right.
 * @property horizontal Whether it runs left to right.
 * @property middle An optional colour halfway between [start] and [end]. Null keeps the two-colour fill.
 */
@Serializable
data class CanvasGradient(val start: String, val end: String, val horizontal: Boolean = false, val middle: String? = null) {
    /** Keeps the original two-colour constructor available to already compiled callers. */
    constructor(start: String, end: String, horizontal: Boolean = false) : this(start, end, horizontal, null)

    /** Keeps the original copy signature, preserving a midpoint when one has since been set. */
    fun copy(start: String = this.start, end: String = this.end, horizontal: Boolean = this.horizontal): CanvasGradient = CanvasGradient(start, end, horizontal, middle)

    internal fun problems(): List<String> = colourProblems("start" to start, "end" to end, "middle" to middle)

    internal fun toBrush(): Brush {
        val from = colorOf(start, Color.Transparent)
        val to = colorOf(end, Color.Transparent)
        middle?.let {
            val colors = listOf(from, colorOf(it, Color.Transparent), to)
            return if (horizontal) Brush.horizontalGradient(colors) else Brush.verticalGradient(colors)
        }
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
        radius = blur.coerceAtLeast(0f).dp,
        color = colorOf(color, Color.Transparent),
        spread = spread.dp,
        offset = DpOffset(offsetX.dp, offsetY.dp),
    )

    private companion object {
        const val DEFAULT_OFFSET_Y = 2f
        const val DEFAULT_BLUR = 4f
    }
}

/**
 * A hard shadow under an element's text.
 *
 * @property color Its colour.
 * @property offsetX How far right it falls, in dp.
 * @property offsetY How far down it falls, in dp.
 */
@Serializable
data class CanvasTextShadow(val color: String = "#000000C0", val offsetX: Float = 1f, val offsetY: Float = 1f) {
    internal fun problems(): List<String> = colourProblems("color" to color)

    internal fun toTextShadow(): TextShadow = TextShadow(colorOf(color, Color.Transparent), offsetX, offsetY)
}

/**
 * An outline around an element's text.
 *
 * @property color Its colour.
 * @property width Its width in dp; above 0.
 */
@Serializable
data class CanvasTextOutline(
    val color: String = "#000000",
    @PropertyRange(min = 0.0, exclusiveMin = true) val width: Float = 1f,
) {
    internal fun problems(): List<String> = buildList {
        addAll(colourProblems("color" to color))
        if (width <= 0f) add("width must be above 0")
    }

    /** The outline, or null for one too thin to draw. */
    internal fun toTextOutline(): TextOutline? = if (width > 0f) TextOutline(colorOf(color, Color.Transparent), width) else null
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

// A colour typed wrong in the editor falls back instead of taking the whole frame down.
internal fun colorOf(hex: String?, fallback: Color): Color =
    if (hex != null && isCanvasColor(hex)) Color.fromHex(hex) else fallback

/** Whether [value] is a `#RRGGBB` or `#RRGGBBAA` colour. */
fun isCanvasColor(value: String): Boolean = HEX_COLOR.matches(value)

private val HEX_COLOR = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

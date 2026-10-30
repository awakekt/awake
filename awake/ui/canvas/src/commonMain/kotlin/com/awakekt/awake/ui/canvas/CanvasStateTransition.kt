/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("LongParameterList")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.style.StyleScope
import com.awakekt.awake.compose.foundation.style.StyleState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.rememberGroup
import com.awakekt.awake.compose.ui.graphics.Brush
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.shadow.Shadow
import com.awakekt.awake.compose.ui.platform.LocalFrameClock
import com.awakekt.awake.compose.ui.unit.DpOffset
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color

context(_: Composer)
internal fun rememberCanvasStyleState(
    style: CanvasStyle,
    baseBack: Color,
    baseText: Color,
    state: StyleState,
    isDisabled: Boolean,
    themeTransitionSeconds: Float?,
): ResolvedCanvasState = rememberGroup(CanvasStyleStateGroup) {
    val clock = LocalFrameClock.current
    val transition = remember { CanvasStyleTransition() }
    val target = style.resolveState(baseBack, baseText, state, isDisabled)
    val duration = style.transitionSeconds ?: themeTransitionSeconds ?: 0f
    transition.advance(target, stateMask(state, isDisabled), duration, clock.deltaSeconds)
}

internal data class ResolvedCanvasState(
    val fill: ResolvedCanvasFill,
    val images: List<CanvasImage>,
    val borderColor: Color?,
    val textColor: Color,
    val shadow: ResolvedCanvasShadow?,
    val alpha: Float,
    val scale: Float,
) {
    fun applyTo(
        scope: StyleScope,
        style: CanvasStyle,
        bitmaps: Map<String, ImageBitmap>,
        applyAlpha: Boolean = true,
    ) {
        style.cornerRadius?.let { scope.cornerRadius(it.dp) }
        shadow?.let { scope.dropShadow(it.toShadow()) }
        fill.applyTo(scope)
        images.asReversed().firstNotNullOfOrNull { it.fill(bitmaps) }?.let(scope::backgroundImage)
        scope.textColor(textColor)
        if (applyAlpha && alpha < 1f) scope.alpha(alpha.coerceIn(0f, 1f))
        val borderWidth = style.borderWidth
        if (borderWidth != null && borderWidth > 0f && borderColor != null) {
            scope.border(borderWidth.dp, borderColor)
        }
    }

    fun lerpTo(target: ResolvedCanvasState, t: Float): ResolvedCanvasState = ResolvedCanvasState(
        fill = fill.lerpTo(target.fill, t),
        images = target.images,
        borderColor = lerpOptionalColor(borderColor, target.borderColor, t),
        textColor = lerpPremultiplied(textColor, target.textColor, t),
        shadow = ResolvedCanvasShadow.lerp(shadow, target.shadow, t),
        alpha = lerpFloat(alpha, target.alpha, t),
        scale = lerpFloat(scale, target.scale, t),
    )
}

internal sealed interface ResolvedCanvasFill {
    fun applyTo(scope: StyleScope)

    fun lerpTo(target: ResolvedCanvasFill, t: Float): ResolvedCanvasFill

    data class Solid(val color: Color) : ResolvedCanvasFill {
        override fun applyTo(scope: StyleScope) = scope.background(color)

        override fun lerpTo(target: ResolvedCanvasFill, t: Float): ResolvedCanvasFill = when (target) {
            is Solid -> Solid(lerpPremultiplied(color, target.color, t))
            is Gradient -> toGradient(target.horizontal).lerpGradient(target, t)
        }

        fun toGradient(horizontal: Boolean): Gradient = Gradient(color, null, color, horizontal)
    }

    data class Gradient(
        val start: Color,
        val middle: Color?,
        val end: Color,
        val horizontal: Boolean,
    ) : ResolvedCanvasFill {
        override fun applyTo(scope: StyleScope) {
            val brush = if (middle != null) {
                val stops = listOf(start, middle, end)
                if (horizontal) Brush.horizontalGradient(stops) else Brush.verticalGradient(stops)
            } else {
                if (horizontal) Brush.horizontal(start, end) else Brush.vertical(start, end)
            }
            scope.background(brush)
        }

        override fun lerpTo(target: ResolvedCanvasFill, t: Float): ResolvedCanvasFill = when (target) {
            is Solid -> lerpGradient(target.toGradient(horizontal), t)
            is Gradient -> if (horizontal == target.horizontal) {
                lerpGradient(target, t)
            } else if (t < HALF) {
                this
            } else {
                target
            }
        }

        fun lerpGradient(target: Gradient, t: Float): Gradient {
            val mid = if (middle == null && target.middle == null) {
                null
            } else {
                val fromMid = middle ?: lerpPremultiplied(start, end, HALF)
                val toMid = target.middle ?: lerpPremultiplied(target.start, target.end, HALF)
                lerpPremultiplied(fromMid, toMid, t)
            }
            return Gradient(
                start = lerpPremultiplied(start, target.start, t),
                middle = mid,
                end = lerpPremultiplied(end, target.end, t),
                horizontal = horizontal,
            )
        }
    }

    companion object {
        fun of(gradient: CanvasGradient): Gradient = Gradient(
            start = colorOf(gradient.start, Color.Transparent),
            middle = gradient.middle?.let { colorOf(it, Color.Transparent) },
            end = colorOf(gradient.end, Color.Transparent),
            horizontal = gradient.horizontal,
        )

        fun resolve(style: CanvasStyle, baseBack: Color, states: List<CanvasStateStyle>): ResolvedCanvasFill {
            var fill: ResolvedCanvasFill = Solid(baseBack)
            style.background?.let { fill = Solid(colorOf(it, Color.Transparent)) }
            style.gradient?.let { fill = of(it) }
            for (active in states) {
                active.background?.let { fill = Solid(colorOf(it, Color.Transparent)) }
                active.gradient?.let { fill = of(it) }
            }
            return fill
        }
    }
}

internal data class ResolvedCanvasShadow(
    val color: Color,
    val offsetX: Float,
    val offsetY: Float,
    val blur: Float,
    val spread: Float,
) {
    fun toShadow(): Shadow = Shadow(
        radius = blur.coerceAtLeast(0f).dp,
        color = color,
        spread = spread.dp,
        offset = DpOffset(offsetX.dp, offsetY.dp),
    )

    companion object {
        fun of(shadow: CanvasShadow): ResolvedCanvasShadow = ResolvedCanvasShadow(
            color = colorOf(shadow.color, Color.Transparent),
            offsetX = shadow.offsetX,
            offsetY = shadow.offsetY,
            blur = shadow.blur.coerceAtLeast(0f),
            spread = shadow.spread,
        )

        fun lerp(a: ResolvedCanvasShadow?, b: ResolvedCanvasShadow?, t: Float): ResolvedCanvasShadow? = when {
            a == null && b == null -> null
            a == null && b != null -> b.copy(color = b.color.withAlpha(b.color.a * t))
            a != null && b == null -> a.copy(color = a.color.withAlpha(a.color.a * (1f - t)))
            else -> {
                val from = checkNotNull(a)
                val to = checkNotNull(b)
                ResolvedCanvasShadow(
                    color = lerpPremultiplied(from.color, to.color, t),
                    offsetX = lerpFloat(from.offsetX, to.offsetX, t),
                    offsetY = lerpFloat(from.offsetY, to.offsetY, t),
                    blur = lerpFloat(from.blur, to.blur, t),
                    spread = lerpFloat(from.spread, to.spread, t),
                )
            }
        }
    }
}

internal fun CanvasStyle.resolveState(
    baseBack: Color,
    baseText: Color,
    state: StyleState,
    isDisabled: Boolean,
): ResolvedCanvasState {
    val states = activeStates(state, isDisabled)
    val pictures = buildList {
        image?.let(::add)
        for (active in states) active.image?.let(::add)
    }
    val resolvedBorder = (states.lastOrNull { it.borderColor != null }?.borderColor ?: borderColor)
        ?.let { colorOf(it, Color.Transparent) }
    val resolvedText = (states.lastOrNull { it.textColor != null }?.textColor ?: textColor)
        ?.let { colorOf(it, Color.White) } ?: baseText
    val resolvedShadow = (states.lastOrNull { it.shadow != null }?.shadow ?: shadow)?.let(ResolvedCanvasShadow::of)
    val resolvedAlpha = (states.lastOrNull { it.alpha != null }?.alpha ?: alpha ?: 1f).coerceIn(0f, 1f)
    return ResolvedCanvasState(
        fill = ResolvedCanvasFill.resolve(this, baseBack, states),
        images = pictures,
        borderColor = resolvedBorder,
        textColor = resolvedText,
        shadow = resolvedShadow,
        alpha = resolvedAlpha,
        scale = stateScale(state, isDisabled),
    )
}

private class CanvasStyleTransition {
    private var initialized = false
    private var mask = 0
    private var from: ResolvedCanvasState? = null
    private var current: ResolvedCanvasState? = null
    private var duration = 0f
    private var elapsed = 0f

    fun advance(target: ResolvedCanvasState, stateMask: Int, durationSeconds: Float, deltaSeconds: Float): ResolvedCanvasState {
        val cleanDuration = if (durationSeconds.isFinite() && durationSeconds > 0f) durationSeconds else 0f
        val firstFrame = !initialized
        if (firstFrame) {
            initialized = true
            mask = stateMask
        } else if (stateMask != mask) {
            from = current ?: target
            mask = stateMask
            duration = cleanDuration
            elapsed = 0f
        }
        val resolved = if (firstFrame || cleanDuration <= 0f || duration <= 0f) {
            from = null
            duration = 0f
            target
        } else {
            elapsed += if (deltaSeconds.isFinite() && deltaSeconds > 0f) deltaSeconds else 0f
            val t = (elapsed / duration).coerceIn(0f, 1f)
            if (t >= 1f) {
                from = null
                duration = 0f
                target
            } else {
                (from ?: target).lerpTo(target, t)
            }
        }
        current = resolved
        return resolved
    }
}

private fun stateMask(state: StyleState, isDisabled: Boolean): Int =
    (if (state.isHovered) HOVERED_BIT else 0) or
        (if (state.isPressed) PRESSED_BIT else 0) or
        (if (isDisabled) DISABLED_BIT else 0)

private fun lerpOptionalColor(a: Color?, b: Color?, t: Float): Color? = when {
    a == null && b == null -> null
    a == null && b != null -> b.withAlpha(b.a * t)
    a != null && b == null -> a.withAlpha(a.a * (1f - t))
    else -> lerpPremultiplied(checkNotNull(a), checkNotNull(b), t)
}

/** Interpolates in premultiplied RGBA so fading to or from transparent never passes through dark grey. */
private fun lerpPremultiplied(a: Color, b: Color, t: Float): Color {
    val alpha = lerpFloat(a.a, b.a, t).coerceIn(0f, 1f)
    if (alpha <= 0f) return Color.Transparent
    val r = (lerpFloat(a.r * a.a, b.r * b.a, t) / alpha).coerceIn(0f, 1f)
    val g = (lerpFloat(a.g * a.a, b.g * b.a, t) / alpha).coerceIn(0f, 1f)
    val blue = (lerpFloat(a.b * a.a, b.b * b.a, t) / alpha).coerceIn(0f, 1f)
    return Color(r, g, blue, alpha)
}

private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private object CanvasStyleStateGroup

private const val HOVERED_BIT = 1
private const val PRESSED_BIT = 2
private const val DISABLED_BIT = 4
private const val HALF = 0.5f

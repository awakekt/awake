/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.runtime.CompositionLocal
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.BackdropGlassStyle
import com.awakekt.awake.compose.ui.draw.BackdropSourceState
import com.awakekt.awake.compose.ui.draw.backdropEffect
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.backdropFallbackColor
import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable

/**
 * Frosted glass behind a [CanvasElement]: whatever is drawn beneath the element this frame, the 3D
 * scene included, blurred inside the element's shape (its [CanvasStyle.cornerRadius] kept), then
 * [tint]ed. The element's own background, image, border and content draw over it.
 *
 * Where blur does not run -- the renderer's effect quality is Off, it cannot sample its target, the
 * frame's blurs ran out, or the host turned blur off with [LocalCanvasBackdropBlur] -- the shape is
 * filled with [fallback] instead, and no blur pass is recorded.
 *
 * Colours are `#RRGGBB` or `#RRGGBBAA`.
 *
 * @property blur The blur's radius in dp, as CSS `blur()`: 0 draws only the tint.
 * @property tint Mixed over the blurred backdrop by its alpha; null leaves the backdrop untinted.
 * @property saturation 1 keeps the backdrop's colours, 0 makes it grey, above 1 strengthens them.
 * @property fallback What fills the shape when it is not blurred; null derives a colour from [tint].
 */
@Serializable
data class CanvasBackdrop(
    @PropertyRange(min = 0.0) val blur: Float = DEFAULT_BLUR,
    val tint: String? = null,
    @PropertyRange(min = 0.0) val saturation: Float = 1f,
    val fallback: String? = null,
) {
    /** What is wrong with this backdrop, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        addAll(colourProblems("tint" to tint, "fallback" to fallback))
        if (!(blur >= 0f && blur.isFinite())) add("blur must not be negative")
        if (!(saturation >= 0f && saturation.isFinite())) add("saturation must not be negative")
    }

    /** This backdrop as the Compose glass style it draws through, built once rather than every frame. */
    internal val glassStyle: BackdropGlassStyle by lazy { toGlassStyle() }

    private fun toGlassStyle(): BackdropGlassStyle {
        val tintColor = colorOf(tint, Color.Transparent)
        return BackdropGlassStyle(
            blurRadius = blur.coerceAtLeast(0f).dp,
            tint = tintColor,
            saturation = saturation.coerceAtLeast(0f),
            // Resolved here rather than left null, so the host turning blur off draws it too.
            fallback = if (fallback != null) colorOf(fallback, Color.Transparent) else backdropFallbackColor(tintColor),
        )
    }

    /** Defaults. */
    companion object {
        /** The blur radius in dp when a backdrop names none, the same as Compose's regular glass. */
        const val DEFAULT_BLUR = 16f
    }
}

/**
 * Whether canvas elements with a [CanvasStyle.backdrop] blur what is behind them. The host provides
 * false to draw every backdrop's fallback colour with no blur pass, for example below the effect
 * quality it wants glass at:
 *
 * ```kotlin
 * CompositionLocalProvider(LocalCanvasBackdropBlur provides (renderer.effectQuality >= EffectQuality.Medium)) {
 *     CanvasOverlay(items)
 * }
 * ```
 *
 * The renderer's own effect quality still applies on top: at Off nothing blurs either way.
 */
val LocalCanvasBackdropBlur: CompositionLocal<Boolean> = compositionLocalOf { true }

/**
 * The backdrop every canvas element samples: the whole target beneath the canvas, which no node
 * marks or moves, so one shared, never-placed source serves every canvas.
 */
private val CanvasBackdropSource = BackdropSourceState(enabled = true)

/** [backdrop] drawn under whatever this modifier wraps, in a rounded rectangle of [cornerRadius] dp. */
internal fun Modifier.canvasBackdrop(backdrop: CanvasBackdrop?, cornerRadius: Float?, blurOn: Boolean): Modifier {
    if (backdrop == null) return this
    return backdropEffect(
        source = CanvasBackdropSource,
        style = backdrop.glassStyle,
        shape = RoundedCornerShape((cornerRadius ?: 0f).coerceAtLeast(0f).dp),
        enabled = blurOn,
    )
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln

/** How a [CanvasScaler] works out a canvas's scale. */
@Serializable
enum class CanvasScaleMode {
    /** One unit is [CanvasScaler.pixelsPerUnit] of the screen's pixels, at any screen size: pixel art. */
    ConstantPixelSize,

    /**
     * The canvas is laid out at [CanvasScaler.referenceWidth] by [CanvasScaler.referenceHeight] units
     * and grows or shrinks with the screen, by its width, its height or a blend, as [CanvasScaler.match]
     * says: a HUD that takes the same share of any screen.
     */
    ScaleWithScreenSize,

    /** One unit is 1 / [CanvasScaler.referenceDpi] of an inch on the screen, at any pixel density. */
    ConstantPhysicalSize,
}

/**
 * Rounds the scale a [CanvasScaler] works out to a step, so a canvas drawn from pixel art lands on a
 * whole number of screen pixels per art pixel.
 *
 * Text does not need it. A canvas rounds each text element's size to whole device pixels, after the
 * scale and the density, so text up to the font's pixel-glyph size is crisp at any scale. What
 * snapping gives is a scale that is a clean multiple of the art, and text sizes that agree with the
 * layout: at 1.5 every even size is whole, at 1 or 2 every size is.
 *
 * It costs layout size. A snapped canvas is laid out at the snapped scale, so against the unsnapped
 * one every size and offset is off by up to half a step, in proportion: a 1017 dp tall window against
 * the default 720 reference gives 1.4125, which [HalfStep] draws at 1.5, 6% larger, and [WholeStep] at 1,
 * 29% smaller. Pick a step small against the scales the game runs at.
 */
@Serializable
enum class CanvasScaleSnap(
    /** The multiple of the scale the rule is rounded to; 0 for none. */
    internal val step: Float,
) {
    /** The scale is used as the rule gives it. */
    None(0f),

    /** The scale is rounded to the nearest whole number: 1, 2, 3. */
    WholeStep(1f),

    /** The scale is rounded to the nearest half: 1, 1.5, 2, 2.5. */
    HalfStep(0.5f),
    ;

    /**
     * [scale] rounded to the nearest multiple of this step, halves up. A scale under one step is left
     * as it is: a canvas smaller than its design cannot be made a whole multiple of it, and rounding
     * it up would grow it past the box it was fitted to.
     */
    internal fun round(scale: Float): Float =
        if (step <= 0f || scale < step) scale else floor(scale / step + HALF) * step

    private companion object {
        const val HALF = 0.5f
    }
}

/** Which part of a canvas a [CanvasScaler] scales. */
@Serializable
enum class CanvasScaleScope {
    /** Every element on the canvas, when the scaler is the canvas's own. */
    Canvas,

    /** Only the node the scaler sits on and the nodes beneath it, at the scale the canvas's box gives. */
    Subtree,
}

/**
 * Works out the scale a [CanvasOverlay] or one of its subtrees draws at from the size of the canvas's
 * box: what Unity calls a canvas scaler. It is data, saved with a scene as its `canvas_scaler` and
 * passed as `CanvasOverlay(scaler = …)` or [CanvasItem.scaler]; [scaleFor] is the whole rule.
 *
 * The scale multiplies dp. At 1 an element of 100 units is 100 dp across, as without a scaler.
 *
 * @property mode Which rule works the scale out.
 * @property referenceWidth With [CanvasScaleMode.ScaleWithScreenSize], the width the canvas is
 *   designed at, in units: a screen this many dp wide draws a unit as one dp, matching by width.
 * @property referenceHeight With [CanvasScaleMode.ScaleWithScreenSize], the height the canvas is
 *   designed at, in units.
 * @property match With [CanvasScaleMode.ScaleWithScreenSize], what the scale follows: 0 the screen's
 *   width, 1 its height, and between them a blend, taken on a log scale so that 0.5 is as much
 *   wider as it is shorter. A landscape HUD usually matches height, a portrait one width.
 * @property pixelsPerUnit With [CanvasScaleMode.ConstantPixelSize], how many of the screen's pixels
 *   one unit covers: 1 for pixel-exact art, 2 to draw it twice the size.
 * @property referenceDpi With [CanvasScaleMode.ConstantPhysicalSize], the dots per inch the canvas is
 *   designed at: one unit is 1 / [referenceDpi] of an inch. 160, the default, makes a unit a dp on a
 *   screen whose host reports its density truly.
 * @property minScale The smallest scale the rule may give; 0 for no limit.
 * @property maxScale The largest scale the rule may give; 0 for no limit.
 * @property playerScale The player's own multiplier, applied after [minScale] and [maxScale] so a
 *   player can always make the UI larger or smaller: a game's settings menu sets it.
 * @property snap Rounds the scale [mode] gives to a step, before the limits hold it and the player
 *   multiplies it; [CanvasScaleSnap.None], the default, draws at the scale as the rule gives it. See
 *   [CanvasScaleSnap] for what it keeps crisp and what it costs.
 * @property scope Whether the scaler applies to the whole [CanvasScaleScope.Canvas], the default, or
 *   only to the [CanvasScaleScope.Subtree] of the node it sits on.
 */
@Serializable
data class CanvasScaler(
    val mode: CanvasScaleMode = CanvasScaleMode.ScaleWithScreenSize,
    @PropertyRange(min = 0.0, exclusiveMin = true) val referenceWidth: Float = DEFAULT_REFERENCE_WIDTH,
    @PropertyRange(min = 0.0, exclusiveMin = true) val referenceHeight: Float = DEFAULT_REFERENCE_HEIGHT,
    @PropertyRange(min = 0.0, max = 1.0) val match: Float = DEFAULT_MATCH,
    @PropertyRange(min = 0.0, exclusiveMin = true) val pixelsPerUnit: Float = 1f,
    @PropertyRange(min = 0.0, exclusiveMin = true) val referenceDpi: Float = DP_PER_INCH,
    @PropertyRange(min = 0.0) val minScale: Float = 0f,
    @PropertyRange(min = 0.0) val maxScale: Float = 0f,
    @PropertyRange(min = 0.0, exclusiveMin = true) val playerScale: Float = 1f,
    val snap: CanvasScaleSnap = CanvasScaleSnap.None,
    val scope: CanvasScaleScope = CanvasScaleScope.Canvas,
) {
    /**
     * The scale for a canvas box [width] by [height] dp, on a screen of [density] pixels per dp and
     * [dpi] dots per inch: by [mode], rounded by [snap], held between [minScale] and [maxScale], then
     * times [playerScale]. The limits come after the rounding, so a scale is never outside them.
     *
     * A host that knows the screen's real dots per inch passes [dpi]; by default it is 160 per dp,
     * the density's own definition, which is true on Android and close elsewhere. A box with no
     * size, or a setting out of range, gives a scale of 1 for that step rather than none.
     */
    fun scaleFor(width: Float, height: Float, density: Float, dpi: Float = density * DP_PER_INCH): Float {
        val pixels = if (density > 0f && density.isFinite()) density else 1f
        val raw = when (mode) {
            CanvasScaleMode.ConstantPixelSize -> pixelsPerUnit / pixels
            CanvasScaleMode.ScaleWithScreenSize -> screenScale(width, height)
            CanvasScaleMode.ConstantPhysicalSize -> dpi / (referenceDpi * pixels)
        }
        var scale = snap.round(if (raw > 0f && raw.isFinite()) raw else 1f)
        if (minScale > 0f) scale = scale.coerceAtLeast(minScale)
        if (maxScale > 0f) scale = scale.coerceAtMost(maxScale)
        val player = if (playerScale > 0f && playerScale.isFinite()) playerScale else 1f
        return scale * player
    }

    /** The screen's width and height over the reference's, blended by [match] on a log scale. */
    private fun screenScale(width: Float, height: Float): Float {
        val sized = width > 0f && height > 0f
        if (!sized || referenceWidth <= 0f || referenceHeight <= 0f) return 1f
        val byWidth = ln(width / referenceWidth)
        val byHeight = ln(height / referenceHeight)
        val blend = match.coerceIn(0f, 1f)
        return exp(byWidth + (byHeight - byWidth) * blend)
    }

    /** What is wrong with these settings, each as `field message`; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (referenceWidth <= 0f || referenceHeight <= 0f) add("referenceWidth and referenceHeight must be positive")
        if (match !in 0f..1f) add("match must be between 0 and 1")
        if (pixelsPerUnit <= 0f) add("pixelsPerUnit must be positive")
        if (referenceDpi <= 0f) add("referenceDpi must be positive")
        if (minScale < 0f || maxScale < 0f) add("minScale and maxScale must not be negative")
        if (minScale > 0f && maxScale > 0f && maxScale < minScale) add("maxScale must not be below minScale")
        if (playerScale <= 0f) add("playerScale must be positive")
    }

    /** The defaults a new scaler starts from. */
    companion object {
        /** A 720p landscape design, the common HUD reference. */
        const val DEFAULT_REFERENCE_WIDTH = 1280f

        /** A 720p landscape design, the common HUD reference. */
        const val DEFAULT_REFERENCE_HEIGHT = 720f

        /** Half by width and half by height. */
        const val DEFAULT_MATCH = 0.5f

        /** A dp is 1/160 of an inch, by its definition. */
        const val DP_PER_INCH = 160f
    }
}

/**
 * The parts of a canvas's box something else covers, in dp from each edge: a notch, rounded
 * corners, a home indicator, a TV's overscan. [CanvasOverlay] keeps anchored elements inside them,
 * except one whose [CanvasElement.safeArea] is false.
 *
 * @property left How far the safe area starts from the left edge, in dp.
 * @property top How far the safe area starts from the top edge, in dp.
 * @property right How far the safe area ends from the right edge, in dp.
 * @property bottom How far the safe area ends from the bottom edge, in dp.
 */
data class CanvasInsets(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f) {
    /** Holds [None]. */
    companion object {
        /** No edge covered: the whole box is safe. */
        val None: CanvasInsets = CanvasInsets()
    }
}

/** Each inset times [factor], and never below nothing. */
internal fun CanvasInsets.scaledBy(factor: Float): CanvasInsets = if (this == CanvasInsets.None) {
    this
} else {
    CanvasInsets(left.coerceAtLeast(0f) * factor, top.coerceAtLeast(0f) * factor, right.coerceAtLeast(0f) * factor, bottom.coerceAtLeast(0f) * factor)
}

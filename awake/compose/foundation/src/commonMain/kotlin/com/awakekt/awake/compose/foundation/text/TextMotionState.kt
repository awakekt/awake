/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Retained per-node layout cache and motion state for [Text] and [BasicTextField].
 *
 * Kept in composition slots rather than on a `Modifier.Node`, so a conditional modifier ahead of
 * text in the chain (such as `Modifier.scale(1f)` eliding its element as an animation passes
 * through unit scale) never resets either the layout cache or the active motion state.
 */
internal class TextNodeState {
    val cache = TextLayoutCache()
    val motion = TextMotionState()
}

/**
 * Tracks frame-to-frame scale, em-size, and subpixel-offset motion for one text node so moving or
 * scaling runs neither re-hint per frame nor switch between exact-size pixel glyphs and the
 * distance field mid-animation.
 *
 * - **At rest or scrolling by whole device pixels**: text snaps to whole pixels and uses exact-size
 *   pixel glyphs up to `UiFont.pixelGlyphMaxSize`.
 * - **Translating by fractions of a pixel at unit scale**: text keeps the exact-size pixel-glyph
 *   bitmaps chosen at rest and slides them smoothly; once motion stops, any subpixel remainder
 *   settles smoothly back onto the nearest whole pixel without a single-frame pop.
 * - **Scaling or animating em size**: text locks onto continuous fractional sizing and the distance
 *   field for the whole animation, including the single frame where a scale passes through `1.0x` or
 *   an em crosses `UiFont.pixelGlyphMaxSize`.
 */
internal class TextMotionState {
    private var measureInitialized = false
    private var lastRawEmPx = 0f
    private var sizeHoldFrames = 0
    private var scaleHoldFrames = 0
    private val paintOffsetTracker = SubpixelOffsetTracker()

    /** True while the run's em size or `TextStyle.scale` is animating across frames. */
    var smoothSizing: Boolean = false
        private set

    /** Updates [smoothSizing] from this pass's unrounded device-pixel em [rawEmPx] and [styleScale]. */
    fun onMeasure(rawEmPx: Float, styleScale: Float): Boolean {
        val explicitScale = abs(styleScale - 1f) > SCALE_EPSILON
        if (!measureInitialized) {
            measureInitialized = true
            lastRawEmPx = rawEmPx
            if (explicitScale) {
                sizeHoldFrames = SETTLE_FRAMES
                smoothSizing = true
            } else {
                sizeHoldFrames = 0
                smoothSizing = false
            }
            return smoothSizing
        }
        val sizeChanged = abs(rawEmPx - lastRawEmPx) > SIZE_EPSILON
        lastRawEmPx = rawEmPx
        if (sizeChanged || explicitScale) {
            sizeHoldFrames = SETTLE_FRAMES
            smoothSizing = true
        } else if (sizeHoldFrames > 0) {
            sizeHoldFrames -= 1
            smoothSizing = sizeHoldFrames > 0
        } else {
            smoothSizing = false
        }
        return smoothSizing
    }

    /**
     * Resolves how `TextRun` should paint this frame, called once per frame before any shadow or
     * outline ring passes so every pass of one decorated run agrees on the same motion state.
     */
    fun resolvePaint(isScopeScaled: Boolean, isScopeAligned: Boolean, offsetX: Float, offsetY: Float): TextPaintMotion {
        if (isScopeScaled) {
            scaleHoldFrames = SETTLE_FRAMES
        } else if (scaleHoldFrames > 0) {
            scaleHoldFrames -= 1
        }
        val forceDistanceField = isScopeScaled || scaleHoldFrames > 0 || sizeHoldFrames > 0
        if (forceDistanceField) {
            paintOffsetTracker.resetTo(offsetX, offsetY)
            return TextPaintMotion(
                forceDistanceField = true,
                subpixelTranslated = false,
                residualX = 0f,
                residualY = 0f,
            )
        }
        val snappedX = wholePixelHalfUp(offsetX).toInt()
        val snappedY = wholePixelHalfUp(offsetY).toInt()
        paintOffsetTracker.step(offsetX, offsetY, snappedX, snappedY)
        val hasDirectResidual = abs(paintOffsetTracker.residualX) > RESIDUAL_EPSILON ||
            abs(paintOffsetTracker.residualY) > RESIDUAL_EPSILON
        return TextPaintMotion(
            forceDistanceField = false,
            subpixelTranslated = !isScopeAligned || hasDirectResidual,
            residualX = paintOffsetTracker.residualX,
            residualY = paintOffsetTracker.residualY,
        )
    }

    companion object {
        private const val SIZE_EPSILON = 1e-4f
        private const val SCALE_EPSILON = 1e-4f
        private const val RESIDUAL_EPSILON = 1e-4f
        internal const val SETTLE_FRAMES = 8
    }
}

/** Per-frame paint decision produced once by [TextMotionState.resolvePaint]. */
internal class TextPaintMotion(
    val forceDistanceField: Boolean,
    val subpixelTranslated: Boolean,
    val residualX: Float,
    val residualY: Float,
)

/**
 * Tracks a continuous pixel offset `(pxX, pxY)` paired with its rounded integer placement
 * `(intX, intY)`.
 *
 * Static fractional offsets snap immediately on their first frame (`residual = 0`). Whole-pixel
 * steps keep `residual = 0` so scrolling lists stay crisp. Fractional deltas keep the exact
 * continuous residual `(px - int)` during motion and ease it smoothly to `0` over
 * [TextMotionState.SETTLE_FRAMES] frames after motion stops, avoiding a visible pop on either end.
 */
internal class SubpixelOffsetTracker {
    private var initialized = false
    private var lastPxX = 0f
    private var lastPxY = 0f
    private var lastRenderedX = 0f
    private var lastRenderedY = 0f
    private var active = false
    private var settleFrames = 0
    private var leadInErrorX = 0f
    private var leadInErrorY = 0f
    private var leadInFrames = 0

    var residualX: Float = 0f
        private set

    var residualY: Float = 0f
        private set

    fun resetTo(pxX: Float, pxY: Float) {
        initialized = true
        lastPxX = pxX
        lastPxY = pxY
        lastRenderedX = pxX
        lastRenderedY = pxY
        active = false
        settleFrames = 0
        leadInErrorX = 0f
        leadInErrorY = 0f
        leadInFrames = 0
        residualX = 0f
        residualY = 0f
    }

    fun step(pxX: Float, pxY: Float, intX: Int, intY: Int) {
        val rawResX = pxX - intX
        val rawResY = pxY - intY
        if (!initialized) {
            initializeAt(pxX, pxY, intX, intY)
            return
        }
        val prevPxX = lastPxX
        val prevPxY = lastPxY
        val dx = pxX - prevPxX
        val dy = pxY - prevPxY
        lastPxX = pxX
        lastPxY = pxY

        val moved = abs(dx) > MOTION_EPSILON || abs(dy) > MOTION_EPSILON
        val hasResidual = abs(rawResX) > FRACTION_EPSILON || abs(rawResY) > FRACTION_EPSILON
        if (moved) {
            advanceMoving(prevPxX, prevPxY, dx, dy, hasResidual)
        } else if (active) {
            advanceStationary(hasResidual)
        }
        computeResiduals(rawResX, rawResY)
        lastRenderedX = intX + residualX
        lastRenderedY = intY + residualY
    }

    private fun initializeAt(pxX: Float, pxY: Float, intX: Int, intY: Int) {
        initialized = true
        lastPxX = pxX
        lastPxY = pxY
        lastRenderedX = intX.toFloat()
        lastRenderedY = intY.toFloat()
        active = false
        settleFrames = 0
        leadInFrames = 0
        residualX = 0f
        residualY = 0f
    }

    private fun advanceMoving(prevPxX: Float, prevPxY: Float, dx: Float, dy: Float, hasResidual: Boolean) {
        val fractionalDelta = abs(dx - dx.roundToInt()) > FRACTION_EPSILON ||
            abs(dy - dy.roundToInt()) > FRACTION_EPSILON
        when {
            fractionalDelta -> {
                if (!active) {
                    startLeadIn(prevPxX, prevPxY)
                } else if (leadInFrames in 1..TextMotionState.SETTLE_FRAMES) {
                    leadInFrames += 1
                }
                settleFrames = 0
            }
            active && hasResidual -> {
                settleFrames = 0
                if (leadInFrames in 1..TextMotionState.SETTLE_FRAMES) leadInFrames += 1
            }
            !hasResidual -> leadInFrames = 0
        }
    }

    private fun startLeadIn(prevPxX: Float, prevPxY: Float) {
        leadInErrorX = lastRenderedX - prevPxX
        leadInErrorY = lastRenderedY - prevPxY
        val hasLeadError = abs(leadInErrorX) > FRACTION_EPSILON || abs(leadInErrorY) > FRACTION_EPSILON
        leadInFrames = if (hasLeadError) 1 else 0
        active = true
    }

    private fun advanceStationary(hasResidual: Boolean) {
        if (!hasResidual) {
            clearActiveMotion()
            return
        }
        settleFrames += 1
        if (settleFrames >= TextMotionState.SETTLE_FRAMES) {
            clearActiveMotion()
        }
    }

    private fun clearActiveMotion() {
        active = false
        settleFrames = 0
        leadInFrames = 0
    }

    private fun computeResiduals(rawResX: Float, rawResY: Float) {
        if (!active) {
            residualX = 0f
            residualY = 0f
            return
        }
        val leadFactor = if (leadInFrames in 1 until TextMotionState.SETTLE_FRAMES) {
            1f - smoothStep(leadInFrames.toFloat() / TextMotionState.SETTLE_FRAMES)
        } else {
            0f
        }
        val baseX = rawResX + leadInErrorX * leadFactor
        val baseY = rawResY + leadInErrorY * leadFactor
        val settleFactor = if (settleFrames > 0) {
            1f - smoothStep(settleFrames.toFloat() / TextMotionState.SETTLE_FRAMES)
        } else {
            1f
        }
        residualX = baseX * settleFactor
        residualY = baseY * settleFactor
    }

    companion object {
        private const val MOTION_EPSILON = 1e-4f
        private const val FRACTION_EPSILON = 1e-3f
    }
}

private fun wholePixelHalfUp(value: Float): Float = floor(value + 0.5f)

private fun smoothStep(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

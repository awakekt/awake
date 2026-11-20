/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.runtime.MutableState
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.runtime.readsAreTracked
import com.awakekt.awake.compose.ui.platform.FrameClock
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

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
 * - **A constant size or `TextStyle.scale`**: at rest, whatever its value, so a static `scale = 2f`
 *   draws exact-size pixel glyphs as it always did, and text returns to them once an animation ends.
 */
internal class TextMotionState {
    private var measureInitialized = false
    private var lastRawEmPx = 0f
    private var lastStyleScale = 1f
    private var sizeHoldFrames = 0
    private var scaleHoldFrames = 0
    private val paintOffsetTracker = SubpixelOffsetTracker()

    /**
     * The composition's clock, so each hold counts frames rather than passes (#1764). A frame can
     * measure and paint a node more than once: a placement write composes and lays out again, and
     * `ComposeHost.verifySkippedCompositions` measures and paints a skipped frame a second time to
     * compare. Counting per pass settled a run faster than [SETTLE_FRAMES] frames on such frames, and
     * the verifier's second pass could cross from the distance field to pixel glyphs that the first
     * had not, a stale skip that was not there. Null outside a host: every pass then counts.
     */
    var clock: FrameClock? = null
    private var measuredFrame = NO_FRAME
    private var paintedFrame = NO_FRAME
    private var lastPaint: TextPaintMotion? = null
    private var lastScaled = false
    private var lastAligned = false
    private var lastOffsetX = 0f
    private var lastOffsetY = 0f

    // A settling run changes on its next pass although nothing tracked changed: the holds count down
    // per pass. A host that skips an unchanged measure or replays an unchanged draw (#1512) would
    // freeze it mid-settle, so while it settles each recorded pass reads and flips this flag, which
    // marks that pass out of date. Made only for such a host, so the default path allocates nothing.
    private var settling: MutableState<Boolean>? = null

    private fun runAgainWhile(unsettled: Boolean) {
        if (!unsettled || !readsAreTracked) return
        val flag = settling ?: mutableStateOf(false).also { settling = it }
        flag.value = !flag.value
    }

    /** True while the run's em size or `TextStyle.scale` is animating across frames. */
    var smoothSizing: Boolean = false
        private set

    /**
     * Updates [smoothSizing] from this pass's unrounded device-pixel em [rawEmPx] and [styleScale].
     *
     * Only a *change* of either since the previous pass counts as motion. A constant size or a
     * constant `TextStyle.scale`, including one that starts away from `1f`, is at rest: it keeps the
     * snapped, exact-size pixel glyphs and decays off the distance field after the hold like any
     * other size. The first pass has nothing to compare against, so it is always at rest.
     */
    fun onMeasure(rawEmPx: Float, styleScale: Float): Boolean {
        val frame = clock?.frame ?: NO_FRAME
        val sameSize = rawEmPx == lastRawEmPx && styleScale == lastStyleScale
        if (isRepeat(frame, measuredFrame) && sameSize) {
            runAgainWhile(sizeHoldFrames > 0)
            return smoothSizing
        }
        measuredFrame = frame
        return measureNewPass(rawEmPx, styleScale)
    }

    private fun measureNewPass(rawEmPx: Float, styleScale: Float): Boolean {
        if (!measureInitialized) {
            measureInitialized = true
            lastRawEmPx = rawEmPx
            lastStyleScale = styleScale
            sizeHoldFrames = 0
            smoothSizing = false
            return false
        }
        val changed = abs(rawEmPx - lastRawEmPx) > SIZE_EPSILON || abs(styleScale - lastStyleScale) > SCALE_EPSILON
        lastRawEmPx = rawEmPx
        lastStyleScale = styleScale
        if (changed) {
            sizeHoldFrames = SETTLE_FRAMES
            smoothSizing = true
        } else if (sizeHoldFrames > 0) {
            sizeHoldFrames -= 1
            smoothSizing = sizeHoldFrames > 0
        } else {
            smoothSizing = false
        }
        runAgainWhile(sizeHoldFrames > 0)
        return smoothSizing
    }

    /**
     * Resolves how `TextRun` should paint this frame, called once per frame before any shadow or
     * outline ring passes so every pass of one decorated run agrees on the same motion state.
     */
    fun resolvePaint(isScopeScaled: Boolean, isScopeAligned: Boolean, offsetX: Float, offsetY: Float): TextPaintMotion {
        val frame = clock?.frame ?: NO_FRAME
        val repeated = lastPaint
        val sameInputs = isScopeScaled == lastScaled && isScopeAligned == lastAligned &&
            offsetX == lastOffsetX && offsetY == lastOffsetY
        if (isRepeat(frame, paintedFrame) && sameInputs && repeated != null) {
            runAgainWhile(scaleHoldFrames > 0 || sizeHoldFrames > 0 || paintOffsetTracker.isSettling)
            return repeated
        }
        paintedFrame = frame
        lastScaled = isScopeScaled
        lastAligned = isScopeAligned
        lastOffsetX = offsetX
        lastOffsetY = offsetY
        return resolveNewPaint(isScopeScaled, isScopeAligned, offsetX, offsetY).also { lastPaint = it }
    }

    /** Whether [frame] is a known frame that already ran [last], so its pass repeats that decision. */
    private fun isRepeat(frame: Long, last: Long): Boolean = frame != NO_FRAME && frame == last

    private fun resolveNewPaint(isScopeScaled: Boolean, isScopeAligned: Boolean, offsetX: Float, offsetY: Float): TextPaintMotion {
        if (isScopeScaled) {
            scaleHoldFrames = SETTLE_FRAMES
        } else if (scaleHoldFrames > 0) {
            scaleHoldFrames -= 1
        }
        val forceDistanceField = isScopeScaled || scaleHoldFrames > 0 || sizeHoldFrames > 0
        if (forceDistanceField) {
            paintOffsetTracker.resetTo(offsetX, offsetY)
            // A constant scale paints the same each pass; a hold counting down to the pixel glyphs does not.
            runAgainWhile((!isScopeScaled && scaleHoldFrames > 0) || sizeHoldFrames > 0)
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
        runAgainWhile(paintOffsetTracker.isSettling)
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

        /** No frame yet: [FrameClock.frame] counts from 1. */
        private const val NO_FRAME = -1L
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

    /** Whether a remainder is still easing in or out, so the next step changes [residualX] or [residualY]. */
    val isSettling: Boolean get() = active

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

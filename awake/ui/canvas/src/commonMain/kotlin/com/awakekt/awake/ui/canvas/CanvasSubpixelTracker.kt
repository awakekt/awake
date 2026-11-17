/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Eases an anchored element's subpixel remainder in and out over a few frames while it moves by
 * fractions of a pixel, so whole-pixel layout neither jitters nor snaps.
 *
 * Its motion state advances once per frame of [step]'s `frame`, however many times that frame
 * draws: a host draws twice in one frame when placement asks for a second pass, or when it checks a
 * skipped composition against a composed one (#1512). A second draw restores the state from before
 * the frame's first step and steps again with its own inputs, so it shows what one step from the
 * last frame gives. A frame of 0, a clock no host advances, steps on every draw.
 */
internal class CanvasSubpixelTracker {
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

    /** Whether the next frame may draw it elsewhere with nothing moved: a lead-in or settle still runs. */
    val easing: Boolean get() = active

    var residualX: Float = 0f
        private set

    var residualY: Float = 0f
        private set

    private var steppedFrame = 0L
    private val before = FloatArray(SAVED_FLOATS)
    private val beforeInts = IntArray(SAVED_INTS)
    private var beforeFlags = 0

    /** Steps to [pxX], [pxY] placed at [intX], [intY] for [frame], or redoes [frame]'s step when it already ran. */
    fun step(frame: Long, pxX: Float, pxY: Float, intX: Int, intY: Int) {
        if (frame != 0L && frame == steppedFrame) restore() else save()
        steppedFrame = frame
        step(pxX, pxY, intX, intY)
    }

    private fun save() {
        before[0] = lastPxX
        before[1] = lastPxY
        before[2] = lastRenderedX
        before[3] = lastRenderedY
        before[4] = leadInErrorX
        before[5] = leadInErrorY
        before[6] = residualX
        before[7] = residualY
        beforeInts[0] = settleFrames
        beforeInts[1] = leadInFrames
        beforeFlags = (if (initialized) 1 else 0) or (if (active) 2 else 0)
    }

    private fun restore() {
        lastPxX = before[0]
        lastPxY = before[1]
        lastRenderedX = before[2]
        lastRenderedY = before[3]
        leadInErrorX = before[4]
        leadInErrorY = before[5]
        residualX = before[6]
        residualY = before[7]
        settleFrames = beforeInts[0]
        leadInFrames = beforeInts[1]
        initialized = beforeFlags and 1 != 0
        active = beforeFlags and 2 != 0
    }

    private fun step(pxX: Float, pxY: Float, intX: Int, intY: Int) {
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
                } else if (leadInFrames in 1..SETTLE_FRAMES) {
                    leadInFrames += 1
                }
                settleFrames = 0
            }
            active && hasResidual -> {
                settleFrames = 0
                if (leadInFrames in 1..SETTLE_FRAMES) leadInFrames += 1
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
        if (settleFrames >= SETTLE_FRAMES) {
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
        val leadFactor = if (leadInFrames in 1 until SETTLE_FRAMES) {
            1f - smoothStep(leadInFrames.toFloat() / SETTLE_FRAMES)
        } else {
            0f
        }
        val baseX = rawResX + leadInErrorX * leadFactor
        val baseY = rawResY + leadInErrorY * leadFactor
        val settleFactor = if (settleFrames > 0) {
            1f - smoothStep(settleFrames.toFloat() / SETTLE_FRAMES)
        } else {
            1f
        }
        residualX = baseX * settleFactor
        residualY = baseY * settleFactor
    }

    private fun smoothStep(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private companion object {
        private const val MOTION_EPSILON = 1e-4f
        private const val FRACTION_EPSILON = 1e-3f
        private const val SETTLE_FRAMES = 8
        private const val SAVED_FLOATS = 8
        private const val SAVED_INTS = 2
    }
}

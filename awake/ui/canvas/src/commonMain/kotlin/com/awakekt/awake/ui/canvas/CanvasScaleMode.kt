/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.min

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

    /**
     * A unit is a whole number of steps of the screen's pixels: the largest that still leaves the
     * box's short side at least [CanvasScaler.referenceHeight] units, in steps of
     * [CanvasScaler.pixelsPerUnit] pixels, and never under one step.
     *
     * A HUD that keeps a minimum layout size and otherwise draws as large as the screen's density
     * allows, on pixel steps so art and strokes stay sharp: 1080 pixels high against a 720 reference
     * is one pixel per unit, 2160 is three. The short side, not the height, so a phone held either way
     * gets the same scale; a touch screen, read from closer, usually wants a smaller reference than a
     * desktop's. Ignores [CanvasScaler.snap], since its scale is already a step.
     */
    DensityStep,
}

/**
 * The screen pixels per unit [CanvasScaleMode.DensityStep] gives a box [width] by [height] dp at
 * [density] pixels per dp: the largest multiple of [step] that keeps the short side at least
 * [minShortSide] units, and at least one [step].
 */
internal fun densityStepPixelsPerUnit(width: Float, height: Float, density: Float, minShortSide: Float, step: Float): Float {
    val shortSidePixels = min(width, height) * density
    return when {
        !(step > 0f) || !step.isFinite() -> 1f
        !(shortSidePixels > 0f) || !(minShortSide > 0f) -> step
        else -> {
            // The nudge keeps a screen exactly on a step, such as 1440 over 720, from flooring to the one below.
            val steps = floor(shortSidePixels / (minShortSide * step) + STEP_EPSILON)
            if (steps >= 1f && steps.isFinite()) steps * step else step
        }
    }
}

private const val STEP_EPSILON = 1e-4f

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import kotlin.math.abs
import kotlin.math.round

/**
 * Pointer-to-value mapping for a horizontal slider, and the track geometry that makes it correct.
 *
 * **Why this is here and not in `:compose:foundation`.** None of it is Compose Foundation API --
 * Compose's `Slider` is Material -- and foundation's whole claim is that its surface mirrors
 * Compose's. A `sliderTrack`/`nearerThumb` pair sitting in there would be a local invention wearing
 * a parity layer's name, which is the thing `mirror-map.md` calls the dangerous category.
 *
 * It is also the layer that owns the numbers. The old `ui-headless` slider baked in `6.dp` and
 * `16.dp` with a comment naming their source -- "shadcn v4's thumb is size-4 (16px)" -- which is
 * `awake-ui-authoring`'s size rule being broken in place: an unowned default becomes the spec, the
 * way a Material-flavoured `40.dp` button fallback did. Here the numbers are shadcn's and this is
 * shadcn's module, so they are owned rather than inherited.
 */

/** Where a track starts and how wide it is, once inset for the thumb. */
data class ShadcnSliderTrack(val x: Float, val width: Float)

/**
 * Insets a slider's track by half a thumb on each side.
 *
 * Without this a thumb centred at fraction 0 or 1 extends half its own width past the widget's
 * bounds and is clipped by any parent that clips -- reported against the old slider as "knob cut
 * when reach start or end". Pointer mapping must use this same range, or the thumb lands somewhere
 * other than where the pointer is.
 *
 * [thumbSize] is a parameter rather than a constant so a variant can size its own thumb; pass `0f`
 * for a track with no thumb, which is the progress-bar case.
 */
fun shadcnSliderTrack(slotX: Float, slotWidth: Float, thumbSize: Float): ShadcnSliderTrack {
    val inset = thumbSize / 2f
    return ShadcnSliderTrack(
        x = slotX + inset,
        width = (slotWidth - inset * 2f).coerceAtLeast(0f),
    )
}

/**
 * The value a pointer at [pointerX] selects on [track].
 *
 * A zero-width track returns [min] rather than dividing by it: a slider measured to nothing still
 * has to answer, and answering with its minimum is the only choice that cannot surprise a caller.
 */
fun shadcnSliderValueAt(pointerX: Float, track: ShadcnSliderTrack, min: Float, max: Float): Float {
    if (track.width <= 0f) return min
    val fraction = ((pointerX - track.x) / track.width).coerceIn(0f, 1f)
    return min + fraction * (max - min)
}

/**
 * Where [value] sits along its range, as 0..1.
 *
 * An empty range is 0f, not a division by zero -- `min == max` is a legitimate degenerate slider,
 * and every position in it is the same position.
 */
fun shadcnSliderFraction(value: Float, min: Float, max: Float): Float {
    if (max <= min) return 0f
    return ((value - min) / (max - min)).coerceIn(0f, 1f)
}

/**
 * Snaps [value] to one of [steps] evenly spaced positions between [min] and [max].
 *
 * [steps] counts the positions *between* the ends, matching Compose's `Slider(steps = )`, so 0 is
 * continuous and 3 gives quarters. Returns [value] unchanged when [steps] is not positive.
 */
fun shadcnSliderSnap(value: Float, min: Float, max: Float, steps: Int): Float {
    if (steps <= 0 || max <= min) return value
    val intervals = steps + 1
    val snapped = round(shadcnSliderFraction(value, min, max) * intervals) / intervals
    return min + snapped * (max - min)
}

/**
 * How far one arrow-key press moves a slider's value.
 *
 * Matches Radix's keyboard step: a step interval when [steps] snaps the slider, otherwise 1% of
 * the range -- Radix's default `step` of 1 over its default 0-100 range. A collapsed range moves
 * by nothing rather than dividing by zero.
 */
fun shadcnSliderKeyStep(min: Float, max: Float, steps: Int): Float {
    val span = max - min
    if (span <= 0f) return 0f
    return if (steps > 0) span / (steps + 1) else span / 100f
}

/** Which of two thumbs a pointer at [pointerX] should move, for a range slider. */
fun shadcnNearerThumb(pointerX: Float, startX: Float, endX: Float): ShadcnSliderThumb =
    if (abs(pointerX - startX) <= abs(pointerX - endX)) {
        ShadcnSliderThumb.Start
    } else {
        ShadcnSliderThumb.End
    }

enum class ShadcnSliderThumb { Start, End }

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/**
 * How a number stepper -- minus and plus buttons around a number -- takes input, shared by every
 * stepper so a keyboard, a controller and a held button step the same way.
 */
object NumberStepping {
    /** How long a button is held before it starts repeating, in seconds. */
    const val REPEAT_DELAY_SECONDS: Float = 0.4f

    /** How often a held button repeats once it has started, in seconds. */
    const val REPEAT_INTERVAL_SECONDS: Float = 0.08f

    /** How many steps Page Up and Page Down take at once. */
    const val PAGE_STEPS: Int = 10

    /**
     * How many steps [key] takes: up or right one more, down or left one fewer, Page Up and Page
     * Down [PAGE_STEPS]; 0 for any other key. Home and End jump to a bound, which [boundOf] answers.
     */
    fun stepsOf(key: Key): Int = when (key) {
        Key.ArrowUp, Key.ArrowRight -> 1
        Key.ArrowDown, Key.ArrowLeft -> -1
        Key.PageUp -> PAGE_STEPS
        Key.PageDown -> -PAGE_STEPS
        else -> 0
    }

    /** How many steps a controller's [button] takes: the pad's up or right one more, down or left one fewer, the shoulders [PAGE_STEPS]. */
    fun stepsOf(button: GamepadButton): Int = when (button) {
        GamepadButton.DpadUp, GamepadButton.DpadRight -> 1
        GamepadButton.DpadDown, GamepadButton.DpadLeft -> -1
        GamepadButton.RightShoulder -> PAGE_STEPS
        GamepadButton.LeftShoulder -> -PAGE_STEPS
        else -> 0
    }

    /** -1 when [key] jumps to the lower bound (Home), 1 to the upper (End), 0 otherwise. */
    fun boundOf(key: Key): Int = when (key) {
        Key.Home -> -1
        Key.End -> 1
        else -> 0
    }

    /**
     * How many repeats a button held [heldSeconds] has made: none before [delaySeconds], then one,
     * and one more each [intervalSeconds]. A caller keeps how many it has applied and applies the
     * rest, so a slow frame catches up instead of losing steps.
     */
    fun repeatsAfter(
        heldSeconds: Float,
        delaySeconds: Float = REPEAT_DELAY_SECONDS,
        intervalSeconds: Float = REPEAT_INTERVAL_SECONDS,
    ): Int {
        // A hold is summed from frame deltas, so it lands a rounding error short of a repeat it has reached.
        val held = heldSeconds + TIME_TOLERANCE
        return when {
            held < delaySeconds -> 0
            intervalSeconds <= 0f -> 1
            else -> 1 + floor((held - delaySeconds) / intervalSeconds).toInt()
        }
    }

    /**
     * [value] moved by [steps] of [step], on the grid of [step]s from [min], and kept between [min]
     * and [max]. A [step] of 0 or less moves it by whole numbers.
     */
    fun stepped(value: Float, steps: Int, min: Float, max: Float, step: Float): Float {
        val size = if (step > 0f) step else 1f
        val start = if (min.isFinite()) min else 0f
        val onGrid = start + round((value - start) / size) * size
        return clamp(onGrid + steps * size, min, max)
    }

    /**
     * [value] written with [decimals] digits after the point, rounded: `3`, `0.25`, `-1.50`. What a
     * stepper shows, the same on every platform.
     */
    fun format(value: Float, decimals: Int = 0): String {
        if (!value.isFinite()) return value.toString()

        val places = decimals.coerceIn(0, MAX_DECIMALS)
        var scale = 1L
        repeat(places) { scale *= 10L }
        val scaled = round(abs(value.toDouble()) * scale).toLong()
        val sign = if (value < 0f && scaled != 0L) "-" else ""
        return if (places == 0) "$sign$scaled" else "$sign${scaled / scale}.${(scaled % scale).toString().padStart(places, '0')}"
    }

    /** Seconds a summed hold may fall short of a repeat by and still make it. */
    private const val TIME_TOLERANCE: Float = 1e-4f

    /** The most digits after the point [format] writes. */
    const val MAX_DECIMALS: Int = 6

    /** [value] held between [min] and [max], or [min] when the range is backwards; NaN reads as [min]. */
    fun clamp(value: Float, min: Float, max: Float): Float = when {
        value.isNaN() || max < min -> min
        else -> value.coerceIn(min, max)
    }
}

/**
 * A stepper button's hold: one step the frame it goes down, then [NumberStepping.repeatsAfter]'s
 * repeats while it stays down. Kept per button and advanced once a frame; it allocates nothing.
 */
class HeldStep {
    private var heldSeconds = -1f
    private var applied = 0

    /**
     * The steps to take this frame for a button that is [pressed], [deltaSeconds] after the last
     * frame: 1 the frame it goes down, the repeats due since while it is held, 0 once it is let go.
     */
    fun advance(
        pressed: Boolean,
        deltaSeconds: Float,
        delaySeconds: Float = NumberStepping.REPEAT_DELAY_SECONDS,
        intervalSeconds: Float = NumberStepping.REPEAT_INTERVAL_SECONDS,
    ): Int {
        val steps = when {
            !pressed -> {
                heldSeconds = -1f
                applied = 0
                0
            }
            heldSeconds < 0f -> {
                heldSeconds = 0f
                1
            }
            else -> {
                heldSeconds += deltaSeconds
                val due = NumberStepping.repeatsAfter(heldSeconds, delaySeconds, intervalSeconds)
                (due - applied).also { applied = due }
            }
        }
        return steps
    }
}

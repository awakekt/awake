/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

/**
 * Linearly interpolates between [start] and [stop] by [fraction].
 *
 * Formula: `start + (stop - start) * fraction`
 *
 * @param start The initial value when [fraction] is 0.0.
 * @param stop The target value when [fraction] is 1.0.
 * @param fraction The interpolation factor (typically between 0.0 and 1.0).
 * @return The interpolated value.
 */
inline fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * Linearly interpolates between [start] and [stop] by [fraction] with double precision.
 *
 * Formula: `start + (stop - start) * fraction`
 *
 * @param start The initial value when [fraction] is 0.0.
 * @param stop The target value when [fraction] is 1.0.
 * @param fraction The interpolation factor (typically between 0.0 and 1.0).
 * @return The interpolated value.
 */
inline fun lerp(start: Double, stop: Double, fraction: Double): Double =
    start + (stop - start) * fraction

/**
 * Clamps [value] between [min] and [max].
 *
 * @param value The value to clamp.
 * @param min The lower bound.
 * @param max The upper bound.
 * @return [min] if value < min, [max] if value > max, else [value].
 */
inline fun clamp(value: Float, min: Float, max: Float): Float =
    value.coerceIn(min, max)

/**
 * Clamps [value] between [min] and [max] with double precision.
 *
 * @param value The value to clamp.
 * @param min The lower bound.
 * @param max The upper bound.
 * @return [min] if value < min, [max] if value > max, else [value].
 */
inline fun clamp(value: Double, min: Double, max: Double): Double =
    value.coerceIn(min, max)

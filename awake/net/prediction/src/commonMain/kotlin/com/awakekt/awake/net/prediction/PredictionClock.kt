/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

/**
 * Monotonic clock in seconds for client prediction and snapshot interpolation.
 *
 * Using a primitive-returning `fun interface` instead of `() -> Double` avoids boxing a
 * `java.lang.Double` on every fixed step or render frame on the JVM once call sites become
 * polymorphic.
 */
fun interface PredictionClock {
    /** Returns the current monotonic timestamp in seconds. */
    fun nowSeconds(): Double
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EasingTest {

    @Test
    fun allCurvesStartAtZeroAndEndAtOne() {
        for (curve in EasingCurve.entries) {
            assertEquals(0f, curve.evaluate(0f), 1e-4f, "${curve.name} at 0")
            assertEquals(1f, curve.evaluate(1f), 1e-4f, "${curve.name} at 1")
            // Out-of-bounds inputs clamp safely.
            assertEquals(0f, curve.evaluate(-0.5f), 1e-4f, "${curve.name} below 0")
            assertEquals(1f, curve.evaluate(1.5f), 1e-4f, "${curve.name} above 1")
        }
    }

    @Test
    fun symmetricCurvesPassThroughHalfAtMidpoint() {
        val symmetric = listOf(
            EasingCurve.Linear,
            EasingCurve.SmoothStep,
            EasingCurve.SmootherStep,
            EasingCurve.EaseInOutQuad,
            EasingCurve.EaseInOutCubic,
            EasingCurve.EaseInOutSine,
        )
        for (curve in symmetric) {
            assertEquals(0.5f, curve.evaluate(0.5f), 1e-4f, "${curve.name} midpoint")
        }
        assertTrue(EasingCurve.EaseOutBack.evaluate(0.65f) > 1f, "EaseOutBack should overshoot past 1")
    }

    @Test
    fun cubicBezierMatchesLinearAndEaseInOutBehavior() {
        assertEquals(0f, cubicBezierEasing(0f, 0.42f, 0f, 0.58f, 1f), 1e-4f)
        assertEquals(0.5f, cubicBezierEasing(0.5f, 0.42f, 0f, 0.58f, 1f), 1e-3f)
        assertEquals(1f, cubicBezierEasing(1f, 0.42f, 0f, 0.58f, 1f), 1e-4f)
        // Linear diagonal control handles.
        assertEquals(0.25f, cubicBezierEasing(0.25f, 0.25f, 0.25f, 0.75f, 0.75f), 1e-3f)
    }
}

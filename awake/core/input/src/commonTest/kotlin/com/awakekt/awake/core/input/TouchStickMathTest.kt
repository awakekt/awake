/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.test.Test
import kotlin.test.assertEquals

class TouchStickMathTest {
    private val out = StickVector()

    @Test
    fun deadZoneIsZeroAndTheRestStretches() {
        TouchStickMath.deflect(10f, 0f, 100f, 0.2f, 1, 0, out)
        assertEquals(0f, out.x)
        TouchStickMath.deflect(60f, 0f, 100f, 0.2f, 1, 0, out)
        assertEquals(0.5f, out.x, 0.001f)
    }

    @Test
    fun theOutputIsClampedToTheUnitCircle() {
        TouchStickMath.deflect(0f, 500f, 100f, 0f, 1, 0, out)
        assertEquals(1f, out.y, 0.001f)
    }

    @Test
    fun responseCurvesAndDirectionsApply() {
        TouchStickMath.deflect(50f, 0f, 100f, 0f, 2, 0, out)
        assertEquals(0.25f, out.x, 0.001f)
        TouchStickMath.deflect(50f, 10f, 100f, 0f, 1, 4, out)
        assertEquals(0f, out.y, 0.001f)
    }

    @Test
    fun aimIsZeroInsideTheDeadZoneThenUnitOrGround() {
        TouchStickMath.aim(5f, 0f, 100f, 0.2f, false, out)
        assertEquals(true, out.isZero)
        TouchStickMath.aim(50f, 0f, 100f, 0.2f, false, out)
        assertEquals(1f, out.x, 0.001f)
        TouchStickMath.aim(50f, 0f, 100f, 0.2f, true, out)
        assertEquals(0.5f, out.x, 0.001f)
    }
}

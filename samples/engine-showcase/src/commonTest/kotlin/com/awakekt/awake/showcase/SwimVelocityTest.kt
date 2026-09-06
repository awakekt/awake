/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.showcase.examples.SwimMotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What holding each key does to a swimmer, and what holding nothing does.
 *
 * The last one is the interesting case: a swimmer that hangs motionless in the water reads as a
 * bug, and a swimmer that falls at full gravity is not swimming at all. Both are easy to write by
 * accident, and neither is visible from a screenshot of someone holding a key.
 */
class SwimVelocityTest {

    @Test
    fun risingAndDivingAreOppositeAndEqual() {
        assertEquals(-SwimMotion.velocity(up = true, down = false), SwimMotion.velocity(up = false, down = true))
        assertTrue(SwimMotion.velocity(up = true, down = false) > 0f, "up should rise")
    }

    @Test
    fun holdingNothingSinksSlowlyRatherThanHangingOrFalling() {
        val drift = SwimMotion.velocity(up = false, down = false)

        // Not zero: hanging motionless in water looks broken. Not full gravity either: that is
        // simply falling, and holding Space would read as flying rather than as swimming.
        assertTrue(drift < 0f, "a swimmer holding nothing should sink: $drift")
        assertTrue(drift > -2f, "a swimmer holding nothing is falling, not swimming: $drift")
    }

    @Test
    fun risingBeatsTheSinkSoHoldingSpaceActuallyGoesUp() {
        // The pair that decides whether the control works at all: if the drift were stronger than
        // the swim speed, holding Space would still sink and the water would feel like tar.
        assertTrue(
            SwimMotion.velocity(up = true, down = false) > -SwimMotion.velocity(up = false, down = false),
            "swimming up is weaker than the sink it has to overcome",
        )
    }

    @Test
    fun holdingBothPicksOneRatherThanCancellingToAHang() {
        // Ctrl is crouch on land and dive in water, so both keys down is reachable by accident.
        // Cancelling to zero would be the motionless hang the drift exists to avoid.
        assertTrue(SwimMotion.velocity(up = true, down = true) != 0f)
    }
}

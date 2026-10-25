/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.core.transform.Transform
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FiniteTurnTest {
    // Daemon workers and a wall-clock bound keep the old infinite loop from hanging the test JVM.
    private fun boundedTurn(yaw: Float, x: Float = 1f, z: Float = 0f, speed: Float = 1f, delta: Float = 0.1f): Float {
        val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "finite-turn-regression").apply { isDaemon = true } }
        return try {
            worker.submit<Float> {
                val transform = Transform().apply { rotation.y = yaw }
                MovementControl().apply { turnSpeed = speed }.turnToward(transform, x, z, delta)
                transform.rotation.y
            }.get(2, TimeUnit.SECONDS)
        } finally {
            worker.shutdownNow()
        }
    }

    @Test
    fun invalidYawReturnsWithoutChangingItsBits() {
        for (yaw in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(yaw.toBits(), boundedTurn(yaw).toBits())
        }
    }

    @Test
    fun invalidDirectionSpeedOrTimeLeavesFiniteYawAlone() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0.5f, boundedTurn(0.5f, x = value))
            assertEquals(0.5f, boundedTurn(0.5f, z = value))
            assertEquals(0.5f, boundedTurn(0.5f, speed = value))
            assertEquals(0.5f, boundedTurn(0.5f, delta = value))
        }
        for (value in listOf(0f, -1f)) {
            assertEquals(0.5f, boundedTurn(0.5f, speed = value))
            assertEquals(0.5f, boundedTurn(0.5f, delta = value))
        }
        assertEquals(0.5f, boundedTurn(0.5f, x = 0f, z = 0f))
    }

    @Test
    fun hugeFiniteYawReturnsFiniteAndCanReachTheTarget() {
        for (yaw in listOf(1e30f, -1e30f, Float.MAX_VALUE, -Float.MAX_VALUE)) {
            assertTrue(boundedTurn(yaw).isFinite())
            assertEquals((PI / 2).toFloat(), boundedTurn(yaw, speed = Float.MAX_VALUE, delta = Float.MAX_VALUE))
        }
    }

    @Test
    fun ordinaryTurnsUseTheShortArcAndRespectTheStep() {
        assertEquals(0.1f, boundedTurn(0f), 1e-5f)
        assertEquals(-0.1f, boundedTurn(0f, x = -1f), 1e-5f)
        val yaw = (PI - 0.1).toFloat()
        assertEquals((PI - 0.05).toFloat(), boundedTurn(yaw, x = -0.1f, z = -1f, delta = 0.05f), 1e-5f)
        assertEquals((PI / 2).toFloat(), boundedTurn(0f, speed = 100f), 1e-5f)
    }
}

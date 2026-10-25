/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.character.CharacterConfig
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FiniteBlendTest {
    private fun boundedBlend(previous: Float, current: Float, alpha: Float = 0.5f): Transform {
        val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "finite-blend-regression").apply { isDaemon = true } }
        return try {
            worker.submit<Transform> {
                val character = CharacterController(CharacterConfig(CapsuleShape(0.5f, 0.3f)), 0f, 0f)
                val transform = Transform().apply { rotation.y = previous; position.x = 0f }
                character.record(transform)
                transform.rotation.y = current
                transform.position.x = 2f
                character.record(transform)
                transform.rotation.y = 0.25f
                character.blendInto(transform, alpha)
                character.restoreSteppedYaw(transform)
                // Restore only affects successful blends; inspect interpolation separately below.
                character.blendInto(transform, alpha)
                transform
            }.get(2, TimeUnit.SECONDS)
        } finally {
            worker.shutdownNow()
        }
    }

    @Test
    fun invalidSteppedYawReturnsAndLeavesRenderedYawFinite() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for ((previous, current) in listOf(value to 1f, 1f to value)) {
                val result = boundedBlend(previous, current)
                assertEquals(0.25f, result.rotation.y)
                assertEquals(1f, result.position.x)
            }
        }
    }

    @Test
    fun hugeFiniteSteppedYawsRemainBounded() {
        for (value in listOf(1e30f, -1e30f, Float.MAX_VALUE, -Float.MAX_VALUE)) {
            val result = boundedBlend(value, -value)
            assertTrue(result.rotation.y.isFinite())
            assertTrue(abs(result.rotation.y) <= 3 * PI)
        }
    }

    @Test
    fun wrapBoundaryUsesShortArcAndInvalidAlphaLeavesPoseAlone() {
        assertEquals(PI.toFloat(), boundedBlend((PI - 0.1).toFloat(), (-PI + 0.1).toFloat()).rotation.y, 1e-5f)
        for (alpha in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f)) {
            val result = boundedBlend(0f, 1f, alpha)
            assertEquals(0.25f, result.rotation.y)
            assertEquals(2f, result.position.x)
        }
    }
}

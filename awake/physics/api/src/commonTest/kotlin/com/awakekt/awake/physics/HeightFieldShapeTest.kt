/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HeightFieldShapeTest {
    @Test
    fun heightAtUsesRowMajorCoordinates() {
        val shape = HeightFieldShape(
            heights = FloatArray(16) { it.toFloat() },
            sampleCount = 4,
            scale = Vec3f(2f, 3f, 4f),
        )

        assertEquals(9f, shape.heightAt(x = 1, z = 2))
    }

    @Test
    fun rejectsInvalidDimensionsAndValues() {
        assertFailsWith<IllegalArgumentException> {
            HeightFieldShape(FloatArray(9), sampleCount = 3, scale = Vec3f(1f, 1f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            HeightFieldShape(FloatArray(15), sampleCount = 4, scale = Vec3f(1f, 1f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            HeightFieldShape(FloatArray(16), sampleCount = 4, scale = Vec3f(1f, 0f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            HeightFieldShape(FloatArray(16) { Float.NaN }, sampleCount = 4, scale = Vec3f(1f, 1f, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            HeightFieldShape(FloatArray(16), sampleCount = 4, scale = Vec3f(Float.POSITIVE_INFINITY, 1f, 1f))
        }
    }

    @Test
    fun onlyStaticBodiesAreSupported() {
        val shape = HeightFieldShape(FloatArray(16), sampleCount = 4, scale = Vec3f(1f, 1f, 1f))

        assertFailsWith<PhysicsCapabilityException> {
            shape.requireSupportedMotionType(MotionType.DYNAMIC)
        }
        assertFailsWith<PhysicsCapabilityException> {
            shape.requireSupportedMotionType(MotionType.KINEMATIC)
        }
    }
}

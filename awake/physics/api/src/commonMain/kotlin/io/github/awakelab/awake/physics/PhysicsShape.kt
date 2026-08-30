/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics

import io.github.awakelab.awake.core.math.Vec3f

/** Backend-neutral collision shape description -- a plain data description, not a live
 * native handle, so `PhysicsWorld.createBody` (the only place a [PhysicsShape] is consumed)
 * can build whatever native shape object its own backend needs (jolt-jni's `BoxShape`/
 * `SphereShape`, eventually JoltC's C shape structs) without this module knowing any of
 * that exists. */
sealed interface PhysicsShape

/** [halfExtents] matches Jolt Physics' own `BoxShape` convention (half the box's total
 * width/height/depth along each axis), not a full-extent size. */
data class BoxShape(val halfExtents: Vec3f) : PhysicsShape

data class SphereShape(val radius: Float) : PhysicsShape

/**
 * A square, row-major heightfield collision surface. The local body origin is sample (0, 0):
 * its world position is [PhysicsWorld.createBody]'s [PhysicsWorld.createBody] position. A
 * sample at `(x, z)` is located at `(x * scale.x, heights[z * sampleCount + x] * scale.y,
 * z * scale.z)` relative to that origin.
 *
 * The array is intentionally not copied: constructing a terrain-sized collider must not make
 * an implicit second terrain-sized allocation. Callers must therefore not mutate [heights]
 * after passing this value to a [PhysicsWorld]. It is not suitable as a structural cache key;
 * Kotlin arrays use referential equality in data classes.
 */
data class HeightFieldShape(
    val heights: FloatArray,
    val sampleCount: Int,
    val scale: Vec3f,
) : PhysicsShape {
    init {
        require(sampleCount >= MIN_SAMPLE_COUNT) {
            "sampleCount must be at least $MIN_SAMPLE_COUNT for a Jolt heightfield: $sampleCount"
        }
        val expectedHeightCount = sampleCount.toLong() * sampleCount
        require(expectedHeightCount <= Int.MAX_VALUE && heights.size == expectedHeightCount.toInt()) {
            "heights must contain sampleCount * sampleCount values: " +
                "expected $expectedHeightCount, got ${heights.size}"
        }
        require(scale.x.isFinite() && scale.y.isFinite() && scale.z.isFinite()) {
            "scale must contain only finite values: $scale"
        }
        require(scale.x > 0f && scale.y > 0f && scale.z > 0f) {
            "scale components must be positive: $scale"
        }
        require(heights.all(Float::isFinite)) { "heights must contain only finite values" }
    }

    /** Height sample at [x], [z], using the documented row-major layout. */
    fun heightAt(x: Int, z: Int): Float {
        require(x in 0 until sampleCount) { "x is outside the heightfield: $x" }
        require(z in 0 until sampleCount) { "z is outside the heightfield: $z" }
        return heights[z * sampleCount + x]
    }

    /** Jolt heightfields cannot be dynamic or kinematic bodies. */
    fun requireSupportedMotionType(motionType: MotionType) {
        if (motionType != MotionType.STATIC) {
            throw PhysicsCapabilityException("HeightFieldShape supports STATIC motion only, not $motionType")
        }
    }

    private companion object {
        // Jolt's default block size is two samples; it requires at least two blocks per edge.
        const val MIN_SAMPLE_COUNT = 4
    }
}

/** A requested physics capability is not available on the selected backend or body type. */
class PhysicsCapabilityException(message: String) : UnsupportedOperationException(message)

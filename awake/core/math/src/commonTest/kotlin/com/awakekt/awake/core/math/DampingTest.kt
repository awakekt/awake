/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DampingTest {

    // damp

    @Test
    fun dampGivesTheSameResultForOneLongStepAsForTwoShortOnes() {
        val oneStep = damp(0f, 10f, HALF_LIFE, 1f / 30f)
        val twoSteps = damp(damp(0f, 10f, HALF_LIFE, 1f / 60f), 10f, HALF_LIFE, 1f / 60f)
        assertEquals(oneStep, twoSteps, EPSILON)
    }

    @Test
    fun dampIsFrameRateIndependentOverASecond() {
        val at30 = (1..30).fold(0f) { value, _ -> damp(value, 10f, HALF_LIFE, 1f / 30f) }
        val at240 = (1..240).fold(0f) { value, _ -> damp(value, 10f, HALF_LIFE, 1f / 240f) }
        assertEquals(at30, at240, EPSILON)
    }

    @Test
    fun dampClosesHalfTheGapAfterOneHalfLife() {
        assertEquals(5f, damp(0f, 10f, HALF_LIFE, HALF_LIFE), EPSILON)
        assertEquals(7.5f, damp(0f, 10f, HALF_LIFE, 2f * HALF_LIFE), EPSILON)
        assertEquals(-5f, damp(0f, -10f, HALF_LIFE, HALF_LIFE), EPSILON)
    }

    @Test
    fun dampNeverOvershootsEvenOnAHugeStep() {
        for (dt in listOf(1f / 240f, 1f / 60f, 0.5f, 10f, 1_000f)) {
            var value = 0f
            repeat(50) {
                val next = damp(value, 10f, HALF_LIFE, dt)
                assertTrue(next >= value && next <= 10f, "dt=$dt stepped from $value to $next")
                value = next
            }
        }
    }

    @Test
    fun dampSnapsWhenHalfLifeIsNotPositiveAndHoldsWhenDtIsNotPositive() {
        assertEquals(10f, damp(0f, 10f, 0f, 1f / 60f))
        assertEquals(10f, damp(0f, 10f, -1f, 1f / 60f))
        assertEquals(3f, damp(3f, 10f, HALF_LIFE, 0f))
        assertEquals(3f, damp(3f, 10f, HALF_LIFE, -1f / 60f))
    }

    @Test
    fun doubleDampMatchesTheFloatContract() {
        assertEquals(5.0, damp(0.0, 10.0, 0.25, 0.25), 1e-9)
        val oneStep = damp(0.0, 10.0, 0.25, 1.0 / 30.0)
        val twoSteps = damp(damp(0.0, 10.0, 0.25, 1.0 / 60.0), 10.0, 0.25, 1.0 / 60.0)
        assertEquals(oneStep, twoSteps, 1e-9)
        assertEquals(10.0, damp(0.0, 10.0, 0.0, 1.0 / 60.0))
        assertEquals(3.0, damp(3.0, 10.0, 0.25, 0.0))
    }

    @Test
    fun halfLifeForRateReproducesTheHandWrittenExponentialLerp() {
        val rate = 10f
        val dt = 1f / 60f
        val handWritten = lerp(2f, 7f, 1f - exp(-rate * dt))
        assertEquals(handWritten, damp(2f, 7f, halfLifeForRate(rate), dt), EPSILON)
        assertEquals(1f - exp(-rate * dt), dampingFraction(halfLifeForRate(rate), dt), EPSILON)
    }

    @Test
    fun dampingFractionCoversItsEdgeCases() {
        assertEquals(0.5f, dampingFraction(HALF_LIFE, HALF_LIFE), EPSILON)
        assertEquals(1f, dampingFraction(0f, 1f / 60f))
        assertEquals(0f, dampingFraction(HALF_LIFE, 0f))
    }

    @Test
    fun vectorDampMutatesInPlaceAndMatchesTheScalarForm() {
        val eye = Vec3f(0f, 4f, -8f)
        val target = Vec3f(10f, 0f, 8f)
        val returned = eye.damp(target, HALF_LIFE, HALF_LIFE)
        assertSame(eye, returned)
        assertEquals(5f, eye.x, EPSILON)
        assertEquals(2f, eye.y, EPSILON)
        assertEquals(0f, eye.z, EPSILON)
        assertEquals(Vec3f(10f, 0f, 8f), target, "The target must not be modified.")
    }

    @Test
    fun vectorDampEdgeCases() {
        val target = Vec3f(1f, 2f, 3f)
        assertEquals(Vec3f(1f, 2f, 3f), Vec3f(0f, 0f, 0f).damp(target, 0f, 1f / 60f))
        assertEquals(Vec3f(5f, 5f, 5f), Vec3f(5f, 5f, 5f).damp(target, HALF_LIFE, 0f))
    }

    @Test
    fun dampAngleTurnsTheShortWayRound() {
        val from = degrees(350f)
        val to = degrees(10f)
        // Half of the 20 degree forward gap: 360, not 180.
        assertEquals(degrees(360f), dampAngle(from, to, HALF_LIFE, HALF_LIFE), EPSILON)
        // Across the -pi/pi seam: from -170 to 170 goes back through -180.
        assertEquals(degrees(-180f), dampAngle(degrees(-170f), degrees(170f), HALF_LIFE, HALF_LIFE), EPSILON)
        // A target several turns away is the same direction.
        assertEquals(degrees(5f), dampAngle(0f, degrees(10f + 720f), HALF_LIFE, HALF_LIFE), EPSILON)
    }

    @Test
    fun dampAngleEdgeCases() {
        assertEquals(degrees(360f), dampAngle(degrees(350f), degrees(0f), 0f, 1f / 60f), EPSILON)
        assertEquals(degrees(350f), dampAngle(degrees(350f), degrees(0f), HALF_LIFE, 0f), EPSILON)
    }

    // smoothDamp

    @Test
    fun smoothDampNeverOvershootsAcrossSmoothTimesAndFrameRates() {
        for (smoothTime in SMOOTH_TIMES) {
            for (dt in FRAME_STEPS) {
                val spring = DampedFloat(0f)
                var previous = 0f
                repeat(2_000) {
                    val next = spring.smoothDamp(10f, smoothTime, dt)
                    assertTrue(next <= 10f, "smoothTime=$smoothTime dt=$dt overshot to $next")
                    assertTrue(next >= previous, "smoothTime=$smoothTime dt=$dt went backwards")
                    previous = next
                }
            }
        }
    }

    @Test
    fun smoothDampConverges() {
        for (smoothTime in SMOOTH_TIMES) {
            val spring = DampedFloat(-3f)
            var time = 0f
            while (time < smoothTime * 10f) {
                spring.smoothDamp(7f, smoothTime, 1f / 60f)
                time += 1f / 60f
            }
            assertEquals(7f, spring.value, 1e-2f, "smoothTime=$smoothTime")
            assertEquals(0f, spring.velocity, 1e-1f, "smoothTime=$smoothTime")
        }
    }

    @Test
    fun smoothDampIsFrameRateIndependent() {
        val at30 = DampedFloat(0f)
        repeat(30) { at30.smoothDamp(10f, 0.4f, 1f / 30f) }
        val at240 = DampedFloat(0f)
        repeat(240) { at240.smoothDamp(10f, 0.4f, 1f / 240f) }
        assertEquals(at30.value, at240.value, 1e-3f)
        assertEquals(at30.velocity, at240.velocity, 1e-3f)
    }

    @Test
    fun smoothDampEasesInFromRest() {
        val spring = DampedFloat(0f)
        val first = spring.smoothDamp(10f, 1f, 1f / 60f)
        val second = spring.smoothDamp(10f, 1f, 1f / 60f) - first
        assertTrue(second > first, "A spring starting at rest should speed up: $first then $second")
    }

    @Test
    fun smoothDampRespectsTheMaximumSpeed() {
        val maxSpeed = 5f
        for (dt in FRAME_STEPS) {
            val spring = DampedFloat(0f)
            repeat(400) {
                val before = spring.value
                spring.smoothDamp(1_000f, 0.5f, dt, maxSpeed)
                assertTrue(spring.value - before <= maxSpeed * dt + 1e-3f, "dt=$dt moved ${spring.value - before}")
                assertTrue(abs(spring.velocity) <= maxSpeed + 1e-4f, "dt=$dt velocity ${spring.velocity}")
            }
        }
    }

    @Test
    fun smoothDampEdgeCases() {
        val spring = DampedFloat(2f, velocity = 3f)
        assertEquals(2f, spring.smoothDamp(10f, 0.5f, 0f))
        assertEquals(3f, spring.velocity)
        assertEquals(10f, spring.smoothDamp(10f, 0f, 1f / 60f))
        assertEquals(0f, spring.velocity)
        spring.velocity = 4f
        assertEquals(-1f, spring.snapTo(-1f))
        assertEquals(0f, spring.velocity)
    }

    @Test
    fun vectorSmoothDampMovesInPlaceWithoutOvershootOrSpeeding() {
        val eye = Vec3f(0f, 0f, 0f)
        val spring = DampedVec3(eye)
        val target = Vec3f(30f, -40f, 0f)
        val maxSpeed = 10f
        val dt = 1f / 60f
        var previousGap = 50f
        repeat(1_200) {
            val bx = eye.x
            val by = eye.y
            val bz = eye.z
            assertSame(eye, spring.smoothDamp(target, 0.3f, dt, maxSpeed))
            val moved = Vec3f(eye.x - bx, eye.y - by, eye.z - bz).length3()
            assertTrue(moved <= maxSpeed * dt + 1e-4f, "moved $moved in one step")
            assertTrue(spring.velocity.length3() <= maxSpeed + 1e-3f)
            val gap = eye.distanceTo(target)
            assertTrue(gap <= previousGap + 1e-4f, "The gap grew from $previousGap to $gap")
            // Starting at rest, the path is the straight line to the target: never past it.
            assertTrue(eye.x <= 30f + 1e-4f && eye.y >= -40f - 1e-4f)
            previousGap = gap
        }
        assertEquals(0f, previousGap, 1e-3f)
        assertEquals(Vec3f(30f, -40f, 0f), target, "The target must not be modified.")
    }

    @Test
    fun vectorSmoothDampIsFrameRateIndependent() {
        val target = Vec3f(3f, 6f, -9f)
        val at30 = DampedVec3()
        repeat(15) { at30.smoothDamp(target, 0.25f, 1f / 30f) }
        val at120 = DampedVec3()
        repeat(60) { at120.smoothDamp(target, 0.25f, 1f / 120f) }
        assertEquals(at30.value.x, at120.value.x, 1e-3f)
        assertEquals(at30.value.y, at120.value.y, 1e-3f)
        assertEquals(at30.value.z, at120.value.z, 1e-3f)
    }

    @Test
    fun vectorSmoothDampEdgeCases() {
        val spring = DampedVec3(Vec3f(1f, 1f, 1f), Vec3f(2f, 0f, 0f))
        spring.smoothDamp(Vec3f(5f, 5f, 5f), 0.5f, 0f)
        assertEquals(Vec3f(1f, 1f, 1f), spring.value)
        assertEquals(Vec3f(2f, 0f, 0f), spring.velocity)
        spring.smoothDamp(Vec3f(5f, 5f, 5f), 0f, 1f / 60f)
        assertEquals(Vec3f(5f, 5f, 5f), spring.value)
        assertEquals(Vec3f(0f, 0f, 0f), spring.velocity)
    }

    // ErrorOffset

    @Test
    fun errorOffsetDecaysToSettled() {
        val offset = ErrorOffset(halfLife = 0.1f)
        offset.add(1f, -2f, 0.5f)
        assertFalse(offset.isSettled(0.01f))
        offset.update(0.1f)
        assertEquals(0.5f, offset.x, EPSILON)
        assertEquals(-1f, offset.y, EPSILON)
        assertEquals(0.25f, offset.z, EPSILON)
        repeat(60) { offset.update(1f / 60f) }
        // 1.1 s is 11 half-lives: about 1/2048 of the original 2.3 length.
        assertTrue(offset.isSettled(0.01f))
        offset.clear()
        assertEquals(0f, offset.x)
        assertEquals(0f, offset.y)
        assertEquals(0f, offset.z)
    }

    @Test
    fun errorOffsetAccumulatesRepeatedCorrections() {
        val offset = ErrorOffset(halfLife = 0.2f)
        offset.add(1f, 0f, 0f)
        offset.add(0.5f, 2f, 0f)
        assertEquals(1.5f, offset.x)
        assertEquals(2f, offset.y)
        offset.update(0.2f)
        offset.add(-0.75f, 0f, 3f)
        assertEquals(0f, offset.x, EPSILON)
        assertEquals(1f, offset.y, EPSILON)
        assertEquals(3f, offset.z, EPSILON)
    }

    @Test
    fun errorOffsetIsFrameRateIndependent() {
        val at30 = ErrorOffset(0.15f).apply { add(4f, -4f, 2f) }
        repeat(15) { at30.update(1f / 30f) }
        val at144 = ErrorOffset(0.15f).apply { add(4f, -4f, 2f) }
        repeat(72) { at144.update(1f / 144f) }
        assertEquals(at30.x, at144.x, EPSILON)
        assertEquals(at30.y, at144.y, EPSILON)
        assertEquals(at30.z, at144.z, EPSILON)
    }

    @Test
    fun errorOffsetEdgeCases() {
        val holding = ErrorOffset(0.1f).apply { add(1f, 1f, 1f) }
        holding.update(0f)
        assertEquals(1f, holding.x)
        val snapping = ErrorOffset(0f).apply { add(1f, 1f, 1f) }
        snapping.update(1f / 60f)
        assertTrue(snapping.isSettled(0f))
    }

    private fun degrees(value: Float): Float = value * PI.toFloat() / 180f

    private companion object {
        const val HALF_LIFE = 0.25f
        const val EPSILON = 1e-4f
        val SMOOTH_TIMES = listOf(0.02f, 0.1f, 0.3f, 1f, 3f)
        val FRAME_STEPS = listOf(1f / 240f, 1f / 144f, 1f / 60f, 1f / 30f, 0.1f, 0.5f)
    }
}

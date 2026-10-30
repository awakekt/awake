/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [FollowerSizing]'s rules: whole buckets of size with hysteresis, and snapping while slow. */
class FollowerSizingTest {
    @Test
    fun wholeOctavesAreExactBuckets() {
        assertEquals(1f, FollowerSizing.scaleOf(FollowerSizing.bucketOf(1f, FollowerSizing.NO_BUCKET)))
        assertEquals(0.5f, FollowerSizing.scaleOf(FollowerSizing.bucketOf(0.5f, FollowerSizing.NO_BUCKET)))
        assertEquals(2f, FollowerSizing.scaleOf(FollowerSizing.bucketOf(2f, FollowerSizing.NO_BUCKET)))
    }

    @Test
    fun aBucketIsWithinItsHalfStepOfTheCurvesScale() {
        var scale = 0.5f
        while (scale <= 2f) {
            val drawn = FollowerSizing.scaleOf(FollowerSizing.bucketOf(scale, FollowerSizing.NO_BUCKET))
            assertTrue(abs(drawn / scale - 1f) <= MAX_SIZE_ERROR, "$scale draws at $drawn")
            scale += 0.01f
        }
    }

    @Test
    fun aDriftingSizeHoldsItsBucketUntilWellPastTheEdge() {
        // 0.6 of a bucket up rounds to the next, but is inside the quarter-bucket hysteresis; 0.8 is past it.
        assertEquals(0, FollowerSizing.bucketOf(bucketsUp(0.6f), previous = 0), "held")
        assertEquals(1, FollowerSizing.bucketOf(bucketsUp(0.8f), previous = 0), "moved on")
        assertEquals(1, FollowerSizing.bucketOf(bucketsUp(0.6f), FollowerSizing.NO_BUCKET), "the first frame takes the nearest")
    }

    private fun bucketsUp(buckets: Float): Float = 2f.pow(buckets / FollowerSizing.BUCKETS_PER_OCTAVE)

    @Test
    fun aSlowFollowerSnapsAndAFastOneDoesNot() {
        assertTrue(FollowerSizing.snaps(wasSnapping = false, dx = 0.3f, dy = 0.2f))
        assertFalse(FollowerSizing.snaps(wasSnapping = true, dx = 3f, dy = 0f))
        assertTrue(FollowerSizing.snaps(wasSnapping = true, dx = 1.5f, dy = 0f), "between the two it keeps snapping")
        assertFalse(FollowerSizing.snaps(wasSnapping = false, dx = 1.5f, dy = 0f), "between the two it keeps not snapping")
    }

    private companion object {
        /** Half a bucket, 2^(1/32) - 1. */
        const val MAX_SIZE_ERROR = 0.0219f
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.netdemo

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnapshotInterpolatorTest {
    private val interpolator = SnapshotInterpolator(delaySeconds = DELAY)

    @Test
    fun `samples halfway between two snapshots`() {
        interpolator.accept(snapshotAt(tick = 1, x = 0f), receivedAtSeconds = 0f)
        interpolator.accept(snapshotAt(tick = 2, x = 10f), receivedAtSeconds = 1f)

        // Render time 0.5s behind the newest, with a 0.5s delay -> exactly halfway.
        val mid = interpolator.sample(nowSeconds = 1f)
        assertEquals(1, mid.count)
        assertTrue(abs(mid.x[0] - 5f) < EPSILON, "expected midpoint, got ${mid.x[0]}")
    }

    @Test
    fun `clamps instead of extrapolating past the newest snapshot`() {
        interpolator.accept(snapshotAt(tick = 1, x = 0f), receivedAtSeconds = 0f)
        interpolator.accept(snapshotAt(tick = 2, x = 10f), receivedAtSeconds = 1f)

        // Far in the future: without a clamp this would extrapolate the entity off the map.
        val late = interpolator.sample(nowSeconds = 100f)
        assertTrue(abs(late.x[0] - 10f) < EPSILON, "extrapolated to ${late.x[0]}")
    }

    @Test
    fun `holds the only snapshot until a second one arrives`() {
        interpolator.accept(snapshotAt(tick = 1, x = 3f), receivedAtSeconds = 0f)
        val held = interpolator.sample(nowSeconds = 0.5f)
        assertEquals(1, held.count)
        assertTrue(abs(held.x[0] - 3f) < EPSILON)
    }

    /** Over an unreliable channel, reordering is routine; a stale snapshot must not become the target. */
    @Test
    fun `ignores an out-of-order snapshot`() {
        interpolator.accept(snapshotAt(tick = 5, x = 0f), receivedAtSeconds = 0f)
        interpolator.accept(snapshotAt(tick = 6, x = 10f), receivedAtSeconds = 1f)
        interpolator.accept(snapshotAt(tick = 4, x = -100f), receivedAtSeconds = 2f)

        val sampled = interpolator.sample(nowSeconds = 100f)
        assertTrue(sampled.x[0] > 0f, "a stale snapshot was accepted: ${sampled.x[0]}")
    }

    /** An entity that appears only in the newer snapshot has no start point to come from. */
    @Test
    fun `snaps in an entity that is new this snapshot`() {
        interpolator.accept(snapshotAt(tick = 1, x = 0f), receivedAtSeconds = 0f)
        val withNewcomer = SnapshotBuffer().apply {
            reset(tick = 2, count = 0)
            add(netId = 1, x = 10f, y = 0f)
            add(netId = 2, x = 4f, y = 0f)
        }
        interpolator.accept(withNewcomer, receivedAtSeconds = 1f)

        val sampled = interpolator.sample(nowSeconds = 1f)
        assertEquals(2, sampled.count)
        val newcomer = sampled.indexOf(2)
        assertTrue(abs(sampled.x[newcomer] - 4f) < EPSILON, "newcomer was interpolated from nothing")
    }

    private fun snapshotAt(tick: Long, x: Float) = SnapshotBuffer().apply {
        reset(tick, 0)
        add(netId = 1, x = x, y = 0f)
    }

    private companion object {
        const val DELAY = 0.5f
        const val EPSILON = 0.001f
    }
}

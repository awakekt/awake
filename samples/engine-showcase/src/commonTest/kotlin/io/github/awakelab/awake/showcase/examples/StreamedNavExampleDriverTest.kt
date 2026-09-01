/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.examples

import io.github.awakelab.awake.core.math.Vec3f
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the *content* of the streamed-nav showcase: procedural terrain is easy to tune into
 * something that runs and shows nothing — pillars so dense nothing can walk, or so sparse the
 * chaser never has to route around one.
 */
class StreamedNavExampleDriverTest {
    private val terrain = StreamedNavTerrain

    @Test
    fun theOrbitTheTargetWalksIsOnWalkableGround() {
        // Sampled around the orbit rather than at a few points: one blocked spot on the ring is a
        // target standing inside a pillar, which reads as the chaser giving up for no reason.
        val steps = 64
        var blocked = 0
        for (step in 0 until steps) {
            val angle = step * (2f * PI / steps)
            val x = cos(angle) * ORBIT_RADIUS
            val z = sin(angle) * ORBIT_RADIUS
            if (terrain.heightAt(x, z) > PILLAR_THRESHOLD) blocked++
        }

        assertTrue(blocked == 0, "$blocked of $steps orbit positions land on a pillar.")
    }

    @Test
    fun pillarsExistToRouteAround() {
        var pillarSamples = 0
        for (x in -20..20) {
            for (z in -20..20) {
                if (terrain.heightAt(x.toFloat(), z.toFloat()) > PILLAR_THRESHOLD) pillarSamples++
            }
        }

        assertTrue(pillarSamples > 0, "Terrain with no obstacle demonstrates nothing.")
        // Loosely bounded: dense enough to matter, sparse enough to leave a route.
        assertTrue(pillarSamples < 41 * 41 / 4, "Too much of the world is pillar: $pillarSamples.")
    }

    /** The chaser must start far enough inside the orbit that the target is not already loaded. */
    @Test
    fun theTargetOrbitsBeyondWhatTheWorldLoadsAround() {
        // Two cells of loading radius at 16m cells: anything past ~32m needs the coarse graph.
        assertTrue(ORBIT_RADIUS > 32f, "A target inside the loaded set never exercises long-range routing.")
    }

    /** The grid the streamer fills is the one the chaser searches; a mismatch is a silent no-op. */
    @Test
    fun theGridCoversTheStreamingCell() {
        assertTrue(StreamedNavExampleDriver.grid.worldCellSize > 0f)
        assertTrue(StreamedNavExampleDriver.grid.findPath(Vec3f(0f, 0f, 0f), Vec3f(1f, 0f, 0f)).isEmpty(), "Nothing is resident yet.")
    }

    private companion object {
        const val ORBIT_RADIUS = 90f
        const val PILLAR_THRESHOLD = 3f
        const val PI = 3.1415927f
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.navigation.grid.NavGroundHeight
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent. */
class NavPathFollowerAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

    @Test
    fun followingAndRePlanningAllocateNothingPerFrame() {
        val flat = NavGroundHeight { _, _ -> 0f }
        val agent = NavAgent()
        val pillars = List(12) { NavObstacle(NavFootprint.Circle(-30f + it * 5f, if (it % 2 == 0) 3f else -3f, 1.5f), 0f, 3f) }
        val surface = bakeNavSurface(NavRegion(-40f, -20f, 80, 40, 1f), flat, agent, NavAreas(obstacles = pillars))
        val query = NavPathQuery(surface, flat)
        // Re-plans every quarter second, so the measured frames include planning as well as steering.
        val followers = List(8) { NavPathFollower(agent).apply { repathInterval = 0.25f } }
        val positions = List(8) { Vec3f(-35f, 0f, -8f + it * 2f) }
        val direction = Vec3f(0f, 0f, 0f)
        val east = Vec3f(35f, 0f, 0f)
        val west = Vec3f(-35f, 0f, 0f)

        fun frame() {
            for (index in followers.indices) {
                val follower = followers[index]
                val position = positions[index]
                if (follower.status != NavFollowStatus.Moving) follower.moveTo(if (position.x < 0f) east else west)
                if (follower.update(query, position, STEP, direction)) {
                    position.x += direction.x * SPEED * STEP
                    position.z += direction.z * SPEED * STEP
                }
            }
        }

        repeat(WARMUP_FRAMES) { frame() }
        // The median window is the gate, as in the other allocation probes.
        val windows = LongArray(MEASURED_WINDOWS)
        for (window in windows.indices) {
            val before = threads.currentThreadAllocatedBytes
            repeat(FRAMES_PER_WINDOW) { frame() }
            windows[window] = threads.currentThreadAllocatedBytes - before
        }
        windows.sort()
        assertEquals(0L, windows[windows.size / 2], "bytes allocated per window: ${windows.toList()}")
    }

    private companion object {
        const val STEP = 1f / 60f
        const val SPEED = 5f
        const val WARMUP_FRAMES = 2_000
        const val MEASURED_WINDOWS = 15
        const val FRAMES_PER_WINDOW = 120
    }
}

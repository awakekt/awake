/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.navigation

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.walkable.NavFollowStatus
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.core.transform.Transform
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent. */
class NavigationAgentSystemAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

    @Test
    fun followingAPathAllocatesNothingPerFrame() {
        val grid = NavigationGrid(SceneNavigation(rows = List(24) { z -> if (z % 6 == 3) "##########.....#########" else "........................" }))
        val world = World()
        val system = NavigationAgentSystem(grid.surface)
        val north = Vec3f(12f, 0f, 1f)
        val south = Vec3f(12f, 0f, 22f)
        val agents = List(8) { index ->
            val entity = world.create()
            world.add(entity, Transform(position = Vec3f(2f + index * 2f, 0f, 1f)))
            if (index % 2 == 0) world.add(entity, MovementControl().apply { driver = MovementDriver.Agent })
            // Re-plans twice a second, so the measured frames include planning as well as steering.
            SceneNavigationAgent(speed = 6f, repathInterval = 0.5f).toAgent().also { world.add(entity, it) }
        }

        fun frame() {
            for (index in agents.indices) {
                val agent = agents[index]
                if (agent.follower.status != NavFollowStatus.Moving) {
                    agent.moveTo(if (agent.follower.destination.z > 10f) north else south)
                }
            }
            system.update(world, DELTA)
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
        const val DELTA = 1f / 60f
        const val WARMUP_FRAMES = 2_000
        const val MEASURED_WINDOWS = 15
        const val FRAMES_PER_WINDOW = 120
    }
}

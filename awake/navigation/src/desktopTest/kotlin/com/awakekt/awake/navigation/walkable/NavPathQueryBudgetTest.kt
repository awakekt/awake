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
import kotlin.test.assertTrue

/**
 * Desktop-only: the query thread's CPU time and `currentThreadAllocatedBytes` are JVM measurements.
 * The route itself is common code and the same on every target; these only put numbers on its cost.
 *
 * The time budget is the thread's CPU time, not the wall clock (#1592): CI runs this suite beside
 * 50 others in one Gradle invocation, and a query waiting for a core it shares with other test
 * workers went over 150 ms of wall clock while its own work stayed the same.
 */
class NavPathQueryBudgetTest {
    private val agent = NavAgent()
    private val flat = NavGroundHeight { _, _ -> 0f }

    /** A 1 km square at 1 m with a 20 m building every 60 m, rows offset so no street runs straight through. */
    private val town: NavSurfaceTile by lazy {
        val buildings = buildList {
            for (row in 0 until 16) {
                for (column in 0 until 16) {
                    val x = -480f + column * 60f + if (row % 2 == 0) 0f else 30f
                    val z = -480f + row * 60f
                    add(NavObstacle(NavFootprint.Rect(x, z, x + 20f, z + 20f), 0f, 10f))
                }
            }
        }
        bakeNavSurface(NavRegion(-500f, -500f, 1000, 1000, 1f), flat, agent, NavAreas(obstacles = buildings))
    }

    @Test
    fun aRouteAcrossAKilometreOfTownStaysInsideItsTimeBudget() {
        val threads = ManagementFactory.getThreadMXBean()
        assertTrue(threads.isCurrentThreadCpuTimeSupported, "this JVM measures no thread CPU time")
        val query = NavPathQuery(town, flat)
        val path = NavPath()
        val from = Vec3f(-495f, 0f, -495f)
        val to = Vec3f(495f, 0f, 495f)
        repeat(WARMUP_QUERIES) { query.findPath(from, to, agent, path) }

        val timings = LongArray(MEASURED_QUERIES)
        for (index in timings.indices) {
            val before = threads.currentThreadCpuTime
            query.findPath(from, to, agent, path)
            timings[index] = (threads.currentThreadCpuTime - before) / NANOS_PER_MICRO
        }
        timings.sort()
        val medianMillis = timings[timings.size / 2] / MICROS_PER_MILLI

        assertEquals(NavPathStatus.Complete, path.status)
        println("1 km town route: ${path.size} waypoints, ${query.lastExpandedNodes} nodes, median $medianMillis ms of CPU time")
        assertTrue(medianMillis < BUDGET_MILLIS, "median $medianMillis ms of CPU time is over the $BUDGET_MILLIS ms budget")
    }

    @Test
    fun anUnreachableTargetIsBoundedByTheNodeBudget() {
        // Inside a building: the target snaps to its wall. Far off the map: nothing snaps, and the
        // search runs until the budget, which is what bounds the worst case.
        val query = NavPathQuery(town, flat, maxExpandedNodes = 50_000)
        val path = NavPath()
        query.findPath(Vec3f(-495f, 0f, -495f), Vec3f(5_000f, 0f, 5_000f), agent, path)
        assertEquals(NavPathStatus.Partial, path.status)
        assertTrue(query.lastExpandedNodes <= 50_000)
    }

    @Test
    fun aWarmQueryAllocatesNothing() {
        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val query = NavPathQuery(town, flat)
        val path = NavPath()
        val from = Vec3f(-495f, 0f, -495f)
        val targets = Array(4) { Vec3f(-300f + it * 150f, 0f, 200f - it * 120f) }
        repeat(WARMUP_QUERIES) { for (to in targets) query.findPath(from, to, agent, path) }

        // The median window is the gate, as in the other allocation probes: a JIT deoptimisation can
        // materialise a few bytes in one window, but an allocation per query fills every window.
        val windows = LongArray(MEASURED_WINDOWS)
        for (window in windows.indices) {
            val before = threads.currentThreadAllocatedBytes
            for (to in targets) query.findPath(from, to, agent, path)
            windows[window] = threads.currentThreadAllocatedBytes - before
        }
        windows.sort()
        assertEquals(0L, windows[windows.size / 2], "bytes allocated per window: ${windows.toList()}")
    }

    private companion object {
        const val WARMUP_QUERIES = 20
        const val MEASURED_QUERIES = 15
        const val MEASURED_WINDOWS = 15
        const val MICROS_PER_MILLI = 1000.0
        const val NANOS_PER_MICRO = 1000L

        /**
         * About 50 ms of CPU time on a developer machine for ~120,000 expansions around 256 buildings; the gate
         * leaves room for a slower CI runner without letting a per-node regression through.
         */
        const val BUDGET_MILLIS = 150.0
    }
}

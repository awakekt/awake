/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.grid

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals

class NavGridDebugLinesTest {

    private val flat: (Float, Float) -> Float = { _, _ -> 0f }

    private fun tile(vararg rows: String): NavGridTile {
        val width = rows.first().length
        val bits = LongArray((width * rows.size + NavGridTile.LONG_MASK) ushr NavGridTile.LONG_SHIFT)
        for (z in rows.indices) {
            for (x in 0 until width) {
                if (rows[z][x] != '.') continue
                val bit = z * width + x
                bits[bit ushr NavGridTile.LONG_SHIFT] =
                    bits[bit ushr NavGridTile.LONG_SHIFT] or (1L shl (bit and NavGridTile.LONG_MASK))
            }
        }
        return NavGridTile(width, rows.size, cellSize = 1f, walkable = bits)
    }

    @Test
    fun onlyBlockedSamplesGetAMarker() {
        val lines = navGridDebugLines(tile("..", "#."), flat)

        // Two segments per cross, one blocked sample.
        assertEquals(2, lines.size, "$lines")
    }

    @Test
    fun twoAgentsGetTwoRouteColours() {
        val world = World()
        val routes = listOf(
            AgentRoute(world.create(), route()),
            AgentRoute(world.create(), route()),
        )

        val colors = navGridDebugLines(tile("..", ".."), flat, routes).map { it.color }.toSet()

        assertEquals(2, colors.size, "Both routes drew in the same colour: $colors")
    }

    /** The goal is where a route stops making sense first, so it gets a marker of its own. */
    @Test
    fun aRouteEndsInAGoalMarker() {
        val world = World()
        val routes = listOf(AgentRoute(world.create(), route()))

        val lines = navGridDebugLines(tile("..", ".."), flat, routes)

        // One segment for the route itself, two for the cross on its goal.
        assertEquals(3, lines.size, "$lines")
    }

    private fun route() = listOf(Vec3f(0f, 0f, 0f), Vec3f(1f, 0f, 1f))
}

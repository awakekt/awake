/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.navigation.grid.CoarseNavGraph
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.scene.world.WorldCellCoord
import kotlin.math.floor

/** The cell containing [position], in a world of [cellSize] metre cells. */
internal fun cellOf(position: Vec3f, cellSize: Float): WorldCellCoord =
    WorldCellCoord(floor(position.x / cellSize).toInt(), floor(position.z / cellSize).toInt())

/**
 * The cells a long-range route passes through, drawn whether or not they are loaded.
 *
 * The navigation overlay stops where the world does, which makes it useless for the one thing a
 * hierarchical route needs shown: the part beyond the streaming radius. This draws the corridor the
 * coarse graph returned, so the plan and the ground it crosses can be compared by eye.
 *
 * Its own file rather than more methods on the driver: this is a way of looking at the world, and
 * the driver is the world.
 */
internal fun corridorLines(
    coarse: CoarseNavGraph,
    from: WorldCellCoord,
    to: WorldCellCoord,
    cellSize: Float,
    heightAt: (Float, Float) -> Float,
): List<LineSegment> {
    val corridor = coarse.corridor(from, to) ?: return emptyList()
    val lines = ArrayList<LineSegment>()
    corridor.forEach { coord ->
        val minX = coord.x * cellSize
        val minZ = coord.z * cellSize
        val maxX = minX + cellSize
        val maxZ = minZ + cellSize
        corridorEdge(lines, minX, minZ, maxX, minZ, heightAt)
        corridorEdge(lines, maxX, minZ, maxX, maxZ, heightAt)
        corridorEdge(lines, maxX, maxZ, minX, maxZ, heightAt)
        corridorEdge(lines, minX, maxZ, minX, minZ, heightAt)
    }
    return lines
}

/** In segments, so an edge crossing a hill follows it instead of sinking through. */
@Suppress("LongParameterList")
private fun corridorEdge(
    lines: MutableList<LineSegment>,
    startX: Float,
    startZ: Float,
    endX: Float,
    endZ: Float,
    heightAt: (Float, Float) -> Float,
) {
    for (step in 0 until CORRIDOR_EDGE_SEGMENTS) {
        lines += LineSegment(
            corridorPoint(startX, startZ, endX, endZ, step.toFloat() / CORRIDOR_EDGE_SEGMENTS, heightAt),
            corridorPoint(startX, startZ, endX, endZ, (step + 1).toFloat() / CORRIDOR_EDGE_SEGMENTS, heightAt),
            CORRIDOR_COLOR,
        )
    }
}

@Suppress("LongParameterList")
private fun corridorPoint(
    startX: Float,
    startZ: Float,
    endX: Float,
    endZ: Float,
    fraction: Float,
    heightAt: (Float, Float) -> Float,
): Vec3f {
    val x = startX + (endX - startX) * fraction
    val z = startZ + (endZ - startZ) * fraction
    return Vec3f(x, heightAt(x, z) + CORRIDOR_LIFT, z)
}

private const val CORRIDOR_EDGE_SEGMENTS = 4

/** Above the navigation markers, so the two overlays read as separate layers when both are on. */
private const val CORRIDOR_LIFT = 1.2f

private val CORRIDOR_COLOR = Color(r = 0.95f, g = 0.75f, b = 0.15f)

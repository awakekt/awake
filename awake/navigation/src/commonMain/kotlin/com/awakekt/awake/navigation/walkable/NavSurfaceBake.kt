/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import com.awakekt.awake.navigation.grid.NavGroundHeight
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.tan

/**
 * Bakes where [agent] can stand across [region]: terrain from [ground], then [areas] on top.
 *
 * A sample is blocked when
 * - [ground] has no height there (`NaN`, which `Heightmap.heightAtWorld` returns off the map);
 * - the terrain to any of its four neighbours is steeper than [NavAgent.maxSlopeDegrees];
 * - the water in [areas] is deeper there than the agent wades;
 * - a no-walk footprint touches its cell; or
 * - an obstacle touches its cell and is too high to step over and too low to walk under, by
 *   [NavObstacle.blocks].
 *
 * Every other sample then gets its clearance (see [NavSurface]), which is what the query reads the
 * radius against. The bake looks [maxAgentRadius] (and two samples more) beyond the region's edges,
 * so an obstacle just over the border of a neighbouring tile still narrows this one: tiles baked
 * separately join without a seam. Agents wider than [maxAgentRadius] still route, but may brush an
 * obstacle that lies across a tile border.
 *
 * Deterministic: the same inputs bake the same bytes on every platform, so a server and a client
 * that bake the same region agree, and a bake can be cached with [NavSurfaceCodec].
 *
 * @param region The samples to bake.
 * @param ground World Y of the terrain at a world (x, z), or `NaN` where there is none.
 * @param agent The height class baked for: its height, step and slope. Its radius sets the default margin.
 * @param areas Obstacles, no-walk outlines and water.
 * @param maxAgentRadius The widest agent this surface is queried for across tile borders.
 * @return The baked tile, covering exactly [region].
 */
fun bakeNavSurface(
    region: NavRegion,
    ground: NavGroundHeight,
    agent: NavAgent,
    areas: NavAreas = NavAreas.NONE,
    maxAgentRadius: Float = agent.radius,
): NavSurfaceTile {
    require(maxAgentRadius >= 0f && maxAgentRadius.isFinite()) { "maxAgentRadius must be non-negative and finite; was $maxAgentRadius." }
    val margin = ceil(maxAgentRadius / region.sampleSize).toInt() + MARGIN_PAD
    val bake = SurfaceBake(region, margin, agent)
    bake.sampleTerrain(ground, areas.water)
    for (area in areas.noWalk) bake.block(area) { _ -> true }
    for (obstacle in areas.obstacles) bake.block(obstacle.footprint) { groundY -> obstacle.blocks(groundY, agent) }
    return bake.finish()
}

/** One bake's working arrays: the region grown by `margin` samples on every side. */
private class SurfaceBake(private val region: NavRegion, private val margin: Int, agent: NavAgent) {
    private val width = region.width + margin * 2
    private val depth = region.depth + margin * 2
    private val step = region.sampleSize
    private val originX = region.originX - margin * step
    private val originZ = region.originZ - margin * step

    /** One more ring than [distance], so every sample has four neighbours to measure slope against. */
    private val heights = FloatArray((width + 2) * (depth + 2))
    private val distance = IntArray(width * depth)
    private val maxRise = tan(agent.maxSlopeDegrees * PI.toFloat() / STRAIGHT_ANGLE_DEGREES) * step

    fun sampleTerrain(ground: NavGroundHeight, water: NavWater?) {
        for (z in -1..depth) {
            for (x in -1..width) heights[heightIndex(x, z)] = ground.heightAt(originX + x * step, originZ + z * step)
        }
        for (z in 0 until depth) {
            for (x in 0 until width) {
                val here = heights[heightIndex(x, z)]
                val standable = !here.isNaN() && (water == null || !water.blocks(here)) && withinSlope(x, z, here)
                distance[z * width + x] = if (standable) NavClearance.MAX else 0
            }
        }
    }

    /**
     * A neighbour with no ground adds no slope constraint: it is itself blocked, so the clearance
     * already keeps the agent's body off it.
     */
    private fun withinSlope(x: Int, z: Int, here: Float): Boolean =
        rise(x - 1, z, here) && rise(x + 1, z, here) && rise(x, z - 1, here) && rise(x, z + 1, here)

    private fun rise(x: Int, z: Int, here: Float): Boolean {
        val there = heights[heightIndex(x, z)]
        return there.isNaN() || abs(there - here) <= maxRise
    }

    /** Blocks every sample whose cell [footprint] touches and whose ground [blocks] accepts. */
    fun block(footprint: NavFootprint, blocks: (groundY: Float) -> Boolean) {
        val half = step * HALF
        val x0 = ceil((footprint.minX - half - originX) / step).toInt().coerceAtLeast(0)
        val x1 = floor((footprint.maxX + half - originX) / step).toInt().coerceAtMost(width - 1)
        val z0 = ceil((footprint.minZ - half - originZ) / step).toInt().coerceAtLeast(0)
        val z1 = floor((footprint.maxZ + half - originZ) / step).toInt().coerceAtMost(depth - 1)
        for (z in z0..z1) {
            for (x in x0..x1) {
                val index = z * width + x
                if (distance[index] == 0) continue
                val inside = footprint.overlapsCell(originX + x * step, originZ + z * step, half)
                if (inside && blocks(heights[heightIndex(x, z)])) distance[index] = 0
            }
        }
    }

    fun finish(): NavSurfaceTile {
        chamferClearance(distance, width, depth)
        val bytes = ByteArray(region.width * region.depth)
        for (z in 0 until region.depth) {
            for (x in 0 until region.width) {
                bytes[z * region.width + x] = distance[(z + margin) * width + x + margin].toByte()
            }
        }
        return NavSurfaceTile(region.width, region.depth, step, region.originX, region.originZ, bytes)
    }

    private fun heightIndex(x: Int, z: Int): Int = (z + 1) * (width + 2) + x + 1
}

/** Two samples beyond the widest radius: the half-sample the agent's centre sits off, and rounding. */
private const val MARGIN_PAD = 2
private const val STRAIGHT_ANGLE_DEGREES = 180f
private const val HALF = 0.5f

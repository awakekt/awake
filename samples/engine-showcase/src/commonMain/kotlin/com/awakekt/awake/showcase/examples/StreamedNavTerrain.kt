/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The shape of the streamed world, as a function of world position.
 *
 * A function rather than an asset because a streamed world has no single heightmap to store: the
 * same arithmetic answers for any cell, including one nobody has ever visited, which is what lets a
 * cell be baked the moment it is needed *and* summarised for routing long before it is.
 *
 * Its own object rather than more members on the driver: this is the world, and the driver is what
 * happens in it.
 */
internal object StreamedNavTerrain {
    const val PILLAR_HEIGHT = 6f

    private const val GROUND_ROLL = 0.6f
    private const val ROLL_FREQUENCY = 0.13f
    private const val PILLAR_SPACING = 7f
    private const val PILLAR_RADIUS = 1.6f
    private const val ORBIT_LANE_HALF_WIDTH = 2f
    private const val HALF = 0.5f

    /** Rolling ground with pillars too steep to climb, and a clear lane where the target walks. */
    fun heightAt(worldX: Float, worldZ: Float): Float {
        val ground = GROUND_ROLL * sin(worldX * ROLL_FREQUENCY) * cos(worldZ * ROLL_FREQUENCY)
        if (onOrbitLane(worldX, worldZ)) return ground
        val pillarX = worldX - round(worldX / PILLAR_SPACING) * PILLAR_SPACING
        val pillarZ = worldZ - round(worldZ / PILLAR_SPACING) * PILLAR_SPACING
        val onPillar = pillarX * pillarX + pillarZ * pillarZ < PILLAR_RADIUS * PILLAR_RADIUS
        return if (onPillar) PILLAR_HEIGHT else ground
    }

    /**
     * The height of the DRAWN ground at a world position, which is not [heightAt].
     *
     * The mesh is this function sampled every [sampleSize] metres and interpolated across each
     * quad, so the surface a player sees is piecewise linear while the function is a sine with a
     * hard step at every pillar edge. An agent settled with [heightAt] therefore rides a curve
     * the ground does not have -- it bobs against the facets as it walks, and at a pillar edge
     * the function jumps [PILLAR_HEIGHT] metres in one frame while the mesh ramps across a whole
     * sample. With the camera bolted to that agent, both read as the screen shaking.
     *
     * Interpolating the same lattice the mesh is built from puts the agent on the ground that is
     * actually drawn.
     */
    fun surfaceAt(worldX: Float, worldZ: Float, sampleSize: Float): Float {
        val gridX = floor(worldX / sampleSize)
        val gridZ = floor(worldZ / sampleSize)
        val x0 = gridX * sampleSize
        val z0 = gridZ * sampleSize
        val fx = (worldX - x0) / sampleSize
        val fz = (worldZ - z0) / sampleSize
        val near = heightAt(x0, z0).let { it + (heightAt(x0 + sampleSize, z0) - it) * fx }
        val far = heightAt(x0, z0 + sampleSize).let {
            it + (heightAt(x0 + sampleSize, z0 + sampleSize) - it) * fx
        }
        return near + (far - near) * fz
    }

    /** Whether an agent can stand at this world position: flat enough, and not a pillar. */
    fun isOpen(worldX: Float, worldZ: Float): Boolean = heightAt(worldX, worldZ) < PILLAR_HEIGHT / 2f

    /**
     * The nearest standable spot to ([startX], [startZ]), or null within [searchRadius] metres.
     *
     * A scene file places a cube at a fixed position and this terrain is a function, so nothing
     * guarantees that position is not inside a pillar — and an agent standing on one has an
     * unwalkable start sample, which makes every search fail and reads as a broken demonstration.
     * Rings outward a metre at a time, so a spawn moves as little as it has to.
     */
    fun nearestOpenGround(startX: Float, startZ: Float, searchRadius: Int): Pair<Float, Float>? {
        for (radius in 0..searchRadius) {
            val offset = ringOffsets(radius).firstOrNull { (dx, dz) -> isOpen(startX + dx, startZ + dz) }
            if (offset != null) return startX + offset.first to startZ + offset.second
        }
        return null
    }

    private fun ringOffsets(radius: Int): List<Pair<Float, Float>> =
        (-radius..radius).flatMap { dz -> (-radius..radius).map { dx -> dx.toFloat() to dz.toFloat() } }

    /**
     * The ring the target walks is kept clear of pillars.
     *
     * Otherwise the lattice eventually puts one where the target is standing, and a target inside
     * an obstacle reads as the chaser having given up. The chaser still crosses the pillar field to
     * reach it, which is the part worth watching.
     */
    private fun onOrbitLane(worldX: Float, worldZ: Float): Boolean {
        val distance = sqrt(worldX * worldX + worldZ * worldZ)
        return abs(distance - StreamedNavExampleDriver.TARGET_ORBIT_RADIUS) < ORBIT_LANE_HALF_WIDTH
    }

    /** `kotlin.math.round` returns a Double for Float input on some targets; keep it Float here. */
    private fun round(value: Float): Float = (value + if (value < 0f) -HALF else HALF).toInt().toFloat()
}

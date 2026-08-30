/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.navigation.grid

import io.github.awakelab.awake.asset.terrain.Heightmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

/**
 * Which positions on a [Heightmap] an agent can stand on, as one bit per navigation sample.
 *
 * A bitset rather than a `BooleanArray`: a 512m cell at 1m navigation resolution is 512x512
 * samples, which is 32KB packed and 256KB as bytes, and a streamed world holds a dozen or more
 * of these resident at once.
 *
 * Samples sit on a regular [cellSize] grid sharing the heightmap's corner origin, so sample
 * `(x, z)` is at world `(x * cellSize, _, z * cellSize)`. Immutable once baked, which is what
 * lets a search running off the frame thread read a tile the streaming system may be unloading.
 */
class NavGridTile internal constructor(
    val width: Int,
    val depth: Int,
    val cellSize: Float,
    private val walkable: LongArray,
) {
    /** Number of samples an agent can stand on. Useful mostly for asserting a bake did something. */
    val walkableCount: Int
        get() {
            var total = 0
            for (word in walkable) total += word.countOneBits()
            return total
        }

    /** Returns whether an agent can stand at sample [x], [z], rejecting out-of-range coordinates. */
    fun isWalkable(x: Int, z: Int): Boolean {
        require(x in 0 until width) { "NavGridTile x must be in 0 until $width; was $x." }
        require(z in 0 until depth) { "NavGridTile z must be in 0 until $depth; was $z." }
        val bit = z * width + x
        return walkable[bit ushr LONG_SHIFT] and (1L shl (bit and LONG_MASK)) != 0L
    }

    /** World X of sample column [x]. */
    fun worldX(x: Int): Float = x * cellSize

    /** World Z of sample row [z]. */
    fun worldZ(z: Int): Float = z * cellSize

    internal companion object {
        const val LONG_SHIFT = 6
        const val LONG_MASK = 63
    }
}

/**
 * Bakes walkability from terrain slope: a sample is walkable when the ground rises no more than
 * [maxSlopeDegrees] between it and each of its four neighbours, [cellSize] metres away.
 *
 * [cellSize] is independent of the heightmap's own sample spacing -- navigation usually wants a
 * coarser grid than the terrain mesh -- which is why this samples through
 * [Heightmap.heightAtWorld] rather than reading grid samples directly.
 *
 * Only terrain shape is considered. Static obstacles rasterize in afterwards, and moving ones are
 * local avoidance's problem rather than a reason to re-bake; see
 * docs/tasks/2026-08-30-navgrid-navigation-plan.md.
 */
fun Heightmap.bakeNavGrid(
    cellSize: Float = 1f,
    maxSlopeDegrees: Float = DEFAULT_MAX_SLOPE_DEGREES,
): NavGridTile {
    val extentX = (width - 1) * scale.x
    val extentZ = (depth - 1) * scale.z
    return bake(
        navWidth = (extentX / cellSize).toInt() + 1,
        navDepth = (extentZ / cellSize).toInt() + 1,
        cellSize = cellSize,
        maxSlopeDegrees = maxSlopeDegrees,
    )
}

/**
 * Bakes exactly [samples] by [samples] navigation samples, for one streamed world cell.
 *
 * The difference from [bakeNavGrid] is the far edge. That one samples both edges of the heightmap
 * — `extent / cellSize + 1` columns — which is right for a standalone grid and wrong for a tiled
 * one: the last column of a cell would sit on the first column of its neighbour, so every sample
 * on a cell boundary would exist twice and the two copies could disagree. Here the tile owns
 * `[0, samples * sampleSize)` and its neighbour owns the metre after it, which is the invariant
 * [StreamedNavGrid] addresses tiles by.
 *
 * Sampling still runs through the heightmap's own coordinates, so the caller passes the heightmap
 * for the cell being baked, not the world's.
 */
fun Heightmap.bakeNavGridCell(
    samples: Int,
    sampleSize: Float,
    maxSlopeDegrees: Float = DEFAULT_MAX_SLOPE_DEGREES,
): NavGridTile {
    require(samples > 0) { "NavGrid samples must be positive; was $samples." }
    return bake(samples, samples, sampleSize, maxSlopeDegrees)
}

private fun Heightmap.bake(
    navWidth: Int,
    navDepth: Int,
    cellSize: Float,
    maxSlopeDegrees: Float,
): NavGridTile {
    require(cellSize > 0f && cellSize.isFinite()) {
        "NavGrid cellSize must be positive and finite; was $cellSize."
    }
    require(maxSlopeDegrees > 0f && maxSlopeDegrees < STRAIGHT_UP_DEGREES) {
        "NavGrid maxSlopeDegrees must be in (0, $STRAIGHT_UP_DEGREES); was $maxSlopeDegrees."
    }

    val scale = scale
    val probe = NavGridProbe(
        heightmap = this,
        cellSize = cellSize,
        // One tan for the whole bake rather than an atan per sample.
        maxGradient = tan(maxSlopeDegrees * PI.toFloat() / STRAIGHT_ANGLE_DEGREES),
        extentX = (width - 1) * scale.x,
        extentZ = (depth - 1) * scale.z,
    )

    val bits = LongArray((navWidth * navDepth + NavGridTile.LONG_MASK) ushr NavGridTile.LONG_SHIFT)
    for (z in 0 until navDepth) {
        for (x in 0 until navWidth) {
            if (!probe.isStandable(x * cellSize, z * cellSize)) continue
            val bit = z * navWidth + x
            val word = bit ushr NavGridTile.LONG_SHIFT
            bits[word] = bits[word] or (1L shl (bit and NavGridTile.LONG_MASK))
        }
    }
    return NavGridTile(navWidth, navDepth, cellSize, bits)
}

/** Holds one bake's invariants so the per-sample slope test does not re-derive or re-pass them. */
private class NavGridProbe(
    private val heightmap: Heightmap,
    private val cellSize: Float,
    private val maxGradient: Float,
    val extentX: Float,
    val extentZ: Float,
) {
    /**
     * Tests the steepest step from ([worldX], [worldZ]) to any of its four neighbours.
     *
     * Forward and backward differences stay separate rather than becoming a central difference:
     * averaging across both sides halves the apparent rise of a one-cell cliff, so the lip of a
     * drop would bake as walkable.
     */
    fun isStandable(worldX: Float, worldZ: Float): Boolean {
        val here = heightmap.heightAtWorld(worldX, worldZ)
        if (here.isNaN()) return false
        return withinGradient(here, worldX, worldZ, -cellSize, 0f) &&
            withinGradient(here, worldX, worldZ, cellSize, 0f) &&
            withinGradient(here, worldX, worldZ, 0f, -cellSize) &&
            withinGradient(here, worldX, worldZ, 0f, cellSize)
    }

    /**
     * Probes are clamped to the map, and the run uses the clamped distance rather than
     * [cellSize] so an edge sample is not credited with a gentler slope than it has. A direction
     * with nowhere left to go contributes no constraint rather than a division by zero.
     */
    private fun withinGradient(
        here: Float,
        worldX: Float,
        worldZ: Float,
        offsetX: Float,
        offsetZ: Float,
    ): Boolean {
        val probeX = (worldX + offsetX).coerceIn(0f, extentX)
        val probeZ = (worldZ + offsetZ).coerceIn(0f, extentZ)
        val run = abs(probeX - worldX) + abs(probeZ - worldZ)
        if (run == 0f) return true
        val there = heightmap.heightAtWorld(probeX, probeZ)
        return !there.isNaN() && abs(there - here) / run <= maxGradient
    }
}

/** The slope a bake accepts when nobody says otherwise. Shared so no caller restates it. */
internal const val DEFAULT_MAX_SLOPE_DEGREES = 45f
private const val STRAIGHT_UP_DEGREES = 90f
private const val STRAIGHT_ANGLE_DEGREES = 180f

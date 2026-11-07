/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import com.awakekt.awake.navigation.grid.NavGridTile

/**
 * Where an agent can stand, as a clearance per sample: how far the sample is from the nearest one
 * nobody can stand on.
 *
 * Clearance rather than a walkable bit is what lets the radius be a query-time question. A bit
 * answers for the one radius it was eroded by; a distance answers for every radius at once, so one
 * surface serves a narrow and a wide agent of the same height class. [NavAgent.requiredClearance]
 * turns a radius into the clearance it needs.
 *
 * Clearance is in chamfer units, [NavClearance.UNITS_PER_SAMPLE] per sample: a 3-4 chamfer distance,
 * within 8% of the true Euclidean distance, saturating at [NavClearance.MAX]. Zero is blocked, and is
 * also the answer anywhere the surface does not cover.
 *
 * Sample `(x, z)` sits at world `(originX + x * sampleSize, _, originZ + z * sampleSize)`, and
 * coordinates may be negative on a tiled surface.
 */
interface NavSurface {
    /** Metres between adjacent samples. */
    val sampleSize: Float

    /** World X of sample column 0. */
    val originX: Float

    /** World Z of sample row 0. */
    val originZ: Float

    /**
     * The clearance at sample ([x], [z]), or 0 where it is blocked or not covered.
     *
     * @param x Sample column.
     * @param z Sample row.
     * @return Clearance in chamfer units, in `0..NavClearance.MAX`.
     */
    fun clearance(x: Int, z: Int): Int
}

/** The scale [NavSurface.clearance] is measured in. */
object NavClearance {
    /** Chamfer units per sample of distance; a diagonal step costs 4. */
    const val UNITS_PER_SAMPLE: Int = 3

    /** The largest stored clearance; distances beyond it read as this. */
    const val MAX: Int = 255
}

/**
 * One baked rectangle of [NavSurface]: [width] by [depth] samples, one byte of clearance each.
 *
 * Immutable once baked, so a search off the frame thread may read it while the world unloads it,
 * as [NavGridTile] is. A 1 km square at 1 m is a megabyte.
 *
 * @property width Samples along X.
 * @property depth Samples along Z.
 * @property sampleSize Metres between samples.
 * @property originX World X of column 0.
 * @property originZ World Z of row 0.
 * @param clearance Row-major `z * width + x` clearance bytes, read unsigned. Owned by the tile.
 */
class NavSurfaceTile(
    val width: Int,
    val depth: Int,
    override val sampleSize: Float,
    override val originX: Float,
    override val originZ: Float,
    clearance: ByteArray,
) : NavSurface {
    private val bytes: ByteArray = clearance

    init {
        require(width > 0 && depth > 0) { "NavSurfaceTile size must be positive; was $width x $depth." }
        require(sampleSize > 0f && sampleSize.isFinite()) { "NavSurfaceTile sampleSize must be positive and finite; was $sampleSize." }
        require(clearance.size == width * depth) {
            "NavSurfaceTile clearance must hold width * depth = ${width * depth} samples; was ${clearance.size}."
        }
    }

    /** Samples some agent can stand on. */
    val walkableCount: Int
        get() = bytes.count { it.toInt() != 0 }

    override fun clearance(x: Int, z: Int): Int =
        if (x in 0 until width && z in 0 until depth) bytes[z * width + x].toInt() and BYTE_MASK else 0

    /**
     * Copies the clearance bytes out, for [NavSurfaceCodec] or a cache.
     *
     * @return A new row-major copy.
     */
    fun copyClearance(): ByteArray = bytes.copyOf()
}

private const val BYTE_MASK = 0xFF

/**
 * The clearance surface of a walkability tile baked from terrain slope alone, placed where the tile
 * is, so an existing [NavGridTile] gains radius-aware queries without re-baking.
 *
 * @return A surface with this tile's size, spacing and origin.
 */
fun NavGridTile.toNavSurface(): NavSurfaceTile {
    val distance = IntArray(width * depth)
    for (z in 0 until depth) {
        for (x in 0 until width) {
            distance[z * width + x] = if (isWalkable(x, z)) NavClearance.MAX else 0
        }
    }
    chamferClearance(distance, width, depth)
    return NavSurfaceTile(width, depth, cellSize, originX, originZ, ByteArray(distance.size) { distance[it].toByte() })
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

import com.awakekt.awake.core.math.GridOrigin
import com.awakekt.awake.core.math.Vec3f

/**
 * One tile's worth of a larger heightfield, as a shape a streamed body can be built from.
 *
 * A world too big for one collider is collided with as a grid of them, one per streamed cell. This
 * cuts that grid out of the source samples.
 *
 * **Tiles share their edge samples, and that is the whole difficulty.** Tile `k` starts at sample
 * `k * (tileSamples - 1)`, not at `k * tileSamples`, so its last row is its neighbour's first row.
 * Cut them disjointly instead and every seam is a step: the two tiles end at different heights and
 * a character walking across the join catches on a wall one sample wide, or drops through a gap.
 * Nothing about that looks like a tiling bug from the outside.
 *
 * Returns `null` when the tile would extend past the source samples, which is a streamer asking
 * about a cell beyond the edge of the map -- an ordinary answer meaning "no collider here", the
 * same one [PhysicsWorld] consumers already handle for ocean or unauthored regions.
 *
 * The result is [GridOrigin.Centered], so the body goes at the tile's centre -- which is where a
 * cell streamer places one, and matches how a mesh cut from the same samples is positioned.
 */
@Suppress("LongParameterList") // A sub-grid needs its source dimensions and its own extent.
fun heightFieldTile(
    heights: FloatArray,
    width: Int,
    depth: Int,
    scale: Vec3f,
    tileX: Int,
    tileZ: Int,
    tileSamples: Int,
): HeightFieldShape? {
    require(tileSamples >= 2) { "a tile needs at least two samples per side: $tileSamples" }
    require(heights.size == width * depth) {
        "heights must hold width * depth samples: expected ${width * depth}, got ${heights.size}"
    }

    val startX = tileX * (tileSamples - 1)
    val startZ = tileZ * (tileSamples - 1)
    val inside = startX >= 0 &&
        startZ >= 0 &&
        startX + tileSamples <= width &&
        startZ + tileSamples <= depth
    if (!inside) return null

    val tile = FloatArray(tileSamples * tileSamples)
    for (z in 0 until tileSamples) {
        val sourceRow = (startZ + z) * width + startX
        heights.copyInto(
            destination = tile,
            destinationOffset = z * tileSamples,
            startIndex = sourceRow,
            endIndex = sourceRow + tileSamples,
        )
    }
    return HeightFieldShape(tile, tileSamples, scale, GridOrigin.Centered)
}

/**
 * Where [heightFieldTile]'s body belongs in the source field's own space.
 *
 * Kept beside the cutter because the two have to agree: the shape is centred on its own tile, so
 * placing that body anywhere else slides a tile's collision away from the terrain it was cut from,
 * and the result reads as terrain being subtly wrong everywhere rather than as a placement bug.
 *
 * [origin] is the *source field's* anchoring, not the tile's -- the tile is always centred on its
 * own body. A [GridOrigin.Centered] field measures tiles from its middle sample, which is what a
 * single authored heightmap does; a [GridOrigin.Corner] one measures from sample zero, which is
 * what a cell streamer keyed to positive-quadrant coordinates does. Getting this wrong offsets the
 * entire world by half a map.
 */
@Suppress("LongParameterList") // A sub-grid needs its source dimensions and its own extent.
fun heightFieldTileCenter(
    width: Int,
    depth: Int,
    scale: Vec3f,
    tileX: Int,
    tileZ: Int,
    tileSamples: Int,
    origin: GridOrigin = GridOrigin.Centered,
): Vec3f {
    val cells = tileSamples - 1
    val tileCenterX = tileX * cells + cells * 0.5f
    val tileCenterZ = tileZ * cells + cells * 0.5f
    val fieldCenterX = if (origin == GridOrigin.Centered) (width - 1) * 0.5f else 0f
    val fieldCenterZ = if (origin == GridOrigin.Centered) (depth - 1) * 0.5f else 0f
    return Vec3f(
        (tileCenterX - fieldCenterX) * scale.x,
        0f,
        (tileCenterZ - fieldCenterZ) * scale.z,
    )
}

/**
 * Visits every tile containing any sample in the given inclusive rectangle.
 *
 * What a deform needs: an edit reports the samples it changed, and these are the tiles whose bodies
 * are now stale. Rebuilding the whole field instead is correct and, on a real map, unaffordable.
 *
 * **A sample on a tile boundary belongs to two tiles, and an edit there visits both.** That falls
 * straight out of tiles sharing their edge samples, and it is the deform-time form of the same
 * trap: rebuild only one side and the seam that used to line up now steps, so terrain that was
 * continuous develops a wall exactly where it was edited.
 *
 * A visitor rather than a list because a caller already has its own cell-coordinate type to build,
 * and an edit touching one sample should not allocate to say so.
 */
@Suppress("LongParameterList") // A sample rectangle is four bounds; naming them beats a new type.
fun forEachHeightFieldTileTouching(
    firstSampleX: Int,
    firstSampleZ: Int,
    lastSampleX: Int,
    lastSampleZ: Int,
    tileSamples: Int,
    onTile: (tileX: Int, tileZ: Int) -> Unit,
) {
    require(tileSamples >= 2) { "a tile needs at least two samples per side: $tileSamples" }
    val cells = tileSamples - 1
    for (tileZ in tileRange(firstSampleZ, lastSampleZ, cells)) {
        for (tileX in tileRange(firstSampleX, lastSampleX, cells)) {
            onTile(tileX, tileZ)
        }
    }
}

/**
 * Which tiles along one axis hold samples `first..last`.
 *
 * Tile `k` covers `k * cells .. k * cells + cells` inclusive, so a sample that is an exact multiple
 * of `cells` is the last of one tile and the first of the next -- hence the `- 1` on the low end.
 * Clamped at zero because a tile index below the field is not a tile.
 */
private fun tileRange(first: Int, last: Int, cells: Int): IntRange {
    val lowest = if (first % cells == 0) first / cells - 1 else first / cells
    return lowest.coerceAtLeast(0)..(last / cells)
}

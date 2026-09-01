/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics.streaming

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.CollisionLayer
import io.github.awakelab.awake.physics.CollisionLayers
import io.github.awakelab.awake.physics.forEachHeightFieldTileTouching
import io.github.awakelab.awake.physics.heightFieldTile
import io.github.awakelab.awake.scene.world.WorldCellCoord

/**
 * Streams one heightfield tile per cell out of a single large sample grid.
 *
 * The cell size is *derived*, not passed, and that is the point of this existing at all.
 * [PhysicsCellStreamer] places a cell's body at `coord * cellSize + cellSize / 2`, and a tile cut
 * by `heightFieldTile` is centred on its own `(tileSamples - 1) * scale` extent. Those two agree
 * only when the cell size is exactly that extent -- and when they disagree, every collider sits a
 * little way from the terrain it was cut from, consistently, everywhere. Nothing about that reads
 * as a configuration mistake.
 *
 * [heights] is read each time a cell loads rather than copied once, so an edited map streams its
 * new samples in without this being rebuilt -- see [tilesTouchedByEdit] for the other half of a
 * deform.
 *
 * Square cells only. A non-square `scale` gives x and z different cell extents, which the
 * one-dimensional `cellSize` cannot express; better to say so than to silently use x for both.
 */
@Suppress("LongParameterList") // A source grid plus the tiling it is cut into.
fun heightFieldCellStreamer(
    physicsWorld: io.github.awakelab.awake.physics.PhysicsWorld,
    width: Int,
    depth: Int,
    scale: Vec3f,
    tileSamples: Int,
    layer: CollisionLayer = CollisionLayers.World,
    heights: () -> FloatArray,
): PhysicsCellStreamer {
    require(tileSamples >= 2) { "a tile needs at least two samples per side: $tileSamples" }
    require(scale.x == scale.z) {
        "cells are square, so a heightfield streamed into them needs scale.x == scale.z: $scale"
    }
    val cellSize = (tileSamples - 1) * scale.x
    return PhysicsCellStreamer(
        physicsWorld = physicsWorld,
        cellSize = cellSize,
        layer = layer,
        shapeFor = { coord ->
            heightFieldTile(heights(), width, depth, scale, coord.x, coord.z, tileSamples)
        },
    )
}

/**
 * The cells whose colliders an edit to these samples has invalidated.
 *
 * Bounds are inclusive sample indices, which is what a heightmap edit reports. **A sample on a tile
 * boundary invalidates the tiles on both sides of it**, because neighbouring tiles share that
 * sample -- rebuild one and the seam that lined up before the edit now steps.
 */
fun tilesTouchedByEdit(
    firstSampleX: Int,
    firstSampleZ: Int,
    lastSampleX: Int,
    lastSampleZ: Int,
    tileSamples: Int,
): Set<WorldCellCoord> = buildSet {
    forEachHeightFieldTileTouching(
        firstSampleX,
        firstSampleZ,
        lastSampleX,
        lastSampleZ,
        tileSamples,
    ) { tileX, tileZ -> add(WorldCellCoord(tileX, tileZ)) }
}

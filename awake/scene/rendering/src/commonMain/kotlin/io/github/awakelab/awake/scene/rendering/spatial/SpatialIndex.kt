/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering.spatial

import io.github.awakelab.awake.ecs.World

/**
 * The scene's [SpatialGrid], on an entity so any system can find it.
 *
 * Here rather than as a field on whichever system maintains it, for the reason
 * [io.github.awakelab.awake.scene.world.WorldOrigin] lives on an entity too: the maintainer and
 * the users sit in different modules -- the index is filled from render bounds and read by
 * culling today, by AI range queries tomorrow -- and a component is the one thing all of them
 * already have a handle to.
 *
 * A scene with no maintainer has no index, and every consumer falls back to whatever it did
 * before one existed. That is what keeps this an optimisation rather than a requirement.
 */
data class SpatialIndex(
    val grid: SpatialGrid = SpatialGrid(),
)

/** This scene's [SpatialIndex], or null when nothing maintains one. */
fun World.findSpatialIndex(): SpatialIndex? {
    var found: SpatialIndex? = null
    queryEach<SpatialIndex> { _, index ->
        if (found == null) found = index
    }
    return found
}

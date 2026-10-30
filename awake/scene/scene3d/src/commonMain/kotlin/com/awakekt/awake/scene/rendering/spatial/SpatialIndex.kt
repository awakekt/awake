/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("DEPRECATION")

package com.awakekt.awake.scene.rendering.spatial

import com.awakekt.awake.ecs.World
import com.awakekt.awake.core.spatial.SpatialGrid as CoreSpatialGrid

/**
 * The scene's [SpatialGrid], on an entity so any system can find it.
 *
 * Here rather than as a field on whichever system maintains it, for the reason
 * [com.awakekt.awake.scene.world.WorldOrigin] lives on an entity too: the maintainer and
 * the users sit in different modules -- the index is filled from render bounds and read by
 * culling today, by AI range queries tomorrow -- and a component is the one thing all of them
 * already have a handle to.
 *
 * A scene with no maintainer has no index, and every consumer falls back to whatever it did
 * before one existed. That is what keeps this an optimisation rather than a requirement.
 *
 * [grid] keeps the type it shipped with in 0.27 so code compiled against 0.27 keeps working; the
 * grid that does the work is `grid.core`, a `com.awakekt.awake.core.spatial.SpatialGrid`. At 1.0
 * [grid] becomes the core type.
 *
 * @property grid The underlying uniform spatial partitioning grid.
 */
data class SpatialIndex(
    val grid: SpatialGrid = SpatialGrid(),
) {
    /** An index over [grid], a core grid built by the caller, for a cell size other than the default. */
    constructor(grid: CoreSpatialGrid) : this(SpatialGrid(grid))
}

/** This scene's [SpatialIndex], or null when nothing maintains one. */
fun World.findSpatialIndex(): SpatialIndex? {
    var found: SpatialIndex? = null
    queryEach<SpatialIndex> { _, index ->
        if (found == null) found = index
    }
    return found
}

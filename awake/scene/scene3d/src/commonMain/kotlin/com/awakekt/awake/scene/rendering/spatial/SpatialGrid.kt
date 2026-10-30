/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.spatial

import com.awakekt.awake.render.passes.ShadowCasterVolume

/** Moved to `com.awakekt.awake.core:spatial`; this constant is removed in a future release. */
@Deprecated(
    "Moved to com.awakekt.awake.core.spatial (com.awakekt.awake.core:spatial).",
    ReplaceWith("DEFAULT_SPATIAL_CELL_SIZE", "com.awakekt.awake.core.spatial.DEFAULT_SPATIAL_CELL_SIZE"),
)
const val DEFAULT_SPATIAL_CELL_SIZE: Float = com.awakekt.awake.core.spatial.DEFAULT_SPATIAL_CELL_SIZE

/** Moved to `com.awakekt.awake.core:spatial`; this alias is removed in a future release. */
@Deprecated(
    "Moved to com.awakekt.awake.core.spatial (com.awakekt.awake.core:spatial).",
    ReplaceWith("SpatialGrid", "com.awakekt.awake.core.spatial.SpatialGrid"),
)
typealias SpatialGrid = com.awakekt.awake.core.spatial.SpatialGrid

/**
 * Moved to [com.awakekt.awake.core.spatial.SpatialGrid.queryVolume]; this forwarder is removed in a
 * future release.
 *
 * @param volume The shadow-caster volume to test indexed bounds against.
 * @param out Destination collection to append matching entity ids to.
 * @return [out] after appending every admitted entity id.
 */
@Deprecated(
    "Use queryVolume on com.awakekt.awake.core.spatial.SpatialGrid.",
    ReplaceWith("queryVolume(volume, out)"),
)
fun com.awakekt.awake.core.spatial.SpatialGrid.queryShadowCasters(
    volume: ShadowCasterVolume,
    out: MutableCollection<Int>,
): MutableCollection<Int> = queryVolume(volume, out)

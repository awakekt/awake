/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.spatial

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.ShadowCasterVolume
import kotlin.jvm.JvmOverloads
import com.awakekt.awake.core.spatial.DEFAULT_SPATIAL_CELL_SIZE as CORE_DEFAULT_SPATIAL_CELL_SIZE
import com.awakekt.awake.core.spatial.SpatialGrid as CoreSpatialGrid

/** Moved to `com.awakekt.awake.core:spatial`; this constant is removed at 1.0. */
@Deprecated(
    "Moved to com.awakekt.awake.core.spatial (com.awakekt.awake.core:spatial).",
    ReplaceWith("DEFAULT_SPATIAL_CELL_SIZE", "com.awakekt.awake.core.spatial.DEFAULT_SPATIAL_CELL_SIZE"),
)
const val DEFAULT_SPATIAL_CELL_SIZE: Float = CORE_DEFAULT_SPATIAL_CELL_SIZE

/**
 * The scene-side name of the grid, kept as a real class so code compiled against 0.27 still finds
 * it; removed at 1.0.
 *
 * Every member forwards to [core], the `com.awakekt.awake.core.spatial.SpatialGrid` that does the
 * work, so a query through this class allocates nothing the core query does not. New code builds
 * the core grid directly, and reaches the one inside a [SpatialIndex] through [core].
 *
 * @property core The core grid every call forwards to.
 */
@Deprecated(
    "Moved to com.awakekt.awake.core.spatial.SpatialGrid (com.awakekt.awake.core:spatial).",
    ReplaceWith("SpatialGrid", "com.awakekt.awake.core.spatial.SpatialGrid"),
)
class SpatialGrid internal constructor(val core: CoreSpatialGrid) {

    /**
     * A grid of [cellSize]-wide columns; see `com.awakekt.awake.core.spatial.SpatialGrid`.
     *
     * Overloaded for the JVM because 0.27 declared this as a primary constructor with every
     * parameter defaulted, which emitted a no-argument constructor this secondary one would not.
     */
    @JvmOverloads
    constructor(cellSize: Float = CORE_DEFAULT_SPATIAL_CELL_SIZE) : this(CoreSpatialGrid(cellSize))

    /** Side of one column, in world units. */
    val cellSize: Float get() = core.cellSize

    /** The number of entities currently indexed. */
    val size: Int get() = core.size

    /** Every indexed id. */
    val ids: Set<Int> get() = core.ids

    /** Indexes [entityId] at [bounds], replacing whatever it was indexed under. */
    fun insert(entityId: Int, bounds: Aabb) = core.insert(entityId, bounds)

    /** Drops [entityId]. Nothing happens when it was not indexed. */
    fun remove(entityId: Int) = core.remove(entityId)

    /** Removes all entities, columns, and height extents. */
    fun clear() = core.clear()

    /** Ids whose bounds overlap [box], appended to [out]. */
    fun queryAabb(box: Aabb, out: MutableCollection<Int>): MutableCollection<Int> = core.queryAabb(box, out)

    /** Ids within [radius] of [center], appended to [out]. */
    fun queryRange(center: Vec3f, radius: Float, out: MutableCollection<Int>): MutableCollection<Int> =
        core.queryRange(center, radius, out)

    /** Ids whose bounds intersect [lens]'s frustum at [aspect], appended to [out]. */
    fun queryFrustum(lens: Lens, aspect: Float, out: MutableCollection<Int>): MutableCollection<Int> =
        core.queryFrustum(lens, aspect, out)

    /** Ids whose bounds [volume] admits, appended to [out]; the core grid's `queryVolume`. */
    fun queryShadowCasters(volume: ShadowCasterVolume, out: MutableCollection<Int>): MutableCollection<Int> =
        core.queryVolume(volume, out)

    /**
     * Kept only for their JVM fields: 0.27 emitted these as public static fields of this class,
     * and the core grid holds the values it uses.
     */
    @Suppress("unused")
    private companion object {
        const val INITIAL_STAMP_CAPACITY = 64
        const val INITIAL_COLUMN_CAPACITY = 4
    }
}

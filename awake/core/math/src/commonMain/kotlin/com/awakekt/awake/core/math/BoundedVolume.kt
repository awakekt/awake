/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

/**
 * A world-space volume that tests axis-aligned boxes for overlap and clips its own reach to the
 * bounds around a candidate set.
 *
 * Used by spatial indices such as `com.awakekt.awake.core.spatial.SpatialGrid` so a query volume
 * that is unbounded in one direction -- for instance, a directional shadow cascade reaching back
 * toward its light without limit -- still walks only the grid columns that overlap indexed content.
 */
interface BoundedVolume {
    /**
     * Whether [box] overlaps this volume.
     *
     * @param box The world-space axis-aligned bounding box to test.
     * @return `true` when [box] is at least partly inside this volume.
     */
    fun admits(box: Aabb): Boolean = admits(box.min.x, box.min.y, box.min.z, box.max.x, box.max.y, box.max.z)

    /**
     * [admits] for a box given as its min and max corner coordinates, without allocating an [Aabb].
     *
     * @param minX Minimum X coordinate of the box.
     * @param minY Minimum Y coordinate of the box.
     * @param minZ Minimum Z coordinate of the box.
     * @param maxX Maximum X coordinate of the box.
     * @param maxY Maximum Y coordinate of the box.
     * @param maxZ Maximum Z coordinate of the box.
     * @return `true` when the box is at least partly inside this volume.
     */
    @Suppress("LongParameterList")
    fun admits(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float): Boolean

    /**
     * Writes into [out] an axis-aligned box enclosing every part of this volume that lies inside
     * [content], or returns `false` (leaving [out] undefined) when no part of this volume overlaps
     * [content].
     *
     * @param content Bounding box around all candidates that could be tested.
     * @param out Destination box written in place when the intersection is non-empty.
     * @return `true` when [out] holds a non-empty bound; `false` when nothing in [content] can be
     * admitted.
     */
    fun bounds(content: Aabb, out: Aabb): Boolean
}

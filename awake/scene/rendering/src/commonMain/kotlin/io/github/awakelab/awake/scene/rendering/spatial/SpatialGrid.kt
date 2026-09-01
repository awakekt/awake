/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering.spatial

import io.github.awakelab.awake.core.math.Aabb
import io.github.awakelab.awake.core.math.Frustum
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.core.math.intersects
import io.github.awakelab.awake.core.math.planes
import io.github.awakelab.awake.core.math.squaredDistanceTo
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Side of one grid column, in world units. Sized so a typical prop lands in one or two. */
const val DEFAULT_SPATIAL_CELL_SIZE = 64f

/**
 * Which entities are where, so a query can visit a neighbourhood instead of the whole scene.
 *
 * A uniform grid of vertical columns keyed by (x, z), not an octree or a BVH. The world it is
 * for is wide, thin and evenly populated, which is exactly the distribution a uniform grid is
 * best at and the one a tree spends its structure on for nothing. Insert and move are hash
 * operations with no rebalancing, which matters more here than the tighter bounds a tree would
 * give: an open world moves things constantly.
 *
 * Columns rather than 3D cells for the same reason: a scene spread over kilometres is rarely
 * more than a few hundred metres tall, so a Y axis would mostly produce empty cells. The ceiling
 * that buys is real -- everything stacked in one column degrades to a linear scan of that
 * column -- and it is the trade a wide world wants.
 *
 * Holds entity ids rather than entities or components: this is `:awake:scene:scene-core`, and an
 * id is the one thing every layer above can resolve for itself.
 *
 * @property cellSize Side of one column. Too small wastes work walking empty columns; too large
 * makes every query a near-linear scan. One or two per typical object is the useful range.
 */
class SpatialGrid(val cellSize: Float = DEFAULT_SPATIAL_CELL_SIZE) {

    /** Column key (packed x/z) to the ids in it. */
    private val columns = HashMap<Long, MutableList<Int>>()

    /** Every id currently indexed, with the bounds and column span it was inserted under, so a
     * move can leave exactly the columns it used to occupy. */
    private val entries = HashMap<Int, Entry>()

    /** Per column, `[minY, maxY]` over everything ever inserted into it -- see [columnBounds]. */
    private val columnHeights = HashMap<Long, FloatArray>()

    /**
     * Per-id stamp marking "already emitted by this query".
     *
     * An entity spanning four columns is in four lists, and a query walking those columns would
     * otherwise return it four times. A stamp array rather than a `HashSet` per query, because a
     * query runs per frame and a set would allocate one per frame.
     */
    private var stamps = IntArray(INITIAL_STAMP_CAPACITY)
    private var stamp = 0

    val size: Int get() = entries.size

    /**
     * Indexes [entityId] at [bounds], replacing whatever it was indexed under.
     *
     * Insert and move are one call on purpose: a caller updating a moved entity would otherwise
     * have to remember to remove it first, and forgetting leaves a stale entry the queries
     * happily return.
     */
    fun insert(entityId: Int, bounds: Aabb) {
        val minX = columnIndexOf(cellSize, bounds.min.x)
        val minZ = columnIndexOf(cellSize, bounds.min.z)
        val maxX = columnIndexOf(cellSize, bounds.max.x)
        val maxZ = columnIndexOf(cellSize, bounds.max.z)
        val existing = entries[entityId]
        if (existing != null) {
            // Same span means the same lists: rewriting them would be identical work with a
            // window where the entity is in neither, and a moving entity usually stays put.
            if (existing.spans(minX, minZ, maxX, maxZ)) {
                existing.bounds = bounds
                return
            }
            detach(entityId, existing)
        }
        entries[entityId] = Entry(bounds, minX, minZ, maxX, maxZ)
        forEachColumn(minX, minZ, maxX, maxZ) { key ->
            columns.getOrPut(key) { ArrayList(INITIAL_COLUMN_CAPACITY) }.add(entityId)
            val height = columnHeights.getOrPut(key) { floatArrayOf(bounds.min.y, bounds.max.y) }
            height[0] = min(height[0], bounds.min.y)
            height[1] = max(height[1], bounds.max.y)
        }
    }

    /** Drops [entityId]. Nothing happens when it was not indexed. */
    fun remove(entityId: Int) {
        val entry = entries.remove(entityId) ?: return
        detach(entityId, entry)
    }

    fun clear() {
        columns.clear()
        columnHeights.clear()
        entries.clear()
    }

    /** Every indexed id -- for a maintainer reconciling the grid against the entities that
     * still exist. */
    val ids: Set<Int> get() = entries.keys

    /**
     * Ids whose bounds overlap [box], appended to [out].
     *
     * Appended rather than returned fresh so a per-frame caller reuses one list. The result is
     * exact, not a candidate set: the column walk narrows, and each candidate's own bounds
     * decide.
     */
    fun queryAabb(box: Aabb, out: MutableCollection<Int>): MutableCollection<Int> {
        stamp += 1
        forEachColumn(
            columnIndexOf(cellSize, box.min.x),
            columnIndexOf(cellSize, box.min.z),
            columnIndexOf(cellSize, box.max.x),
            columnIndexOf(cellSize, box.max.z),
        ) { key ->
            val column = columns[key] ?: return@forEachColumn
            for (index in column.indices) {
                val id = column[index]
                if (claim(id)) {
                    val bounds = entries[id]?.bounds ?: continue
                    if (bounds.intersects(box)) out.add(id)
                }
            }
        }
        return out
    }

    /**
     * Ids within [radius] of [center], appended to [out].
     *
     * Bounds-versus-sphere, so an entity counts when any part of it is in range rather than only
     * when its centre is -- the same rule [queryAabb] uses, and the one that stops a large object
     * disappearing from a query it obviously overlaps.
     */
    fun queryRange(center: Vec3f, radius: Float, out: MutableCollection<Int>): MutableCollection<Int> {
        val box = Aabb(
            Vec3f(center.x - radius, center.y - radius, center.z - radius),
            Vec3f(center.x + radius, center.y + radius, center.z + radius),
        )
        stamp += 1
        val radiusSquared = radius * radius
        forEachColumn(
            columnIndexOf(cellSize, box.min.x),
            columnIndexOf(cellSize, box.min.z),
            columnIndexOf(cellSize, box.max.x),
            columnIndexOf(cellSize, box.max.z),
        ) { key ->
            val column = columns[key] ?: return@forEachColumn
            for (index in column.indices) {
                val id = column[index]
                if (claim(id)) {
                    val bounds = entries[id]?.bounds ?: continue
                    if (bounds.squaredDistanceTo(center) <= radiusSquared) out.add(id)
                }
            }
        }
        return out
    }

    /**
     * Ids whose bounds intersect [lens]'s frustum at [aspect], appended to [out].
     *
     * Two levels, both through the same [Frustum.intersects] the caller would have used itself:
     * a column's own box first, then each entity in a surviving column. Using one predicate for
     * both is what makes the result identical to testing every entity -- a cheaper approximation
     * at the column level would cull things the per-entity test would have kept.
     */
    fun queryFrustum(lens: Lens, aspect: Float, out: MutableCollection<Int>): MutableCollection<Int> {
        val corners = Frustum.corners(lens, aspect)
        var minX = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        for (corner in corners) {
            minX = min(minX, corner.x)
            maxX = max(maxX, corner.x)
            minZ = min(minZ, corner.z)
            maxZ = max(maxZ, corner.z)
        }
        // Once, not per column and per entity: Frustum.intersects(lens, aspect, box) rebuilds
        // six planes on every call, which at two levels of testing costs more than the culling
        // saves.
        val planes = Frustum.planes(lens, aspect)
        stamp += 1
        forEachColumn(columnIndexOf(cellSize, minX), columnIndexOf(cellSize, minZ), columnIndexOf(cellSize, maxX), columnIndexOf(cellSize, maxZ)) { key ->
            val column = columns[key] ?: return@forEachColumn
            if (!planes.intersects(columnBounds(key))) return@forEachColumn
            for (index in column.indices) {
                val id = column[index]
                if (claim(id)) {
                    val bounds = entries[id]?.bounds ?: continue
                    if (planes.intersects(bounds)) out.add(id)
                }
            }
        }
        return out
    }

    private fun detach(entityId: Int, entry: Entry) {
        forEachColumn(entry.minX, entry.minZ, entry.maxX, entry.maxZ) { key ->
            val column = columns[key] ?: return@forEachColumn
            column.remove(entityId)
            if (column.isEmpty()) columns.remove(key)
        }
    }

    /** True the first time this query sees [entityId]. */
    private fun claim(entityId: Int): Boolean {
        if (entityId >= stamps.size) {
            stamps = stamps.copyOf(maxOf(stamps.size * 2, entityId + 1))
        }
        if (stamps[entityId] == stamp) return false
        stamps[entityId] = stamp
        return true
    }

    /**
     * A column's own box: its footprint, and the Y range of everything ever put in it.
     *
     * A real range rather than an infinite one, because a plane test on an infinite coordinate
     * produces NaN wherever the plane's normal has no Y component, and NaN compares false --
     * which would silently cull whole columns.
     *
     * The range only ever grows: an entity leaving does not shrink it. That over-includes after
     * a tall object is removed, which costs a column of per-entity tests and can never cull
     * something visible. Shrinking would mean recomputing from the column's remaining members on
     * every removal, for a bound that is about to grow again.
     */
    private fun columnBounds(key: Long): Aabb {
        val x = (key shr Int.SIZE_BITS).toInt()
        val z = key.toInt()
        val height = columnHeights[key] ?: EMPTY_HEIGHT
        return Aabb(
            Vec3f(x * cellSize, height[0], z * cellSize),
            Vec3f((x + 1) * cellSize, height[1], (z + 1) * cellSize),
        )
    }

    private inline fun forEachColumn(minX: Int, minZ: Int, maxX: Int, maxZ: Int, block: (Long) -> Unit) {
        for (z in minZ..maxZ) {
            for (x in minX..maxX) {
                block(columnKeyOf(x, z))
            }
        }
    }

    private class Entry(
        var bounds: Aabb,
        val minX: Int,
        val minZ: Int,
        val maxX: Int,
        val maxZ: Int,
    ) {
        fun spans(minX: Int, minZ: Int, maxX: Int, maxZ: Int): Boolean =
            this.minX == minX && this.minZ == minZ && this.maxX == maxX && this.maxZ == maxZ
    }

    private companion object {
        const val INITIAL_STAMP_CAPACITY = 64
        const val INITIAL_COLUMN_CAPACITY = 4
        /** A column with no members has no height; an empty range contains nothing, which is
         * what a query should find there. */
        val EMPTY_HEIGHT = floatArrayOf(0f, 0f)
    }
}

/** Which column [coordinate] falls in, for a grid of [cellSize] columns. */
private fun columnIndexOf(cellSize: Float, coordinate: Float): Int = floor(coordinate / cellSize).toInt()

/** One `Long` rather than a data class key, so a query allocates nothing per column. */
private fun columnKeyOf(x: Int, z: Int): Long =
    (x.toLong() shl Int.SIZE_BITS) or (z.toLong() and INT_MASK)

private const val INT_MASK = 0xFFFFFFFFL

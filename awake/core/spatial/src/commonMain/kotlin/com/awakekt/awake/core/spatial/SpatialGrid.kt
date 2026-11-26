/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.spatial

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.BoundedVolume
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.intersects
import com.awakekt.awake.core.math.squaredDistanceTo
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Side of one grid column, in world units. Sized so a typical prop lands in one or two. */
const val DEFAULT_SPATIAL_CELL_SIZE = 64f

/**
 * Which objects are where, so a query can visit a neighbourhood instead of the whole world.
 *
 * A uniform grid of vertical columns keyed by (x, z), backed by primitive open-addressing tables
 * ([LongColumnMap], [IntEntryMap]) and a reusable [ScratchFrustum] so per-frame frustum and
 * bounded-volume queries into [IntIdSet] allocate zero bytes on JVM and Kotlin/Wasm.
 *
 * @property cellSize Side of one column. Too small wastes work walking empty columns; too large
 * makes every query a near-linear scan. One or two per typical object is the useful range.
 */
@Suppress("TooManyFunctions") // Symmetric MutableCollection<Int> and zero-boxing IntIdSet query overloads.
class SpatialGrid(val cellSize: Float = DEFAULT_SPATIAL_CELL_SIZE) {

    /** Primitive column key (packed x/z) to its member ID array and Y-height envelope. */
    private val columns = LongColumnMap()

    /** Every id currently indexed, with its bounds and column span (primitive Int keys, zero boxing). */
    private val entries = IntEntryMap<Entry>()

    /** Per-id stamp marking "already emitted by this query". */
    private var stamps = IntArray(INITIAL_STAMP_CAPACITY)
    private var stamp = 0

    private var contentMinColumnX = Int.MAX_VALUE
    private var contentMinColumnZ = Int.MAX_VALUE
    private var contentMaxColumnX = Int.MIN_VALUE
    private var contentMaxColumnZ = Int.MIN_VALUE
    private var contentMinY = Float.POSITIVE_INFINITY
    private var contentMaxY = Float.NEGATIVE_INFINITY
    private val contentBounds = Aabb(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 0f))

    /** Scratch buffers owned by the grid so per-frame queries allocate nothing. */
    private val volumeReach = Aabb(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 0f))
    private val scratchFrustum = ScratchFrustum()

    /** The number of entries currently indexed in this spatial grid. */
    val size: Int get() = entries.size

    /**
     * Indexes [entityId] at [bounds], replacing whatever it was indexed under.
     */
    fun insert(entityId: Int, bounds: Aabb) {
        val minX = columnIndexOf(cellSize, bounds.min.x)
        val minZ = columnIndexOf(cellSize, bounds.min.z)
        val maxX = columnIndexOf(cellSize, bounds.max.x)
        val maxZ = columnIndexOf(cellSize, bounds.max.z)
        val existing = entries[entityId]
        if (existing != null && existing.spans(minX, minZ, maxX, maxZ)) {
            existing.bounds = bounds
            if (bounds.min.y < existing.foldedMinY || bounds.max.y > existing.foldedMaxY) {
                if (bounds.min.y < contentMinY) contentMinY = bounds.min.y
                if (bounds.max.y > contentMaxY) contentMaxY = bounds.max.y
                existing.widenHeights(bounds, columns)
            }
            return
        }
        if (existing != null) detach(entityId, existing)
        entries[entityId] = Entry(bounds, minX, minZ, maxX, maxZ)
        widenContentBounds(minX, minZ, maxX, maxZ, bounds.min.y, bounds.max.y)
        forEachColumn(minX, minZ, maxX, maxZ) { key ->
            val column = columns.getOrCreate(key, bounds.min.y, bounds.max.y)
            column.add(entityId)
            column.widen(bounds.min.y, bounds.max.y)
        }
    }

    /** Drops [entityId]. Nothing happens when it was not indexed. */
    fun remove(entityId: Int) {
        val entry = entries.remove(entityId) ?: return
        detach(entityId, entry)
    }

    /** Removes all entries, columns, and height extents from this spatial grid. */
    fun clear() {
        columns.clear()
        entries.clear()
        contentMinColumnX = Int.MAX_VALUE
        contentMinColumnZ = Int.MAX_VALUE
        contentMaxColumnX = Int.MIN_VALUE
        contentMaxColumnZ = Int.MIN_VALUE
        contentMinY = Float.POSITIVE_INFINITY
        contentMaxY = Float.NEGATIVE_INFINITY
    }

    /** Every indexed id -- for a maintainer reconciling the grid against the objects that still exist. */
    val ids: Set<Int> get() = entries.keySet()

    /**
     * Ids whose bounds overlap [box], appended to [out].
     */
    fun queryAabb(box: Aabb, out: MutableCollection<Int>): MutableCollection<Int> {
        if (contentMinColumnX > contentMaxColumnX) return out
        stamp += 1
        val minX = max(columnIndexOf(cellSize, box.min.x), contentMinColumnX)
        val minZ = max(columnIndexOf(cellSize, box.min.z), contentMinColumnZ)
        val maxX = min(columnIndexOf(cellSize, box.max.x), contentMaxColumnX)
        val maxZ = min(columnIndexOf(cellSize, box.max.z), contentMaxColumnZ)
        forEachColumn(minX, minZ, maxX, maxZ) { key ->
            val column = columns[key] ?: return@forEachColumn
            val ids = column.ids
            for (index in 0 until column.size) {
                val id = ids[index]
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
     */
    fun queryRange(center: Vec3f, radius: Float, out: MutableCollection<Int>): MutableCollection<Int> {
        if (contentMinColumnX > contentMaxColumnX) return out
        stamp += 1
        val radiusSquared = radius * radius
        val minX = max(columnIndexOf(cellSize, center.x - radius), contentMinColumnX)
        val minZ = max(columnIndexOf(cellSize, center.z - radius), contentMinColumnZ)
        val maxX = min(columnIndexOf(cellSize, center.x + radius), contentMaxColumnX)
        val maxZ = min(columnIndexOf(cellSize, center.z + radius), contentMaxColumnZ)
        forEachColumn(minX, minZ, maxX, maxZ) { key ->
            val column = columns[key] ?: return@forEachColumn
            val ids = column.ids
            for (index in 0 until column.size) {
                val id = ids[index]
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
     */
    fun queryFrustum(lens: Lens, aspect: Float, out: MutableCollection<Int>): MutableCollection<Int> {
        forEachFrustumHit(lens, aspect) { out.add(it) }
        return out
    }

    /**
     * Non-boxing twin of [queryFrustum] that writes matching primitive IDs straight into [out].
     */
    fun queryFrustum(lens: Lens, aspect: Float, out: IntIdSet): IntIdSet {
        forEachFrustumHit(lens, aspect) { out.add(it) }
        return out
    }

    private inline fun forEachFrustumHit(lens: Lens, aspect: Float, onHit: (Int) -> Unit) {
        if (contentMinColumnX > contentMaxColumnX) return
        val frustum = scratchFrustum
        frustum.set(lens, aspect)
        val minX = max(columnIndexOf(cellSize, frustum.minX), contentMinColumnX)
        val minZ = max(columnIndexOf(cellSize, frustum.minZ), contentMinColumnZ)
        val maxX = min(columnIndexOf(cellSize, frustum.maxX), contentMaxColumnX)
        val maxZ = min(columnIndexOf(cellSize, frustum.maxZ), contentMaxColumnZ)
        if (minX > maxX || minZ > maxZ) return
        stamp += 1
        for (cz in minZ..maxZ) {
            val z0 = cz * cellSize
            val z1 = (cz + 1) * cellSize
            for (cx in minX..maxX) {
                val column = columns[columnKeyOf(cx, cz)]
                if (column != null && column.size > 0 &&
                    frustum.intersects(cx * cellSize, column.minY, z0, (cx + 1) * cellSize, column.maxY, z1)
                ) {
                    visitColumnFrustum(column, frustum, onHit)
                }
            }
        }
    }

    private inline fun visitColumnFrustum(column: SpatialColumn, frustum: ScratchFrustum, onHit: (Int) -> Unit) {
        val ids = column.ids
        for (index in 0 until column.size) {
            val id = ids[index]
            if (claim(id)) {
                val bounds = entries[id]?.bounds ?: continue
                if (frustum.intersects(bounds)) onHit(id)
            }
        }
    }

    /**
     * Ids whose bounds [volume] admits, appended to [out].
     */
    fun queryVolume(volume: BoundedVolume, out: MutableCollection<Int>): MutableCollection<Int> {
        forEachVolumeHit(volume) { out.add(it) }
        return out
    }

    /**
     * Non-boxing twin of [queryVolume] that writes matching primitive IDs straight into [out].
     */
    fun queryVolume(volume: BoundedVolume, out: IntIdSet): IntIdSet {
        forEachVolumeHit(volume) { out.add(it) }
        return out
    }

    private inline fun forEachVolumeHit(volume: BoundedVolume, onHit: (Int) -> Unit) {
        val hasContent = contentMinColumnX <= contentMaxColumnX
        if (hasContent) {
            contentBounds.min.set(contentMinColumnX * cellSize, contentMinY, contentMinColumnZ * cellSize)
            contentBounds.max.set((contentMaxColumnX + 1) * cellSize, contentMaxY, (contentMaxColumnZ + 1) * cellSize)
        }
        if (!hasContent || !volume.bounds(contentBounds, volumeReach)) return
        val minX = max(columnIndexOf(cellSize, volumeReach.min.x), contentMinColumnX)
        val minZ = max(columnIndexOf(cellSize, volumeReach.min.z), contentMinColumnZ)
        val maxX = min(columnIndexOf(cellSize, volumeReach.max.x), contentMaxColumnX)
        val maxZ = min(columnIndexOf(cellSize, volumeReach.max.z), contentMaxColumnZ)
        if (minX > maxX || minZ > maxZ) return
        stamp += 1
        for (cz in minZ..maxZ) {
            val z0 = cz * cellSize
            val z1 = (cz + 1) * cellSize
            for (cx in minX..maxX) {
                val column = columns[columnKeyOf(cx, cz)]
                if (column != null && column.size > 0 &&
                    volume.admits(cx * cellSize, column.minY, z0, (cx + 1) * cellSize, column.maxY, z1)
                ) {
                    visitColumnVolume(column, volume, onHit)
                }
            }
        }
    }

    private inline fun visitColumnVolume(column: SpatialColumn, volume: BoundedVolume, onHit: (Int) -> Unit) {
        val ids = column.ids
        for (index in 0 until column.size) {
            val id = ids[index]
            if (claim(id)) {
                val bounds = entries[id]?.bounds ?: continue
                if (volume.admits(bounds)) onHit(id)
            }
        }
    }

    @Suppress("LongParameterList")
    private fun widenContentBounds(minX: Int, minZ: Int, maxX: Int, maxZ: Int, minY: Float, maxY: Float) {
        if (minX < contentMinColumnX) contentMinColumnX = minX
        if (minZ < contentMinColumnZ) contentMinColumnZ = minZ
        if (maxX > contentMaxColumnX) contentMaxColumnX = maxX
        if (maxZ > contentMaxColumnZ) contentMaxColumnZ = maxZ
        if (minY < contentMinY) contentMinY = minY
        if (maxY > contentMaxY) contentMaxY = maxY
    }

    private fun detach(entityId: Int, entry: Entry) {
        forEachColumn(entry.minX, entry.minZ, entry.maxX, entry.maxZ) { key ->
            columns[key]?.remove(entityId)
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

    private class Entry(
        var bounds: Aabb,
        val minX: Int,
        val minZ: Int,
        val maxX: Int,
        val maxZ: Int,
    ) {
        var foldedMinY: Float = bounds.min.y
        var foldedMaxY: Float = bounds.max.y

        fun spans(minX: Int, minZ: Int, maxX: Int, maxZ: Int): Boolean =
            this.minX == minX && this.minZ == minZ && this.maxX == maxX && this.maxZ == maxZ

        /** Folds [moved] into the folded range and into [columns] for every column of the span. */
        fun widenHeights(moved: Aabb, columns: LongColumnMap) {
            foldedMinY = min(foldedMinY, moved.min.y)
            foldedMaxY = max(foldedMaxY, moved.max.y)
            forEachColumn(minX, minZ, maxX, maxZ) { key ->
                columns[key]?.widen(foldedMinY, foldedMaxY)
            }
        }
    }

    private companion object {
        const val INITIAL_STAMP_CAPACITY = 64
        const val INITIAL_COLUMN_CAPACITY = 4
    }
}

/** Every column key in the span, row by row. */
private inline fun forEachColumn(minX: Int, minZ: Int, maxX: Int, maxZ: Int, block: (Long) -> Unit) {
    for (z in minZ..maxZ) {
        for (x in minX..maxX) {
            block(columnKeyOf(x, z))
        }
    }
}

/** Which column [coordinate] falls in, for a grid of [cellSize] columns. */
private fun columnIndexOf(cellSize: Float, coordinate: Float): Int = floor(coordinate / cellSize).toInt()

/** Packs column coordinates `(x, z)` into one primitive `Long` key. */
private fun columnKeyOf(x: Int, z: Int): Long =
    (x.toLong() shl Int.SIZE_BITS) or (z.toLong() and INT_MASK)

private const val INT_MASK = 0xFFFFFFFFL

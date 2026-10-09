/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain

import kotlin.math.floor

private const val UNSIGNED_16_MAX = 65535f

/**
 * Absolute square page identifier, independent of a scene or its floating origin.
 * @property x Absolute cell coordinate along X.
 * @property z Absolute cell coordinate along Z.
 */
data class TerrainPageCoord(val x: Int, val z: Int)

/** Fixed page lattice. Height samples include both endpoints of a cell. */
data class TerrainPageLayout(
    /** First absolute cell coordinate along X. */
    val minCellX: Int,
    /** First absolute cell coordinate along Z. */
    val minCellZ: Int,
    /** Number of cells along X. */
    val cellCountX: Int,
    /** Number of cells along Z. */
    val cellCountZ: Int,
    /** World-space side length of each square cell. */
    val cellSize: Float,
    /** Fine sample intervals per cell edge; pages store one more sample. */
    val intervals: Int,
    /** Vertical world-space scale applied to raw elevations. */
    val heightScale: Float = 1f,
    /** Minimum raw elevation encoded as unsigned zero. */
    val minElevation: Float = 0f,
    /** Maximum raw elevation encoded as unsigned 65535. */
    val maxElevation: Float = 1f,
) {
    init {
        require(cellCountX > 0 && cellCountZ > 0 && intervals >= 1)
        require(cellSize.isFinite() && cellSize > 0f && heightScale.isFinite() && heightScale > 0f)
        require(minElevation.isFinite() && maxElevation.isFinite() && maxElevation > minElevation)
        require(((maxElevation - minElevation) * heightScale).isFinite())
        require((cellSize / intervals).isFinite() && cellSize / intervals > 0f)
        require((minElevation * heightScale).isFinite() && (maxElevation * heightScale).isFinite())
        require(minCellX.toLong() + cellCountX - 1 <= Int.MAX_VALUE)
        require(minCellZ.toLong() + cellCountZ - 1 <= Int.MAX_VALUE)
        require(cellCountX.toLong() * intervals < Int.MAX_VALUE && cellCountZ.toLong() * intervals < Int.MAX_VALUE)
        require((intervals.toLong() + 1) * (intervals + 1L) <= Int.MAX_VALUE)
        require((cellCountX.toDouble() * cellSize).toFloat().isFinite() && (cellCountZ.toDouble() * cellSize).toFloat().isFinite())
    }

    /** Samples per page edge, including both endpoints. */
    val samplesPerPage: Int get() = intervals + 1

    /** World-space distance between fine samples. */
    val sampleSpacing: Float get() = cellSize / intervals

    /** Fine intervals across the footprint along X. */
    val worldIntervalsX: Int get() = cellCountX * intervals

    /** Fine intervals across the footprint along Z. */
    val worldIntervalsZ: Int get() = cellCountZ * intervals

    /** Absolute X coordinate of the footprint start. */
    val minX: Double get() = minCellX.toDouble() * cellSize

    /** Absolute Z coordinate of the footprint start. */
    val minZ: Double get() = minCellZ.toDouble() * cellSize

    /** Whether the absolute cell belongs to this footprint. */
    fun contains(coord: TerrainPageCoord): Boolean =
        coord.x.toLong() - minCellX in 0L until cellCountX.toLong() &&
            coord.z.toLong() - minCellZ in 0L until cellCountZ.toLong()

    /** Positive-side ownership, except the last sample on the world boundary. */
    fun owner(x: Int, z: Int): TerrainPageCoord {
        require(x in 0..worldIntervalsX && z in 0..worldIntervalsZ)
        return TerrainPageCoord(minCellX + minOf(x / intervals, cellCountX - 1), minCellZ + minOf(z / intervals, cellCountZ - 1))
    }

    /** Every stored copy of one global sample, including shared edges and corners. */
    fun copies(x: Int, z: Int): Set<TerrainPageCoord> {
        val owner = owner(x, z)
        val xs = mutableListOf(owner.x)
        val zs = mutableListOf(owner.z)
        if (x > 0 && x < worldIntervalsX && x % intervals == 0) xs += owner.x - 1
        if (z > 0 && z < worldIntervalsZ && z % intervals == 0) zs += owner.z - 1
        return buildSet { for (cz in zs) for (cx in xs) add(TerrainPageCoord(cx, cz)) }
    }

    /** Rejects page dimensions, scales or elevations incompatible with this lattice. */
    fun validate(page: Heightmap) {
        require(page.width == samplesPerPage && page.depth == samplesPerPage) { "Height page must be $samplesPerPage square." }
        val scale = page.scale
        require(scale.x == sampleSpacing && scale.z == sampleSpacing && scale.y == heightScale) { "Height page scale disagrees with its lattice." }
        require(page.copySamples().all { it in minElevation..maxElevation }) { "Height page exceeds its encoding range." }
    }
}

/**
 * An atomic edit's per-page dirty rectangles and current coarse fallback.
 * @property revision Current height state revision.
 * @property pages Changed cells and their dirty rectangles.
 * @property fallbackChanged Whether a nested coarse knot changed.
 */
data class PagedHeightmapChange(val revision: Long, val pages: Map<TerrainPageCoord, HeightmapDirtyRegion>, val fallbackChanged: Boolean)

/**
 * Sparse editable height pages over a mandatory nested coarse fallback. Single-owner state;
 * workers supply immutable [Heightmap] values and never mutate this object.
 * @property layout Fixed page lattice and encoding range.
 * @param fallback Mandatory immutable coarse height image.
 */
class PagedHeightmap(val layout: TerrainPageLayout, fallback: Heightmap) {
    private val pages = LinkedHashMap<TerrainPageCoord, Heightmap>()

    /** Current immutable coarse height image. */
    var fallback: Heightmap = fallback
        private set

    /** Monotonically increasing version of this state. */
    var revision: Long = 0L
        private set

    init {
        require(fallback.width >= 2 && fallback.depth >= 2)
        require(layout.worldIntervalsX % (fallback.width - 1) == 0 && layout.worldIntervalsZ % (fallback.depth - 1) == 0)
        require(layout.worldIntervalsX / (fallback.width - 1) == layout.worldIntervalsZ / (fallback.depth - 1)) { "Fallback lattice must be nested and square-spaced." }
        val scale = fallback.scale
        require(scale.x == layout.cellCountX * layout.cellSize / (fallback.width - 1))
        require(scale.z == layout.cellCountZ * layout.cellSize / (fallback.depth - 1) && scale.y == layout.heightScale)
        require(fallback.copySamples().all { it in layout.minElevation..layout.maxElevation })
    }

    /** Snapshot of currently resident cell coordinates. */
    val residentCoords: Set<TerrainPageCoord> get() = pages.keys.toSet()

    /** Returns the immutable resident page, or null when it uses fallback. */
    fun page(coord: TerrainPageCoord): Heightmap? = pages[coord]

    /** Rejects mismatched shared edges before making the page visible. */
    fun put(coord: TerrainPageCoord, page: Heightmap) {
        require(layout.contains(coord))
        layout.validate(page)
        val n = layout.intervals
        for (z in 0..n) {
            for (x in 0..n) {
                if (x in 1 until n && z in 1 until n) continue
                val gx = (coord.x - layout.minCellX) * n + x
                val gz = (coord.z - layout.minCellZ) * n + z
                validateNeighbours(coord, page, gx, gz)
            }
        }
        pages[coord] = page
        revision++
    }

    private fun validateNeighbours(coord: TerrainPageCoord, page: Heightmap, gx: Int, gz: Int) {
        val n = layout.intervals
        for (other in layout.copies(gx, gz).filter { it != coord }) {
            val neighbour = pages[other] ?: continue
            require(page.heightAt(gx - (coord.x - layout.minCellX) * n, gz - (coord.z - layout.minCellZ) * n) == neighbour.heightAt(gx - (other.x - layout.minCellX) * n, gz - (other.z - layout.minCellZ) * n)) {
                "Height page $coord disagrees with neighbour $other at sample ($gx, $gz)."
            }
        }
    }

    /** Removes a resident page and advances the revision when present. */
    fun remove(coord: TerrainPageCoord): Heightmap? = pages.remove(coord)?.also { revision++ }

    /** Raw elevation at one global fine-grid knot; missing canonical owners use fallback. */
    fun sample(x: Int, z: Int): Float {
        val coord = layout.owner(x, z)
        val page = pages[coord]
        if (page != null) return page.heightAt(x - (coord.x - layout.minCellX) * layout.intervals, z - (coord.z - layout.minCellZ) * layout.intervals)
        val stride = layout.worldIntervalsX / (fallback.width - 1)
        return interpolate(fallback, x.toFloat() / stride, z.toFloat() / stride)
    }

    /** Absolute world-space query; NaN outside the index footprint. */
    fun heightAtWorld(x: Double, z: Double): Float {
        // Grid coordinates stay in double precision: on a large footprint a Float loses the sub-sample fraction.
        val gx = (x - layout.minX) / layout.sampleSpacing
        val gz = (z - layout.minZ) / layout.sampleSpacing
        if (gx !in 0.0..layout.worldIntervalsX.toDouble() || gz !in 0.0..layout.worldIntervalsZ.toDouble()) return Float.NaN
        val ix = floor(gx).toInt()
        val iz = floor(gz).toInt()
        val nx = minOf(ix + 1, layout.worldIntervalsX)
        val nz = minOf(iz + 1, layout.worldIntervalsZ)
        val fx = (gx - ix).toFloat()
        val fz = (gz - iz).toFloat()
        val a = sample(ix, iz)
        val b = sample(nx, iz)
        val c = sample(ix, nz)
        val d = sample(nx, nz)
        return ((a + (b - a) * fx) * (1f - fz) + (c + (d - c) * fx) * fz) * layout.heightScale
    }

    /** Global sample edits. All shared copies must be loaded; validation precedes every write. */
    fun apply(edits: List<HeightmapSampleEdit>): PagedHeightmapChange? {
        val snapped = edits.map { edit ->
            require(edit.height.isFinite() && edit.height in layout.minElevation..layout.maxElevation)
            // The value the 16-bit page codec writes back, so a saved and reloaded copy still equals a resident neighbour.
            edit.copy(height = quantize(edit.height))
        }
        val perPage = LinkedHashMap<TerrainPageCoord, MutableList<HeightmapSampleEdit>>()
        for (edit in snapped) {
            for (coord in layout.copies(edit.x, edit.z)) {
                require(coord in pages) { "Load and pin page $coord before editing a shared sample." }
                perPage.getOrPut(coord) { mutableListOf() } += HeightmapSampleEdit(
                    edit.x - (coord.x - layout.minCellX) * layout.intervals,
                    edit.z - (coord.z - layout.minCellZ) * layout.intervals,
                    edit.height,
                )
            }
        }
        val changed = LinkedHashMap<TerrainPageCoord, HeightmapDirtyRegion>()
        val replacements = LinkedHashMap<TerrainPageCoord, Heightmap>()
        for ((coord, batch) in perPage) {
            val mutable = pages.getValue(coord).mutableCopy()
            val change = mutable.apply(batch) ?: continue
            changed[coord] = change.dirtyRegion
            replacements[coord] = mutable.snapshot()
        }
        if (changed.isEmpty()) return null
        val stride = layout.worldIntervalsX / (fallback.width - 1)
        val coarseEdits = snapped.filter { it.x % stride == 0 && it.z % stride == 0 }
            .map { HeightmapSampleEdit(it.x / stride, it.z / stride, it.height) }
        val coarse = fallback.mutableCopy()
        val coarseChange = coarse.apply(coarseEdits)
        pages.putAll(replacements)
        if (coarseChange != null) fallback = coarse.snapshot()
        revision++
        return PagedHeightmapChange(revision, changed, coarseChange != null)
    }

    /** Mirrors RawHeightmapCodec's unsigned 16-bit round trip over the lattice's elevation range. */
    private fun quantize(height: Float): Float {
        val range = layout.maxElevation - layout.minElevation
        val value = (((height - layout.minElevation) / range).coerceIn(0f, 1f) * UNSIGNED_16_MAX + 0.5f).toInt().coerceIn(0, 0xFFFF)
        return layout.minElevation + value / UNSIGNED_16_MAX * range
    }

    private fun interpolate(map: Heightmap, x: Float, z: Float): Float {
        val ix = floor(x).toInt().coerceIn(0, map.width - 1)
        val iz = floor(z).toInt().coerceIn(0, map.depth - 1)
        val nx = minOf(ix + 1, map.width - 1)
        val nz = minOf(iz + 1, map.depth - 1)
        val a = map.heightAt(ix, iz)
        val b = map.heightAt(nx, iz)
        val c = map.heightAt(ix, nz)
        val d = map.heightAt(nx, nz)
        return (a + (b - a) * (x - ix)) * (1f - (z - iz)) + (c + (d - c) * (x - ix)) * (z - iz)
    }
}

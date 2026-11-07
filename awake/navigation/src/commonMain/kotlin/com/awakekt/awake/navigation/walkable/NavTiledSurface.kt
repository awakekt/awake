/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

/**
 * A large region as a lattice of [tilesX] by [tilesZ] square tiles, each [tileSamples] samples
 * across, searched as one [NavSurface].
 *
 * Tiles are baked, cached and replaced one at a time: bake each with [regionOf] so it lands where
 * the lattice expects, and [put] it when it is ready. A tile not yet put reads as blocked, so a route
 * stops at the edge of what is loaded rather than walking through it. Because [bakeNavSurface] looks
 * past a tile's border, tiles baked separately join without a seam.
 *
 * A fixed lattice rather than a map of coordinates, so finding the tile under a sample is two
 * divisions and an array read, with no boxing on the search's hot path. A world that streams without
 * bounds keeps one of these per streamed region.
 *
 * [put] and [remove] swap one reference, so a query running on another thread sees a tile either
 * before or after the swap, never half of it; it may see some tiles from before a batch of swaps and
 * some from after.
 *
 * @property tilesX Tiles along X.
 * @property tilesZ Tiles along Z.
 * @property tileSamples Samples along each side of a tile.
 * @property sampleSize Metres between samples.
 * @property originX World X of the lattice's first sample.
 * @property originZ World Z of the lattice's first sample.
 */
class NavTiledSurface(
    val tilesX: Int,
    val tilesZ: Int,
    val tileSamples: Int,
    override val sampleSize: Float,
    override val originX: Float = 0f,
    override val originZ: Float = 0f,
) : NavSurface {
    private val tiles = arrayOfNulls<NavSurfaceTile>(tilesX * tilesZ)

    init {
        require(tilesX > 0 && tilesZ > 0) { "NavTiledSurface must have at least one tile; was $tilesX x $tilesZ." }
        require(tileSamples > 0) { "NavTiledSurface tileSamples must be positive; was $tileSamples." }
        require(sampleSize > 0f && sampleSize.isFinite()) { "NavTiledSurface sampleSize must be positive and finite; was $sampleSize." }
    }

    /**
     * The region tile ([tileX], [tileZ]) covers, to bake it with [bakeNavSurface].
     *
     * @param tileX Tile column.
     * @param tileZ Tile row.
     * @return The tile's samples, placed in the world.
     */
    fun regionOf(tileX: Int, tileZ: Int): NavRegion {
        checkTile(tileX, tileZ)
        val metres = tileSamples * sampleSize
        return NavRegion(originX + tileX * metres, originZ + tileZ * metres, tileSamples, tileSamples, sampleSize)
    }

    /**
     * The tile at ([tileX], [tileZ]), or null when none is loaded.
     *
     * @param tileX Tile column.
     * @param tileZ Tile row.
     * @return The loaded tile, or null.
     */
    fun tileAt(tileX: Int, tileZ: Int): NavSurfaceTile? {
        checkTile(tileX, tileZ)
        return tiles[tileZ * tilesX + tileX]
    }

    /**
     * Loads [tile] at ([tileX], [tileZ]), replacing whatever was there.
     *
     * @param tileX Tile column.
     * @param tileZ Tile row.
     * @param tile A tile baked for [regionOf] of the same coordinates.
     * @throws IllegalArgumentException When the tile's size, spacing or origin do not match.
     */
    fun put(tileX: Int, tileZ: Int, tile: NavSurfaceTile) {
        val expected = regionOf(tileX, tileZ)
        require(tile.width == tileSamples && tile.depth == tileSamples && tile.sampleSize == sampleSize) {
            "Tile ($tileX, $tileZ) must be $tileSamples x $tileSamples at $sampleSize m; was ${tile.width} x ${tile.depth} at ${tile.sampleSize} m."
        }
        require(tile.originX == expected.originX && tile.originZ == expected.originZ) {
            "Tile ($tileX, $tileZ) must start at (${expected.originX}, ${expected.originZ}); was (${tile.originX}, ${tile.originZ})."
        }
        tiles[tileZ * tilesX + tileX] = tile
    }

    /**
     * Unloads the tile at ([tileX], [tileZ]); its samples read as blocked until another is put.
     *
     * @param tileX Tile column.
     * @param tileZ Tile row.
     */
    fun remove(tileX: Int, tileZ: Int) {
        checkTile(tileX, tileZ)
        tiles[tileZ * tilesX + tileX] = null
    }

    override fun clearance(x: Int, z: Int): Int {
        val tileX = if (x >= 0) x / tileSamples else -1
        val tileZ = if (z >= 0) z / tileSamples else -1
        val inside = tileX in 0 until tilesX && tileZ in 0 until tilesZ
        val tile = if (inside) tiles[tileZ * tilesX + tileX] else null
        return tile?.clearance(x - tileX * tileSamples, z - tileZ * tileSamples) ?: 0
    }

    private fun checkTile(tileX: Int, tileZ: Int) {
        require(tileX in 0 until tilesX && tileZ in 0 until tilesZ) {
            "Tile ($tileX, $tileZ) is outside the $tilesX x $tilesZ lattice."
        }
    }
}

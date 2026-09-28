/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldstream

import com.awakekt.awake.asset.terrain.splat.TerrainSplatWeightMap
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.world.AsyncWorldCellStreamListener
import com.awakekt.awake.scene.world.CellContent
import com.awakekt.awake.scene.world.WorldCellCoord

/**
 * Configuration parameters for cell-streamed virtual terrain texturing.
 *
 * @property tileSize Resolution of each cell's splat and texture tile in texels.
 * @property cacheCapacity Maximum number of resident tiles kept in memory before LRU eviction.
 * @property prefetchRadius Cell ring radius around the loaded cell to asynchronously prefetch.
 */
data class VirtualTerrainTextureConfig(
    val tileSize: Int = DEFAULT_TILE_SIZE,
    val cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
    val prefetchRadius: Int = 1,
) {
    init {
        require(tileSize > 0) { "tileSize must be positive: $tileSize" }
        require(cacheCapacity > 0) { "cacheCapacity must be positive: $cacheCapacity" }
        require(prefetchRadius >= 0) { "prefetchRadius cannot be negative: $prefetchRadius" }
    }

    companion object {
        const val DEFAULT_TILE_SIZE = 512
        const val DEFAULT_CACHE_CAPACITY = 64
    }
}

/**
 * High-resolution virtual terrain texture tile associated with a spatial [WorldCellCoord].
 *
 * Holds the 4-channel [TerrainSplatWeightMap] together with optional compressed or raw
 * diffuse/normal texture data streams for GPU virtual texturing.
 *
 * @property coord Spatial cell coordinate.
 * @property splatWeightMap Splat weight map for 4-channel blending.
 * @property diffuseBytes Optional raw diffuse byte buffer.
 * @property normalBytes Optional raw normal byte buffer.
 */
data class VirtualTerrainTile(
    val coord: WorldCellCoord,
    val splatWeightMap: TerrainSplatWeightMap,
    val diffuseBytes: ByteArray? = null,
    val normalBytes: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VirtualTerrainTile) return false
        return coord == other.coord &&
            splatWeightMap == other.splatWeightMap &&
            (
                (diffuseBytes == null && other.diffuseBytes == null) ||
                    (diffuseBytes != null && other.diffuseBytes != null && diffuseBytes.contentEquals(other.diffuseBytes))
                ) &&
            (
                (normalBytes == null && other.normalBytes == null) ||
                    (normalBytes != null && other.normalBytes != null && normalBytes.contentEquals(other.normalBytes))
                )
    }

    override fun hashCode(): Int {
        var result = coord.hashCode()
        result = 31 * result + splatWeightMap.hashCode()
        result = 31 * result + (diffuseBytes?.contentHashCode() ?: 0)
        result = 31 * result + (normalBytes?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Provider interface for loading or synthesizing [VirtualTerrainTile] instances for a given [WorldCellCoord].
 */
fun interface VirtualTerrainTileProvider {
    suspend fun provideTile(coord: WorldCellCoord): VirtualTerrainTile?
}

/**
 * Least-Recently-Used (LRU) in-memory cache for [VirtualTerrainTile] instances.
 *
 * Provides thread-safe / deterministic coordinate lookup, hit/miss tracking, and automatic
 * eviction when [capacity] is exceeded.
 *
 * @property capacity Maximum number of resident tiles before eviction.
 * @property onEvict Callback triggered when a resident tile is evicted from cache.
 */
class VirtualTerrainTileCache(
    val capacity: Int,
    val onEvict: (VirtualTerrainTile) -> Unit = {},
) {
    init {
        require(capacity > 0) { "capacity must be positive: $capacity" }
    }

    private val entries = LinkedHashMap<WorldCellCoord, VirtualTerrainTile>()

    var hitCount: Long = 0L
        private set
    var missCount: Long = 0L
        private set
    var evictionCount: Long = 0L
        private set

    val size: Int get() = entries.size

    val residentCoords: Set<WorldCellCoord> get() = entries.keys.toSet()

    /**
     * Looks up the tile at [coord]. Promotes the entry to the most recently used position on hit.
     */
    fun get(coord: WorldCellCoord): VirtualTerrainTile? {
        val tile = entries.remove(coord)
        return if (tile != null) {
            entries[coord] = tile
            hitCount++
            tile
        } else {
            missCount++
            null
        }
    }

    /**
     * Inserts [tile] into the cache. If [size] exceeds [capacity], the least recently used
     * entry is evicted and passed to [onEvict].
     */
    fun put(tile: VirtualTerrainTile) {
        entries.remove(tile.coord)
        entries[tile.coord] = tile

        if (entries.size > capacity) {
            val oldestKey = entries.keys.firstOrNull()
            if (oldestKey != null) {
                val evicted = entries.remove(oldestKey)
                if (evicted != null) {
                    evictionCount++
                    onEvict(evicted)
                }
            }
        }
    }

    fun remove(coord: WorldCellCoord): VirtualTerrainTile? = entries.remove(coord)

    fun contains(coord: WorldCellCoord): Boolean = entries.containsKey(coord)

    fun clear() {
        entries.clear()
        hitCount = 0L
        missCount = 0L
        evictionCount = 0L
    }
}

/**
 * ECS component holding a loaded [VirtualTerrainTile] for an active terrain cell entity.
 *
 * @property tile The resident virtual terrain tile.
 */
data class VirtualTerrainTileComponent(
    val tile: VirtualTerrainTile,
)

/**
 * Asynchronous cell streaming listener that coordinates with [VirtualTerrainTileCache] and
 * [VirtualTerrainTileProvider] to keep virtual texture tiles resident in sync with world streaming.
 *
 * @property cache Tile cache instance.
 * @property provider Tile provider instance.
 * @property prefetchRadius Radius in cells to prefetch.
 */
class VirtualTerrainCellStreamListener(
    val cache: VirtualTerrainTileCache,
    val provider: VirtualTerrainTileProvider,
    val prefetchRadius: Int = 1,
) : AsyncWorldCellStreamListener {

    private val activeCellEntities = HashMap<WorldCellCoord, Entity>()

    override suspend fun loadCell(coord: WorldCellCoord): CellContent {
        var tile = cache.get(coord)
        if (tile == null) {
            tile = provider.provideTile(coord)
            if (tile != null) {
                cache.put(tile)
            }
        }

        prefetchNeighbours(coord)

        val resolvedTile = tile ?: return CellContent { }
        return CellContent { world ->
            val entity = world.create()
            world.add(entity, VirtualTerrainTileComponent(resolvedTile))
            activeCellEntities[coord] = entity
        }
    }

    private suspend fun prefetchNeighbours(coord: WorldCellCoord) {
        if (prefetchRadius <= 0) return
        for (dx in -prefetchRadius..prefetchRadius) {
            for (dz in -prefetchRadius..prefetchRadius) {
                if (dx == 0 && dz == 0) continue
                prefetchSingleCell(WorldCellCoord(coord.x + dx, coord.z + dz))
            }
        }
    }

    private suspend fun prefetchSingleCell(neighbourCoord: WorldCellCoord) {
        if (!cache.contains(neighbourCoord)) {
            val neighbourTile = provider.provideTile(neighbourCoord)
            if (neighbourTile != null) {
                cache.put(neighbourTile)
            }
        }
    }

    override fun onCellUnload(world: World, coord: WorldCellCoord) {
        val entity = activeCellEntities.remove(coord)
        if (entity != null) {
            world.destroy(entity)
        }
    }
}

/**
 * Runtime manager entity component exposing the active [VirtualTerrainTextureConfig] and [cache].
 *
 * @property config Active virtual terrain configuration.
 * @property cache LRU tile cache.
 */
data class VirtualTerrainTextureManager(
    val config: VirtualTerrainTextureConfig,
    val cache: VirtualTerrainTileCache,
)

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.terrain

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.RawHeightmapCodec
import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.asset.terrain.TerrainPageLayout
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.resolve
import com.awakekt.awake.core.math.Vec3f
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One cell's project-relative assets. Heights are unsigned 16-bit little endian. */
@Serializable
data class TerrainPageAssets(
    /** Absolute cell coordinate along X. */
    val x: Int,
    /** Absolute cell coordinate along Z. */
    val z: Int,
    /** Relative unsigned 16-bit little-endian height asset path. */
    val height: String,
    /** Relative packed control-map asset path, when supplied. */
    val control: String? = null,
    /** Relative baked lightmap asset path, when supplied. */
    val lightmap: String? = null,
)

/** Versioned sparse index; absent cells intentionally use the mandatory coarse fallback. */
@Serializable
data class TerrainPageIndex(
    /** Serialized index schema version, currently one. */
    val version: Int = 1,
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
    /** Minimum raw elevation encoded as unsigned zero. */
    val minElevation: Float,
    /** Maximum raw elevation encoded as unsigned 65535. */
    val maxElevation: Float,
    /** World-space vertical scale applied to raw elevations. */
    val heightScale: Float = 1f,
    /** Relative mandatory whole-footprint raw height asset path. */
    val fallbackHeight: String,
    /** Coarse height samples along X, including endpoints. */
    val fallbackWidth: Int,
    /** Coarse height samples along Z, including endpoints. */
    val fallbackDepth: Int,
    /** Relative shared surface palette asset path, when supplied. */
    val palette: String? = null,
    /** Relative whole-footprint coarse control-map asset path. */
    val fallbackControl: String? = null,
    /** Relative whole-footprint coarse baked lightmap asset path. */
    val fallbackLightmap: String? = null,
    /** Authored control texels per cell along X. */
    val controlWidth: Int = 128,
    /** Authored control texels per cell along Z. */
    val controlDepth: Int = 128,
    /** Packed layer slots per authored control texel, four or eight. */
    val controlSlots: Int = 4,
    /** Sparse cell asset entries; omitted cells intentionally use fallback. */
    val pages: List<TerrainPageAssets>,
) {
    /** Builds the fixed lattice shared by height queries, rendering and collision. */
    fun layout(): TerrainPageLayout = TerrainPageLayout(minCellX, minCellZ, cellCountX, cellCountZ, cellSize, intervals, heightScale, minElevation, maxElevation)

    /** Rejects unsupported versions, duplicate cells and incompatible fallback lattices. */
    fun validate() {
        require(version == 1) { "Unsupported terrain index version $version." }
        require(controlWidth in 1..4096 && controlDepth in 1..8192 && controlSlots in setOf(4, 8))
        val layout = layout()
        require(fallbackWidth >= 2 && fallbackDepth >= 2)
        require(pages.map { TerrainPageCoord(it.x, it.z) }.toSet().size == pages.size) { "Duplicate terrain cells." }
        require(pages.all { layout.contains(TerrainPageCoord(it.x, it.z)) })
        require(layout.worldIntervalsX % (fallbackWidth - 1) == 0 && layout.worldIntervalsZ % (fallbackDepth - 1) == 0)
        require(layout.worldIntervalsX / (fallbackWidth - 1) == layout.worldIntervalsZ / (fallbackDepth - 1))
    }
}

/** Pure index codec; paths are resolved by the consuming asset source. */
object TerrainPageIndexCodec {
    private val json = Json { prettyPrint = true }

    /** Decodes and validates a version-one UTF-8 JSON terrain index. */
    fun decode(bytes: ByteArray): TerrainPageIndex = json.decodeFromString<TerrainPageIndex>(bytes.decodeToString()).also { it.validate() }

    /** Validates and encodes the index as UTF-8 JSON. */
    fun encode(index: TerrainPageIndex): ByteArray = index.also { it.validate() }.let { json.encodeToString(TerrainPageIndex.serializer(), it).encodeToByteArray() }
}

/**
 * Strict raw-height reader shared by render residency and collision loading.
 * @property index Validated index defining cell paths and the shared encoding range.
 * @param indexPath Asset path used as the relative cell-path base.
 * @param assets Reader for the consuming project's asset storage.
 */
class TerrainPageHeightReader(val index: TerrainPageIndex, private val indexPath: AssetPath, private val assets: AssetSource) {
    private val entries = index.pages.associateBy { TerrainPageCoord(it.x, it.z) }
    init {
        index.validate()
    }

    /** Loads one indexed cell; returns null when the sparse index omits it. */
    suspend fun read(coord: TerrainPageCoord): Heightmap? {
        val entry = entries[coord] ?: return null
        val layout = index.layout()
        return decode(entry.height, layout.samplesPerPage, layout.samplesPerPage, layout.sampleSpacing, layout.sampleSpacing)
    }

    /** Loads the mandatory coarse height image using the shared elevation range. */
    suspend fun fallback(): Heightmap = decode(
        index.fallbackHeight,
        index.fallbackWidth,
        index.fallbackDepth,
        index.cellCountX * index.cellSize / (index.fallbackWidth - 1),
        index.cellCountZ * index.cellSize / (index.fallbackDepth - 1),
    )

    private suspend fun decode(path: String, width: Int, depth: Int, spacingX: Float, spacingZ: Float): Heightmap {
        require(width.toLong() * depth * 2 <= Int.MAX_VALUE)
        val bytes = assets.read(indexPath.resolve(path)).getOrThrow()
        require(bytes.size.toLong() == width.toLong() * depth * 2) { "Incorrect terrain height byte count for $path." }
        return RawHeightmapCodec.decode(bytes, width, depth, Vec3f(spacingX, index.heightScale, spacingZ), minElevation = index.minElevation, maxElevation = index.maxElevation)
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.asset.shaderpack.pagedTerrainContentFeature
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.terrain.PagedHeightmap
import com.awakekt.awake.asset.terrain.RawHeightmapCodec
import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.resolve
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.terrain.PagedTerrain
import com.awakekt.awake.terrain.TerrainPage
import com.awakekt.awake.terrain.TerrainPageHeightReader
import com.awakekt.awake.terrain.TerrainPageIndex
import com.awakekt.awake.terrain.TerrainPageIndexCodec
import com.awakekt.awake.terrain.TerrainPageSave

/** Loaded shared palette/fallback plus cell readers; attach [content] once and stream [read]. */
class PagedTerrainLayers private constructor(
    /** Streaming residency controller owned by the caller. */
    val terrain: PagedTerrain,
    /** Validated asset index defining the lattice and cell paths. */
    val index: TerrainPageIndex,
    private val indexPath: AssetPath,
    private val assets: AssetSource,
    private val palette: TerrainLayerPalette,
    private val shared: Map<Int, TextureAsset>,
) {
    private val reader = TerrainPageHeightReader(index, indexPath, assets)
    private val entries = index.pages.associateBy { TerrainPageCoord(it.x, it.z) }
    val content = pagedTerrainContentFeature(terrain, shaders = aslShaderSet { terrainLayersShader(it, index.controlSlots, true) }, sharedTextures = shared, samplerTextures = mapOf(7 to LAYER_ALBEDO_BINDING))

    /** Replaces cell control data and updates the coarse control texels owned by that cell. */
    fun editControl(coord: TerrainPageCoord, control: TerrainControlMap) {
        validateControl(control, palette, index.controlSlots)
        require(control.width == index.controlWidth && control.depth == index.controlDepth)
        require(terrain.page(coord) != null)
        val coarseImages = terrain.surfaceFallbackSaveSnapshot().second
        val coarseIndices = coarseImages.getValue(FALLBACK_CONTROL_INDICES_BINDING)
        val coarseWeights = coarseImages.getValue(FALLBACK_CONTROL_WEIGHTS_BINDING)
        val columns = coarseIndices.width * 4 / index.controlSlots
        val indices = coarseIndices.data.copyOf()
        val weights = coarseWeights.data.copyOf()
        for (z in 0 until coarseIndices.height) {
            for (x in 0 until columns) {
                val cellX = (x + 0.5) / columns * index.cellCountX
                val cellZ = (z + 0.5) / coarseIndices.height * index.cellCountZ
                if (kotlin.math.floor(cellX).toInt() + index.minCellX != coord.x || kotlin.math.floor(cellZ).toInt() + index.minCellZ != coord.z) continue
                val localX = ((cellX - kotlin.math.floor(cellX)) * control.width).toInt().coerceIn(0, control.width - 1)
                val localZ = ((cellZ - kotlin.math.floor(cellZ)) * control.depth).toInt().coerceIn(0, control.depth - 1)
                for (slot in 0 until index.controlSlots) {
                    val offset = (z * columns + x) * index.controlSlots + slot
                    indices[offset] = control.layerAt(localX, localZ, slot).toByte()
                    weights[offset] = control.weightAt(localX, localZ, slot).toByte()
                }
            }
        }
        terrain.editTexture(coord, CONTROL_INDICES_BINDING, control.indicesTexture())
        terrain.editTexture(coord, CONTROL_WEIGHTS_BINDING, control.weightsTexture())
        terrain.editFallbackTextures(mapOf(FALLBACK_CONTROL_INDICES_BINDING to coarseIndices.copy(data = indices), FALLBACK_CONTROL_WEIGHTS_BINDING to coarseWeights.copy(data = weights)))
    }

    /** Loads one indexed cell; returns null when the sparse index omits it. */
    suspend fun read(coord: TerrainPageCoord): TerrainPage? {
        val entry = entries[coord] ?: return null
        val height = requireNotNull(reader.read(coord))
        val control = TerrainControlMapCodec.decode(assets.read(indexPath.resolve(requireNotNull(entry.control) { "Layered cell $coord requires control assets." })).getOrThrow())
        validateControl(control, palette, index.controlSlots)
        require(control.width == index.controlWidth && control.depth == index.controlDepth)
        val light = entry.lightmap?.let { TerrainLightmapCodec.decode(assets.read(indexPath.resolve(it)).getOrThrow()) } ?: neutral(index.controlWidth, index.controlDepth)
        require(light.width == index.controlWidth && light.depth == index.controlDepth)
        return TerrainPage(height, mapOf(CONTROL_INDICES_BINDING to control.indicesTexture(), CONTROL_WEIGHTS_BINDING to control.weightsTexture(), LIGHTMAP_BINDING to light.texture()))
    }

    /** Writes only this cell's assets. Dirty state clears only after all writes succeed. */
    suspend fun save(snapshot: TerrainPageSave, write: suspend (AssetPath, ByteArray) -> Unit) {
        val entry = entries.getValue(snapshot.coord)
        val page = snapshot.page
        val control = TerrainControlMap(
            index.controlWidth,
            index.controlDepth,
            page.textures.getValue(CONTROL_INDICES_BINDING).data,
            page.textures.getValue(CONTROL_WEIGHTS_BINDING).data,
            index.controlSlots,
        )
        validateControl(control, palette, index.controlSlots)
        val light = page.textures.getValue(LIGHTMAP_BINDING)
        if (entry.lightmap == null) require(light.data.contentEquals(neutral(light.width, light.height).copyRgba())) { "Author a lightmap path in the index before saving baked-light edits." }
        write(indexPath.resolve(entry.height), RawHeightmapCodec.encode16LittleEndian(page.height, index.minElevation, index.maxElevation))
        write(indexPath.resolve(requireNotNull(entry.control)), TerrainControlMapCodec.encode(control))
        entry.lightmap?.let { write(indexPath.resolve(it), TerrainLightmapCodec.encode(TerrainLightmap(light.width, light.height, light.data))) }
        // Owner-thread caller acknowledges after returning; workers never mutate residency.
    }

    /** Save coarse height knots before acknowledging the snapshot on the terrain owner thread. */
    suspend fun saveFallback(snapshot: Pair<Long, com.awakekt.awake.asset.terrain.Heightmap>, write: suspend (AssetPath, ByteArray) -> Unit) {
        write(indexPath.resolve(index.fallbackHeight), RawHeightmapCodec.encode16LittleEndian(snapshot.second, index.minElevation, index.maxElevation))
    }

    /** Writes coarse surface images; acknowledge the snapshot after all writes succeed. */
    suspend fun saveSurfaceFallback(snapshot: Pair<Long, Map<Int, TextureAsset>>, write: suspend (AssetPath, ByteArray) -> Unit) {
        val coarseLight = snapshot.second.getValue(FALLBACK_LIGHTMAP_BINDING)
        if (index.fallbackLightmap == null) require(coarseLight.data.contentEquals(neutral(coarseLight.width, coarseLight.height).copyRgba())) { "Author a fallback lightmap path before saving coarse baked-light edits." }
        val indices = snapshot.second.getValue(FALLBACK_CONTROL_INDICES_BINDING)
        val weights = snapshot.second.getValue(FALLBACK_CONTROL_WEIGHTS_BINDING)
        val control = TerrainControlMap(indices.width * 4 / index.controlSlots, indices.height, indices.data, weights.data, index.controlSlots)
        write(indexPath.resolve(requireNotNull(index.fallbackControl)), TerrainControlMapCodec.encode(control))
        index.fallbackLightmap?.let {
            val light = snapshot.second.getValue(FALLBACK_LIGHTMAP_BINDING)
            write(indexPath.resolve(it), TerrainLightmapCodec.encode(TerrainLightmap(light.width, light.height, light.data)))
        }
    }

    /** Builds a shared palette and fixed-shape cell readers. */
    companion object {
        /** Loads a versioned index and shared assets. Cell reads remain lazy and bounded by the streamer. */
        suspend fun load(
            assets: AssetSource,
            indexPath: AssetPath,
            capacity: Int = 64,
            maxUploadBytes: Int = 8 * 1024 * 1024,
            decodeImage: suspend (ByteArray) -> TextureAsset = { bytes -> createBitmap(bytes).let { TextureAsset(it.toRgba8Bytes(), it.width, it.height) } },
        ): PagedTerrainLayers {
            val index = TerrainPageIndexCodec.decode(assets.read(indexPath).getOrThrow())
            val palettePath = indexPath.resolve(requireNotNull(index.palette))
            val palette = TerrainLayerPaletteCodec.decode(assets.read(palettePath).getOrThrow())
            val albedo = palette.layers.map { decodeImage(assets.read(palettePath.resolve(it.albedo)).getOrThrow()) }
            val heights = palette.layers.map { layer -> layer.height?.let { decodeImage(assets.read(palettePath.resolve(it)).getOrThrow()) } }
            require(palette.validate().isEmpty())
            val coarse = TerrainControlMapCodec.decode(assets.read(indexPath.resolve(requireNotNull(index.fallbackControl))).getOrThrow())
            validateControl(coarse, palette, index.controlSlots)
            val light = index.fallbackLightmap?.let { TerrainLightmapCodec.decode(assets.read(indexPath.resolve(it)).getOrThrow()) } ?: TerrainLightmap.Neutral
            val template = TerrainControlMap(index.controlWidth, index.controlDepth, ByteArray(index.controlWidth * index.controlDepth * index.controlSlots), ByteArray(index.controlWidth * index.controlDepth * index.controlSlots), index.controlSlots)

            val terrain = PagedTerrain(
                PagedHeightmap(index.layout(), TerrainPageHeightReader(index, indexPath, assets).fallback()),
                capacity,
                mapOf(CONTROL_INDICES_BINDING to template.indicesTexture(), CONTROL_WEIGHTS_BINDING to template.weightsTexture(), LIGHTMAP_BINDING to neutral(index.controlWidth, index.controlDepth).texture()),
                mapOf(CONTROL_INDICES_BINDING to (FALLBACK_CONTROL_INDICES_BINDING to coarse.indicesTexture()), CONTROL_WEIGHTS_BINDING to (FALLBACK_CONTROL_WEIGHTS_BINDING to coarse.weightsTexture()), LIGHTMAP_BINDING to (FALLBACK_LIGHTMAP_BINDING to light.texture())),
                maxUploadBytes,
            )
            return PagedTerrainLayers(terrain, index, indexPath, assets, palette, mapOf(LAYER_ALBEDO_BINDING to packLayerArray(albedo, heights), LAYER_TABLE_BINDING to layerTable(palette)))
        }

        private fun validateControl(control: TerrainControlMap, palette: TerrainLayerPalette, slots: Int) {
            require(control.slots == slots && control.highestLayer() < palette.layers.size)
            val weights = control.copyWeights()
            for (offset in weights.indices step slots) require((0 until slots).sumOf { weights[offset + it].toInt() and 255 } == 255) { "Control weights must sum to 255." }
        }

        private fun neutral(width: Int, depth: Int): TerrainLightmap = TerrainLightmap(width, depth, ByteArray(width * depth * 4) { if (it % 4 == 3) 0 else 128.toByte() })
    }
}

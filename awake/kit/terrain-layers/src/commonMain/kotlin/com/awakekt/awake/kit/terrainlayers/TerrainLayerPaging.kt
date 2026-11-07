/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.terrain.TerrainPageIndex

private const val RGBA = 4

/**
 * A whole-footprint control map and lightmap cut onto a page index's cells, ready to write next to
 * the index. [PagedTerrainLayers.load] then reads only the coarse maps at entry, and
 * [PagedTerrainLayers.read] reads one cell's control and light with its heights, as the streamer
 * reaches that cell.
 *
 * @property index The input index naming each cell's control and lightmap file and the coarse maps,
 * with its per-cell control size and slot count set from the paged maps.
 * @property files Each encoded `*.terrainctl` and `*.terrainlight`, keyed by its path relative to
 * the index, as the index names it.
 */
class TerrainLayerPages(
    val index: TerrainPageIndex,
    val files: Map<String, ByteArray>,
)

/**
 * Pages a terrain-layers control map and lightmap on the cells a [TerrainPageIndex] pages heights
 * on, so a streamed terrain stops loading them whole when it is entered.
 */
object TerrainLayerPaging {
    /**
     * Cuts [control], and [lightmap] when given, into one file per cell [index] lists, and decimates
     * both into coarse maps [coarseTexelsPerCell] texels a cell across, which draw a cell that is not
     * resident yet. Both maps cover the index's whole footprint, `cellCountX` by `cellCountZ` cells,
     * row-major from its minimum cell, like the whole maps a layered terrain surface reads, and each
     * cell's share must be a whole number of texels. A cell the sparse index omits gets no file; its
     * texels only feed the coarse maps.
     *
     * The returned index replaces [index]'s control and lightmap paths with ones under [directory]:
     * `<x>_<z>.terrainctl` and `<x>_<z>.terrainlight` per cell, and `coarse.*` for the fallbacks.
     * Without a [lightmap], cells and the coarse surface draw with neutral light.
     *
     * @param index A layered index (one that names a palette) whose cells the maps are paged on.
     * @param control The whole-footprint control map.
     * @param lightmap The whole-footprint lightmap, one texel per [control] texel, or null for none.
     * @param coarseTexelsPerCell Coarse texels per cell along each axis, at most a cell's own size.
     * @param directory Directory, relative to the index, that holds the paged files.
     * @return The paged index and the files it names.
     */
    fun page(
        index: TerrainPageIndex,
        control: TerrainControlMap,
        lightmap: TerrainLightmap? = null,
        coarseTexelsPerCell: Int = 4,
        directory: String = "layers",
    ): TerrainLayerPages {
        index.validate()
        require(index.palette != null) { "Paged control maps draw through a palette; name one in the index." }
        require(control.width % index.cellCountX == 0 && control.depth % index.cellCountZ == 0) {
            "A ${control.width} x ${control.depth} control map does not split into ${index.cellCountX} x ${index.cellCountZ} whole cells."
        }
        require(lightmap == null || (lightmap.width == control.width && lightmap.depth == control.depth)) {
            "The lightmap must have one texel per control texel (${control.width} x ${control.depth})."
        }
        require(directory.isNotBlank() && !directory.startsWith("/")) { "The paged directory is a path relative to the index." }
        val cellWidth = control.width / index.cellCountX
        val cellDepth = control.depth / index.cellCountZ
        require(coarseTexelsPerCell in 1..minOf(cellWidth, cellDepth)) {
            "coarseTexelsPerCell must be 1..${minOf(cellWidth, cellDepth)}, a cell's own control size."
        }
        val whole = WholeMaps(control, lightmap)
        val files = LinkedHashMap<String, ByteArray>()
        val pages = index.pages.map { entry ->
            val firstX = (entry.x - index.minCellX) * cellWidth
            val firstZ = (entry.z - index.minCellZ) * cellDepth
            val name = "$directory/${entry.x}_${entry.z}"
            files["$name.terrainctl"] = TerrainControlMapCodec.encode(whole.control(cellWidth, cellDepth) { x, z -> (firstZ + z) * control.width + firstX + x })
            val light = whole.light(cellWidth, cellDepth) { x, z -> (firstZ + z) * control.width + firstX + x }
            light?.let { files["$name.terrainlight"] = TerrainLightmapCodec.encode(it) }
            entry.copy(control = "$name.terrainctl", lightmap = light?.let { "$name.terrainlight" })
        }
        val coarseWidth = index.cellCountX * coarseTexelsPerCell
        val coarseDepth = index.cellCountZ * coarseTexelsPerCell
        // Centre sampling, as PagedTerrainLayers.editControl writes coarse texels back from a cell.
        val coarseTexel = { x: Int, z: Int ->
            val sourceX = ((x + 0.5) / coarseWidth * control.width).toInt().coerceIn(0, control.width - 1)
            val sourceZ = ((z + 0.5) / coarseDepth * control.depth).toInt().coerceIn(0, control.depth - 1)
            sourceZ * control.width + sourceX
        }
        files["$directory/coarse.terrainctl"] = TerrainControlMapCodec.encode(whole.control(coarseWidth, coarseDepth, coarseTexel))
        val coarseLight = whole.light(coarseWidth, coarseDepth, coarseTexel)
        coarseLight?.let { files["$directory/coarse.terrainlight"] = TerrainLightmapCodec.encode(it) }
        val paged = index.copy(
            fallbackControl = "$directory/coarse.terrainctl",
            fallbackLightmap = coarseLight?.let { "$directory/coarse.terrainlight" },
            controlWidth = cellWidth,
            controlDepth = cellDepth,
            controlSlots = control.slots,
            pages = pages,
        )
        paged.validate()
        return TerrainLayerPages(paged, files)
    }

    /** The whole maps' bytes, copied once, and texel-by-texel copies out of them. */
    private class WholeMaps(control: TerrainControlMap, lightmap: TerrainLightmap?) {
        private val slots = control.slots
        private val indices = control.copyIndices()
        private val weights = control.copyWeights()
        private val rgba = lightmap?.copyRgba()

        /** A [width] x [depth] control map whose texel `(x, z)` is whole texel `source(x, z)`. */
        fun control(width: Int, depth: Int, source: (Int, Int) -> Int): TerrainControlMap {
            val outIndices = ByteArray(width * depth * slots)
            val outWeights = ByteArray(width * depth * slots)
            for (z in 0 until depth) {
                for (x in 0 until width) {
                    val from = source(x, z) * slots
                    val to = (z * width + x) * slots
                    indices.copyInto(outIndices, to, from, from + slots)
                    weights.copyInto(outWeights, to, from, from + slots)
                }
            }
            return TerrainControlMap(width, depth, outIndices, outWeights, slots)
        }

        /** The lightmap counterpart of [control], or null without a whole lightmap. */
        fun light(width: Int, depth: Int, source: (Int, Int) -> Int): TerrainLightmap? {
            val whole = rgba ?: return null
            val out = ByteArray(width * depth * RGBA)
            for (z in 0 until depth) {
                for (x in 0 until width) {
                    val from = source(x, z) * RGBA
                    whole.copyInto(out, (z * width + x) * RGBA, from, from + RGBA)
                }
            }
            return TerrainLightmap(width, depth, out)
        }
    }
}

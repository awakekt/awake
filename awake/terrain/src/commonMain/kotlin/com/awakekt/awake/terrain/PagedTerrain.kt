/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.terrain

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.HeightmapSampleEdit
import com.awakekt.awake.asset.terrain.PagedHeightmap
import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.render.passes.ContentTextureUpdate
import com.awakekt.awake.render.passes.ContentTextureUpdates
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.TextureRegion

/** Shader binding for the resident height texture array. */
const val TERRAIN_HEIGHT_PAGES_BINDING: Int = 32

/** Shader binding for the whole-footprint cell-to-layer table. */
const val TERRAIN_PAGE_TABLE_BINDING: Int = 33

/** Immutable loaded cell. Surface textures are base-level cell images, indexed by array binding. */
data class TerrainPage(
    /** Immutable height samples for this cell. */
    val height: Heightmap,
    /** Immutable base-level cell images keyed by paged surface binding. */
    val textures: Map<Int, TextureAsset> = emptyMap(),
)

/** Snapshot sent to a consumer's writer. Acknowledge exactly this revision after a successful save. */
data class TerrainPageSave(
    /** Absolute coordinate of the saved cell. */
    val coord: TerrainPageCoord,
    /** Monotonically increasing version of this state. */
    val revision: Long,
    /** Immutable cell snapshot sent to the asset writer. */
    val page: TerrainPage,
)

/**
 * Bounded page storage and per-frame-slot upload journals. Single frame-thread owner. Mutable
 * pages stay pinned until saved; replacing a layer also replaces its table in the same submission.
 * [fallbackTextures] maps each paged surface binding to its whole-world fallback binding and image.
 */
@Suppress("TooManyFunctions") // One lifecycle owns residency, edit/save revisions and frame publication.
class PagedTerrain(
    /** Sparse height state owned by this residency controller. */
    val heights: PagedHeightmap,
    /** Maximum number of resident GPU array layers. */
    val capacity: Int,
    /** Single-layer images defining each paged surface shape. */
    val pageTextureTemplates: Map<Int, TextureAsset> = emptyMap(),
    val fallbackTextures: Map<Int, Pair<Int, TextureAsset>> = emptyMap(),
    /** Maximum combined region-upload bytes emitted per frame slot update. */
    val maxUploadBytes: Int = 8 * 1024 * 1024,
) : ContentTextureUpdates {
    private data class Resident(var page: TerrainPage, val layer: Int, var revision: Long, var saved: Long)
    private val residents = LinkedHashMap<TerrainPageCoord, Resident>()
    private val slotRevisions = mutableMapOf<Int, MutableMap<Int, Long>>()
    private val tableRevisions = mutableMapOf<Int, Long>()
    private var revision = 1L
    private var fallbackRevision = 1L
    private var savedFallbackRevision = 1L
    private val fallbackRevisions = mutableMapOf<Int, Long>()
    private val fallbackImages = fallbackTextures.values.associate { it.first to it.second }.toMutableMap()
    private var surfaceFallbackRevision = 1L
    private var savedSurfaceFallbackRevision = 1L
    private val surfaceFallbackRevisions = mutableMapOf<Int, Long>()

    /** Fixed lattice and shared elevation encoding range. */
    val layout get() = heights.layout

    /** Absolute origin subtracted by the scene adapter, never a scene-layer type. */
    var originX: Double = 0.0

    /** Absolute Y origin subtracted from render and collision positions. */
    var originY: Double = 0.0

    /** Absolute Z origin subtracted from render and collision positions. */
    var originZ: Double = 0.0

    init {
        require(capacity in 2..65534)
        require(heights.residentCoords.isEmpty()) { "Residency must be owned by PagedTerrain." }
        require(layout.cellCountX <= 8192 && layout.cellCountZ <= 8192 && layout.samplesPerPage <= 8192)
        require(heights.fallback.width <= 8192 && heights.fallback.depth <= 8192)
        require(pageTextureTemplates.keys.intersect(setOf(1, TERRAIN_HEIGHT_PAGES_BINDING, TERRAIN_PAGE_TABLE_BINDING)).isEmpty())
        require(pageTextureTemplates.keys == fallbackTextures.keys)
        require(fallbackTextures.values.map { it.first }.toSet().size == fallbackTextures.size)
        require(fallbackTextures.values.none { it.first in pageTextureTemplates.keys || it.first in setOf(1, 32, 33) })
        require((pageTextureTemplates.values + fallbackImages.values).all { it.layerCount == 1 && !it.isCubemap && it.width in 1..8192 && it.height in 1..8192 })
        require(maxUploadBytes >= pageBytes + tableBytes + heights.fallback.width.toLong() * heights.fallback.depth * 4 + fallbackImages.values.sumOf { it.data.size.toLong() })
    }

    private val pageBytes: Long get() = layout.samplesPerPage.toLong() * layout.samplesPerPage * 4 + pageTextureTemplates.values.sumOf { it.data.size.toLong() }
    private val tableBytes: Long get() = layout.cellCountX.toLong() * layout.cellCountZ * 4

    /** Bytes per GPU frame slot, excluding the shared palette, geometry and staging. */
    val textureBytesPerSlot: Long get() = pageBytes * capacity + tableBytes + heights.fallback.width.toLong() * heights.fallback.depth * 4 + fallbackTextures.values.sumOf { it.second.data.size.toLong() }

    /** Snapshot of the currently resident cell coordinates. */
    val residentCoords: Set<TerrainPageCoord> get() = residents.keys.toSet()

    /** Whether coarse height changes remain unacknowledged by the writer. */
    val fallbackDirty: Boolean get() = fallbackRevision > savedFallbackRevision

    /** Whether coarse surface changes remain unacknowledged by the writer. */
    val surfaceFallbackDirty: Boolean get() = surfaceFallbackRevision > savedSurfaceFallbackRevision

    /** Resident cells pinned until their current revision is saved. */
    val dirtyCoords: Set<TerrainPageCoord> get() = residents.filterValues { it.revision > it.saved }.keys

    /** Returns the immutable resident cell, or null when it uses fallback. */
    fun page(coord: TerrainPageCoord): TerrainPage? = residents[coord]?.page

    /** Returns false when every layer is occupied; caller must evict a clean page first. */
    fun put(coord: TerrainPageCoord, page: TerrainPage): Boolean {
        require(page.textures.keys == pageTextureTemplates.keys)
        for ((binding, texture) in page.textures) {
            val template = pageTextureTemplates.getValue(binding)
            require(texture.width == template.width && texture.height == template.height && texture.layerCount == 1 && !texture.isCubemap)
        }
        require(coord !in dirtyCoords) { "Unsaved terrain page is pinned." }
        val existing = residents[coord]
        val layer = existing?.layer ?: (0 until capacity).firstOrNull { candidate -> residents.values.none { it.layer == candidate } } ?: return false
        heights.put(coord, page.height)
        revision++
        residents[coord] = Resident(page, layer, revision, revision)
        return true
    }

    /** Evicts a clean cell; returns false while unsaved edits pin it. */
    fun evict(coord: TerrainPageCoord): Boolean {
        val resident = residents[coord] ?: return true
        val clean = resident.revision == resident.saved
        if (clean) {
            residents.remove(coord)
            heights.remove(coord)
            revision++
        }
        return clean
    }

    /** Atomically edits global fine samples and pins every changed shared page. */
    fun editHeights(edits: List<HeightmapSampleEdit>): Set<TerrainPageCoord> {
        val change = heights.apply(edits) ?: return emptySet()
        revision++
        change.pages.keys.forEach { coord ->
            residents.getValue(coord).apply {
                page = page.copy(height = requireNotNull(heights.page(coord)))
                this.revision = this@PagedTerrain.revision
            }
        }
        if (change.fallbackChanged) fallbackRevision = revision
        return change.pages.keys
    }

    /** Replaces one authored surface image and pins the page for saving. */
    fun editTexture(coord: TerrainPageCoord, binding: Int, texture: TextureAsset) {
        val resident = residents.getValue(coord)
        val template = pageTextureTemplates.getValue(binding)
        require(texture.width == template.width && texture.height == template.height && texture.layerCount == 1 && !texture.isCubemap)
        revision++
        resident.page = resident.page.copy(textures = resident.page.textures + (binding to texture))
        resident.revision = revision
    }

    /** Copies the current cell and its revision for a consumer-owned asset writer. */
    fun saveSnapshot(coord: TerrainPageCoord): TerrainPageSave = residents.getValue(coord).let { TerrainPageSave(coord, it.revision, it.page.copy(textures = it.page.textures.mapValues { entry -> entry.value.copy(data = entry.value.data.copyOf()) })) }

    /** Unpins the cell only when the successful save still matches its current revision. */
    fun acknowledgeSave(save: TerrainPageSave) {
        val resident = residents[save.coord] ?: return
        if (resident.revision == save.revision) resident.saved = save.revision
    }

    /** Coarse knots touched by a height edit are saved separately from cell assets. */
    fun fallbackSaveSnapshot(): Pair<Long, Heightmap> = fallbackRevision to heights.fallback

    /** Marks the coarse height saved only if no newer coarse edit exists. */
    fun acknowledgeFallbackSave(revision: Long) {
        if (revision == fallbackRevision) savedFallbackRevision = revision
    }

    /** A provider updates its small whole-world coarse surface after editing resident detail. */
    fun editFallbackTextures(images: Map<Int, TextureAsset>) {
        require(images.keys.all { it in fallbackImages })
        for ((binding, image) in images) {
            val existing = fallbackImages.getValue(binding)
            require(image.width == existing.width && image.height == existing.height && image.layerCount == 1 && !image.isCubemap)
        }
        fallbackImages.putAll(images.mapValues { it.value.copy(data = it.value.data.copyOf()) })
        surfaceFallbackRevision = ++revision
    }

    /** Copies current coarse surface images and their save revision. */
    fun surfaceFallbackSaveSnapshot(): Pair<Long, Map<Int, TextureAsset>> = surfaceFallbackRevision to fallbackImages.mapValues { it.value.copy(data = it.value.data.copyOf()) }

    /** Acknowledges coarse surface writes only if their revision is current. */
    fun acknowledgeSurfaceFallbackSave(revision: Long) {
        if (revision == surfaceFallbackRevision) savedSurfaceFallbackRevision = revision
    }

    /** Whether this cell revision is visible in the specified GPU frame slot. */
    fun isUploaded(coord: TerrainPageCoord, frameIndex: Int): Boolean = residents[coord]?.let { slotRevisions[frameIndex]?.get(it.layer) == it.revision } ?: false

    override val bindings: Set<Int> get() = pageTextureTemplates.keys + fallbackImages.keys + setOf(1, TERRAIN_HEIGHT_PAGES_BINDING, TERRAIN_PAGE_TABLE_BINDING)

    /** Allocates fixed-shape arrays, an empty page table and mandatory coarse images. */
    fun initialTextures(): Map<Int, TextureAsset> = buildMap {
        put(1, encode(heights.fallback))
        put(TERRAIN_HEIGHT_PAGES_BINDING, TextureAsset(ByteArray((layout.samplesPerPage.toLong() * layout.samplesPerPage * 4 * capacity).also { require(it <= Int.MAX_VALUE) }.toInt()), layout.samplesPerPage, layout.samplesPerPage, capacity, filtering = TextureFiltering.BaseLevelLinear))
        put(TERRAIN_PAGE_TABLE_BINDING, TextureAsset(ByteArray(tableBytes.toInt()), layout.cellCountX, layout.cellCountZ, filtering = TextureFiltering.Nearest))
        for ((binding, template) in pageTextureTemplates) put(binding, TextureAsset(ByteArray((template.data.size.toLong() * capacity).also { require(it <= Int.MAX_VALUE) }.toInt()), template.width, template.height, capacity, filtering = TextureFiltering.BaseLevelLinear))
        for ((binding, fallback) in fallbackImages) put(binding, fallback.copy(filtering = TextureFiltering.BaseLevelLinear))
    }

    override fun updates(frameIndex: Int): List<ContentTextureUpdate> {
        val seen = slotRevisions.getOrPut(frameIndex) { mutableMapOf() }
        val result = mutableListOf<ContentTextureUpdate>()
        var bytes = tableBytes
        if (fallbackRevisions[frameIndex] != fallbackRevision) {
            val image = encode(heights.fallback)
            result += ContentTextureUpdate(1, TextureRegion(image.data, image.width, image.height))
            bytes += image.data.size
            fallbackRevisions[frameIndex] = fallbackRevision
        }
        if (surfaceFallbackRevisions[frameIndex] != surfaceFallbackRevision) {
            for ((binding, image) in fallbackImages) {
                result += ContentTextureUpdate(binding, TextureRegion(image.data, image.width, image.height))
                bytes += image.data.size
            }
            surfaceFallbackRevisions[frameIndex] = surfaceFallbackRevision
        }
        for (resident in residents.values) {
            if (seen[resident.layer] == resident.revision || bytes + pageBytes > maxUploadBytes) continue
            /** Immutable height samples for this cell. */
            val height = encode(resident.page.height)
            result += ContentTextureUpdate(TERRAIN_HEIGHT_PAGES_BINDING, TextureRegion(height.data, height.width, height.height, layer = resident.layer))
            for ((binding, image) in resident.page.textures) result += ContentTextureUpdate(binding, TextureRegion(image.data, image.width, image.height, layer = resident.layer))
            seen[resident.layer] = resident.revision
            bytes += pageBytes
        }
        if (result.isEmpty() && tableRevisions[frameIndex] == revision) return result
        // Until all of a cell's images arrive, its table entry is zero and reads the fallback.
        val table = ByteArray(tableBytes.toInt())
        for ((coord, resident) in residents) {
            if (seen[resident.layer] == resident.revision) {
                val offset = ((coord.z - layout.minCellZ) * layout.cellCountX + coord.x - layout.minCellX) * 4
                val value = resident.layer + 1
                table[offset] = (value and 255).toByte()
                table[offset + 1] = (value ushr 8).toByte()
            }
        }
        result += ContentTextureUpdate(TERRAIN_PAGE_TABLE_BINDING, TextureRegion(table, layout.cellCountX, layout.cellCountZ))
        tableRevisions[frameIndex] = revision
        return result
    }

    private fun encode(map: Heightmap): TextureAsset {
        val pixels = ByteArray(map.width * map.depth * 4)
        map.copySamples().forEachIndexed { i, height ->
            val value = (((height - layout.minElevation) / (layout.maxElevation - layout.minElevation)) * 65535f + 0.5f).toInt().coerceIn(0, 65535)
            pixels[i * 4] = (value ushr 8).toByte()
            pixels[i * 4 + 1] = value.toByte()
            pixels[i * 4 + 3] = 255.toByte()
        }
        return TextureAsset(pixels, map.width, map.depth, filtering = TextureFiltering.BaseLevelLinear)
    }
}

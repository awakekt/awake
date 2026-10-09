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
import com.awakekt.awake.render.texture.TextureAsset

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
 * A resident cell as an uploader reads it: the layer it holds, the revision that placed it there,
 * and the latest revision of its heights and of each surface image. Live state, read on the
 * terrain's owner thread.
 */
class ResidentTerrainPage internal constructor(
    /** Absolute cell coordinate. */
    val coord: TerrainPageCoord,
    /** Array layer this cell holds until it is evicted. */
    val layer: Int,
    /** Revision that put this cell in [layer]. Replacing a resident cell keeps it. */
    val placedRevision: Long,
    page: TerrainPage,
    revision: Long,
) {
    private val textures = page.textures.keys.associateWithTo(LinkedHashMap()) { revision }

    /** Current cell contents. */
    var page: TerrainPage = page
        internal set

    /** Revision of the latest change to [page]'s heights. */
    var heightRevision: Long = revision
        internal set

    /** Revision of the latest change to each surface image, keyed by paged surface binding. */
    val textureRevisions: Map<Int, Long> get() = textures

    internal var revision: Long = revision
    internal var saved: Long = revision

    internal fun editTexture(binding: Int, revision: Long) {
        textures[binding] = revision
    }
}

/**
 * Bounded page storage with edit and save revisions. Single frame-thread owner. Mutable pages stay
 * pinned until saved. An uploader such as `PagedTerrainUploads` publishes it from [residentPages]
 * and the revision counters, and owns the GPU images, their binding numbers and what each frame slot
 * holds. [fallbackTextures] maps each paged surface binding to its whole-world fallback binding and
 * image.
 */
@Suppress("TooManyFunctions") // One lifecycle owns residency and edit/save revisions.
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
) {
    private val residents = LinkedHashMap<TerrainPageCoord, ResidentTerrainPage>()
    private var revision = 1L
    private var savedFallbackRevision = 1L
    private val fallbackImages = fallbackTextures.values.associate { it.first to it.second }.toMutableMap()
    private var savedSurfaceFallbackRevision = 1L

    /** Fixed lattice and shared elevation encoding range. */
    val layout get() = heights.layout

    /** Absolute origin subtracted by the scene adapter, never a scene-layer type. */
    var originX: Double = 0.0

    /** Absolute Y origin subtracted from render and collision positions. */
    var originY: Double = 0.0

    /** Absolute Z origin subtracted from render and collision positions. */
    var originZ: Double = 0.0

    /** Advances whenever a cell enters or leaves a layer; edits leave it alone. */
    var residencyRevision: Long = 1L
        private set

    /** Revision of the latest change to the coarse height image, [PagedHeightmap.fallback]. */
    var fallbackRevision: Long = 1L
        private set

    /** Revision of the latest change to [surfaceFallbacks]. */
    var surfaceFallbackRevision: Long = 1L
        private set

    /** Current whole-world coarse surface images keyed by fallback binding. Live, owner thread only. */
    val surfaceFallbacks: Map<Int, TextureAsset> get() = fallbackImages

    /** Resident cells in residency order. Live, owner thread only; do not mutate the terrain while iterating. */
    val residentPages: Collection<ResidentTerrainPage> get() = residents.values

    init {
        require(capacity in 2..65534)
        require(heights.residentCoords.isEmpty()) { "Residency must be owned by PagedTerrain." }
        require(layout.cellCountX <= 8192 && layout.cellCountZ <= 8192 && layout.samplesPerPage <= 8192)
        require(heights.fallback.width <= 8192 && heights.fallback.depth <= 8192)
        require(pageTextureTemplates.keys == fallbackTextures.keys)
        require(fallbackTextures.values.map { it.first }.toSet().size == fallbackTextures.size)
        require(fallbackTextures.values.none { it.first in pageTextureTemplates.keys })
        require((pageTextureTemplates.values + fallbackImages.values).all { it.layerCount == 1 && !it.isCubemap && it.width in 1..8192 && it.height in 1..8192 })
        require(maxUploadBytes >= pageBytes + tableBytes + heights.fallback.width.toLong() * heights.fallback.depth * 4 + fallbackImages.values.sumOf { it.data.size.toLong() })
    }

    private val heightPageBytes: Long get() = layout.samplesPerPage.toLong() * layout.samplesPerPage * 4
    private val pageBytes: Long get() = heightPageBytes + pageTextureTemplates.values.sumOf { it.data.size.toLong() }
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

    /** Returns the resident cell's layer and revisions, or null when it uses fallback. */
    fun resident(coord: TerrainPageCoord): ResidentTerrainPage? = residents[coord]

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
        residencyRevision++
        // A replacement keeps its placement, so slots draw the previous images until the new ones arrive.
        residents[coord] = ResidentTerrainPage(coord, layer, existing?.placedRevision ?: revision, page, revision)
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
            residencyRevision++
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
                heightRevision = this@PagedTerrain.revision
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
        resident.editTexture(binding, revision)
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
}

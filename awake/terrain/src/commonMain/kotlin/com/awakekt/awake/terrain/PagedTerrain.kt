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

private const val UPLOADS_MOVED = "Upload journals and binding numbers moved to PagedTerrainUploads in " +
    "awake:asset:shader-pack, which pagedTerrainContentFeature uses. Removed in a later minor."

/** Shader binding for the resident height texture array. */
@Deprecated(UPLOADS_MOVED, ReplaceWith("TERRAIN_HEIGHT_PAGES_BINDING", "com.awakekt.awake.asset.shaderpack.TERRAIN_HEIGHT_PAGES_BINDING"))
const val TERRAIN_HEIGHT_PAGES_BINDING: Int = 32

/** Shader binding for the whole-footprint cell-to-layer table. */
@Deprecated(UPLOADS_MOVED, ReplaceWith("TERRAIN_PAGE_TABLE_BINDING", "com.awakekt.awake.asset.shaderpack.TERRAIN_PAGE_TABLE_BINDING"))
const val TERRAIN_PAGE_TABLE_BINDING: Int = 33

// The deprecated journal's layout, kept private so it does not reference its own deprecated constants.
private const val LEGACY_COARSE_HEIGHT_BINDING = 1
private const val LEGACY_HEIGHT_PAGES_BINDING = 32
private const val LEGACY_PAGE_TABLE_BINDING = 33
private val LEGACY_RESERVED_BINDINGS = setOf(LEGACY_COARSE_HEIGHT_BINDING, LEGACY_HEIGHT_PAGES_BINDING, LEGACY_PAGE_TABLE_BINDING)

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
 * and the revision counters. [fallbackTextures] maps each paged surface binding to its whole-world
 * fallback binding and image.
 */
@Suppress("TooManyFunctions") // One lifecycle owns residency, edit/save revisions and, until removal, the deprecated journal.
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
    private val residents = LinkedHashMap<TerrainPageCoord, ResidentTerrainPage>()
    private var revision = 1L
    private var savedFallbackRevision = 1L
    private val fallbackImages = fallbackTextures.values.associate { it.first to it.second }.toMutableMap()
    private var savedSurfaceFallbackRevision = 1L
    private var journal = UploadJournal()

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
        require(pageTextureTemplates.keys.intersect(LEGACY_RESERVED_BINDINGS).isEmpty())
        require(pageTextureTemplates.keys == fallbackTextures.keys)
        require(fallbackTextures.values.map { it.first }.toSet().size == fallbackTextures.size)
        require(fallbackTextures.values.none { it.first in pageTextureTemplates.keys || it.first in LEGACY_RESERVED_BINDINGS })
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

    /** Whether this cell revision is visible in the specified GPU frame slot of the latest journal. */
    @Deprecated(UPLOADS_MOVED)
    fun isUploaded(coord: TerrainPageCoord, frameIndex: Int): Boolean = journal.isUploaded(coord, frameIndex)

    @Deprecated(UPLOADS_MOVED)
    override val bindings: Set<Int> get() = legacyBindings

    private val legacyBindings: Set<Int> get() = pageTextureTemplates.keys + fallbackImages.keys + LEGACY_RESERVED_BINDINGS

    /** Allocates fixed-shape arrays, an empty page table and mandatory coarse images. */
    @Deprecated(UPLOADS_MOVED, ReplaceWith("PagedTerrainUploads(this).initialImages()", "com.awakekt.awake.asset.shaderpack.PagedTerrainUploads"))
    fun initialTextures(): Map<Int, TextureAsset> = buildMap {
        put(LEGACY_COARSE_HEIGHT_BINDING, encode(heights.fallback))
        put(LEGACY_HEIGHT_PAGES_BINDING, TextureAsset(ByteArray((heightPageBytes * capacity).also { require(it <= Int.MAX_VALUE) }.toInt()), layout.samplesPerPage, layout.samplesPerPage, capacity, filtering = TextureFiltering.BaseLevelLinear))
        put(LEGACY_PAGE_TABLE_BINDING, TextureAsset(ByteArray(tableBytes.toInt()), layout.cellCountX, layout.cellCountZ, filtering = TextureFiltering.Nearest))
        for ((binding, template) in pageTextureTemplates) put(binding, TextureAsset(ByteArray((template.data.size.toLong() * capacity).also { require(it <= Int.MAX_VALUE) }.toInt()), template.width, template.height, capacity, filtering = TextureFiltering.BaseLevelLinear))
        for ((binding, fallback) in fallbackImages) put(binding, fallback.copy(filtering = TextureFiltering.BaseLevelLinear))
    }

    /**
     * Starts the journal for one new set of GPU images holding [initialTextures]. Each attached
     * content feature needs its own: a journal remembers what its images already hold, so new
     * images sharing an old journal would never receive the pages it already published.
     * [updates] and [isUploaded] follow the most recently started journal.
     */
    @Deprecated(UPLOADS_MOVED, ReplaceWith("PagedTerrainUploads(this)", "com.awakekt.awake.asset.shaderpack.PagedTerrainUploads"))
    fun uploadJournal(): ContentTextureUpdates = UploadJournal().also { journal = it }

    @Deprecated(UPLOADS_MOVED)
    override fun updates(frameIndex: Int): List<ContentTextureUpdate> = journal.updates(frameIndex)

    private fun imageBytes(binding: Int): Long = if (binding == LEGACY_HEIGHT_PAGES_BINDING) heightPageBytes else pageTextureTemplates.getValue(binding).data.size.toLong()

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

    /** The cell's heights under the legacy height binding, then each surface image, with their revisions. */
    private inline fun ResidentTerrainPage.forEachImage(action: (binding: Int, revision: Long) -> Unit) {
        action(LEGACY_HEIGHT_PAGES_BINDING, heightRevision)
        for ((binding, revision) in textureRevisions) action(binding, revision)
    }

    /** The deprecated journal: what one set of GPU images holds, per frame slot. */
    private inner class UploadJournal : ContentTextureUpdates {
        private val slotImages = mutableMapOf<Int, MutableMap<Int, MutableMap<Int, Long>>>()
        private val tableResidency = mutableMapOf<Int, Long>()
        private val fallbackRevisions = mutableMapOf<Int, Long>()
        private val surfaceFallbackRevisions = mutableMapOf<Int, Long>()

        override val bindings: Set<Int> get() = legacyBindings

        fun isUploaded(coord: TerrainPageCoord, frameIndex: Int): Boolean {
            val resident = residents[coord]
            val uploaded = resident?.let { slotImages[frameIndex]?.get(it.layer) }
            var current = resident != null && uploaded != null
            resident?.forEachImage { binding, revision -> if (uploaded?.get(binding) != revision) current = false }
            return current
        }

        override fun updates(frameIndex: Int): List<ContentTextureUpdate> {
            val slot = slotImages.getOrPut(frameIndex) { mutableMapOf() }
            val result = mutableListOf<ContentTextureUpdate>()
            val published = uploadPages(slot, tableBytes + uploadFallbacks(frameIndex, result), result)
            // Edits never move a table entry; only residency changes and newly complete cells do.
            if (published || tableResidency[frameIndex] != residencyRevision) {
                result += table(slot)
                tableResidency[frameIndex] = residencyRevision
            }
            return result
        }

        /** Queues the coarse images changed since this slot last received them; returns their bytes. */
        private fun uploadFallbacks(frameIndex: Int, result: MutableList<ContentTextureUpdate>): Long {
            var bytes = 0L
            if (fallbackRevisions[frameIndex] != fallbackRevision) {
                val image = encode(heights.fallback)
                result += ContentTextureUpdate(LEGACY_COARSE_HEIGHT_BINDING, TextureRegion(image.data, image.width, image.height))
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
            return bytes
        }

        /** Queues each cell's stale images within the budget; true when a cell became complete in this slot. */
        private fun uploadPages(slot: MutableMap<Int, MutableMap<Int, Long>>, queued: Long, result: MutableList<ContentTextureUpdate>): Boolean {
            var bytes = queued
            var published = false
            for (resident in residents.values) {
                val uploaded = slot.getOrPut(resident.layer) { mutableMapOf() }
                var stale = 0L
                resident.forEachImage { binding, revision -> if (uploaded[binding] != revision) stale += imageBytes(binding) }
                if (stale == 0L || bytes + stale > maxUploadBytes) continue
                if (!visible(resident, uploaded)) published = true
                resident.forEachImage { binding, revision ->
                    if (uploaded[binding] != revision) {
                        val image = if (binding == LEGACY_HEIGHT_PAGES_BINDING) encode(resident.page.height) else resident.page.textures.getValue(binding)
                        result += ContentTextureUpdate(binding, TextureRegion(image.data, image.width, image.height, layer = resident.layer))
                        uploaded[binding] = revision
                    }
                }
                bytes += stale
            }
            return published
        }

        /** Until all of a cell's images arrive in this slot, its entry is zero and reads the fallback. */
        private fun table(slot: Map<Int, Map<Int, Long>>): ContentTextureUpdate {
            val table = ByteArray(tableBytes.toInt())
            for ((coord, resident) in residents) {
                if (visible(resident, slot.getValue(resident.layer))) {
                    val offset = ((coord.z - layout.minCellZ) * layout.cellCountX + coord.x - layout.minCellX) * 4
                    val value = resident.layer + 1
                    table[offset] = (value and 255).toByte()
                    table[offset + 1] = (value ushr 8).toByte()
                }
            }
            return ContentTextureUpdate(LEGACY_PAGE_TABLE_BINDING, TextureRegion(table, layout.cellCountX, layout.cellCountZ))
        }

        /** Every image of the cell's placement has arrived, so its layer holds a complete, if older, cell. */
        private fun visible(resident: ResidentTerrainPage, uploaded: Map<Int, Long>): Boolean {
            var complete = true
            resident.forEachImage { binding, _ -> if ((uploaded[binding] ?: 0L) < resident.placedRevision) complete = false }
            return complete
        }
    }
}

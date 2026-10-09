/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.render.passes.ContentTextureUpdate
import com.awakekt.awake.render.passes.ContentTextureUpdates
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.TextureRegion
import com.awakekt.awake.terrain.PagedTerrain
import com.awakekt.awake.terrain.ResidentTerrainPage

/** Shader binding for the resident height texture array. */
const val TERRAIN_HEIGHT_PAGES_BINDING: Int = 32

/** Shader binding for the whole-footprint cell-to-layer table. */
const val TERRAIN_PAGE_TABLE_BINDING: Int = 33

/** The clipmap stage's whole-world height binding, which a paged surface reads as its coarse fallback. */
private const val COARSE_HEIGHT_BINDING = 1

private val RESERVED_BINDINGS = setOf(COARSE_HEIGHT_BINDING, TERRAIN_HEIGHT_PAGES_BINDING, TERRAIN_PAGE_TABLE_BINDING)

/**
 * Publishes a [PagedTerrain] into one set of GPU images, per frame slot: the images changed since
 * the slot last received them, within the terrain's byte budget, then the cell-to-layer table and
 * the coarse fallbacks. Create one per attached content feature, whose images start as
 * [initialImages]; it then republishes every resident cell to them. Drive it on the terrain's
 * owner thread.
 */
class PagedTerrainUploads(private val terrain: PagedTerrain) : ContentTextureUpdates {
    private val slotImages = HashMap<Int, HashMap<Int, HashMap<Int, Long>>>()
    private val tableResidency = HashMap<Int, Long>()
    private val fallbackRevisions = HashMap<Int, Long>()
    private val surfaceFallbackRevisions = HashMap<Int, Long>()
    private val layout get() = terrain.layout
    private val heightPageBytes: Long get() = layout.samplesPerPage.toLong() * layout.samplesPerPage * 4
    private val tableBytes: Long get() = layout.cellCountX.toLong() * layout.cellCountZ * 4

    init {
        val surfaces = terrain.pageTextureTemplates.keys + terrain.surfaceFallbacks.keys
        require(surfaces.none { it in RESERVED_BINDINGS }) { "Paged surface bindings must avoid $RESERVED_BINDINGS." }
    }

    override val bindings: Set<Int> = terrain.pageTextureTemplates.keys + terrain.surfaceFallbacks.keys + RESERVED_BINDINGS

    /** Fixed-shape arrays, an empty page table and the current coarse images. */
    fun initialImages(): Map<Int, TextureAsset> = buildMap {
        put(COARSE_HEIGHT_BINDING, encode(terrain.heights.fallback))
        put(TERRAIN_HEIGHT_PAGES_BINDING, TextureAsset(ByteArray((heightPageBytes * terrain.capacity).also { require(it <= Int.MAX_VALUE) }.toInt()), layout.samplesPerPage, layout.samplesPerPage, terrain.capacity, filtering = TextureFiltering.BaseLevelLinear))
        put(TERRAIN_PAGE_TABLE_BINDING, TextureAsset(ByteArray(tableBytes.toInt()), layout.cellCountX, layout.cellCountZ, filtering = TextureFiltering.Nearest))
        for ((binding, template) in terrain.pageTextureTemplates) {
            put(binding, TextureAsset(ByteArray((template.data.size.toLong() * terrain.capacity).also { require(it <= Int.MAX_VALUE) }.toInt()), template.width, template.height, terrain.capacity, filtering = TextureFiltering.BaseLevelLinear))
        }
        for ((binding, fallback) in terrain.surfaceFallbacks) put(binding, fallback.copy(filtering = TextureFiltering.BaseLevelLinear))
    }

    /** Whether every image of the cell's current revision is in the specified frame slot. */
    fun isUploaded(coord: TerrainPageCoord, frameIndex: Int): Boolean {
        val resident = terrain.resident(coord)
        val uploaded = resident?.let { slotImages[frameIndex]?.get(it.layer) }
        var current = resident != null && uploaded != null
        resident?.forEachImage { binding, revision -> if (uploaded?.get(binding) != revision) current = false }
        return current
    }

    override fun updates(frameIndex: Int): List<ContentTextureUpdate> {
        val slot = slotImages.getOrPut(frameIndex) { HashMap() }
        val result = mutableListOf<ContentTextureUpdate>()
        val published = uploadPages(slot, tableBytes + uploadFallbacks(frameIndex, result), result)
        // Edits never move a table entry; only residency changes and newly complete cells do.
        if (published || tableResidency[frameIndex] != terrain.residencyRevision) {
            result += table(slot)
            tableResidency[frameIndex] = terrain.residencyRevision
        }
        return result
    }

    /** Queues the coarse images changed since this slot last received them; returns their bytes. */
    private fun uploadFallbacks(frameIndex: Int, result: MutableList<ContentTextureUpdate>): Long {
        var bytes = 0L
        if (fallbackRevisions[frameIndex] != terrain.fallbackRevision) {
            val image = encode(terrain.heights.fallback)
            result += ContentTextureUpdate(COARSE_HEIGHT_BINDING, TextureRegion(image.data, image.width, image.height))
            bytes += image.data.size
            fallbackRevisions[frameIndex] = terrain.fallbackRevision
        }
        if (surfaceFallbackRevisions[frameIndex] != terrain.surfaceFallbackRevision) {
            for ((binding, image) in terrain.surfaceFallbacks) {
                result += ContentTextureUpdate(binding, TextureRegion(image.data, image.width, image.height))
                bytes += image.data.size
            }
            surfaceFallbackRevisions[frameIndex] = terrain.surfaceFallbackRevision
        }
        return bytes
    }

    /** Queues each cell's stale images within the budget; true when a cell became complete in this slot. */
    private fun uploadPages(slot: HashMap<Int, HashMap<Int, Long>>, queued: Long, result: MutableList<ContentTextureUpdate>): Boolean {
        var bytes = queued
        var published = false
        for (resident in terrain.residentPages) {
            val uploaded = slot.getOrPut(resident.layer) { HashMap() }
            // Only the images edited since this slot last received them; a height edit leaves surfaces alone.
            var stale = 0L
            resident.forEachImage { binding, revision -> if (uploaded[binding] != revision) stale += imageBytes(binding) }
            if (stale == 0L || bytes + stale > terrain.maxUploadBytes) continue
            if (!visible(resident, uploaded)) published = true
            resident.forEachImage { binding, revision ->
                if (uploaded[binding] != revision) {
                    val image = if (binding == TERRAIN_HEIGHT_PAGES_BINDING) encode(resident.page.height) else resident.page.textures.getValue(binding)
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
        for (resident in terrain.residentPages) {
            if (visible(resident, slot.getValue(resident.layer))) {
                val offset = ((resident.coord.z - layout.minCellZ) * layout.cellCountX + resident.coord.x - layout.minCellX) * 4
                val value = resident.layer + 1
                table[offset] = (value and 255).toByte()
                table[offset + 1] = (value ushr 8).toByte()
            }
        }
        return ContentTextureUpdate(TERRAIN_PAGE_TABLE_BINDING, TextureRegion(table, layout.cellCountX, layout.cellCountZ))
    }

    /** Every image of the cell's placement has arrived, so its layer holds a complete, if older, cell. */
    private fun visible(resident: ResidentTerrainPage, uploaded: Map<Int, Long>): Boolean {
        var complete = true
        resident.forEachImage { binding, _ -> if ((uploaded[binding] ?: 0L) < resident.placedRevision) complete = false }
        return complete
    }

    private fun imageBytes(binding: Int): Long =
        if (binding == TERRAIN_HEIGHT_PAGES_BINDING) heightPageBytes else terrain.pageTextureTemplates.getValue(binding).data.size.toLong()

    /** Unsigned 16-bit heights over the lattice's elevation range, high byte in red and low in green. */
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

/** The cell's heights under [TERRAIN_HEIGHT_PAGES_BINDING], then each surface image, with their revisions. */
private inline fun ResidentTerrainPage.forEachImage(action: (binding: Int, revision: Long) -> Unit) {
    action(TERRAIN_HEIGHT_PAGES_BINDING, heightRevision)
    for ((binding, revision) in textureRevisions) action(binding, revision)
}

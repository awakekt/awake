/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.HeightmapSampleEdit
import com.awakekt.awake.asset.terrain.PagedHeightmap
import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.asset.terrain.TerrainPageLayout
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.ContentTextureUpdate
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.terrain.PagedTerrain
import com.awakekt.awake.terrain.TerrainPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PagedTerrainUploadsTest {
    private fun terrain(uploadBytes: Int = 512, surfaces: Boolean = false): PagedTerrain {
        val layout = TerrainPageLayout(-1, 0, 4, 1, 4f, 4, minElevation = 0f, maxElevation = 10f)
        val surface = TextureAsset(ByteArray(16), 2, 2)
        return PagedTerrain(
            PagedHeightmap(layout, Heightmap(FloatArray(10), 5, 2, Vec3f(4f, 1f, 4f))),
            capacity = 2,
            pageTextureTemplates = if (surfaces) mapOf(SURFACE to surface) else emptyMap(),
            fallbackTextures = if (surfaces) mapOf(SURFACE to (COARSE_SURFACE to surface)) else emptyMap(),
            maxUploadBytes = uploadBytes,
        )
    }

    private fun page(surfaces: Boolean = false, value: Float = 1f) =
        TerrainPage(Heightmap(FloatArray(25) { value }, 5, 5, Vec3f(1f, 1f, 1f)), if (surfaces) mapOf(SURFACE to TextureAsset(ByteArray(16), 2, 2)) else emptyMap())

    private fun List<ContentTextureUpdate>.table() = single { it.binding == TERRAIN_PAGE_TABLE_BINDING }.region.data

    @Test fun partialUploadsPublishOnlyCompleteCellsAndEveryFrameSlotCatchesUp() {
        val terrain = terrain(uploadBytes = 156) // 100-byte page + 16-byte table + 40-byte fallback.
        val uploads = PagedTerrainUploads(terrain)
        terrain.put(TerrainPageCoord(-1, 0), page())
        terrain.put(TerrainPageCoord(0, 0), page())
        val first = uploads.updates(0)
        assertTrue(first.sumOf { it.region.data.size } <= 156)
        assertEquals(1, first.count { it.binding == TERRAIN_HEIGHT_PAGES_BINDING })
        assertEquals(1, first.table()[0].toInt())
        assertEquals(0, first.table()[4].toInt())
        assertTrue(uploads.isUploaded(TerrainPageCoord(-1, 0), 0))
        assertFalse(uploads.isUploaded(TerrainPageCoord(0, 0), 0))
        assertEquals(2, uploads.updates(0).table()[4].toInt())
        assertTrue(uploads.updates(0).isEmpty())
        assertEquals(1, uploads.updates(1).count { it.binding == TERRAIN_HEIGHT_PAGES_BINDING })
        terrain.evict(TerrainPageCoord(-1, 0))
        terrain.put(TerrainPageCoord(1, 0), TerrainPage(Heightmap(FloatArray(25) { if (it % 5 == 0) 1f else 2f }, 5, 5, Vec3f(1f, 1f, 1f))))
        val replacement = uploads.updates(0).table()
        assertEquals(0, replacement[0].toInt())
        assertEquals(1, replacement[8].toInt())
    }

    @Test fun freshUploadsRepublishEveryResidentCellToNewImages() {
        val terrain = terrain()
        terrain.put(TerrainPageCoord(-1, 0), page())
        terrain.put(TerrainPageCoord(0, 0), page())
        PagedTerrainUploads(terrain).updates(0)
        // A re-attach builds new images from initialImages, whose table is empty.
        val reattached = PagedTerrainUploads(terrain)
        assertTrue(reattached.initialImages().getValue(TERRAIN_PAGE_TABLE_BINDING).data.all { it.toInt() == 0 })
        val republished = reattached.updates(0)
        assertEquals(2, republished.count { it.binding == TERRAIN_HEIGHT_PAGES_BINDING })
        assertEquals(1, republished.table()[0].toInt())
        assertEquals(2, republished.table()[4].toInt())
    }

    @Test fun anEditResendsOnlyItsImagesAndKeepsTheCellDrawn() {
        val terrain = terrain(surfaces = true)
        val uploads = PagedTerrainUploads(terrain)
        val coord = TerrainPageCoord(-1, 0)
        terrain.put(coord, page(surfaces = true))
        uploads.updates(0)
        terrain.editHeights(listOf(HeightmapSampleEdit(1, 1, 3f)))
        val heightEdit = uploads.updates(0)
        assertEquals(listOf(TERRAIN_HEIGHT_PAGES_BINDING), heightEdit.map { it.binding })
        terrain.editTexture(coord, SURFACE, TextureAsset(ByteArray(16) { 7 }, 2, 2))
        assertFalse(uploads.isUploaded(coord, 0))
        assertEquals(listOf(SURFACE), uploads.updates(0).map { it.binding })
        assertTrue(uploads.isUploaded(coord, 0))
    }

    @Test fun bindingsCoverSurfacesAndSharedTexturesCannotTakeOne() {
        val layout = TerrainPageLayout(0, 0, 1, 1, 4f, 4)
        val clash = TextureAsset(ByteArray(16), 2, 2)
        val terrain = PagedTerrain(PagedHeightmap(layout, Heightmap(FloatArray(4), 2, 2, Vec3f(4f, 1f, 4f))), 2, mapOf(SURFACE to clash), mapOf(SURFACE to (TERRAIN_PAGE_TABLE_BINDING + 1 to clash)))
        assertEquals(setOf(1, TERRAIN_HEIGHT_PAGES_BINDING, TERRAIN_PAGE_TABLE_BINDING, SURFACE, TERRAIN_PAGE_TABLE_BINDING + 1), PagedTerrainUploads(terrain).bindings)
        assertFailsWith<IllegalArgumentException> { pagedTerrainContentFeature(terrain, sharedTextures = mapOf(SURFACE to clash)) }
    }

    /** The binding layout is the uploads', so they, not the terrain, refuse a surface that takes one of its bindings. */
    @Test fun aSurfaceOnAReservedBindingIsRefusedByTheUploads() {
        val layout = TerrainPageLayout(0, 0, 1, 1, 4f, 4)
        val image = TextureAsset(ByteArray(16), 2, 2)
        fun heights() = PagedHeightmap(layout, Heightmap(FloatArray(4), 2, 2, Vec3f(4f, 1f, 4f)))
        val onTheTable = PagedTerrain(heights(), 2, mapOf(TERRAIN_PAGE_TABLE_BINDING to image), mapOf(TERRAIN_PAGE_TABLE_BINDING to (COARSE_SURFACE to image)))
        val fallbackOnTheHeights = PagedTerrain(heights(), 2, mapOf(SURFACE to image), mapOf(SURFACE to (TERRAIN_HEIGHT_PAGES_BINDING to image)))

        assertFailsWith<IllegalArgumentException> { PagedTerrainUploads(onTheTable) }
        assertFailsWith<IllegalArgumentException> { PagedTerrainUploads(fallbackOnTheHeights) }
    }

    private companion object {
        const val SURFACE = 5
        const val COARSE_SURFACE = 34
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.pagedTerrainContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.PagedHeightmap
import com.awakekt.awake.asset.terrain.RawHeightmapCodec
import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.asset.terrain.TerrainPageLayout
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.kit.terrainlayers.PagedTerrainLayers
import com.awakekt.awake.kit.terrainlayers.TerrainControlMap
import com.awakekt.awake.kit.terrainlayers.TerrainControlMapCodec
import com.awakekt.awake.kit.terrainlayers.TerrainLayer
import com.awakekt.awake.kit.terrainlayers.TerrainLayerPalette
import com.awakekt.awake.kit.terrainlayers.TerrainLayerPaletteCodec
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.terrain.PagedTerrain
import com.awakekt.awake.terrain.TerrainPage
import com.awakekt.awake.terrain.TerrainPageAssets
import com.awakekt.awake.terrain.TerrainPageIndex
import com.awakekt.awake.terrain.TerrainPageIndexCodec
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Desktop wgpu-native: residency changes 2,272 pixels; the fallback-only control changes zero. */
class WebGpuPagedTerrainTest {
    @Test fun residentHeightUploadChangesPixelsAndEvictionReturnsToFallback() = runBlocking {
        val session = webGpuHeadlessScene()
        val renderer = session.renderer
        try {
            val terrain = PagedTerrain(
                PagedHeightmap(
                    TerrainPageLayout(0, 0, 1, 1, 16f, 16, maxElevation = 8f),
                    Heightmap(FloatArray(25), 5, 5, Vec3f(4f, 1f, 4f)),
                ),
                2,
            )
            val attached = (renderer as ContentFeatureHost).attachContentFeature(pagedTerrainContentFeature(terrain, TerrainClipmapConfig(ringCount = 3, ringResolution = 16, baseSpacing = 1f)))
            val lens = Lens(eye = Vec3f(8f, 8f, 28f), center = Vec3f(8f, 0f, 8f), fovYRadians = 1f, near = 0.1f, far = 200f)
            suspend fun capture(): ByteArray {
                val target = renderer.createRenderTarget(128, 128)
                try {
                    renderer.renderSceneToTexture(target, lens, emptyList())
                    return renderer.readPixels(target).data
                } finally {
                    target.destroy()
                }
            }
            val baseline = capture()
            val coord = TerrainPageCoord(0, 0)
            terrain.put(
                coord,
                TerrainPage(
                    Heightmap(
                        FloatArray(289) { index ->
                            val x = (index % 17 - 8) / 8f
                            val z = (index / 17 - 8) / 8f
                            (1f - x * x - z * z).coerceAtLeast(0f) * 6f
                        },
                        17,
                        17,
                        Vec3f(1f, 1f, 1f),
                    ),
                ),
            )
            val raised = capture()
            fun changed(a: ByteArray, b: ByteArray) = (0 until a.size / 4).count { pixel -> (0..2).any { channel -> abs((a[pixel * 4 + channel].toInt() and 255) - (b[pixel * 4 + channel].toInt() and 255)) > 8 } }
            val count = changed(baseline, raised)
            println("PAGED_TERRAIN changed pixels=$count")
            assertTrue(count > 100, "Resident height upload changed only $count pixels.")
            terrain.evict(coord)
            val control = changed(baseline, capture())
            println("PAGED_TERRAIN fallback negative control=$control")
            assertTrue(control < 10, "Evicted page still affects $control pixels.")
            attached.detach()
        } finally {
            session.close()
        }
    }

    @Test fun streamedControlsAndOriginShiftPreserveSurfacePixels() = runBlocking {
        val session = webGpuHeadlessScene()
        val renderer = session.renderer
        try {
            val index = TerrainPageIndex(
                minCellX = 0, minCellZ = 0, cellCountX = 1, cellCountZ = 1, cellSize = 16f, intervals = 16,
                minElevation = 0f, maxElevation = 8f, fallbackHeight = "coarse.raw", fallbackWidth = 5, fallbackDepth = 5,
                palette = "palette.json", fallbackControl = "coarse.terrainctl", controlWidth = 4, controlDepth = 4, controlSlots = 8,
                pages = listOf(TerrainPageAssets(0, 0, "cell.raw", "cell.terrainctl")),
            )
            fun control(layer: Int) = TerrainControlMap(4, 4, ByteArray(128) { layer.toByte() }, ByteArray(128) { if (it % 8 == 0) 255.toByte() else 0 }, 8)
            val files = mapOf(
                "index.json" to TerrainPageIndexCodec.encode(index),
                "palette.json" to TerrainLayerPaletteCodec.encode(TerrainLayerPalette(layers = listOf(TerrainLayer("soil", "soil.png"), TerrainLayer("stone", "stone.png")))),
                "soil.png" to byteArrayOf(0),
                "stone.png" to byteArrayOf(2),
                "coarse.raw" to RawHeightmapCodec.encode16LittleEndian(Heightmap(FloatArray(25), 5, 5, Vec3f(4f, 1f, 4f)), 0f, 8f),
                "cell.raw" to RawHeightmapCodec.encode16LittleEndian(Heightmap(FloatArray(289), 17, 17, Vec3f(1f, 1f, 1f)), 0f, 8f),
                "coarse.terrainctl" to TerrainControlMapCodec.encode(control(0)),
                "cell.terrainctl" to TerrainControlMapCodec.encode(control(1)),
            )
            val loaded = PagedTerrainLayers.load(AssetSource { Result.success(files.getValue(it.value)) }, AssetPath("index.json"), capacity = 2) { bytes ->
                TextureAsset(
                    ByteArray(16) { i ->
                        if (i % 4 == 3) {
                            255.toByte()
                        } else if (i % 4 == bytes[0].toInt()) {
                            (if (i / 4 % 3 == 0) 240 else 110).toByte()
                        } else {
                            16
                        }
                    },
                    2,
                    2,
                )
            }
            val attached = (renderer as ContentFeatureHost).attachContentFeature(loaded.content)
            val lens = Lens(eye = Vec3f(8f, 8f, 28f), center = Vec3f(8f, 0f, 8f), fovYRadians = 1f, near = 0.1f, far = 200f)
            suspend fun capture(view: Lens): ByteArray {
                val target = renderer.createRenderTarget(128, 128)
                try {
                    renderer.renderSceneToTexture(target, view, emptyList())
                    return renderer.readPixels(target).data
                } finally {
                    target.destroy()
                }
            }
            fun changed(a: ByteArray, b: ByteArray) = (0 until a.size / 4).count { pixel -> (0..2).any { channel -> abs((a[pixel * 4 + channel].toInt() and 255) - (b[pixel * 4 + channel].toInt() and 255)) > 8 } }
            val baseline = capture(lens)
            val coord = TerrainPageCoord(0, 0)
            loaded.terrain.put(coord, requireNotNull(loaded.read(coord)))
            val resident = capture(lens)
            val painted = changed(baseline, resident)
            println("PAGED_SURFACE changed pixels=$painted")
            assertTrue(painted > 100, "Resident control upload changed only $painted pixels.")
            loaded.terrain.originX = 16.0
            loaded.terrain.originZ = -16.0
            val shifted = Lens(eye = Vec3f(-8f, 8f, 44f), center = Vec3f(-8f, 0f, 24f), fovYRadians = 1f, near = 0.1f, far = 200f)
            val originChanges = changed(resident, capture(shifted))
            println("PAGED_SURFACE origin negative control=$originChanges")
            assertTrue(originChanges < 10, "Origin shift changed $originChanges surface pixels.")
            loaded.editControl(coord, control(0))
            val restored = changed(baseline, capture(shifted))
            println("PAGED_SURFACE edit negative control=$restored")
            assertTrue(restored < 10, "Control edit left $restored unexpected pixels.")
            attached.detach()
        } finally {
            session.close()
        }
    }
}

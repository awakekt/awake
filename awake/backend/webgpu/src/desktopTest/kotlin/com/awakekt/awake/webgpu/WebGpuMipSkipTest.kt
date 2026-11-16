/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.render.pipeline.GpuDevice
import com.awakekt.awake.render.pipeline.TextureUpload
import com.awakekt.awake.render.pipeline.TextureUploadObserver
import com.awakekt.awake.render.pipeline.TextureUploads
import com.awakekt.awake.render.pipeline.createImageMaterial
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.webgpu.renderer.uploadTexture
import com.awakekt.awake.webgpu.texture.Texture
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Renderer.textureMipSkip` on WebGPU (#1660): a texture only ever sampled by UV -- a material's, or a
 * content feature's that is never written -- uploads without its top levels, while its `width` and
 * `height` stay the source size, so a pixel rect turned into UVs with them samples the same place.
 */
class WebGpuMipSkipTest {
    @Test
    fun aSkippedUploadIsExactlyHalfOrQuarterSizeAndKeepsItsSourceSize() {
        for (skip in 0..2) {
            withContentProbe(gradient(), scale = 1f to 1f, beforeAttach = { it.textureMipSkip = skip }) { renderer, device, frame ->
                frame()
                val content = device.sampledTextures.single { it.width == TEXELS }
                assertEquals(TEXELS shr skip, content.uploadedWidth, "a terrain layer at skip $skip")
                assertEquals(TEXELS shr skip, content.uploadedHeight)
                assertEquals(LEVELS - skip, content.uploadedMipLevels)
                assertEquals(TEXELS, content.height, "the source size is kept")

                val material = renderer.uploadTexture(gradient())
                assertEquals(TEXELS shr skip, material.uploadedWidth, "a material texture at skip $skip")
                assertEquals(TEXELS, material.width)
            }
        }
    }

    @Test
    fun pixelExactUiAndSingleLevelTexturesUploadWhole() = withContentProbe(gradient(), scale = 1f to 1f) { renderer, device, _ ->
        renderer.textureMipSkip = 5
        assertEquals(2, renderer.textureMipSkip, "clamped to quarter size")
        for (filtering in listOf(TextureFiltering.Nearest, TextureFiltering.BaseLevelLinear)) {
            val texture = renderer.uploadTexture(gradient(filtering))
            assertEquals(TEXELS, texture.uploadedWidth, "$filtering uploads whole")
        }
        // UI images, glyph atlases and written textures are made with the constructor, which never skips.
        val ui = Texture(device, {}, gradient().data, TEXELS, TEXELS)
        assertEquals(TEXELS, ui.uploadedWidth, "a UI image uploads whole")
        assertEquals(LEVELS, ui.uploadedMipLevels)
        ui.destroy()
    }

    /** The upload observer a consumer totals GPU texture memory with sees the half-size image at skip 1. */
    @Test
    fun theUploadObserverReportsTheUploadedSize() = withContentProbe(gradient(), scale = 1f to 1f) { renderer, _, _ ->
        val seen = mutableListOf<TextureUpload>()
        TextureUploads.observer = TextureUploadObserver { seen += it }
        try {
            renderer.textureMipSkip = 1
            renderer.createImageMaterial(gradient().asGpuImageSource(), TextureFiltering.Linear, GpuDevice.DEFAULT_UNIFORM_FLOAT_COUNT, null, label = "ground").destroy()
        } finally {
            TextureUploads.observer = null
        }
        val upload = seen.single()
        assertEquals(TEXELS, upload.width, "the source size")
        assertEquals(TEXELS / 2, upload.uploadedWidth, "the GPU image, at skip 1")
        assertEquals(TEXELS / 2, upload.uploadedHeight)
        assertEquals(LEVELS - 1, upload.uploadedMipLevels)
        println("WebGpuMipSkip: observer saw $upload")
    }

    /**
     * A sprite-sheet draw: the probe samples the pixel rect (128, 0)-(256, 128) of the texture, turned
     * into UVs by dividing by `width`, as an atlas or a nine-slice does. A half-size upload keeps
     * `width`, so the rect maps to the same UVs and the frame matches the full upload within filtering.
     */
    @Test
    fun aPixelRectDrawsTheSameRegionAtHalfSize() {
        val frames = (0..1).map { skip ->
            var frame = ByteArray(0)
            withContentProbe(gradient(), scale = RECT / TEXELS to RECT / TEXELS, offset = RECT / TEXELS to 0f, beforeAttach = { it.textureMipSkip = skip }) { _, device, draw ->
                val texture = device.sampledTextures.single { it.width == TEXELS }
                assertEquals(TEXELS, texture.width, "the rect is divided by the source width at skip $skip")
                frame = draw()
            }
            frame
        }
        val reds = frames[0].indices.step(4).map { frames[0][it].toInt() and 0xFF }
        assertTrue(reds.max() - reds.min() > RECT / 2, "the frame shows the rect's red ramp, not one colour: ${reds.min()}..${reds.max()}")
        val worst = frames[0].indices.maxOf { abs((frames[0][it].toInt() and 0xFF) - (frames[1][it].toInt() and 0xFF)) }
        println("WebGpuMipSkip: the rect's worst channel difference between skip 0 and 1 is $worst of 255")
        assertTrue(worst <= TOLERANCE, "the same region, within filtering: $worst")
    }

    private companion object {
        const val TEXELS = 256
        const val LEVELS = 9
        const val RECT = 128f
        const val TOLERANCE = 4

        /** Red along x, green along y, blue by quadrant: smooth, so a box-filtered level matches it closely. */
        fun gradient(filtering: TextureFiltering = TextureFiltering.Linear) = TextureAsset(
            data = ByteArray(TEXELS * TEXELS * 4) { index ->
                val pixel = index / 4
                val x = pixel % TEXELS
                val y = pixel / TEXELS
                when (index % 4) {
                    0 -> x.toByte()
                    1 -> y.toByte()
                    2 -> (if (x < TEXELS / 2) 64 else 192).toByte()
                    else -> -1
                }
            },
            width = TEXELS,
            height = TEXELS,
            filtering = filtering,
        )
    }
}

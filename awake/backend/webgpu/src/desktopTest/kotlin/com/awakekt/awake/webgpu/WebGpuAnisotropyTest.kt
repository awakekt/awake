/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.render.texture.TextureAsset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Renderer.maxAnisotropy` on WebGPU (#1660), through a content feature attached as a terrain layer
 * is: its texture is the feature's own, which reaches the setting through the device's set of sampled
 * textures, not the renderer's. Its probe samples 8-texel stripes with a footprint 8 times longer along
 * one axis through `textureSample` with implicit derivatives, which lavapipe filters anisotropically;
 * it ignores anisotropy for `textureSampleGrad`, so the PBR textured shader's change shows only on a
 * real GPU -- recorded on #1660.
 */
class WebGpuAnisotropyTest {
    @Test
    fun aChangedAnisotropyRebuildsTheContentGroupAndResolvesTheStripes() = withContentProbe(stripes(), scale = 1f to 8f) { renderer, device, frame ->
        val off = frame()
        val texture = device.sampledTextures.single { it.width == TEXELS }
        assertEquals(1, texture.samplerAnisotropy)

        renderer.maxAnisotropy = SAMPLES
        assertEquals(SAMPLES, texture.samplerAnisotropy, "the content feature's texture took the setting")
        val generation = device.samplerGeneration
        val on = frame()
        val changed = off.indices.step(4).count { kotlin.math.abs((off[it].toInt() and 0xFF) - (on[it].toInt() and 0xFF)) > 2 }
        println("WebGpuAnisotropy: $changed of ${SIZE * SIZE} pixels changed between 1 and $SAMPLES samples")
        assertTrue(changed > SIZE * SIZE / 2, "stripes resolve along the long axis at $SAMPLES samples: $changed changed")

        renderer.maxAnisotropy = SAMPLES
        assertEquals(generation, device.samplerGeneration, "the same value again makes nothing")
        renderer.maxAnisotropy = 1
        assertContentEquals(off, frame(), "back at 1, the frame is the first one again")
        assertEquals(16, renderer.maxSupportedAnisotropy)
    }

    private companion object {
        const val SIZE = PROBE_SIZE
        const val SAMPLES = 8
        const val TEXELS = 256
        const val STRIPE = 8

        fun stripes() = TextureAsset(
            data = ByteArray(TEXELS * TEXELS * 4) { index -> if (index % 4 == 3 || ((index / 4) % TEXELS / STRIPE) % 2 == 0) -1 else 0 },
            width = TEXELS,
            height = TEXELS,
        )
    }
}

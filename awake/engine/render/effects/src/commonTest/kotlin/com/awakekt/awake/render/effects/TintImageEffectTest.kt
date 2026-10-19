/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.imagepass.ImagePassTargetContext
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TintImageEffectTest {

    @Test
    fun `shader registry contains built-in copy and tint specs`() {
        val copy = ImagePassShaderRegistry[ImageEffectShaders.COPY_SHADER_ID]
        val tint = ImagePassShaderRegistry[ImageEffectShaders.TINT_SHADER_ID]
        assertNotNull(copy)
        assertNotNull(tint)
        assertTrue(copy.wgslSource.contains("fn vertexMain"))
        assertTrue(copy.wgslSource.contains("fn fragmentMain"))
        assertTrue(tint.wgslSource.contains("fn vertexMain"))
        assertTrue(tint.wgslSource.contains("fn fragmentMain"))
        assertEquals(4, ImageEffectShaders.CopyUniformLayout.total)
        assertEquals(8, ImageEffectShaders.TintUniformLayout.total)
    }

    @Test
    fun `buildChain produces two-step snapshot-to-transient-to-active chain when enabled`() {
        val context = ImagePassTargetContext(
            width = 128,
            height = 64,
            clipSpace = ClipSpace.Vulkan,
            snapshotSupported = true,
            effectQuality = EffectQuality.High,
        )
        val bounds = ImagePassRect(16f, 8f, 64f, 32f)
        val chain = TintImageEffect.buildChain(
            bounds = bounds,
            tintColor = Color(0f, 1f, 0f, 1f),
            strength = 0.5f,
            targetContext = context,
        )

        assertFalse(chain.isEmpty)
        assertTrue(chain.requiresDestinationSnapshot)
        assertEquals(1, chain.transientTargets.size)
        assertEquals(64, chain.transientTargets.single().width)
        assertEquals(32, chain.transientTargets.single().height)
        assertEquals(2, chain.steps.size)
        assertEquals(ImagePassSource.DestinationSnapshot, chain.steps[0].inputs.single().source)
        assertEquals(
            ImagePassDestination.TransientTarget(TintImageEffect.TRANSIENT_TARGET_ID),
            chain.steps[0].destination,
        )
        assertEquals(
            ImagePassSource.TransientTarget(TintImageEffect.TRANSIENT_TARGET_ID),
            chain.steps[1].inputs.single().source,
        )
        assertTrue(chain.steps[1].destination is ImagePassDestination.ActiveTarget)
    }

    @Test
    fun `buildChain downscales transient target on Low quality and returns Empty on Off`() {
        val bounds = ImagePassRect(0f, 0f, 64f, 32f)
        val lowChain = TintImageEffect.buildChain(
            bounds = bounds,
            tintColor = Color(1f, 0f, 0f, 1f),
            strength = 0.5f,
            targetContext = ImagePassTargetContext(
                width = 64,
                height = 32,
                clipSpace = ClipSpace.WebGpu,
                effectQuality = EffectQuality.Low,
            ),
        )
        assertEquals(32, lowChain.transientTargets.single().width)
        assertEquals(16, lowChain.transientTargets.single().height)

        val offChain = TintImageEffect.buildChain(
            bounds = bounds,
            tintColor = Color(1f, 0f, 0f, 1f),
            strength = 0.5f,
            targetContext = ImagePassTargetContext(
                width = 64,
                height = 32,
                clipSpace = ClipSpace.WebGpu,
                effectQuality = EffectQuality.Off,
            ),
        )
        assertTrue(offChain.isEmpty)
    }

    @Test
    fun `applyReference tints only pixels inside bounds`() {
        val width = 4
        val height = 4
        val pixels = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            pixels[i * 4] = 200.toByte()
            pixels[i * 4 + 1] = 40.toByte()
            pixels[i * 4 + 2] = 40.toByte()
            pixels[i * 4 + 3] = 255.toByte()
        }
        val source = TextureAsset(pixels, width, height)
        val result = TintImageEffect.applyReference(
            source = source,
            bounds = ImagePassRect(1f, 1f, 2f, 2f),
            tintColor = Color(0f, 200f / 255f, 40f / 255f, 1f),
            strength = 0.5f,
        )

        // Outside bounds (0, 0) stays (200, 40, 40)
        assertEquals(200, result.data[0].toInt() and 0xFF)
        assertEquals(40, result.data[1].toInt() and 0xFF)

        // Inside bounds (1, 1) is mixed 50% toward (0, 200, 40) -> (100, 120, 40)
        val insideOffset = (1 * width + 1) * 4
        assertEquals(100, result.data[insideOffset].toInt() and 0xFF)
        assertEquals(120, result.data[insideOffset + 1].toInt() and 0xFF)
        assertEquals(40, result.data[insideOffset + 2].toInt() and 0xFF)
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.imagepass

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.recommendedEffectQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImagePassTest {
    private val testShader = ImagePassShaderSpec(
        id = "test-copy",
        wgslSource = "@vertex fn vs_main() -> @builtin(position) vec4<f32> { return vec4<f32>(0.0); }",
        groupBindings = GroupBindings.TexturedMaterial,
        vertexFormat = VertexFormat.PositionUv,
    )

    @Test
    fun `rect clamp and normalization stay within target bounds`() {
        val rect = ImagePassRect(-10f, 20f, 110f, 100f)
        val clamped = rect.clampTo(80f, 100f)
        assertEquals(ImagePassRect(0f, 20f, 80f, 80f), clamped)

        val normalized = rect.normalizedIn(80, 100)
        assertEquals(ImagePassRect(0f, 0.2f, 1f, 0.8f), normalized)
        assertTrue(ImagePassRect(10f, 10f, 0f, 20f).isEmpty)
    }

    @Test
    fun `target spec scales down by divisor and clamps to at least one pixel`() {
        val half = ImagePassTargetSpec(id = "half", scaleDivisor = 2)
        assertEquals(64, half.scaledWidth(128))
        assertEquals(32, half.scaledHeight(64))
        assertEquals(1, half.scaledWidth(1))
        assertEquals(1, half.scaledHeight(1))

        val explicit = ImagePassTargetSpec(id = "fixed", scaleDivisor = 4, width = 48, height = 24)
        assertEquals(48, explicit.scaledWidth(128))
        assertEquals(24, explicit.scaledHeight(64))
    }

    @Test
    fun `chain detects destination snapshot dependency`() {
        val step = ImagePassStep(
            shader = testShader,
            inputs = listOf(
                ImagePassTextureBinding(
                    binding = 0,
                    source = ImagePassSource.DestinationSnapshot,
                ),
            ),
            destination = ImagePassDestination.ActiveTarget(),
        )
        val chain = ImagePassChain(
            sourceBounds = ImagePassRect(0f, 0f, 32f, 32f),
            transientTargets = emptyList(),
            steps = listOf(step),
        )

        assertFalse(chain.isEmpty)
        assertTrue(chain.requiresDestinationSnapshot)
        assertTrue(ImagePassChain.Empty.isEmpty)
        assertFalse(ImagePassChain.Empty.requiresDestinationSnapshot)
    }

    @Test
    fun `device class maps to expected recommended effect quality`() {
        assertEquals(EffectQuality.High, GpuDeviceClass.Discrete.recommendedEffectQuality())
        assertEquals(EffectQuality.Medium, GpuDeviceClass.Integrated.recommendedEffectQuality())
        assertEquals(EffectQuality.Low, GpuDeviceClass.Mobile.recommendedEffectQuality())
        assertEquals(EffectQuality.Low, GpuDeviceClass.Software.recommendedEffectQuality())
        assertEquals(EffectQuality.Medium, GpuDeviceClass.Unknown.recommendedEffectQuality())
    }
}

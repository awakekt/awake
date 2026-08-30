/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.pipeline

import io.github.awakelab.awake.core.geometry.VertexFormats2D
import io.github.awakelab.awake.core.graphics2d.BlendMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiPipelineDescriptorTest {

    @Test
    fun defaultsToStandardValues() {
        val desc = UiPipelineDescriptor(
            variant = UiPipelineVariant.Quad,
            vertexFormat = VertexFormats2D.Quad,
        )

        assertEquals(UiPipelineVariant.Quad, desc.variant)
        assertEquals(VertexFormats2D.Quad, desc.vertexFormat)
        assertEquals(BlendMode.SourceOver, desc.blendMode)
        assertFalse(desc.isPremultiplied)
    }

    @Test
    fun supportsTargetCompositeAndPremultipliedAlpha() {
        val desc = UiPipelineDescriptor(
            variant = UiPipelineVariant.TargetComposite,
            vertexFormat = VertexFormats2D.Glyph,
            blendMode = BlendMode.Plus,
            isPremultiplied = true,
        )

        assertEquals(UiPipelineVariant.TargetComposite, desc.variant)
        assertEquals(BlendMode.Plus, desc.blendMode)
        assertTrue(desc.isPremultiplied)
    }
}

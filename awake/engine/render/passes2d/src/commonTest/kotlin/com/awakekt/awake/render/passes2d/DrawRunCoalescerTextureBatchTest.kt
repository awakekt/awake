/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.TextureRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class DrawRunCoalescerTextureBatchTest {
    @Test
    fun fiveHundredRotatedTintedAtlasParticlesSharingOneTextureCoalesceIntoSingleRun() {
        val sharedAtlas = Any()
        val commands = List(500) { index ->
            val col = index % 4
            val row = (index / 4) % 4
            DrawCommand.Texture(
                x = (index % 25) * 12f,
                y = (index / 25) * 12f,
                w = 10f,
                h = 10f,
                material = sharedAtlas,
                alpha = 0.25f + (index % 4) * 0.2f,
                rotationDegrees = index * 13f,
                blendMode = BlendMode.SourceOver,
                premultiplied = true,
                region = TextureRegion(
                    u0 = col * 0.25f,
                    v0 = row * 0.25f,
                    u1 = (col + 1) * 0.25f,
                    v1 = (row + 1) * 0.25f,
                ),
                tint = Color(
                    r = (index % 7) / 6f,
                    g = 0.8f,
                    b = 1f,
                    a = 1f,
                ),
            )
        }

        val staged = DrawRunCoalescer.coalesce(commands)
        assertEquals(1, staged.size)
        val textureRun = assertIs<StagedDrawRun.TextureRun>(staged.single())
        assertEquals(1, textureRun.primitives.size, "500 same-texture rotated quads must coalesce into 1 TexturedDrawRun")

        val batched = textureRun.primitives.single()
        assertSame(sharedAtlas, batched.texture)
        assertEquals(BlendMode.SourceOver, batched.blendMode)
        assertEquals(true, batched.premultiplied)
        assertEquals(500 * VertexFormats2D.VERTICES_PER_QUAD * VertexFormats2D.GLYPH_FLOATS_PER_VERTEX, batched.vertices.size)
        assertEquals(500 * VertexFormats2D.INDICES_PER_QUAD, batched.indices.size)
        assertEquals(499 * VertexFormats2D.VERTICES_PER_QUAD, batched.indices[499 * VertexFormats2D.INDICES_PER_QUAD])
    }

    @Test
    fun textureRunsSplitOnlyWhenMaterialBlendModePremultipliedOrMaxQuadsChanges() {
        val texA = Any()
        val texB = Any()
        val commands = listOf(
            DrawCommand.Texture(0f, 0f, 8f, 8f, texA, blendMode = BlendMode.SourceOver, premultiplied = true),
            DrawCommand.Texture(8f, 0f, 8f, 8f, texA, blendMode = BlendMode.SourceOver, premultiplied = true),
            DrawCommand.Texture(16f, 0f, 8f, 8f, texA, blendMode = BlendMode.Plus, premultiplied = true),
            DrawCommand.Texture(24f, 0f, 8f, 8f, texA, blendMode = BlendMode.Plus, premultiplied = true),
            DrawCommand.Texture(32f, 0f, 8f, 8f, texB, blendMode = BlendMode.Plus, premultiplied = true),
        )

        val staged = DrawRunCoalescer.coalesce(commands)
        val textureRun = assertIs<StagedDrawRun.TextureRun>(staged.single())
        assertEquals(3, textureRun.primitives.size)
        assertEquals(2 * VertexFormats2D.INDICES_PER_QUAD, textureRun.primitives[0].indices.size)
        assertEquals(BlendMode.SourceOver, textureRun.primitives[0].blendMode)
        assertEquals(2 * VertexFormats2D.INDICES_PER_QUAD, textureRun.primitives[1].indices.size)
        assertEquals(BlendMode.Plus, textureRun.primitives[1].blendMode)
        assertEquals(1 * VertexFormats2D.INDICES_PER_QUAD, textureRun.primitives[2].indices.size)
        assertSame(texB, textureRun.primitives[2].texture)

        val chunked = DrawRunCoalescer.coalesce(
            List(5) { DrawCommand.Texture(it * 4f, 0f, 4f, 4f, texA, premultiplied = true) },
            maxQuadsPerRun = 2,
        )
        val chunkedRun = assertIs<StagedDrawRun.TextureRun>(chunked.single())
        assertEquals(3, chunkedRun.primitives.size)
    }
}

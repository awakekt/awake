/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedRenderFeature2DTest {
    private class Recording : DrawRunRecorder<String> {
        val calls = mutableListOf<String>()
        override fun bindPipeline(kind: DrawPipelineKind): Boolean = true.also { calls += "pipeline $kind" }
        override fun drawMesh(mesh: String) { calls += "draw $mesh" }
        override fun setScissor(x: Int, y: Int, width: Int, height: Int) { calls += "scissor $x,$y ${width}x$height" }
        override fun drawTexturePrimitive(primitive: TexturedDrawRun, slotIndex: Int) { calls += "texture $slotIndex" }
    }

    /** Runs split by clips keep their pipeline, and a clip repeating the current rect is not set again. */
    @Test
    fun aPipelineOrScissorAlreadyInPlaceIsNotSetAgain() {
        val clip = Rectangle(0f, 0f, 100f, 50f)
        val runs = listOf(
            DrawRun.ClipRun(clip),
            DrawRun.QuadRun("a"),
            DrawRun.ClipRun(clip),
            DrawRun.QuadRun("b"),
            DrawRun.GlyphRun("c"),
            DrawRun.ClipRun(Rectangle(10f, 0f, 90f, 50f)),
            DrawRun.GlyphRun("d"),
            DrawRun.TextureRun(emptyList()),
            DrawRun.GlyphRun("e"),
        )
        val recording = Recording()

        SharedRenderFeature2D().recordCommands(runs, surfaceWidth = 200, surfaceHeight = 200, recorder = recording)

        assertEquals(
            listOf(
                "scissor 0,0 100x50",
                "pipeline Quad", "draw a",
                "draw b",
                "pipeline Glyph", "draw c",
                "scissor 10,0 90x50",
                "draw d",
                "pipeline Texture",
                "pipeline Glyph", "draw e",
            ),
            recording.calls,
        )
    }
}

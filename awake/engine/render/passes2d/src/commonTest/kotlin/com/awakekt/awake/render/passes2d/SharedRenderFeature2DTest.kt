/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedRenderFeature2DTest {
    private val roboto = UiFonts.default()
    private val bitmap = UiFonts.bitmap()

    private inner class Recording : DrawRunRecorder<String> {
        val calls = mutableListOf<String>()
        override fun bindPipeline(kind: DrawPipelineKind): Boolean = true.also { calls += "pipeline $kind" }
        override fun bindGlyphFont(font: UiFont): Boolean = true.also { calls += "font ${if (font === roboto) "roboto" else "bitmap"}" }
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
            DrawRun.GlyphRun("c", roboto),
            DrawRun.ClipRun(Rectangle(10f, 0f, 90f, 50f)),
            DrawRun.GlyphRun("d", roboto),
            DrawRun.TextureRun(emptyList()),
            DrawRun.GlyphRun("e", roboto),
        )
        val recording = Recording()

        SharedRenderFeature2D().recordCommands(runs, surfaceWidth = 200, surfaceHeight = 200, recorder = recording)

        assertEquals(
            listOf(
                "scissor 0,0 100x50",
                "pipeline Quad", "draw a",
                "draw b",
                "pipeline Glyph", "font roboto", "draw c",
                "scissor 10,0 90x50",
                "draw d",
                "pipeline Texture",
                "pipeline Glyph", "font roboto", "draw e",
            ),
            recording.calls,
        )
    }

    /**
     * A font switch rebinds the font and nothing else, in paint order: the glyph pipeline binds
     * once, and the third run's font, though it was bound before, is bound again because the
     * second replaced it.
     */
    @Test
    fun aFontSwitchBindsTheNextAtlasWithoutRebindingThePipeline() {
        val runs = listOf(DrawRun.GlyphRun("a", roboto), DrawRun.GlyphRun("b", bitmap), DrawRun.GlyphRun("c", roboto))
        val recording = Recording()

        SharedRenderFeature2D().recordCommands(runs, surfaceWidth = 200, surfaceHeight = 200, recorder = recording)

        assertEquals(
            listOf("pipeline Glyph", "font roboto", "draw a", "font bitmap", "draw b", "font roboto", "draw c"),
            recording.calls,
        )
    }

    /** A glyph without a font samples the frame's default; with no default either it is dropped, not drawn wrong. */
    @Test
    fun uploadResolvesTheDefaultFontAndDropsAGlyphRunWithNone() {
        val staged = listOf(
            StagedDrawRun.GlyphRun(FloatArray(0), IntArray(0), font = null),
            StagedDrawRun.GlyphRun(FloatArray(0), IntArray(0), font = bitmap),
        )
        val uploader = object : DrawMeshUploader<String> {
            override fun quadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = "quad$runIndex"
            override fun roundedQuadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = "rounded$runIndex"
            override fun glyphMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = "glyph$runIndex"
        }

        val withDefault = uploadDrawRuns(staged, uploader, defaultFont = roboto).map { it as DrawRun.GlyphRun }
        assertEquals(listOf(roboto, bitmap), withDefault.map { it.font })

        val withoutDefault = uploadDrawRuns(staged, uploader).map { it as DrawRun.GlyphRun }
        assertEquals(listOf("glyph0"), withoutDefault.map { it.mesh })
        assertEquals(listOf<UiFont>(bitmap), withoutDefault.map { it.font })
    }
}

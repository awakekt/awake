/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.BackdropBlurQuality
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawTransform
import com.awakekt.awake.core.graphics2d.DualKawaseBlur
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.text.font.UiFont
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackdropBlurStagingTest {
    private val high = BackdropBlurTarget(BackdropBlurQuality.High, 800, 600)
    private val tint = Color(1f, 1f, 1f, 0.2f)

    private fun panel(x: Float, y: Float, w: Float = 100f, h: Float = 60f, radius: Float = 12f) =
        DrawCommand.BackdropBlur(x, y, w, h, blurRadius = radius, tint = tint, cornerRadius = 8f)

    private fun backdrops(runs: List<StagedDrawRun>) = runs.filterIsInstance<StagedDrawRun.BackdropRun>()

    @Test
    fun offFillsThePanelWithItsFallback() {
        val runs = DrawRunCoalescer.coalesce(listOf(panel(10f, 10f)), backdrop = BackdropBlurTarget(BackdropBlurQuality.Off, 800, 600))
        assertEquals(0, backdrops(runs).size)
        val fallback = runs.single() as StagedDrawRun.RoundedQuadRun
        val colorOffset = VertexFormats2D.RoundedQuad.floatOffsetOf(com.awakekt.awake.core.geometry.VertexSemantic.Color)
        assertEquals(0.68f, fallback.vertices[colorOffset + 3], 1e-5f, "a 0.2 tint falls back at 0.6 + 0.4 * 0.2")
    }

    @Test
    fun withoutATargetEveryPanelFallsBack() {
        val runs = DrawRunCoalescer.coalesce(listOf(panel(10f, 10f)))
        assertTrue(runs.single() is StagedDrawRun.RoundedQuadRun)
    }

    /** Side-by-side panels whose content stays inside them share one blur over both regions. */
    @Test
    fun panelsThatDoNotSeeEachOtherShareOneBlur() {
        val label = DrawCommand.Quad(20f, 20f, 40f, 10f, Color.White)
        val runs = DrawRunCoalescer.coalesce(
            listOf(panel(10f, 10f), label, panel(500f, 10f), DrawCommand.Quad(510f, 20f, 40f, 10f, Color.White)),
            backdrop = high,
        )
        val glass = backdrops(runs)
        assertEquals(2, glass.size)
        val work = assertNotNull(glass[0].blur, "the first panel runs the blur")
        assertNull(glass[1].blur, "the second samples it")
        assertTrue(work.region.left <= 10 && work.region.right >= 600, "the blur reads both panels: ${work.region}")
        assertEquals(work.blur.passCount, work.passes.size)
    }

    /** A panel over another one sees it, so it needs a blur of its own, run after the first is drawn. */
    @Test
    fun aPanelOverAnotherBlursAgain() {
        val runs = DrawRunCoalescer.coalesce(listOf(panel(10f, 10f, 300f, 300f), panel(50f, 50f)), backdrop = high)
        val glass = backdrops(runs)
        assertNotNull(glass[0].blur)
        assertNotNull(glass[1].blur)
        assertTrue(glass[0].blur !== glass[1].blur)
    }

    /** Content drawn over the region a later panel reads breaks the sharing, even far from the first panel. */
    @Test
    fun paintingWhatALaterPanelReadsGivesItItsOwnBlur() {
        val runs = DrawRunCoalescer.coalesce(
            listOf(panel(10f, 10f), DrawCommand.Quad(480f, 20f, 40f, 10f, Color.White), panel(500f, 10f)),
            backdrop = high,
        )
        val glass = backdrops(runs)
        assertNotNull(glass[0].blur)
        assertNotNull(glass[1].blur)
    }

    @Test
    fun aDifferentRadiusIsADifferentBlur() {
        val runs = DrawRunCoalescer.coalesce(listOf(panel(10f, 10f, radius = 4f), panel(500f, 10f, radius = 30f)), backdrop = high)
        val glass = backdrops(runs)
        assertNotNull(glass[0].blur)
        assertNotNull(glass[1].blur)
    }

    /** Low allows two blurs a frame: a third panel that needs its own blur falls back to its tint. */
    @Test
    fun blursPastTheFrameLimitFallBack() {
        val nested = listOf(panel(0f, 0f, 400f, 400f), panel(20f, 20f, 300f, 300f), panel(40f, 40f, 200f, 200f))
        val runs = DrawRunCoalescer.coalesce(nested, backdrop = BackdropBlurTarget(BackdropBlurQuality.Low, 800, 600))
        assertEquals(2, backdrops(runs).count { it.blur != null })
        assertTrue(runs.last() is StagedDrawRun.RoundedQuadRun, "the third panel is its fallback")
    }

    @Test
    fun theFirstPassReadsTheTargetAndTheLastWritesTheOutputLevel() {
        val runs = DrawRunCoalescer.coalesce(listOf(panel(100f, 100f, radius = 20f)), backdrop = high)
        val work = assertNotNull(backdrops(runs).single().blur)
        val passes = work.passes
        assertEquals(0, passes.first().sourceLevel)
        assertEquals(1, passes.first().targetLevel)
        assertEquals(work.blur.outputLevel, passes.last().targetLevel)
        assertTrue(passes.last().isUpsample)
        assertEquals(3 * VertexFormats2D.BackdropBlurPass.strideFloats, passes.first().vertices.size)
        val expected = DualKawaseBlur.forRadius(20f, BackdropBlurQuality.High)
        assertEquals(expected, work.blur)
    }

    @Test
    fun aScaledPanelBlursWhereItIsDrawn() {
        val scaled = panel(100f, 100f).copy(transform = DrawTransform(0.5f, 0.5f, 100f, 100f))
        val glass = backdrops(DrawRunCoalescer.coalesce(listOf(scaled), backdrop = high)).single()
        val stride = VertexFormats2D.BackdropComposite.strideFloats
        // Corner 2 is the bottom-right one.
        assertEquals(150f, glass.vertices[2 * stride], 1e-4f)
        assertEquals(130f, glass.vertices[2 * stride + 1], 1e-4f)
    }

    private class Recording(private val blurs: Boolean = true) : DrawRunRecorder<String> {
        val calls = mutableListOf<String>()
        override fun bindPipeline(kind: DrawPipelineKind): Boolean = true.also { calls += "pipeline $kind" }
        override fun bindGlyphFont(font: UiFont): Boolean = true
        override fun drawMesh(mesh: String) {
            calls += "draw $mesh"
        }
        override fun setScissor(x: Int, y: Int, width: Int, height: Int) {
            calls += "scissor $x,$y ${width}x$height"
        }
        override fun drawTexturePrimitive(primitive: TexturedDrawRun, slotIndex: Int) = Unit
        override fun blurBackdrop(blur: BackdropBlurRecording<String>, more: Boolean): Boolean = blurs.also { calls += "blur more=$more" }
    }

    private fun recordedRuns(): List<DrawRun<String>> {
        val primitives = listOf(
            DrawCommand.ClipPush(Rectangle(0f, 0f, 400f, 300f)),
            panel(10f, 10f, 300f, 200f),
            DrawCommand.Quad(20f, 20f, 10f, 10f, Color.White),
            panel(40f, 40f),
            panel(300f, 40f),
        )
        val uploader = object : DrawMeshUploader<String> {
            override fun quadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = "quad$runIndex"
            override fun roundedQuadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = "rounded$runIndex"
            override fun glyphMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = "glyph$runIndex"
            override fun backdropMesh(runIndex: Int, vertices: FloatArray, indices: IntArray, blurPass: Boolean) =
                if (blurPass) "pass$runIndex" else "glass$runIndex"
        }
        return uploadDrawRuns(DrawRunCoalescer.coalesce(primitives, backdrop = high), uploader)
    }

    /**
     * Each blur breaks the pass off, so the shared feature binds again and restores the clip in
     * force; the last blur is told no other follows.
     */
    @Test
    fun aBlurIsFollowedByARebindAndTheClipInForce() {
        val recording = Recording()
        SharedRenderFeature2D().recordCommands(recordedRuns(), surfaceWidth = 800, surfaceHeight = 600, recorder = recording)
        assertEquals(
            listOf(
                "scissor 0,0 400x300",
                "blur more=true",
                "scissor 0,0 400x300",
                "pipeline BackdropComposite", "draw glass0",
                "pipeline Quad", "draw quad0",
                "blur more=false",
                "scissor 0,0 400x300",
                "pipeline BackdropComposite", "draw glass1",
                "draw glass2",
            ),
            recording.calls,
        )
    }

    /** A blur the target cannot run draws none of the panels sharing it, rather than stale glass. */
    @Test
    fun aBlurThatCannotRunDrawsNoneOfItsPanels() {
        val recording = Recording(blurs = false)
        SharedRenderFeature2D().recordCommands(recordedRuns(), surfaceWidth = 800, surfaceHeight = 600, recorder = recording)
        assertTrue(recording.calls.none { it.startsWith("draw glass") }, recording.calls.toString())
        assertTrue("draw quad0" in recording.calls)
    }

    @Test
    fun aBackendWithoutBackdropMeshesDropsThePanels() {
        val uploader = object : DrawMeshUploader<String> {
            override fun quadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = "quad$runIndex"
            override fun roundedQuadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = "rounded$runIndex"
            override fun glyphMesh(runIndex: Int, vertices: FloatArray, indices: IntArray) = "glyph$runIndex"
        }
        val runs = uploadDrawRuns(DrawRunCoalescer.coalesce(listOf(panel(10f, 10f)), backdrop = high), uploader)
        assertTrue(runs.isEmpty())
    }
}

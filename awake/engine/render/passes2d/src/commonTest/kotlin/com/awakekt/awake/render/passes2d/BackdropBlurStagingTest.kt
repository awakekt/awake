/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawTransform
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.effects.BackdropBlurEffect
import com.awakekt.awake.render.effects.BlurChain
import com.awakekt.awake.render.effects.BlurEffectShaders
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.imagepass.ImagePassTargetContext
import com.awakekt.awake.render.pipeline.EffectQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackdropBlurStagingTest {
    private val high = ImagePassTargetContext(800, 600, ClipSpace.Vulkan, effectQuality = EffectQuality.High)
    private val off = ImagePassTargetContext(800, 600, ClipSpace.Vulkan, effectQuality = EffectQuality.Off)

    private fun panel(
        x: Float,
        y: Float,
        w: Float = 100f,
        h: Float = 60f,
        radius: Float = 12f,
        tint: Color = Color.Transparent,
    ) = DrawCommand.BackdropBlur(x = x, y = y, w = w, h = h, blurRadius = radius, tint = tint)

    @Test
    fun offQualityFallsBackToTint() {
        val runs = DrawRunCoalescer.coalesce(
            listOf(panel(10f, 10f, tint = Color(1f, 0f, 0f, 0.2f))),
            backdrop = off,
        )
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
        val imagePassRuns = runs.filterIsInstance<StagedDrawRun.ImagePassRun>()
        assertEquals(2, imagePassRuns.size)
        val firstChain = imagePassRuns[0].chain
        val secondChain = imagePassRuns[1].chain
        assertTrue(firstChain.steps.size > 1, "the first panel runs the blur passes and composite")
        assertEquals(1, secondChain.steps.size, "the second samples it with only the composite step")
        assertEquals(BlurEffectShaders.BLUR_COMPOSITE_SHADER_ID, secondChain.steps.single().shader.id)
        assertTrue(
            firstChain.sourceBounds.x <= 10f && firstChain.sourceBounds.right >= 600f,
            "the blur reads both panels: ${firstChain.sourceBounds}",
        )
    }

    /** A panel over another one sees it, so it needs a blur of its own, run after the first is drawn. */
    @Test
    fun aPanelOverAnotherBlursAgain() {
        val runs = DrawRunCoalescer.coalesce(listOf(panel(10f, 10f, 300f, 300f), panel(50f, 50f)), backdrop = high)
        val imagePassRuns = runs.filterIsInstance<StagedDrawRun.ImagePassRun>()
        assertEquals(2, imagePassRuns.size)
        assertTrue(imagePassRuns[0].chain.steps.size > 1)
        assertTrue(imagePassRuns[1].chain.steps.size > 1)
    }

    /** Content drawn over the region a later panel reads breaks the sharing, even far from the first panel. */
    @Test
    fun paintingWhatALaterPanelReadsGivesItItsOwnBlur() {
        val runs = DrawRunCoalescer.coalesce(
            listOf(panel(10f, 10f), DrawCommand.Quad(480f, 20f, 40f, 10f, Color.White), panel(500f, 10f)),
            backdrop = high,
        )
        val imagePassRuns = runs.filterIsInstance<StagedDrawRun.ImagePassRun>()
        assertEquals(2, imagePassRuns.size)
        assertTrue(imagePassRuns[0].chain.steps.size > 1)
        assertTrue(imagePassRuns[1].chain.steps.size > 1)
    }

    @Test
    fun aDifferentRadiusIsADifferentBlur() {
        val runs = DrawRunCoalescer.coalesce(
            listOf(panel(10f, 10f, radius = 4f), panel(500f, 10f, radius = 30f)),
            backdrop = high,
        )
        val imagePassRuns = runs.filterIsInstance<StagedDrawRun.ImagePassRun>()
        assertEquals(2, imagePassRuns.size)
        assertTrue(imagePassRuns[0].chain.steps.size > 1)
        assertTrue(imagePassRuns[1].chain.steps.size > 1)
    }

    /** Low allows two blurs a frame: a third panel that needs its own blur falls back to its tint. */
    @Test
    fun blursPastTheFrameLimitFallBack() {
        val nested = listOf(panel(0f, 0f, 400f, 400f), panel(20f, 20f, 300f, 300f), panel(40f, 40f, 200f, 200f))
        val low = ImagePassTargetContext(800, 600, ClipSpace.Vulkan, effectQuality = EffectQuality.Low)
        val runs = DrawRunCoalescer.coalesce(nested, backdrop = low)
        assertEquals(2, runs.filterIsInstance<StagedDrawRun.ImagePassRun>().size)
        assertTrue(runs.last() is StagedDrawRun.RoundedQuadRun, "the third panel is its fallback")
    }

    @Test
    fun theFirstPassReadsTheTargetAndTheLastWritesTheOutputLevel() {
        val runs = DrawRunCoalescer.coalesce(listOf(panel(100f, 100f, radius = 20f)), backdrop = high)
        val chain = runs.filterIsInstance<StagedDrawRun.ImagePassRun>().single().chain
        val blur = BlurChain.forRadius(20f, EffectQuality.High)!!
        assertEquals(blur.passCount + 1, chain.steps.size)
        val firstStep = chain.steps.first()
        assertEquals(ImagePassSource.DestinationSnapshot, firstStep.inputs.single().source)
        val lastBlurStep = chain.steps[blur.passCount - 1]
        assertEquals(
            ImagePassDestination.TransientTarget(BackdropBlurEffect.levelTargetId(blur.outputLevel)),
            lastBlurStep.destination,
        )
        val compositeStep = chain.steps.last()
        assertEquals(ImagePassDestination.ActiveTarget(), compositeStep.destination)
        assertEquals(
            ImagePassSource.TransientTarget(BackdropBlurEffect.levelTargetId(blur.outputLevel)),
            compositeStep.inputs.single().source,
        )
    }

    @Test
    fun aScaledPanelBlursWhereItIsDrawn() {
        val scaled = panel(100f, 100f).copy(transform = DrawTransform(0.5f, 0.5f, 100f, 100f))
        val run = DrawRunCoalescer.coalesce(listOf(scaled), backdrop = high).filterIsInstance<StagedDrawRun.ImagePassRun>().single()
        val compositeStep = run.chain.steps.last()
        val stride = 16
        // Corner 2 is the bottom-right one.
        assertEquals(150f, compositeStep.quadVertices[2 * stride], 1e-4f)
        assertEquals(130f, compositeStep.quadVertices[2 * stride + 1], 1e-4f)
    }

    private class Recording : DrawRunRecorder<String> {
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
        override fun executeImagePass(chain: ImagePassChain, hasMoreImagePasses: Boolean) {
            calls += "executeImagePass more=$hasMoreImagePasses"
        }
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
                "executeImagePass more=true",
                "pipeline Quad",
                "draw quad0",
                "executeImagePass more=true",
                "executeImagePass more=false",
            ),
            recording.calls,
        )
    }
}

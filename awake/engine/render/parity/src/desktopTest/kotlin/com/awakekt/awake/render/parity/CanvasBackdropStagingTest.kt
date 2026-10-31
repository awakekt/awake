/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.effects.BackdropRegion
import com.awakekt.awake.render.effects.BlurChain
import com.awakekt.awake.render.imagepass.ImagePassTargetContext
import com.awakekt.awake.render.passes2d.StagedDrawRun
import com.awakekt.awake.render.passes2d.UiRunCoalescer
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.ui.canvas.CanvasBackdrop
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasItem
import com.awakekt.awake.ui.canvas.CanvasOverlay
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.ui.canvas.LocalCanvasBackdropBlur
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How a canvas's glass panels stage, through the shared UI coalescer both backends record from: one
 * blur per group of panels that cannot see each other, the fallback with no pass where blur is off,
 * and the passes and texels one HUD window costs.
 */
class CanvasBackdropStagingTest {
    private fun glass(x: Float, y: Float, w: Float = 100f, h: Float = 80f) = CanvasElement().apply {
        kind = CanvasElementKind.Panel
        offsetX = x; offsetY = y; width = w; height = h
        background = "#10141880"
        style = CanvasStyle(cornerRadius = 10f, backdrop = CanvasBackdrop(blur = 16f, tint = "#FFFFFF20"))
    }

    private fun stage(elements: List<CanvasElement>, quality: EffectQuality, blurOn: Boolean = true, width: Int = 800, height: Int = 600): List<StagedDrawRun> {
        val items = elements.mapIndexed { index, element -> CanvasItem(index, element) }
        val primitives: List<UiDrawPrimitive> = ComposeHost().frame(FrameInput(width, height)) {
            CompositionLocalProvider(LocalCanvasBackdropBlur provides blurOn) { CanvasOverlay(items) }
        }.primitives
        val target = ImagePassTargetContext(width, height, ClipSpace.Vulkan, effectQuality = quality)
        return UiRunCoalescer.coalesce(primitives, backdrop = target)
    }

    private fun List<StagedDrawRun>.glassPanels() = filterIsInstance<StagedDrawRun.ImagePassRun>()

    /** The panels that run a blur's passes; the rest only composite a blur another panel ran. */
    private fun List<StagedDrawRun>.blurs() = glassPanels().count { it.chain.steps.size > 1 }

    @Test
    fun panelsThatCannotSeeEachOtherShareOneBlur() {
        // Two windows apart, then a third over the first, whose glass must show the first's fill.
        val runs = stage(listOf(glass(10f, 10f), glass(500f, 10f), glass(60f, 40f)), EffectQuality.High)

        assertEquals(3, runs.glassPanels().size, "every panel composites glass")
        assertEquals(2, runs.blurs(), "the two apart share one blur; the overlapping one runs its own")
    }

    @Test
    fun blurOffDrawsTheFallbackWithNoPass() {
        val panels = listOf(glass(10f, 10f), glass(500f, 10f))
        val offQuality = stage(panels, EffectQuality.Off)
        val hostOff = stage(panels, EffectQuality.High, blurOn = false)

        for ((case, runs) in listOf("quality Off" to offQuality, "host off" to hostOff)) {
            assertEquals(0, runs.glassPanels().size, "$case: no image pass is staged")
            val roundedQuads = runs.sumOf { (it as? StagedDrawRun.RoundedQuadRun)?.indices?.size ?: 0 } / QUAD_INDICES
            assertEquals(4, roundedQuads, "$case: each panel fills its fallback, then its own fill")
        }
    }

    /**
     * One 400 x 300 dp glass window at 1080p, density 1, blur 16: printed for the PR, and held to
     * the passes the blur's own chain says it records.
     */
    @Test
    fun oneHudWindowAt1080pCosts() {
        for (quality in listOf(EffectQuality.High, EffectQuality.Medium, EffectQuality.Low)) {
            val runs = stage(listOf(glass(760f, 390f, 400f, 300f)), quality, width = HD_W, height = HD_H)
            val chain = requireNotNull(BlurChain.forRadius(16f, quality))
            val region = BackdropRegion.around(760f, 390f, 400f, 300f, chain.extent, HD_W, HD_H)
            val downTexels = (1..chain.iterations).sumOf { region.atLevel(it, HD_W, HD_H).area() }
            val upTexels = (chain.outputLevel until chain.iterations).sumOf { region.atLevel(it, HD_W, HD_H).area() }
            val texels = downTexels + upTexels + 400 * 300
            println(
                "glass 400x300 @1920x1080 $quality: ${chain.passCount} blur passes + 1 composite " +
                    "(${chain.iterations} down, ${chain.upPasses} up, output level ${chain.outputLevel}), " +
                    "snapshot ${region.width}x${region.height}, ~$texels texels written",
            )
            assertEquals(chain.passCount + 1, runs.glassPanels().single().chain.steps.size, "$quality: blur passes plus the composite")
        }
    }

    private fun BackdropRegion.area() = width.toLong() * height

    private companion object {
        const val HD_W = 1920
        const val HD_H = 1080
        const val QUAD_INDICES = 6
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.imagepass.ImagePassTargetContext
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes2d.StagedDrawRun
import com.awakekt.awake.render.passes2d.UiRunCoalescer
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.vulkan.application.VulkanEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What one frame of a panel look cost, counted from the renderer's own stats and its staged runs. */
data class PanelCost(
    val draws: Int,
    val passes: Int,
    val triangles: Long,
    val vertices: Int,
    val texturedParts: Int,
    val sheets: Int,
    val cpuMs: Double,
    val allocKb: Long,
) {
    override fun toString() = "draws=$draws passes=$passes tris=$triangles verts=$vertices parts=$texturedParts sheets=$sheets " +
        "| cpu=${"%.2f".format(cpuMs)}ms alloc=${allocKb}KB (reference only)"
}

/**
 * Does a styled panel do more GPU work than a skinned one, in principle? One 400x300 panel (window,
 * title, three buttons, a gauge, a text block) dressed four ways, drawn alone into a 1280x720
 * surface on headless Vulkan (lavapipe): draws, passes and triangles come from the renderer's frame
 * stats, vertices and texture binds from the staged runs both backends record from. Time and
 * allocation are printed, never asserted: lavapipe's timing does not predict a real GPU.
 */
class UiPanelCostTest {
    private val sheet = panelSheet()
    private val skin = panelSkin(sheet)
    private val apart = listOf(60 to 60, 700 to 360)
    private val overlapping = listOf(60 to 60, 180 to 140)

    @Test
    fun theFourLooksCostWhatTheirCeilingsSay() = withRenderer { renderer ->
        val one = listOf(440 to 210)
        val c = PanelLook.entries.associateWith { measure(renderer, it, one) }
        c.forEach { (look, cost) -> println("panel $look: $cost") }
        val skinned = c.getValue(PanelLook.Skinned)
        val plain = c.getValue(PanelLook.Plain)
        val styled = c.getValue(PanelLook.Styled)
        val glass = c.getValue(PanelLook.Glass)

        // Skinned: the plain panel's draws, plus at most one batch per image part kind.
        assertTrue(skinned.draws <= plain.draws + IMAGE_PARTS, "skinned ${skinned.draws} draws vs plain ${plain.draws} + $IMAGE_PARTS")
        assertEquals(1, skinned.sheets, "every skinned part samples the one sheet, so one texture bind")
        assertTrue(skinned.texturedParts in 1..IMAGE_PARTS, "image parts ${skinned.texturedParts}, ceiling $IMAGE_PARTS")
        assertEquals(skinned.passes, plain.passes, "a skin adds no render pass")
        // Shadow: bounded draws per shadowed node (the window and three buttons).
        val shadowDraws = styled.draws - plain.draws
        assertTrue(shadowDraws <= SHADOW_DRAWS, "shadow adds $shadowDraws draws, ceiling $SHADOW_DRAWS")
        assertEquals(plain.passes, styled.passes, "a shadow is a quad, not a pass")
        // Blur: a bounded number of extra passes, one blur chain.
        val blurPasses = glass.passes - styled.passes
        assertTrue(blurPasses in 1..BLUR_PASSES, "one blurred panel adds $blurPasses passes, ceiling $BLUR_PASSES")
        println("blur adds $blurPasses passes, ${glass.draws - styled.draws} draws, shadow adds $shadowDraws draws")
    }

    @Test
    fun aSecondBlurredPanelCostsItsOwnBlurChainTodayAndMustNotCostMore() = withRenderer { renderer ->
        val one = measure(renderer, PanelLook.Glass, listOf(60 to 60))
        val twoApart = measure(renderer, PanelLook.Glass, apart)
        val twoStacked = measure(renderer, PanelLook.Glass, overlapping)
        println("glass x1: $one\nglass x2 apart: $twoApart\nglass x2 overlapping: $twoStacked")
        println("passes per extra panel apart: ${twoApart.passes - one.passes}")
        // Measured: 6 passes per blurred panel, apart or overlapping, so the count DOES grow with the
        // number of panels (#1512). The ceiling ratchets that; the target is one shared chain.
        assertTrue(twoApart.passes <= one.passes + BLUR_PASSES - 1, "two apart: ${twoApart.passes} passes vs one ${one.passes}")
        assertTrue(twoStacked.passes <= one.passes + BLUR_PASSES - 1, "two stacked: ${twoStacked.passes} passes vs one ${one.passes}")
    }

    private fun measure(renderer: Renderer, look: PanelLook, positions: List<Pair<Int, Int>>): PanelCost {
        val host = ComposeHost()
        fun frame() = host.frame(FrameInput(FRAME_W, FRAME_H)) { PanelFrame(look, positions, skin.takeIf { look == PanelLook.Skinned }) }.primitives
        // The host swaps a drawn bitmap for a material the first time it sees it; do the same, once per image.
        val materials = HashMap<Any, com.awakekt.awake.render.material.Material>()
        val primitives: List<UiDrawPrimitive> = frame().map { p ->
            val image = (p as? UiDrawPrimitive.Texture)?.material as? ImageBitmap ?: return@map p
            p.copy(material = materials.getOrPut(image) { renderer.createMaterial(texture = TextureAsset(image.pixels, image.width, image.height)) })
        }
        repeat(WARMUP) { frame() }
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val bytes0 = threads.currentThreadAllocatedBytes
        val t0 = System.nanoTime()
        repeat(TIMED) { frame() }
        val cpuMs = (System.nanoTime() - t0) / 1e6 / TIMED
        val allocKb = (threads.currentThreadAllocatedBytes - bytes0) / TIMED / 1024

        repeat(2) { present(renderer, primitives) } // the first builds pipelines and uploads the sheet
        val stats = requireNotNull(renderer.frameStats) { "the frame published no stats" }
        val runs = UiRunCoalescer.coalesce(primitives, backdrop = ImagePassTargetContext(FRAME_W, FRAME_H, ClipSpace.Vulkan, effectQuality = EffectQuality.High))
        val textured = runs.filterIsInstance<StagedDrawRun.TextureRun>().flatMap { it.primitives }
        val vertices = runs.sumOf {
            when (it) {
                is StagedDrawRun.QuadRun -> it.vertices.size
                is StagedDrawRun.RoundedQuadRun -> it.vertices.size
                is StagedDrawRun.GlyphRun -> it.vertices.size
                else -> 0
            }
        } + textured.sumOf { it.vertices.size }
        renderer.waitIdle()
        val cost = PanelCost(stats.drawCalls, renderer.frameRenderPasses, stats.triangles, vertices, textured.size, textured.map { it.texture }.distinct().size, cpuMs, allocKb)
        materials.values.forEach { it.destroy() }
        return cost
    }

    private fun present(renderer: Renderer, primitives: List<UiDrawPrimitive>) {
        val lens = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 20f)
        renderer.drawUi(primitives, UiFonts.default())
        renderer.draw(
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = emptyList(),
                light = SceneLight(direction = Vec3f(0f, 0f, 1f), color = Vec3f(1f, 1f, 1f)),
                clipSpace = renderer.clipSpace,
                aspect = FRAME_W.toFloat() / FRAME_H,
                drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
            ),
        )
    }

    private fun withRenderer(block: (Renderer) -> Unit) = runBlocking {
        val ready = CompletableDeferred<Renderer>()
        val app = AppSpecBuilder().apply { ready { ready.complete(it) } }.build().createLifecycle()
        val engine = VulkanEngine(app, EngineShowcaseRenderPlan)
        engine.create(HeadlessSurface(FRAME_W, FRAME_H))
        val renderer = withTimeout(READY_TIMEOUT_MS) { ready.await() }
        renderer.effectQuality = EffectQuality.High
        try {
            block(renderer)
        } finally {
            renderer.waitIdle()
            engine.dispose()
        }
    }

    private companion object {
        const val WARMUP = 20
        const val TIMED = 50
        const val READY_TIMEOUT_MS = 30_000L
        const val IMAGE_PARTS = 6 // measured 5 textured parts, 0 extra draws over plain
        const val SHADOW_DRAWS = 5 // measured 4: one per shadowed node (window + 3 buttons)
        const val BLUR_PASSES = 7 // measured 6 extra passes and 6 extra draws for one blurred panel
    }
}

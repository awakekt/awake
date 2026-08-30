/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan

import io.github.awakelab.awake.asset.shaders.uiShaderSet
import io.github.awakelab.awake.asset.shaders.ShaderSet
import io.github.awakelab.awake.asset.shaders.ShaderStage
import io.github.awakelab.awake.asset.shaders.resourcePath
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.graphics2d.ColoredTriangleMesh
import io.github.awakelab.awake.core.graphics2d.ColoredVertex
import io.github.awakelab.awake.core.graphics2d.DrawPoint
import io.github.awakelab.awake.render.passes2d.StagedDrawRun
import io.github.awakelab.awake.render.passes2d.UiRunCoalescer
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.core.host.readResourceBytes
import io.github.awakelab.awake.render.passes2d.UiRenderFeature
import io.github.awakelab.awake.vulkan.commands.TransferContext
import io.github.awakelab.awake.vulkan.device.GraphicsDevice
import io.github.awakelab.awake.vulkan.material.Material
import io.github.awakelab.awake.vulkan.pipeline.PipelineTable
import io.github.awakelab.awake.vulkan.pipeline.RenderPipeline
import io.github.awakelab.awake.vulkan.pipeline.ShaderPair
import io.github.awakelab.awake.vulkan.pipeline.VulkanUiPass
import io.github.awakelab.awake.vulkan.pipeline.createSceneRenderPass
import io.github.awakelab.awake.vulkan.renderer.Renderer
import io.github.awakelab.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * What `Renderer.drawUi` costs on a real device, for a frame shaped like Studio's.
 *
 * Studio's overlay reports this as its `stage` phase. Coalescing is 1.3 ms of it and the index
 * conversion 0.1 ms, both measured on the JVM, which leaves the per-run buffer uploads --
 * `writeBufferMemoryFloats`/`Bytes`, one JNI crossing each, 144 per frame. Nothing headless
 * exercised those before this, so the remainder was inferred rather than measured.
 *
 * Diagnostic, like `RendererHeadlessFrameTimingTest`: it prints and asserts only that it ran.
 */
class UiUploadCostBenchmark {

    @Test
    fun drawUiUploadCost() {
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.createHeadless()
        val swapchainManager = SwapchainManager(graphicsDevice, 1)
        swapchainManager.createHeadless(TARGET, TARGET)
        val pipelineLayoutMaterial = Material(graphicsDevice)
        val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
        val renderPipeline = RenderPipeline(
            graphicsDevice,
            swapchainManager,
            sceneRenderPass,
            pipelineLayoutMaterial.descriptorSetLayout,
            runBlocking { packShaderPair("triangle") },
            VertexFormat.PositionColorUv,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
        )
        val transferContext = TransferContext(graphicsDevice)
        val renderer = Renderer(
            graphicsDevice = graphicsDevice,
            swapchainManager = swapchainManager,
            pipelines = PipelineTable(primary = renderPipeline, primaryFormat = renderPipeline.vertexFormat),
            renderFeatures = listOf(UiRenderFeature(VulkanUiPass())),
            transferContext = transferContext,
            uiShaderPairs = runBlocking { uiShaderSet(::packShaderPair) },
            maxFramesInFlight = 1,
        )

        var worstMs = 0.0
        for ((label, primitives) in listOf("quads" to studioShapedFrame(), "meshes" to meshHeavyFrame())) {
            repeat(WARMUP) { renderer.drawUi(primitives, null) }
            val whole = measureTime { repeat(MEASURED) { renderer.drawUi(primitives, null) } }
            // Coalescing on its own, so the remainder is the pipeline ensures plus the buffer
            // uploads -- the part only a real device exercises.
            repeat(WARMUP) { UiRunCoalescer.coalesce(primitives, 1024) }
            val coalesce = measureTime { repeat(MEASURED) { UiRunCoalescer.coalesce(primitives, 1024) } }
            val verts = primitives.filterIsInstance<UiDrawPrimitive.Mesh>().sumOf { it.mesh.vertices.size }
            // What actually crosses JNI: the staged float and index arrays, per frame.
            val staged = UiRunCoalescer.coalesce(primitives, 1024)
            var floats = 0L
            var indices = 0L
            staged.forEach { run ->
                when (run) {
                    is StagedDrawRun.QuadRun -> { floats += run.vertices.size; indices += run.indices.size }
                    is StagedDrawRun.RoundedQuadRun -> { floats += run.vertices.size; indices += run.indices.size }
                    is StagedDrawRun.GlyphRun -> { floats += run.vertices.size; indices += run.indices.size }
                    else -> Unit
                }
            }
            worstMs = maxOf(worstMs, whole.inWholeMicroseconds / 1000.0 / MEASURED)
            println(
                "PERF drawUi[$label] primitives=${primitives.size} meshVerts=$verts " +
                    "runs=${UiRunCoalescer.coalesce(primitives, 1024).size} " +
                    "drawUiMs=${whole.inWholeMicroseconds / 1000.0 / MEASURED} " +
                    "coalesceMs=${coalesce.inWholeMicroseconds / 1000.0 / MEASURED} " +
                    "remainderMs=${(whole - coalesce).inWholeMicroseconds / 1000.0 / MEASURED} " +
                    "stagedFloats=$floats stagedIndices=$indices",
            )
        }

        // Grow-on-demand converges or it is a leak in disguise: the pools size themselves to the
        // largest run they have carried during warmup, and drawing the same frames again must not
        // reallocate anything. A climbing count would mean every frame reallocates GPU buffers,
        // which is worse than the fixed ceiling this replaced.
        val growthAfterWarmup = renderer.bufferPools.uiMeshGrowthCount
        repeat(MEASURED) { renderer.drawUi(meshHeavyFrame(), null) }
        val growthAtSteadyState = renderer.bufferPools.uiMeshGrowthCount
        println("PERF drawUi growthAfterWarmup=$growthAfterWarmup steadyState=$growthAtSteadyState")
        assertTrue(
            growthAtSteadyState == growthAfterWarmup,
            "UI mesh buffers grew ${growthAtSteadyState - growthAfterWarmup} more times while " +
                "redrawing an unchanged frame -- capacity is not converging",
        )

        renderer.destroy()

        // A ceiling, not a target, and a deliberately loose one. Counts are the real guard --
        // StudioFrameCostRatchetTest pins geometry volume and draw runs deterministically -- but
        // no count catches a change in how the data crosses JNI. Copying each array into a
        // std::vector before the memcpy once cost 8.46 ms here against 1.18; a bound with this
        // much headroom sleeps through machine noise and still fails that.
        assertTrue(
            worstMs < MAX_DRAW_UI_MS,
            "drawUi took ${worstMs}ms, over the ${MAX_DRAW_UI_MS}ms ceiling. This bound is loose " +
                "on purpose, so exceeding it means an order-of-magnitude regression in how UI " +
                "geometry reaches the GPU, not a slow machine.",
        )
    }

    /**
     * Studio's shape rather than its content: alternating spans so the coalescer produces about
     * the same run count (72) over about the same vertex count (~72k), which is what decides how
     * many buffer writes a frame makes.
     */
    private fun studioShapedFrame(): List<UiDrawPrimitive> = buildList {
        val white = Color(1f, 1f, 1f, 1f)
        repeat(36) { group ->
            repeat(250) { i ->
                val x = (i % 40) * 4f
                val y = group * 8f
                add(UiDrawPrimitive.Quad(x, y, 3f, 3f, white))
            }
            repeat(250) { i ->
                val x = (i % 40) * 4f
                val y = group * 8f + 4f
                add(UiDrawPrimitive.Glyph(x, y, 3f, 3f, 0f, 0f, 1f, 1f, white))
            }
        }
    }

    /**
     * Studio's real shape: most of its vertices arrive as already-tessellated meshes -- icons and
     * borders -- not as quads the coalescer writes straight from primitive fields. A mesh is
     * staged by walking a boxed ColoredVertex list, which is a different cost entirely.
     */
    private fun meshHeavyFrame(): List<UiDrawPrimitive> = buildList {
        val white = Color(1f, 1f, 1f, 1f)
        val icon = ColoredTriangleMesh(
            (0 until 2000).map { ColoredVertex(DrawPoint(it % 16f, (it / 16) % 16f), white) },
            IntArray(2000) { it },
        )
        repeat(34) { i ->
            add(UiDrawPrimitive.Mesh(icon, offsetX = (i % 8) * 20f, offsetY = (i / 8) * 20f))
            add(UiDrawPrimitive.Quad((i % 8) * 20f, (i / 8) * 20f, 16f, 16f, white))
        }
        repeat(160) { i ->
            add(UiDrawPrimitive.Glyph((i % 40) * 6f, (i / 40) * 12f, 5f, 10f, 0f, 0f, 1f, 1f, white))
        }
    }

    private companion object {
        const val TARGET = 256
        const val WARMUP = 30
        const val MEASURED = 200

        /**
         * Measured 2026-08-29 at 0.66 ms (quads) and 0.51 ms (meshes) on an M-series Mac through
         * MoltenVK. Set an order of magnitude above that: this runs on whatever hardware a
         * developer has, under whatever else they are running, and a tight bound here would be
         * turned off within a week. It exists to catch the shape of regression a count cannot --
         * see the note at the assertion.
         */
        const val MAX_DRAW_UI_MS = 6.0
    }
}

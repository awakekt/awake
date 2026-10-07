/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaders.EngineShaderSets
import com.awakekt.awake.asset.shaders.program
import com.awakekt.awake.render.pipeline.PipelineFactory
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.pipeline.ShaderReplacementException
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.WebGpuShaderReplacement
import com.awakekt.awake.webgpu.pipeline.WebGpuUiPipelineTarget
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import com.awakekt.awake.webgpu.ui.UiRenderPipeline
import io.ygdrasil.webgpu.GPUUncapturedErrorCallback
import io.ygdrasil.webgpu.canvasContextRenderer
import kotlinx.coroutines.test.runTest
import web.dom.document
import web.html.HTMLCanvasElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** Exercises the Wasm capability with Chrome's real validation scopes, without the native compiler. */
class WebGpuShaderReplacementBrowserTest {
    @Test
    fun browserReloadsRepeatAndRejectedProgramsKeepTheWorkingPipeline() = runTest {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val uncaptured = mutableListOf<String>()
        val context = canvasContextRenderer(
            htmlCanvas = canvas,
            width = 16,
            height = 16,
            onUncapturedError = GPUUncapturedErrorCallback { uncaptured += it.message },
        )
        val graphics = GraphicsDevice().apply { create(context.wgpuContext) }
        val swapchain = SwapchainManager(graphics, 1).apply { create() }
        val original = EngineShaderSets.UiQuad.webGpu.program()
        val source = original.vertex as ShaderSource.InlineText
        val pipeline = UiRenderPipeline(graphics, swapchain, source.sourceCode.encodeToByteArray())
        val replacement = WebGpuShaderReplacement(
            registry = PipelineRegistry(PipelineFactory<RenderPipeline> { _, _ -> error("No scene pipelines in this fixture.") }),
            device = graphics.wgpuContext.device,
            uiTargets = {
                // The renderer returns fresh adapters too; tracking must follow the underlying pipeline.
                listOf(object : WebGpuUiPipelineTarget {
                    override val identity: Any = pipeline
                    override val program = original
                    override val bindingsByGroup = checkNotNull(original.bindingsByGroup)
                    override fun buildPipeline(wgslSource: String, vertexEntryPoint: String, fragmentEntryPoint: String) =
                        pipeline.buildPipeline(wgslSource, vertexEntryPoint, fragmentEntryPoint)
                    override fun swapIn(newPipeline: io.ygdrasil.webgpu.GPURenderPipeline) = pipeline.swapIn(newPipeline)
                })
            },
        )
        try {
            val updated = original.copy(
                vertex = source.copy(sourceCode = source.sourceCode + "\n// reload\n"),
                fragment = (original.fragment as ShaderSource.InlineText).copy(sourceCode = source.sourceCode + "\n// reload\n"),
            )
            assertEquals(1, replacement.replace(original, updated))
            assertEquals(1, replacement.replace(updated, original))
            val working = pipeline.pipeline
            val missingEntry = original.copy(vertex = source.copy(entryPoint = "missingVertex"))
            assertFailsWith<ShaderReplacementException> { replacement.replace(original, missingEntry) }
            assertSame(working, pipeline.pipeline)
            val broken = original.copy(vertex = source.copy(sourceCode = "fn broken( {"))
            assertFailsWith<ShaderReplacementException> { replacement.prepare(broken) }
            assertSame(working, pipeline.pipeline)
            assertEquals(emptyList(), uncaptured, "validation errors must be captured by preparation")
        } finally {
            pipeline.pipeline.close()
            pipeline.destroy()
            swapchain.destroy()
            graphics.destroy()
        }
    }
}

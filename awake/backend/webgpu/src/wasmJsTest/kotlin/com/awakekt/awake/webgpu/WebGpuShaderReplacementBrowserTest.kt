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
import io.ygdrasil.webgpu.GPUUncapturedErrorCallback
import io.ygdrasil.webgpu.canvasContextRenderer
import kotlinx.coroutines.test.runTest
import web.dom.document
import web.html.HTMLCanvasElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Exercises the Wasm capability with Chrome's real validation scopes, without the native compiler. */
class WebGpuShaderReplacementBrowserTest {
    @Test
    fun browserPreparationValidatesShadersWithoutUncapturedErrors() = runTest {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val uncaptured = mutableListOf<String>()
        val context = canvasContextRenderer(
            htmlCanvas = canvas,
            width = 16,
            height = 16,
            onUncapturedError = GPUUncapturedErrorCallback { uncaptured += it.message },
        )
        val graphics = GraphicsDevice().apply { create(context.wgpuContext) }
        val original = EngineShaderSets.UiQuad.webGpu.program()
        val replacement = WebGpuShaderReplacement(
            registry = PipelineRegistry(PipelineFactory<RenderPipeline> { _, _ -> error("No pipelines in this fixture.") }),
            device = graphics.wgpuContext.device,
        )
        try {
            replacement.prepare(original).use { assertEquals(original, it.program) }
            val source = original.vertex as ShaderSource.InlineText
            val broken = original.copy(vertex = source.copy(sourceCode = "fn broken( {"))
            assertFailsWith<ShaderReplacementException> { replacement.prepare(broken) }
            replacement.prepare(original).close()
            assertEquals(emptyList(), uncaptured, "validation errors must be captured by preparation")
        } finally {
            graphics.destroy()
        }
    }
}

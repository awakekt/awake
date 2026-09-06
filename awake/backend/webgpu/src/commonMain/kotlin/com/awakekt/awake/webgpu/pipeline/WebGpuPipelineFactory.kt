/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.asset.shaders.ResolvedShader
import com.awakekt.awake.asset.shaders.RuntimeShaderResolver
import com.awakekt.awake.render.pipeline.PipelineFactory
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.renderer.CullMode
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import io.ygdrasil.webgpu.GPUCullMode
import io.ygdrasil.webgpu.GPUPrimitiveTopology

/**
 * Turns a backend-neutral [PipelineSpec] into a WebGPU [RenderPipeline].
 *
 * The whole WebGPU-specific half of pipeline creation. Which pipelines exist and which companions
 * each one needs is `buildPipelineTable`'s job, shared with Vulkan -- this only translates one
 * already-decided description.
 *
 * Two spellings differ from Vulkan's and are resolved here rather than in the shared spec:
 * wireframe is a `LineList` topology (WebGPU has no `VK_POLYGON_MODE_LINE`), and the fragment
 * shader shares the vertex shader's source file, so only one resource is read.
 */
class WebGpuPipelineFactory(
    private val graphicsDevice: GraphicsDevice,
    private val swapchainManager: SwapchainManager,
    private val shaderResolver: RuntimeShaderResolver,
) : PipelineFactory<RenderPipeline> {

    override suspend fun create(key: PipelineKey, spec: PipelineSpec): RenderPipeline {
        val shader = shaderResolver.resolve(spec.vertexShader)
        val wgsl = (shader as? ResolvedShader.Wgsl)?.source
            ?: error("WebGPU shader resolver returned a non-WGSL shader.")
        return RenderPipeline(
            graphicsDevice,
            swapchainManager,
            DescriptorSetLayoutHandle(0),
            wgsl.encodeToByteArray(),
            // One WGSL file carries both stages on this backend, and RenderPipeline reads the
            // vertex bytes for both -- so the fragment slot is deliberately empty rather than a
            // second read of the same file.
            ByteArray(0),
            spec.vertexFormat,
            spec.vertexEntryPoint,
            spec.fragmentEntryPoint,
            topology = if (spec.wireframe) {
                GPUPrimitiveTopology.LineList
            } else {
                GPUPrimitiveTopology.TriangleList
            },
            variant = spec.variant,
            uniforms = spec.uniforms,
            bindingLayout = spec.bindingLayout,
            materialBindings = spec.materialBindings,
            cullMode = when (spec.cullMode) {
                CullMode.Back -> GPUCullMode.Back
                // Front-culling has no pipeline on either backend, so a Front mesh draws
                // unculled -- exactly what it did before this factory existed. Stated rather
                // than left as a non-exhaustive `when`: when something asks for Front, this is
                // the line that has to change.
                CullMode.None, CullMode.Front -> GPUCullMode.None
            },
        )
    }
}

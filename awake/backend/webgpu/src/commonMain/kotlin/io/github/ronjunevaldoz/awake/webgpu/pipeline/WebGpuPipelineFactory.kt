// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.pipeline

import io.github.ronjunevaldoz.awake.render.pipeline.PipelineFactory
import io.github.ronjunevaldoz.awake.render.pipeline.PipelineKey
import io.github.ronjunevaldoz.awake.render.pipeline.PipelineSpec
import io.github.ronjunevaldoz.awake.render.renderer.CullMode
import io.github.ronjunevaldoz.awake.webgpu.device.GraphicsDevice
import io.github.ronjunevaldoz.awake.webgpu.handles.DescriptorSetLayoutHandle
import io.github.ronjunevaldoz.awake.webgpu.swapchain.SwapchainManager
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
    private val loadShader: suspend (resourcePath: String) -> ByteArray,
) : PipelineFactory<RenderPipeline> {

    override suspend fun create(key: PipelineKey, spec: PipelineSpec): RenderPipeline =
        RenderPipeline(
            graphicsDevice,
            swapchainManager,
            DescriptorSetLayoutHandle(0),
            loadShader(spec.vertexShaderResourcePath),
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

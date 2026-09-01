/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu

import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.asset.shaderpack.PackShaderSets
import io.github.awakelab.awake.asset.shaders.ShaderStage
import io.github.awakelab.awake.asset.shaders.resolveBytes
import io.github.awakelab.awake.render.passes.uniforms.MaterialUniformLayouts
import io.github.awakelab.awake.webgpu.device.GraphicsDevice
import io.github.awakelab.awake.webgpu.handles.DescriptorSetLayoutHandle
import io.github.awakelab.awake.webgpu.pipeline.DepthOnlyPipeline
import io.github.awakelab.awake.webgpu.pipeline.RenderPipeline
import io.github.awakelab.awake.webgpu.swapchain.SwapchainManager
import io.github.awakelab.awake.webgpu.texture.DepthTarget
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The WebGPU half of shadow support, on a real device.
 *
 * WebGPU validates a bind group against the layout its shader declares, so this is the check
 * that could not be argued from reading code: `lit_shadow.wgsl` must compile here, its group-1
 * layout must accept a `Depth32Float` view plus a sampler, and the uniform block the renderer
 * writes must fit the buffer the pool sizes. All three used to be untrue -- the shader was
 * Vulkan-only, declared its map as `texture_2d<f32>` (which fails validation against a depth
 * view), and the pool sized every slot for the 24-float primary block.
 */
class WebGpuShadowBindingTest {

    @Test
    fun litShadowCompilesAndItsDepthBindGroupValidates() = runBlocking {
        val context = glfwContextRenderer(
            width = 1,
            height = 1,
            title = "awake-shadow-binding",
            onUncapturedError = { failure: Any? -> error("WGPU UNCAPTURED: $failure") },
        )
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.create(context.wgpuContext)
        val swapchainManager = SwapchainManager(graphicsDevice, 1)
        swapchainManager.create()

        // From the shader set, not a resource path: the packed shaders carry their WGSL inline
        // now, and the file this used to read stopped existing -- which failed the test at its
        // first line and left the binding it exists to check unverified.
        val litShadow = checkNotNull(PackShaderSets.LitShadow.webGpu[ShaderStage.VERTEX]) {
            "lit_shadow declares no WebGPU vertex stage."
        }.resolveBytes()
        val pipeline = RenderPipeline(
            graphicsDevice,
            swapchainManager,
            DescriptorSetLayoutHandle(0),
            litShadow,
            ByteArray(0),
            VertexFormat.PositionNormalColor,
            "vertexMain",
            "fragmentMain",
        )
        // Comparison, as the renderer builds it: lit_shadow declares `sampler_comparison`, and
        // the auto layout derived from that declaration rejects a plain sampler at bind time.
        val depthTarget = DepthTarget(graphicsDevice, comparison = true)

        // The binding the renderer builds per shadowed draw. An invalid layout/resource pair
        // raises a WebGPU validation error rather than returning null, which the uncaptured
        // error callback above turns into a test failure.
        val bindGroup = graphicsDevice.wgpuContext.device.createBindGroup(
            BindGroupDescriptor(
                layout = pipeline.handle.pipeline.getBindGroupLayout(1u),
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = depthTarget.depthView),
                    BindGroupEntry(binding = 1u, resource = depthTarget.sampler),
                ),
            ),
        )
        assertNotNull(bindGroup, "lit_shadow's group 1 must accept a depth view + sampler")

        // Depth-only companion: same vertex format, so the pre-pass draws the same meshes.
        val depthOnly = DepthOnlyPipeline(
            graphicsDevice = graphicsDevice,
            shaderCode = checkNotNull(PackShaderSets.ShadowDepth.webGpu[ShaderStage.VERTEX]) {
                "shadow_depth declares no WebGPU vertex stage."
            }.resolveBytes(),
            vertexFormat = VertexFormat.PositionNormalColor,
        )
        assertNotNull(depthOnly.handle, "shadow_depth must compile on WebGPU")

        // The slot the pool hands a shadowed draw must hold the whole block; a short buffer is
        // exactly how the old Primary-sized pool would have failed at write time.
        assertEquals(
            168,
            MaterialUniformLayouts.LitShadow.total,
            "lit_shadow's Uniforms is 168 floats -- the pool sizes every primary slot for it",
        )

        depthOnly.destroy()
        depthTarget.destroy()
        pipeline.destroy()
        swapchainManager.destroy()
        graphicsDevice.destroy()
    }
}

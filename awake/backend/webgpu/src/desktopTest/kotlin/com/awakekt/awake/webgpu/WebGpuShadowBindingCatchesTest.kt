/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import com.awakekt.awake.webgpu.texture.DepthTarget
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The negative control for [WebGpuShadowBindingTest] (#1709): the binding it checks, built wrong on
 * purpose with a plain sampler where `lit_shadow` declares `sampler_comparison`, is a validation
 * error wgpu raises on its callback, and the uncaptured-error collector that test closes with does
 * catch it. Were the collector or its callback lost, that test would pass on a broken binding again,
 * as it once did; this one would fail. The error is recorded, not reported, so the build's
 * uncaptured-error backstop does not fail the run over an error provoked on purpose.
 */
class WebGpuShadowBindingCatchesTest {
    @Test
    fun aPlainSamplerOnTheShadowBindingIsCaught() = runBlocking {
        WebGpuUncapturedErrors.drain()
        val context = glfwContextRenderer(
            width = 1,
            height = 1,
            title = "awake-shadow-binding-catches",
            onUncapturedError = { failure: Any? -> WebGpuUncapturedErrors.record(failure) },
        )
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.create(context.wgpuContext)
        val swapchainManager = SwapchainManager(graphicsDevice, 1)
        swapchainManager.create()
        val pipeline = RenderPipeline(
            graphicsDevice,
            swapchainManager,
            DescriptorSetLayoutHandle(0),
            checkNotNull(PackShaderSets.LitShadow.webGpu[ShaderStage.VERTEX]).resolveBytes(),
            ByteArray(0),
            VertexFormat.PositionNormalColor,
            "vertexMain",
            "fragmentMain",
            bindingsByGroup = PackShaderSets.LitShadow.webGpu.bindingsByGroup,
            bindingsMetadataAvailable = PackShaderSets.LitShadow.webGpu.bindingsMetadataAvailable,
        )
        // Wrong on purpose: a plain sampler, which the shader's comparison binding rejects.
        val depthTarget = DepthTarget(graphicsDevice, arrayed = true, comparison = false)
        graphicsDevice.wgpuContext.device.createBindGroup(
            BindGroupDescriptor(
                layout = pipeline.handle.pipeline.getBindGroupLayout(1u),
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = depthTarget.depthView),
                    BindGroupEntry(binding = 1u, resource = depthTarget.sampler),
                ),
            ),
        )
        depthTarget.destroy()
        pipeline.destroy()
        swapchainManager.destroy()
        graphicsDevice.destroy()
        val caught = WebGpuUncapturedErrors.drain()
        assertTrue(caught.any { "comparison" in it }, "the wrong sampler raised no uncaptured error the test could see: $caught")
    }
}

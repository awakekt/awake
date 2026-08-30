/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu

import io.github.awakelab.awake.asset.shaderpack.depthFogShader
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.render.pipeline.PipelineVariant
import io.github.awakelab.awake.render.renderer.DepthFogUniformLayout
import io.github.awakelab.awake.webgpu.device.GraphicsDevice
import io.github.awakelab.awake.webgpu.handles.DescriptorSetLayoutHandle
import io.github.awakelab.awake.webgpu.pipeline.RenderPipeline
import io.github.awakelab.awake.webgpu.renderer.sceneDepthBindingFor
import io.github.awakelab.awake.webgpu.swapchain.SwapchainManager
import io.github.awakelab.awake.webgpu.texture.DepthTarget
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * WebGPU's half of the depth-fog seam, on a real device: the shader compiles, and the scene-depth
 * target binds to the group its pipeline declares.
 *
 * Short of the Vulkan sibling's pixel assertion on purpose, and the reason is a known gap rather
 * than a choice: this backend's `performRenderToTexture` is a hand-written copy of the on-screen
 * draw path that records no render features, so there is no offscreen frame a
 * `DepthFogRenderFeature` could draw into to be read back. Collapsing that duplication onto the
 * shared prepared-draw machinery is what would make the pixel test writable here.
 *
 * What it still separates:
 * - WGSL that naga accepts and wgpu rejects (the Vulkan test compiles the Vulkan half only);
 * - a fog pipeline whose depth group lands at a different index than the depth target's layout,
 *   which `getBindGroupLayout` refuses rather than silently sampling the wrong group.
 */
class WebGpuDepthFogTest {

    @Test
    fun theFogPipelineTakesTheSceneDepthGroup(): Unit = runBlocking {
        val context = glfwContextRenderer(
            width = 1,
            height = 1,
            title = "awake-depth-fog",
            onUncapturedError = { error -> println("WGPU UNCAPTURED: $error") },
        )
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.create(context.wgpuContext)
        val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
        swapchainManager.create()
        val depthTarget = DepthTarget(graphicsDevice)
        try {
            val fog = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                DescriptorSetLayoutHandle(0),
                // WebGPU's half of the shared definition -- see depthFogShader's flipDepthV.
                depthFogShader(flipDepthV = true).emitWgsl().encodeToByteArray(),
                ByteArray(0),
                VertexFormat.None,
                "vertexMain",
                "fragmentMain",
                variant = PipelineVariant.Overlay,
                uniforms = DepthFogUniformLayout,
            )
            assertNotNull(
                fog.uniformBlock,
                "The fog pipeline was built with a uniform layout, so it owns a block -- a null " +
                    "one leaves DepthFogRenderFeature with nothing to write this frame's camera " +
                    "into.",
            )
            val pools = io.github.awakelab.awake.webgpu.renderer.GpuBufferPoolManager(graphicsDevice)
            try {
                assertNotNull(
                    pools.sceneDepthBindingFor(fog.handle, depthTarget),
                    "The depth target must bind to the fog pipeline's " +
                        "${BindingSemantic.SceneDepth} group.",
                )
            } finally {
                pools.destroy()
            }
        } finally {
            graphicsDevice.destroy()
        }
    }

    private companion object {
        const val MAX_FRAMES_IN_FLIGHT = 1
    }
}

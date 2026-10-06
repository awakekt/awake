/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Scene replacement releases submitted vertex/index buffers, per-draw UBO blocks and descriptor
 * pools without a caller-side idle wait. Device teardown also checks for leaked child objects.
 * Validation must really be enabled: an absent or unloadable layer is a failure, not a skipped gate.
 * Before the destruction waits, this test reported VUID-vkDestroyBuffer-buffer-00922 for mesh and
 * UBO buffers, and pending framebuffer/command-pool destruction errors at shutdown on desktop Vulkan.
 */
class RendererResourceDestructionTest {
    @Test
    fun sceneReplacementAndShutdownReleaseSubmittedResources() {
        val device = GraphicsDevice()
        device.createHeadless()
        try {
            assertTrue(device.validationLayerEnabled, "Install VK_LAYER_KHRONOS_validation to run the resource lifetime gate")
            exerciseSceneReplacement(device)
        } finally {
            // createHeadless makes every validation ERROR, including leaked children, fail here.
            device.destroy()
        }
    }

    private fun exerciseSceneReplacement(device: GraphicsDevice) {
        val swapchain = SwapchainManager(device, FRAMES_IN_FLIGHT)
        swapchain.createHeadlessPresentable(SIZE, SIZE)
        val layout = Material.createDescriptorSetLayout(device)
        val renderPass = createSceneRenderPass(device, swapchain)
        val pipeline = RenderPipeline(
            device,
            swapchain,
            renderPass,
            layout,
            runBlocking { packShaderPair("triangle") },
            VertexFormat.PositionNormalColor,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
        )
        val transfer = TransferContext(device)
        val lines = LineRenderPipeline(device, swapchain, renderPass, runBlocking { packShaderPair("debug_line") }, FRAMES_IN_FLIGHT)
        val renderer = Renderer(
            graphicsDevice = device,
            swapchainManager = swapchain,
            pipelines = PipelineTable(primary = pipeline, primaryFormat = VertexFormat.PositionNormalColor),
            uiShaderPairs = runBlocking { defaultUiShaderPairs() },
            transferContext = transfer,
            renderFeatures = listOf(OpaqueRenderFeature(VulkanLinePass(lines))),
            maxFramesInFlight = FRAMES_IN_FLIGHT,
        )
        swapchain.createSyncObjects()
        val cleanup = headlessCleanup(device, transfer, renderPass, layout, pipeline)
        try {
            repeat(RELOADS) { reload ->
                val mesh = renderer.createMesh(MeshGeometry(VERTICES, intArrayOf(0, 1, 2), VertexFormat.PositionNormalColor))
                val material = renderer.createMaterial()
                try {
                    // Two frame slots and more than one allocation block of UBOs/descriptors.
                    repeat(FRAMES_IN_FLIGHT) { draw(renderer, List(DRAWS) { RenderDrawCommand(mesh, material) }) }
                } finally {
                    // Exercise each resource's wait independently as the first destroyed object.
                    if (reload % 2 == 0) {
                        mesh.destroy()
                        material.destroy()
                    } else {
                        material.destroy()
                        mesh.destroy()
                    }
                }
            }
            // Leave a real submitted presentation frame for Renderer.destroy itself to drain.
            draw(renderer, emptyList())
        } finally {
            renderer.destroy()
            swapchain.destroy()
            swapchain.destroySyncObjects()
            cleanup()
        }
    }

    private fun draw(renderer: Renderer, draws: List<RenderDrawCommand>) {
        renderer.draw(
            ScenePassCompiler.compile(
                lens = Lens(eye = Vec3f(0f, 0f, 2f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 10f),
                drawCalls = draws,
                light = DEFAULT_SCENE_LIGHT,
                environment = EnvironmentUniforms.Default,
                clipSpace = renderer.clipSpace,
                aspect = 1f,
                viewport = null,
                drawPreparer = renderer.gpuDrawPreparer,
            ),
        )
    }

    private companion object {
        const val SIZE = 128
        const val FRAMES_IN_FLIGHT = 2
        const val RELOADS = 6
        const val DRAWS = 64
        val VERTICES = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 0f, 1f, 1f, 0f, 0f,
            0.5f, -0.5f, 0f, 0f, 0f, 1f, 0f, 1f, 0f,
            0f, 0.5f, 0f, 0f, 0f, 1f, 0f, 0f, 1f,
        )
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import kotlin.test.assertTrue

/**
 * Runs [block] with a UI-only renderer over a headless swapchain that presents, so `drawUi` then
 * `presentWithoutScene` records the swapchain UI pass and `readPresentedPixels` reads it back.
 *
 * Validation must be on, and every validation error fails the run when the device is destroyed.
 */
internal fun withPresentableUiRenderer(size: Int, framesInFlight: Int, block: (Renderer) -> Unit) {
    val device = GraphicsDevice()
    device.createHeadless()
    try {
        assertTrue(device.validationLayerEnabled, "Install VK_LAYER_KHRONOS_validation to run this gate")
        val swapchain = SwapchainManager(device, framesInFlight)
        swapchain.createHeadlessPresentable(size, size)
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
        val renderer = Renderer(
            graphicsDevice = device,
            swapchainManager = swapchain,
            pipelines = PipelineTable(primary = pipeline, primaryFormat = VertexFormat.PositionNormalColor),
            uiShaderPairs = runBlocking { backdropUiShaderPairs() },
            transferContext = transfer,
            renderFeatures = listOf(UiRenderFeature(VulkanUiPass())),
            maxFramesInFlight = framesInFlight,
        )
        swapchain.createSyncObjects()
        val cleanup = headlessCleanup(device, transfer, renderPass, layout, pipeline)
        try {
            block(renderer)
        } finally {
            renderer.destroy()
            swapchain.destroy()
            swapchain.destroySyncObjects()
            cleanup()
        }
    } finally {
        // createHeadless makes every validation ERROR fail here.
        device.destroy()
    }
}

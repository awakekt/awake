/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.engine.window.GlfwWindow
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkResult
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Presents through a real window, surface and swapchain (#1688): the path every desktop and Android
 * consumer presents through, which the headless swapchains of the other tests never reach. A GLFW
 * window under xvfb gets a `VkSurfaceKHR` from lavapipe's X11 WSI, and frames are presented through
 * `vkQueuePresentKHRStatus`. Then the window is resized without telling the renderer, as no host
 * callback runs here, so only acquire or present can find the swapchain stale: it must be built
 * again once, at the new size, and presenting must go on.
 *
 * Run by `desktopWindowedTest` under `xvfb-run`, which `desktopTest` leaves out: it needs a display,
 * and on macOS a window needs the process's first thread, which a test worker is not.
 */
class WindowedPresentTest {
    @Test
    fun framesPresentToARealSurfaceAndAResizeRebuildsTheSwapchainOnce() {
        check(GlfwWindow.glfwInit()) { "glfwInit failed: is a display (xvfb) available?" }
        GlfwWindow.glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        val window = GlfwWindow.glfwCreateWindow(WIDTH, HEIGHT, "awake-windowed-present")
        check(window != 0L) { "glfwCreateWindow returned null" }
        try {
            withWindowRenderer(window) { renderer, swapchain ->
                assertEquals(1, swapchain.builds, "the swapchain is built once for the window")
                present(renderer, FRAMES)
                assertEquals(1, swapchain.builds, "steady presents keep the swapchain")

                GlfwWindow.glfwSetWindowWindowed(window, 0, 0, RESIZED_WIDTH, RESIZED_HEIGHT)
                GlfwWindow.glfwPollEvents()
                present(renderer, FRAMES)
                val width = GlfwWindow.glfwGetFramebufferWidth(window)
                val height = GlfwWindow.glfwGetFramebufferHeight(window)
                val results = VkResult.entries.filter { swapchain.presentResults[it.ordinal] > 0 }.associateWith { swapchain.presentResults[it.ordinal] }
                println("WindowedPresent: ${swapchain.builds} builds, extent ${swapchain.extent.width}x${swapchain.extent.height}, framebuffer ${width}x$height, presents $results")
                assertTrue(results.getOrDefault(VkResult.VK_SUCCESS, 0) >= FRAMES, "frames were presented through vkQueuePresentKHRStatus: $results")
                assertEquals(results.keys - VkResult.VK_SUCCESS - VkResult.VK_SUBOPTIMAL_KHR - VkResult.VK_ERROR_OUT_OF_DATE_KHR, emptySet(), "only the three answered results")
                assertEquals(2, swapchain.builds, "a resize found by acquire or present rebuilds the swapchain once")
                assertEquals(width, swapchain.extent.width, "the rebuilt swapchain has the window's new size")
                assertEquals(height, swapchain.extent.height)
            }
        } finally {
            GlfwWindow.glfwDestroyWindow(window)
            GlfwWindow.glfwTerminate()
        }
    }

    /** Presents [frames] scene-less frames, each acquiring, recording, submitting and presenting. */
    private fun present(renderer: Renderer, frames: Int) = repeat(frames) {
        GlfwWindow.glfwPollEvents()
        renderer.presentWithoutScene()
    }

    private fun withWindowRenderer(window: Long, block: (Renderer, SwapchainManager) -> Unit) {
        val device = GraphicsDevice()
        // A windowed device only prints validation errors unless told otherwise.
        device.failOnValidationError = true
        device.create(window)
        try {
            val swapchain = SwapchainManager(device, FRAMES_IN_FLIGHT, surfaceExtentProvider = { surfaceFramebufferExtent(window) })
            swapchain.create()
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
                uiShaderPairs = runBlocking { defaultUiShaderPairs() },
                transferContext = transfer,
                renderFeatures = listOf(UiRenderFeature(VulkanUiPass())),
                maxFramesInFlight = FRAMES_IN_FLIGHT,
            )
            swapchain.createSyncObjects()
            val cleanup = headlessCleanup(device, transfer, renderPass, layout, pipeline)
            try {
                block(renderer, swapchain)
                assertTrue(swapchain.builds >= 1)
            } finally {
                renderer.waitIdle()
                renderer.destroy()
                swapchain.destroy()
                swapchain.destroySyncObjects()
                cleanup()
            }
        } finally {
            device.destroy()
        }
    }

    private companion object {
        const val GLFW_CLIENT_API = 0x00022001
        const val GLFW_NO_API = 0
        const val WIDTH = 160
        const val HEIGHT = 120
        const val RESIZED_WIDTH = 240
        const val RESIZED_HEIGHT = 180
        const val FRAMES = 30
        const val FRAMES_IN_FLIGHT = 2
    }
}

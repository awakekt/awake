/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
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
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * UI staged by `drawUi` is recorded at the next `draw`, so a render target it samples can be
 * destroyed in between: a scene switch frees the old scene's canvas View targets before the new
 * scene's first frame. That frame then wrote the freed image view and sampler into a UI descriptor
 * set, which crashed natively inside MoltenVK's `vkUpdateDescriptorSets`.
 *
 * Validation must be on: it names the freed handles, and it drops the call that would crash.
 * Before the fix the second frame reported `vkUpdateDescriptorSets():
 * pDescriptorWrites[0].pImageInfo[0].imageView Invalid VkImageView Object`, the same for the
 * sampler, and `vkCmdDrawIndexed()` reading both destroyed descriptors.
 *
 * Staged UI is used up by that `draw`, so a frame that stages none draws none.
 */
class RendererStagedUiTargetTest {
    @Test
    fun aTargetDestroyedAfterItsUiWasStagedIsNotDrawn() = withRenderer { renderer ->
        val target = renderer.createRenderTarget(SIZE, SIZE)
        renderer.drawUiToTexture(target, listOf(redQuad()))
        val material = renderer.createMaterial(renderTarget = target)
        val ui = listOf(UiDrawPrimitive.Texture(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), material, premultiplied = true))

        // Positive control: the staged UI really samples the target, so a clean second frame
        // is not just a frame that drew no texture at all.
        renderer.drawUi(ui)
        renderer.presentWithoutScene()
        assertTrue(isRed(centre(renderer)), "the staged UI shows the target")

        renderer.drawUi(ui)
        material.destroy()
        target.destroy()
        renderer.presentWithoutScene()
    }

    @Test
    fun aFrameThatStagesNoUiDrawsNone() = withRenderer { renderer ->
        renderer.drawUi(listOf(redQuad()))
        renderer.presentWithoutScene()
        assertTrue(isRed(centre(renderer)), "the staged UI is drawn")

        // Each frame in flight has its own UI mesh, so a stale UI shows again on the slot it was
        // uploaded to: every frame of a full cycle must come out without it.
        repeat(FRAMES_IN_FLIGHT) { frame ->
            renderer.presentWithoutScene()
            assertFalse(isRed(centre(renderer)), "frame ${frame + 1} after the last staged UI drew it again")
        }
    }

    private fun redQuad() = UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color(1f, 0f, 0f, 1f))

    private fun withRenderer(block: (Renderer) -> Unit) {
        val device = GraphicsDevice()
        device.createHeadless()
        try {
            assertTrue(device.validationLayerEnabled, "Install VK_LAYER_KHRONOS_validation to run this gate")
            exercise(device, block)
        } finally {
            // createHeadless makes every validation ERROR fail here.
            device.destroy()
        }
    }

    private fun exercise(device: GraphicsDevice, block: (Renderer) -> Unit) {
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
            block(renderer)
        } finally {
            renderer.destroy()
            swapchain.destroy()
            swapchain.destroySyncObjects()
            cleanup()
        }
    }

    private fun centre(renderer: Renderer): ByteArray {
        val pixels = runBlocking { renderer.readPresentedPixels() }.data
        val offset = (SIZE / 2 * SIZE + SIZE / 2) * 4
        return pixels.copyOfRange(offset, offset + 4)
    }

    private fun isRed(rgba: ByteArray): Boolean =
        (rgba[0].toInt() and 0xFF) > 200 && (rgba[1].toInt() and 0xFF) < 50 && (rgba[2].toInt() and 0xFF) < 50

    private companion object {
        const val SIZE = 32
        const val FRAMES_IN_FLIGHT = 2
    }
}

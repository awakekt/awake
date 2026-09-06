/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.vulkan.pipeline.RenderPipeline

/**
 * What a headless fixture built by hand and nothing else frees, as one teardown lambda.
 *
 * `Renderer.destroy` frees what the renderer built; it does not free the render pass, pipelines,
 * descriptor set layout or transfer context a test constructed to hand it, and it does not touch
 * the device underneath. A fixture that caches its renderer for the whole class therefore leaked
 * all of those for the rest of the JVM run -- invisible until `vkDestroyDevice` is finally called
 * and the validation layer names them, which is exactly why every caching fixture now destroys
 * its device in an `@AfterClass` rather than leaving it open.
 *
 * Returned as a lambda so a caller assigns it where those locals are still in scope, instead of
 * threading each one back out through a holder class.
 */
internal fun headlessCleanup(
    graphicsDevice: GraphicsDevice,
    transferContext: TransferContext,
    sceneRenderPass: Long,
    descriptorSetLayout: DescriptorSetLayoutHandle,
    vararg pipelines: RenderPipeline,
): () -> Unit = {
    pipelines.forEach { it.destroy() }
    transferContext.destroy()
    Vulkan.vkDestroyRenderPass(graphicsDevice.device, sceneRenderPass)
    VulkanDescriptors.vkDestroyDescriptorSetLayout(graphicsDevice.device, descriptorSetLayout.handle)
}

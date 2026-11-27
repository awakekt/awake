/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.MASK_TARGET_LAYER_COUNT
import com.awakekt.awake.render.renderer.RenderScale
import com.awakekt.awake.vulkan.texture.DepthTarget

/**
 * Reclaims temporary Vulkan staging buffers, retired resources, unused native-resolution swapchain
 * depth when scaled, idle transient render targets, scene-depth/mask targets (shrunk to dormant 1x1
 * stand-ins until the next frame that needs them), cached image-pass textures, pooled instance/UI
 * buffers, CPU shader compiler caches, and empty `DEVICE_LOCAL` memory slabs without touching
 * caller-owned meshes, materials, or textures.
 */
internal fun Renderer.performTrimMemory() {
    if (device == 0L) return
    waitIdle()
    awaitSubmittedOffscreenCommands()
    retiredSamplers.destroyAll()
    if (RenderScale.sceneExtent(outputWidth, renderScale) != outputWidth ||
        RenderScale.sceneExtent(outputHeight, renderScale) != outputHeight
    ) {
        retireSwapchainSceneFramebuffers()
    }
    sceneDepthPassFeature?.resize(0, DepthTarget.DORMANT_SIZE, dormantLayers = 1)
    maskPass?.resize(0, DepthTarget.DORMANT_SIZE, dormantLayers = MASK_TARGET_LAYER_COUNT)
    deferredResourceReaper.destroyAll()
    imagePassExecutor.releaseTransientTargets()
    if (imagePassTextures.isNotEmpty()) {
        imagePassTextures.values.forEach { texture ->
            imagePassExecutor.invalidateImageView(texture.imageView.handle)
            textureResources.release(texture)
        }
        imagePassTextures.clear()
    }
    transferContext.uploadBatcher.trimIdleChunks()
    graphicsDevice.stagingBufferPool.trim()
    textureUploads.trimIdle()
    val trimUi = uiRuns.isEmpty()
    val trimInstances = !redrawsStillFrames
    bufferPools.trimPools(trimUiMeshes = trimUi, trimInstanceBuffers = trimInstances)
    if (trimUi) {
        uiRunCache.clear()
    }
    if (trimInstances) {
        resetDrawPreparersAfterTrim()
    }
    onTrimMemory?.invoke()
    graphicsDevice.deviceMemoryAllocator.trimEmptySlabs()
    graphicsDevice.uniformMemoryPool.trimEmptySlabs()
    graphicsDevice.descriptorSetPool.trimEmptyPools()
}

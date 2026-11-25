/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.renderer.RenderScale

/**
 * Reclaims temporary Vulkan staging buffers, retired resources, unused native-resolution swapchain
 * depth when scaled, idle transient render targets, pooled instance/UI buffers, and empty
 * `DEVICE_LOCAL` memory slabs without touching caller-owned meshes, materials, or textures.
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
    deferredResourceReaper.destroyAll()
    imagePassExecutor.releaseTransientTargets()
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
    graphicsDevice.deviceMemoryAllocator.trimEmptySlabs()
}

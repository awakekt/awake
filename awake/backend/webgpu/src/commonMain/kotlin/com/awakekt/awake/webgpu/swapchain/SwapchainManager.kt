/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.swapchain

import com.awakekt.awake.webgpu.device.GraphicsDevice
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.TextureViewDescriptor

/**
 * Module restructuring slice 2 (see docs/mvp-plan.md): partial real implementation --
 * the frame-in-flight sync fields ([imageAvailableSemaphores]/[renderFinishedSemaphores]/
 * [inFlightFences]) have no WebGPU equivalent (the browser's own frame pacing replaces
 * them) and stay empty. `extent`/`imageFormat` (Vulkan-typed, `VkExtent2D`/`VkFormat`) were
 * dropped entirely once this backend moved to its own module: they existed only to satisfy
 * the old shared `expect` contract with the Vulkan backend, and grep confirmed neither is
 * read anywhere in this module either -- canvas size/format come from `imageFormatWebGpu`
 * and `renderingContext.width`/`.height` directly (see [com.awakekt.awake.webgpu.renderer.Renderer.draw]).
 */
class SwapchainManager(
    graphicsDevice: GraphicsDevice,
    /**
     * Frames in flight the shared renderer contract asks for. WebGPU needs no per-frame
     * synchronisation, so it only sizes the placeholder sync arrays.
     */
    val maxFramesInFlight: Int,
) {
    private val graphicsDevice = graphicsDevice

    /** Vulkan-parity placeholder, always 0: presentation goes through the canvas context. */
    var swapChain: Long = 0

    /**
     * Vulkan-parity placeholder, always empty: the canvas texture is acquired per frame, not held
     * as a list.
     */
    var imageViews: List<Long> = emptyList()
    val imageAvailableSemaphores = LongArray(maxFramesInFlight)
    val renderFinishedSemaphores = LongArray(maxFramesInFlight)
    val inFlightFences = LongArray(maxFramesInFlight)

    /** Vulkan-parity frame-slot index. Nothing in this backend advances it, so it stays 0. */
    var currentFrame = 0

    // Mirrors the browser's real preferred canvas format so every pipeline agrees with what
    // WebGpuCanvasHost.configureSurface() configures -- a mismatch forces an extra copy per present.
    internal var imageFormatWebGpu: GPUTextureFormat = GPUTextureFormat.RGBA8Unorm
        private set

    internal var depthTextureView: GPUTextureView? = null
        private set

    private var depthTextureHandle: io.ygdrasil.webgpu.GPUTexture? = null
    private var configuredWidth = 0u
    private var configuredHeight = 0u

    private val renderingContext get() = graphicsDevice.wgpuContext.renderingContext

    /**
     * Reads the canvas's preferred texture format and builds the canvas-sized depth attachment.
     * Call once, after the context has been configured.
     */
    fun create() {
        imageFormatWebGpu = renderingContext.textureFormat
        syncSurface()
    }

    /** Closes the depth texture and drops its view. Safe to call when nothing was created. */
    fun destroy() {
        depthTextureHandle?.close()
        depthTextureHandle = null
        depthTextureView = null
    }

    /** Does nothing: the browser's frame pacing replaces explicit semaphores and fences. */
    fun createSyncObjects() {
        // Browser frame pacing replaces explicit swapchain semaphores/fences.
    }

    /** Does nothing: see [createSyncObjects]. */
    fun destroySyncObjects() {
        // Browser frame pacing replaces explicit swapchain semaphores/fences.
    }

    /**
     * Re-reads the canvas texture format and size and rebuilds the depth attachment when the size
     * changed or none exists yet.
     *
     * A no-op while the canvas keeps its size. The renderer calls it at the start of every 3D draw,
     * so a canvas resize takes effect on the next frame.
     */
    fun syncSurface() {
        imageFormatWebGpu = renderingContext.textureFormat
        val width = renderingContext.width
        val height = renderingContext.height
        if (width == configuredWidth && height == configuredHeight && depthTextureView != null) return

        depthTextureHandle?.close()
        depthTextureHandle = graphicsDevice.wgpuContext.device.createTexture(
            TextureDescriptor(
                size = io.ygdrasil.webgpu.Extent3D(width = width, height = height),
                format = GPUTextureFormat.Depth32Float,
                usage = GPUTextureUsage.RenderAttachment,
            ),
        )
        depthTextureView = depthTextureHandle?.createView(TextureViewDescriptor())
        configuredWidth = width
        configuredHeight = height
    }
}

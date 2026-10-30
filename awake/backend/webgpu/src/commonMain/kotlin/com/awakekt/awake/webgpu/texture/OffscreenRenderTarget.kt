/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.texture

import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.webgpu.device.GraphicsDevice
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureDimension
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.TextureViewDescriptor

/**
 * An offscreen color render destination (`Renderer.createRenderTarget`) -- a
 * [RenderTarget] implementation. Unlike Vulkan's [com.awakekt.awake.vulkan.texture.OffscreenRenderTarget],
 * WebGPU has no framebuffer object at all -- a render pass just names a [GPUTextureView]
 * directly, so there's no render-pass-compatibility concern to design around here (see that
 * Vulkan class's doc comment for the format-matching constraint it has to work around).
 *
 * Carries its own depth attachment so offscreen readbacks match the same occlusion rules as
 * the on-screen WebGPU path.
 *
 * [colorTexture]'s format follows the same browser-preferred format as the swapchain
 * ([graphicsDevice].wgpuContext.renderingContext.textureFormat, see `SwapchainManager` and
 * `WebGpuCanvasHost.configureSurface`) rather than a hardcoded guess -- `Renderer.renderToTexture`
 * draws into this target with the SAME `RenderPipeline` instance used for the on-screen pass
 * (built with `ColorTargetState(format = swapchainManager.imageFormatWebGpu)`), and WebGPU
 * requires a render pass's color attachment format to exactly match the bound pipeline's
 * target format. That format is UNORM, not sRGB, wherever the surface offers one (see
 * `withDisplayEncodedColorFormat`), so the bytes this target holds are the ones the shaders
 * wrote, as on Vulkan.
 */
class OffscreenRenderTarget(
    graphicsDevice: GraphicsDevice,
    override val width: Int,
    override val height: Int,
    private val onDestroy: (() -> Unit)? = null,
    sampleCount: Int = 1,
) : RenderTarget {
    /** The attachment's byte order, needed to normalize CPU readback to RGBA. */
    val colorFormat: GPUTextureFormat = graphicsDevice.wgpuContext.renderingContext.textureFormat

    val colorTexture: GPUTexture = graphicsDevice.wgpuContext.device.createTexture(
        TextureDescriptor(
            size = Extent3D(width = width.toUInt(), height = height.toUInt()),
            format = colorFormat,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding or GPUTextureUsage.CopySrc,
            dimension = GPUTextureDimension.TwoD,
        ),
    )

    /**
     * View of [colorTexture] that a render pass names as its colour attachment and a UI pass
     * samples to composite the target.
     */
    val colorView: GPUTextureView = colorTexture.createView(TextureViewDescriptor())
    private val device = graphicsDevice.wgpuContext.device
    private val sampleCount = sampleCount
    private var scene: SceneAttachments? = null

    /** Depth and, above 1x, multisampled colour: what only a scene pass binds, built by the first one. */
    private class SceneAttachments(val multisampleColor: GPUTexture?, val depth: GPUTexture) {
        val depthView: GPUTextureView = depth.createView(TextureViewDescriptor())
        val colorView: GPUTextureView? = multisampleColor?.createView()

        fun close() {
            colorView?.close()
            depthView.close()
            multisampleColor?.close()
            depth.close()
        }
    }

    private fun scene(): SceneAttachments {
        check(!destroyed) { "The render target was destroyed." }
        scene?.let { return it }
        val size = Extent3D(width = width.toUInt(), height = height.toUInt())
        val multisampleColor = if (sampleCount > 1) {
            device.createTexture(
                TextureDescriptor(size = size, format = colorFormat, usage = GPUTextureUsage.RenderAttachment, sampleCount = sampleCount.toUInt()),
            )
        } else {
            null
        }
        val depth = device.createTexture(
            TextureDescriptor(
                size = size,
                format = GPUTextureFormat.Depth32Float,
                sampleCount = sampleCount.toUInt(),
                usage = GPUTextureUsage.RenderAttachment,
                dimension = GPUTextureDimension.TwoD,
            ),
        )
        return SceneAttachments(multisampleColor, depth).also { scene = it }
    }

    /** The view a scene pass renders colour into: the multisampled one above 1x, else [colorView]. */
    internal val sceneColorView: GPUTextureView get() = scene().colorView ?: colorView

    /** Where a multisampled scene pass resolves, or `null` at 1x. */
    internal val sceneResolveView: GPUTextureView? get() = colorView.takeIf { scene().multisampleColor != null }

    /**
     * The `Depth32Float` attachment sized like the colour texture, so offscreen draws occlude as
     * on-screen ones do. It is a render attachment only and cannot be sampled. The first read
     * allocates it, with the multisampled colour; a target only UI passes draw into never reads it.
     */
    val depthTexture: GPUTexture get() = scene().depth

    /** View of [depthTexture] that a render pass names as its depth attachment. */
    val depthView: GPUTextureView get() = scene().depthView

    /** Every texture this target holds now, resolved colour first; the scene attachments only once built. */
    internal val attachmentAllocations: List<AttachmentAllocation>
        get() = listOfNotNull(colorTexture, scene?.multisampleColor, scene?.depth).map {
            AttachmentAllocation(it.format, it.sampleCount.toInt(), it.width.toInt(), it.height.toInt())
        }

    /** Idempotent because `Renderer.destroy` frees every still-live target while a caller holding
     * one may also destroy it directly. Both are legitimate; the guard keeps whichever runs
     * second from closing an already-closed texture. */
    private var destroyed = false

    /** Lets renderer-owned bind-group caches discard entries that point at a retired target. */
    internal val isDestroyed: Boolean get() = destroyed

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        colorView.close()
        colorTexture.close()
        scene?.close()
        scene = null
        onDestroy?.invoke()
    }
}

/** One texture a render target holds, as requested: format, sample count and size. */
internal data class AttachmentAllocation(val format: GPUTextureFormat, val samples: Int, val width: Int, val height: Int)

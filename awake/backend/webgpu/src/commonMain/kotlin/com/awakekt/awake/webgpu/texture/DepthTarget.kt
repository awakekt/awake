/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.texture

import com.awakekt.awake.render.renderer.ShadowDepthFormat
import com.awakekt.awake.webgpu.device.GraphicsDevice
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureDimension
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.TextureViewDescriptor

/**
 * A depth-only render target a feature can render into and then sample. Mirrors Vulkan's
 * `DepthTarget` (square 2048x2048 by default, or rectangular for screen-matched scene-depth and
 * mask targets; [ShadowDepthFormat.Depth32Float] unless built with another [format]).
 *
 * Hardware only: it knows a depth format and an extent, and nothing about why a caller wants depth
 * rendered offscreen.
 */
class DepthTarget(
    private val graphicsDevice: GraphicsDevice,
    size: Int = DEFAULT_SIZE,
    layers: Int = 1,
    /** Whether [depthView] is an array view; see Vulkan's `DepthTarget.arrayed` for why this is
     * declared rather than derived from [layers]. */
    val arrayed: Boolean = false,
    /**
     * Whether [sampler] is a comparison sampler (LessEqual, linear-filtered) rather than a plain
     * nearest one. A flag, not the default, because this class also serves the scene-depth
     * target: `depth_fog` declares a plain `sampler`, and the auto bind-group layout derived
     * from that declaration rejects a comparison sampler at bind time.
     */
    comparison: Boolean = false,
    /** The texture's depth format: [ShadowDepthFormat.Depth32Float] unless a mobile tier asked for
     * less, as on Vulkan (#1709). A pipeline drawing into this target must be built for the same
     * [textureFormat]. */
    val format: ShadowDepthFormat = ShadowDepthFormat.Depth32Float,
) {
    /** The WebGPU texture format [format] names: what a pipeline and a render bundle drawing into
     * this target declare as their depth attachment. */
    val textureFormat: GPUTextureFormat = webGpuDepthFormat(format)

    /** Width, in texels, of the depth texture. */
    var width: Int = size
        private set

    /** Height, in texels, of the depth texture. */
    var height: Int = size
        private set

    /** Longest edge, in texels, of the depth texture (equals both [width] and [height] for square targets). */
    val size: Int get() = maxOf(width, height)

    /** One layer per shadow cascade -- Vulkan's `DepthTarget.layers`, same reasoning. Grows when a
     * frame draws more layers than it holds; see [reallocate]. */
    var layers: Int = layers
        private set

    /** The texture's bytes: [layers] of [width] x [height] texels of [format]. */
    val allocatedBytes: Long get() = width.toLong() * height.toLong() * format.bytesPerTexel * layers.toLong()

    /**
     * Whether the texture is the shadow array a light renders into rather than the 1x1 [DORMANT_SIZE]
     * stand-in a shadow bind group points at while none casts (#1698), as on Vulkan.
     */
    internal val resident: Boolean get() = width != DORMANT_SIZE || height != DORMANT_SIZE

    /**
     * The [textureFormat] texture, with [layers] array layers. It is both a render attachment and
     * bindable for sampling. Replaced by [reallocate].
     */
    var depthTexture: GPUTexture = createTexture()
        private set

    /** What a shader samples: the whole array when [arrayed], layer 0 otherwise. */
    var depthView: GPUTextureView = createSampledView()
        private set

    /** One per layer: a render pass attaches a single layer, so a cascade renders through its
     * own view. */
    private var layerViews: List<GPUTextureView> = createLayerViews()

    /**
     * A new texture of [layers] layers of [width] x [height] texels, its views with it; the old
     * ones are closed. Work already submitted against them still completes, as WebGPU defines a
     * texture's destroy. Anything built over [depthView] -- a bind group -- is the caller's to
     * rebuild.
     */
    internal fun reallocate(layers: Int, width: Int, height: Int) {
        releaseTexture()
        this.layers = layers
        this.width = width
        this.height = height
        depthTexture = createTexture()
        depthView = createSampledView()
        layerViews = createLayerViews()
    }

    /** Square overload of [reallocate] for shadow arrays. */
    internal fun reallocate(layers: Int, size: Int = this.size) = reallocate(layers, size, size)

    private fun createTexture(): GPUTexture = graphicsDevice.wgpuContext.device.createTexture(
        TextureDescriptor(
            size = Extent3D(
                width = width.toUInt(),
                height = height.toUInt(),
                depthOrArrayLayers = layers.toUInt(),
            ),
            format = textureFormat,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
            dimension = GPUTextureDimension.TwoD,
        ),
    )

    private fun createSampledView(): GPUTextureView = depthTexture.createView(
        TextureViewDescriptor(
            dimension = if (arrayed) GPUTextureViewDimension.TwoDArray else GPUTextureViewDimension.TwoD,
            baseArrayLayer = 0u,
            arrayLayerCount = if (arrayed) layers.toUInt() else 1u,
        ),
    )

    private fun createLayerViews(): List<GPUTextureView> = List(layers) { layer ->
        depthTexture.createView(
            TextureViewDescriptor(
                dimension = GPUTextureViewDimension.TwoD,
                baseArrayLayer = layer.toUInt(),
                arrayLayerCount = 1u,
            ),
        )
    }

    /** The view a pass renders [layer] through. */
    fun viewFor(layer: Int): GPUTextureView = layerViews[layer]

    // LessEqual matches the shader's sense exactly: the manual PCF this replaces counted a tap
    // lit when (ndc.z - bias) was NOT greater than the stored depth. Linear filters so each tap
    // blends four comparison RESULTS -- the free 2x2 PCF comparison sampling exists for.

    /**
     * Sampler for reading [depthView], clamped to the edge on every axis. A linear-filtered
     * `LessEqual` comparison sampler when built with `comparison = true`, otherwise a plain
     * nearest-filter sampler.
     */
    val sampler: GPUSampler = graphicsDevice.wgpuContext.device.createSampler(
        if (comparison) {
            SamplerDescriptor(
                addressModeU = GPUAddressMode.ClampToEdge,
                addressModeV = GPUAddressMode.ClampToEdge,
                addressModeW = GPUAddressMode.ClampToEdge,
                magFilter = GPUFilterMode.Linear,
                minFilter = GPUFilterMode.Linear,
                compare = GPUCompareFunction.LessEqual,
            )
        } else {
            SamplerDescriptor(
                addressModeU = GPUAddressMode.ClampToEdge,
                addressModeV = GPUAddressMode.ClampToEdge,
                addressModeW = GPUAddressMode.ClampToEdge,
                magFilter = GPUFilterMode.Nearest,
                minFilter = GPUFilterMode.Nearest,
            )
        },
    )

    /** Closes the views and the sampler derived from [depthTexture], then the texture itself. */
    fun destroy() {
        releaseTexture()
        sampler.close()
    }

    private fun releaseTexture() {
        layerViews.forEach(GPUTextureView::close)
        depthView.close()
        depthTexture.close()
    }

    /** Default size for a depth target. */
    companion object {
        /** Default edge length of a depth target: 2048 texels, the same as the Vulkan backend's. */
        const val DEFAULT_SIZE = 2048

        /** The stand-in's edge while no light casts: one texel, one layer, as on Vulkan (#1698). */
        internal const val DORMANT_SIZE = 1
    }
}

/** The WebGPU texture format a [ShadowDepthFormat] stores its texels in. */
fun webGpuDepthFormat(format: ShadowDepthFormat): GPUTextureFormat = when (format) {
    ShadowDepthFormat.Depth32Float -> GPUTextureFormat.Depth32Float
    ShadowDepthFormat.Depth16Unorm -> GPUTextureFormat.Depth16Unorm
}

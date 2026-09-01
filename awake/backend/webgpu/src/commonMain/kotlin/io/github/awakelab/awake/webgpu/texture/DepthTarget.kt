/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu.texture

import io.github.awakelab.awake.webgpu.device.GraphicsDevice
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
 * A square depth-only render target a feature can render into and then sample. Mirrors Vulkan's
 * `DepthTarget` (2048x2048 Depth32Float).
 *
 * Hardware only: it knows a depth format and a size, and nothing about why a caller wants depth
 * rendered offscreen. A shadow map is the one use today.
 */
class DepthTarget(
    graphicsDevice: GraphicsDevice,
    val size: Int = DEFAULT_SIZE,
    /** One layer per shadow cascade -- Vulkan's `DepthTarget.layers`, same reasoning. */
    val layers: Int = 1,
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
) {
    val depthTexture: GPUTexture = graphicsDevice.wgpuContext.device.createTexture(
        TextureDescriptor(
            size = Extent3D(
                width = size.toUInt(),
                height = size.toUInt(),
                depthOrArrayLayers = layers.toUInt(),
            ),
            format = GPUTextureFormat.Depth32Float,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
            dimension = GPUTextureDimension.TwoD,
        ),
    )

    /** What a shader samples: the whole array when [arrayed], layer 0 otherwise. */
    val depthView: GPUTextureView = depthTexture.createView(
        TextureViewDescriptor(
            dimension = if (arrayed) GPUTextureViewDimension.TwoDArray else GPUTextureViewDimension.TwoD,
            baseArrayLayer = 0u,
            arrayLayerCount = if (arrayed) layers.toUInt() else 1u,
        ),
    )

    /** One per layer: a render pass attaches a single layer, so a cascade renders through its
     * own view. */
    private val layerViews: List<GPUTextureView> = List(layers) { layer ->
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

    fun destroy() {
        depthTexture.close()
    }

    companion object {
        const val DEFAULT_SIZE = 2048
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu.texture

import io.github.awakelab.awake.webgpu.device.GraphicsDevice
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureDimension
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
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
) {
    val depthTexture: GPUTexture = graphicsDevice.wgpuContext.device.createTexture(
        TextureDescriptor(
            size = Extent3D(width = size.toUInt(), height = size.toUInt()),
            format = GPUTextureFormat.Depth32Float,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
            dimension = GPUTextureDimension.TwoD,
        ),
    )

    val depthView: GPUTextureView = depthTexture.createView(TextureViewDescriptor())

    val sampler: GPUSampler = graphicsDevice.wgpuContext.device.createSampler(
        SamplerDescriptor(
            addressModeU = GPUAddressMode.ClampToEdge,
            addressModeV = GPUAddressMode.ClampToEdge,
            addressModeW = GPUAddressMode.ClampToEdge,
            magFilter = GPUFilterMode.Nearest,
            minFilter = GPUFilterMode.Nearest,
        ),
    )

    fun destroy() {
        depthTexture.close()
    }

    companion object {
        const val DEFAULT_SIZE = 2048
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.WGPUContext

/**
 * This context with a colour format that stores what a shader writes as it is: the plain UNORM
 * counterpart of an sRGB surface format, where the surface offers one.
 *
 * Every shader here writes colour that is already display-encoded (the lit shaders end with the
 * gamma 2.2 encode, and UI colours are sRGB bytes), as on Vulkan, whose swapchain and offscreen
 * targets are UNORM for that reason. An `-srgb` target makes the GPU encode those values a second
 * time on every write, and decode them on every sample (#1011). A browser's preferred canvas
 * format is never an sRGB one, so only wgpu-native's surface, which lists the sRGB format first,
 * has anything to change.
 */
internal expect fun WGPUContext.withDisplayEncodedColorFormat(): WGPUContext

/** The UNORM format with [this] sRGB format's layout, or null when [this] is not an sRGB format. */
internal fun GPUTextureFormat.unormCounterpart(): GPUTextureFormat? = when (this) {
    GPUTextureFormat.BGRA8UnormSrgb -> GPUTextureFormat.BGRA8Unorm
    GPUTextureFormat.RGBA8UnormSrgb -> GPUTextureFormat.RGBA8Unorm
    else -> null
}

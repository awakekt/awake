/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import io.ygdrasil.webgpu.SurfaceRenderingContext
import io.ygdrasil.webgpu.WGPUContext

/**
 * wgpu4k's GLFW bootstrap takes the surface's first supported format, `BGRA8UnormSrgb` on a Vulkan
 * surface, and every pipeline and offscreen target follows it. Swap it for its UNORM counterpart
 * when the surface supports that too; keep it otherwise, since a surface must be configured with a
 * format it lists.
 */
internal actual fun WGPUContext.withDisplayEncodedColorFormat(): WGPUContext {
    val unorm = (renderingContext as? SurfaceRenderingContext)
        ?.textureFormat
        ?.unormCounterpart()
        ?.takeIf { it in surface.supportedFormats }
    return if (unorm == null) this else WGPUContext(surface, adapter, device, SurfaceRenderingContext(surface, unorm))
}

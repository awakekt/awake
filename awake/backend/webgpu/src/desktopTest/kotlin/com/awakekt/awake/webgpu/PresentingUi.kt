/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import io.ygdrasil.webgpu.CompositeAlphaMode
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.SurfaceConfiguration
import kotlin.test.assertEquals
import com.awakekt.awake.webgpu.renderer.Renderer as WebGpuRenderer

/**
 * Runs [block] with a renderer whose surface presents, and a frame step that records the staged UI
 * through the swapchain UI pass. No WebGPU error may occur.
 */
internal fun withPresentingUi(block: (WebGpuRenderer, frame: () -> Unit) -> Unit) {
    val errors = mutableListOf<String>()
    webGpuHeadlessUi(onUncapturedError = { errors += it }).use { session ->
        val renderer = session.renderer as WebGpuRenderer
        val context = renderer.graphicsDevice.wgpuContext
        context.surface.configure(
            SurfaceConfiguration(
                device = context.device,
                format = context.renderingContext.textureFormat,
                usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
                alphaMode = CompositeAlphaMode.Opaque,
            ),
        )
        // A browser presents on its own; over wgpu-native the test hands each frame back.
        block(renderer) {
            renderer.presentWithoutScene()
            context.surface.present()
        }
    }
    assertEquals(emptyList(), errors)
}

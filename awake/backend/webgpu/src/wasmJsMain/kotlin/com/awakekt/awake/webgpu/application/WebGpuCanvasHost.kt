/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.application

import com.awakekt.awake.engine.window.findCanvas
import com.awakekt.awake.engine.window.runBrowserCanvas
import io.ygdrasil.webgpu.CompositeAlphaMode
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUUncapturedErrorCallback
import io.ygdrasil.webgpu.SurfaceConfiguration
import io.ygdrasil.webgpu.WGPUContext
import io.ygdrasil.webgpu.canvasContextRenderer
import org.w3c.dom.HTMLCanvasElement

/**
 * Reusable WebGPU canvas host for authored games.
 */
fun launchWebGpuGame(
    canvasId: String = "awake-canvas",
    applicationFactory: () -> WebGpuEngine,
) {
    val canvas = findCanvas(canvasId)
    launchWebGpuGame(canvas, applicationFactory)
}

fun launchWebGpuGame(
    canvas: HTMLCanvasElement,
    applicationFactory: () -> WebGpuEngine,
) {
    check(browserExposesWebGpu()) {
        "WebGPU is unavailable in this browser or device. " +
            "Use a current Chrome/Edge build with WebGPU enabled and a compatible adapter."
    }

    val application = applicationFactory()
    var wgpuContext: WGPUContext? = null
    var runtimeFailure: String? = null

    runBrowserCanvas(
        canvas = canvas,
        lifecycle = application,
        onResize = { _, _ ->
            wgpuContext?.let(::configureSurface)
        },
        initBackend = { targetCanvas, width, height ->
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            val webCanvas = targetCanvas.unsafeCast<web.html.HTMLCanvasElement>()
            val canvasContext = canvasContextRenderer(
                htmlCanvas = webCanvas,
                width = width,
                height = height,
                onUncapturedError = GPUUncapturedErrorCallback { error ->
                    if (runtimeFailure == null) {
                        val message = "WebGPU uncaptured error: ${error.message}"
                        runtimeFailure = message
                        reportWebGpuRuntimeFailure(message)
                    }
                },
            )
            val resolvedContext = canvasContext.wgpuContext
            wgpuContext = resolvedContext
            configureSurface(resolvedContext)
            application.create(resolvedContext)
        },
    )
}

/** Capability check kept at the browser boundary so unsupported hosts fail before engine setup. */
@JsFun("() => typeof navigator !== 'undefined' && navigator.gpu != null")
private external fun browserExposesWebGpu(): Boolean

/** Reports an asynchronous device validation failure before the next animation frame. */
@JsFun("(message) => console.error(message)")
private external fun reportWebGpuRuntimeFailure(message: String)

/**
 * Configures the canvas surface using the browser's own preferred format
 * ([WGPUContext.renderingContext]'s `textureFormat`, resolved once by wgpu4k's
 * `canvasContextRenderer()` from `navigator.gpu.getPreferredCanvasFormat()`) rather than a
 * hardcoded guess -- configuring with any other format forces WebGPU to insert an extra copy
 * on every present (Chrome's console warns about exactly this). Every render pipeline in this
 * module reads the same resolved format back via `SwapchainManager.imageFormatWebGpu`, so
 * this stays the single source of truth for what the swapchain (and anything built to match
 * it, like [com.awakekt.awake.webgpu.texture.OffscreenRenderTarget]) is configured with.
 */
private fun configureSurface(wgpuContext: WGPUContext) {
    wgpuContext.surface.configure(
        SurfaceConfiguration(
            device = wgpuContext.device,
            format = wgpuContext.renderingContext.textureFormat,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
            alphaMode = CompositeAlphaMode.Opaque,
        ),
    )
}

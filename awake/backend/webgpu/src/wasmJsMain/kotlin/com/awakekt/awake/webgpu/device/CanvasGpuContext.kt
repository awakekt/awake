/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.device

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUUncapturedErrorCallback
import io.ygdrasil.webgpu.Surface
import io.ygdrasil.webgpu.SurfaceRenderingContext
import io.ygdrasil.webgpu.WGPUContext
import io.ygdrasil.webgpu.getCanvasSurface
import io.ygdrasil.webgpu.requestAdapter
import web.html.HTMLCanvasElement
import web.navigator.navigator

/** Canvas bootstrap with optional timing and the browser's preferred presentation format. */
internal suspend fun createCanvasGpuContext(
    canvas: HTMLCanvasElement,
    width: Int,
    height: Int,
    onError: GPUUncapturedErrorCallback? = null,
): WGPUContext {
    canvas.width = width
    canvas.height = height
    val adapter = requestAdapter().getOrThrow()
    val device = adapter.requestDevice(adapter.timingDeviceDescriptor(onError)).getOrThrow()
    val surface = Surface(canvas.getCanvasSurface())
    val format = GPUTextureFormat.of(checkNotNull(navigator.gpu).getPreferredCanvasFormat().unsafeCast<kotlin.js.JsString>().toString())
        ?: error("The browser's preferred canvas format is unsupported.")
    return WGPUContext(surface, adapter, device, SurfaceRenderingContext(surface, format))
}

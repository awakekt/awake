/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.device

import io.ygdrasil.webgpu.Device
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUUncapturedErrorCallback
import io.ygdrasil.webgpu.Surface
import io.ygdrasil.webgpu.SurfaceRenderingContext
import io.ygdrasil.webgpu.WGPUContext
import io.ygdrasil.webgpu.WGPUDevice
import io.ygdrasil.webgpu.getCanvasSurface
import io.ygdrasil.webgpu.requestAdapter
import web.html.HTMLCanvasElement
import web.navigator.navigator

/**
 * Canvas bootstrap with optional timing and the browser's preferred presentation format.
 *
 * @param onDeviceLost Called once when the device's `lost` promise resolves, with the
 * `GPUDeviceLostInfo` reason and message. wgpu4k does not expose `lost`, so it is read from the
 * browser's own device.
 */
internal suspend fun createCanvasGpuContext(
    canvas: HTMLCanvasElement,
    width: Int,
    height: Int,
    onError: GPUUncapturedErrorCallback? = null,
    onDeviceLost: ((reason: String, message: String) -> Unit)? = null,
): WGPUContext {
    canvas.width = width
    canvas.height = height
    val adapter = requestAdapter().getOrThrow()
    val device = adapter.requestDevice(adapter.preferredDeviceDescriptor(onError)).getOrThrow()
    if (onDeviceLost != null) {
        val browserDevice = checkNotNull(device as? Device) { "The browser's device is not wgpu4k's web Device." }
        watchDeviceLost(browserDevice.handler, onDeviceLost)
    }
    val surface = Surface(canvas.getCanvasSurface())
    val format = GPUTextureFormat.of(checkNotNull(navigator.gpu).getPreferredCanvasFormat().unsafeCast<kotlin.js.JsString>().toString())
        ?: error("The browser's preferred canvas format is unsupported.")
    return WGPUContext(surface, adapter, device, SurfaceRenderingContext(surface, format))
}

/** Calls [lost] with the reason and message once [device]'s `lost` promise resolves. */
@JsFun("(device, lost) => { device.lost.then((info) => lost(String(info.reason), String(info.message))); }")
private external fun watchDeviceLost(device: WGPUDevice, lost: (String, String) -> Unit)

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import com.awakekt.awake.render.renderer.GpuDeviceClass
import io.ygdrasil.webgpu.GPUAdapterInfo

/** Adapter vendors whose GPUs are found only in phones and tablets. */
private val MOBILE_VENDORS = listOf("arm", "qualcomm", "imagination", "samsung")

/**
 * The [GpuDeviceClass] of a WebGPU adapter: a fallback adapter is a CPU rasterizer, and a
 * phone GPU is known by its vendor. A browser may report an empty vendor, which stays
 * [GpuDeviceClass.Unknown] rather than guessing.
 *
 * @param info The adapter's report, or null when it gave none.
 * @return The adapter's class.
 */
internal fun gpuDeviceClass(info: GPUAdapterInfo?): GpuDeviceClass {
    if (info == null) return GpuDeviceClass.Unknown
    if (info.isFallbackAdapter) return GpuDeviceClass.Software
    val vendor = info.vendor.lowercase()
    return if (MOBILE_VENDORS.any { vendor.contains(it) }) GpuDeviceClass.Mobile else GpuDeviceClass.Unknown
}

/** This device's [GpuDeviceClass], from the adapter the context was created with. */
internal val GraphicsDevice.deviceClass: GpuDeviceClass
    get() = gpuDeviceClass(runCatching { wgpuContext.adapter.info }.getOrNull())

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import io.ygdrasil.webgpu.DeviceDescriptor
import io.ygdrasil.webgpu.GPUAdapter
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFeatureName
import io.ygdrasil.webgpu.GPUUncapturedErrorCallback
import io.ygdrasil.webgpu.SurfaceRenderingContext
import io.ygdrasil.webgpu.WGPUContext

/** Requests timing only when advertised; unsupported adapters keep the normal rendering path. */
internal fun GPUAdapter.timingDeviceDescriptor(onError: GPUUncapturedErrorCallback? = null): DeviceDescriptor = DeviceDescriptor(
    requiredFeatures = listOf(GPUFeatureName.TimestampQuery).filter { it in features },
    onUncapturedError = onError,
)

/** The desktop toolkit does not expose device feature selection. Upgrade before any resources exist. */
internal suspend fun WGPUContext.withGpuTiming(onError: GPUUncapturedErrorCallback? = null): WGPUContext {
    if (GPUFeatureName.TimestampQuery !in adapter.features || GPUFeatureName.TimestampQuery in device.features) return this
    check(renderingContext is SurfaceRenderingContext) { "Upgrade the device before creating a texture rendering context." }
    val timedDevice = adapter.requestDevice(
        adapter.timingDeviceDescriptor(onError).copy(requiredFeatures = (device.features + GPUFeatureName.TimestampQuery).toList()),
    ).getOrThrow()
    device.close()
    return WGPUContext(surface, adapter, timedDevice, renderingContext)
}

/** Browsers resolve nanoseconds; wgpu-native resolves ticks with a queue-specific period. */
internal expect fun GPUDevice.timestampPeriodNs(): Float

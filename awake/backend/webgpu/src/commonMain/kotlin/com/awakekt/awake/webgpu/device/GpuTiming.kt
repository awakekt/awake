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

/**
 * Requests the optional features the renderer uses, each only when advertised, so an adapter
 * without one keeps the normal rendering path: timing, and `depth-clip-control` where this
 * platform passes `unclippedDepth` through to pipelines (see [UNCLIPPED_DEPTH_REACHES_PIPELINES]).
 */
internal fun GPUAdapter.preferredDeviceDescriptor(onError: GPUUncapturedErrorCallback? = null): DeviceDescriptor = DeviceDescriptor(
    requiredFeatures = listOfNotNull(
        GPUFeatureName.TimestampQuery,
        GPUFeatureName.DepthClipControl.takeIf { UNCLIPPED_DEPTH_REACHES_PIPELINES },
    ).filter { it in features },
    onUncapturedError = onError,
)

/** The desktop toolkit does not expose device feature selection. Upgrade before any resources exist. */
internal suspend fun WGPUContext.withGpuTiming(onError: GPUUncapturedErrorCallback? = null): WGPUContext {
    if (GPUFeatureName.TimestampQuery !in adapter.features || GPUFeatureName.TimestampQuery in device.features) return this
    check(renderingContext is SurfaceRenderingContext) { "Upgrade the device before creating a texture rendering context." }
    val timedDevice = adapter.requestDevice(
        adapter.preferredDeviceDescriptor(onError).copy(requiredFeatures = (device.features + GPUFeatureName.TimestampQuery).toList()),
    ).getOrThrow()
    device.close()
    return WGPUContext(surface, adapter, timedDevice, renderingContext)
}

/** Browsers resolve nanoseconds; wgpu-native resolves ticks with a queue-specific period. */
internal expect fun GPUDevice.timestampPeriodNs(): Float

/**
 * Whether this platform's WebGPU binding passes a pipeline's `unclippedDepth` through to the
 * device. Where it does not, the flag is silently dropped and requesting `depth-clip-control`
 * gains nothing, so the shadow pass must not count on it: the scene compiler pulls cascade near
 * planes back to their casters instead.
 */
internal expect val UNCLIPPED_DEPTH_REACHES_PIPELINES: Boolean

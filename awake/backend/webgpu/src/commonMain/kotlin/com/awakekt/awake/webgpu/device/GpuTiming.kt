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
 * without one keeps the normal rendering path: timing, `depth-clip-control` where this platform
 * passes `unclippedDepth` through to pipelines (see [UNCLIPPED_DEPTH_REACHES_PIPELINES]), and the
 * texture-compression families block-compressed textures upload in.
 */
internal fun GPUAdapter.preferredDeviceDescriptor(onError: GPUUncapturedErrorCallback? = null): DeviceDescriptor = DeviceDescriptor(
    requiredFeatures = preferredFeatures(),
    onUncapturedError = onError,
)

private fun GPUAdapter.preferredFeatures(): List<GPUFeatureName> = listOfNotNull(
    GPUFeatureName.TimestampQuery,
    GPUFeatureName.DepthClipControl.takeIf { UNCLIPPED_DEPTH_REACHES_PIPELINES },
    GPUFeatureName.TextureCompressionBC,
    GPUFeatureName.TextureCompressionETC2,
    GPUFeatureName.TextureCompressionASTC,
).filter { it in features }

/**
 * The desktop toolkit does not expose device feature selection, so the device it made is replaced,
 * before any resources exist, by one with every [preferredDeviceDescriptor] feature it lacks.
 */
internal suspend fun WGPUContext.withGpuTiming(onError: GPUUncapturedErrorCallback? = null): WGPUContext {
    val missing = adapter.preferredFeatures().filter { it !in device.features }
    if (missing.isEmpty()) return this
    check(renderingContext is SurfaceRenderingContext) { "Upgrade the device before creating a texture rendering context." }
    val upgraded = adapter.requestDevice(
        adapter.preferredDeviceDescriptor(onError).copy(requiredFeatures = (device.features + missing).toList()),
    ).getOrThrow()
    closeDevice(device)
    return WGPUContext(surface, adapter, upgraded, renderingContext)
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

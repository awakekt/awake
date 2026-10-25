/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.Queue
import io.ygdrasil.webgpu.WGPULowLevelApi
import io.ygdrasil.wgpu.wgpuQueueGetTimestampPeriod

@OptIn(WGPULowLevelApi::class)
internal actual fun GPUDevice.timestampPeriodNs(): Float = wgpuQueueGetTimestampPeriod((queue as Queue).handler)

/**
 * False: wgpu4k's native pipeline mapper leaves `WGPUPrimitiveState.unclippedDepth` unset (its
 * primitive-state mapping carries a TODO for it), so the flag never reaches wgpu-native.
 */
internal actual val UNCLIPPED_DEPTH_REACHES_PIPELINES: Boolean = false

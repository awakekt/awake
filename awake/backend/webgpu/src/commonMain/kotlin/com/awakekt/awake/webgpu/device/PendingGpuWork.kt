/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import io.ygdrasil.webgpu.GPUDevice

/**
 * Drives this device's pending callbacks until [isDone], for the one place that needs it:
 * awaiting a buffer map during pixel readback.
 *
 * A genuine platform primitive, which is the only thing `expect`/`actual` is for here (see
 * `docs/reference/render-hardware-interface.md`). `GPUBuffer.mapAsync` suspends until the map
 * callback fires. In a browser the event loop fires it; on wgpu-native nothing does unless the
 * device is polled, so the await never completes and `getMappedRange` then aborts the process
 * with SIGABRT rather than throwing.
 *
 * @param isDone Whether the work being waited on has completed.
 */
internal expect suspend fun GPUDevice.drivePendingWork(isDone: () -> Boolean)

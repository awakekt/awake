/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import io.ygdrasil.webgpu.GPUDevice

internal actual fun GPUDevice.timestampPeriodNs(): Float = 1f

/** True: wgpu4k's browser pipeline mapper copies `unclippedDepth` onto the JS descriptor. */
internal actual val UNCLIPPED_DEPTH_REACHES_PIPELINES: Boolean = true

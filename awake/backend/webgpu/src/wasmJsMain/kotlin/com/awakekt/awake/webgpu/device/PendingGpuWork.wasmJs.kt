/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import io.ygdrasil.webgpu.GPUDevice

/** Nothing to drive: the browser's event loop resolves the map callback, and the caller's own
 * suspension point is what waits for it. */
internal actual suspend fun GPUDevice.drivePendingWork(isDone: () -> Boolean) = Unit

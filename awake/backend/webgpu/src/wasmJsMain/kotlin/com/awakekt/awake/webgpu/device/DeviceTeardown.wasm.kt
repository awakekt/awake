/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import io.ygdrasil.webgpu.GPUDevice

/** A browser queue is a garbage-collected JS object with no handle to release. */
internal actual fun GPUDevice.releaseQueueHandle() = Unit

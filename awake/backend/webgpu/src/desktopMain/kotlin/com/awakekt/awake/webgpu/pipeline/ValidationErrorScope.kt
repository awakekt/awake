/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import io.ygdrasil.webgpu.GPUDevice

internal actual suspend fun GPUDevice.popValidationError(): String? = popErrorScope().getOrThrow()?.message

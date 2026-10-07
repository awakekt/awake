/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.render.pipeline.ShaderReplacementException
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUErrorFilter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** WebGPU can return an invalid object instead of throwing. Await its validation before use. */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T : AutoCloseable> GPUDevice.createValidated(create: () -> T): T {
    pushErrorScope(GPUErrorFilter.Validation)
    val created = runCatching(create)
    val error = try {
        // Pop the scope even if preparation was cancelled while waiting for the driver.
        withContext(NonCancellable) { popValidationError() }.also {
            currentCoroutineContext().ensureActive()
        }
    } catch (failure: Throwable) {
        created.getOrNull()?.close()
        throw failure
    }
    if (error != null) {
        created.getOrNull()?.close()
        throw ShaderReplacementException("A replacement GPU resource failed validation: $error")
    }
    return created.getOrThrow()
}

internal expect suspend fun GPUDevice.popValidationError(): String?

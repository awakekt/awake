/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import io.ygdrasil.webgpu.Device
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.WGPUError
import io.ygdrasil.webgpu.WGPULowLevelApi
import js.promise.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.unsafeCast

@OptIn(WGPULowLevelApi::class, ExperimentalWasmJsInterop::class)
internal actual suspend fun GPUDevice.popValidationError(): String? {
    // The pinned wgpu4k mapper dereferences null on a successful browser scope. Preserve
    // WebGPU's nullable result directly until that upstream mapper is corrected.
    val error = (this as Device).handler.popErrorScope().await()?.unsafeCast<WGPUError>()
    return error?.message
}

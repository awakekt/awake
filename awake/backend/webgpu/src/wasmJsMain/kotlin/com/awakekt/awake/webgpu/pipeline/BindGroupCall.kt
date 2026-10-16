/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import io.ygdrasil.webgpu.BindGroup
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPURenderPassEncoder
import io.ygdrasil.webgpu.RenderPassEncoder
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.toJsNumber

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun GPURenderPassEncoder.bindGroup(index: UInt, group: GPUBindGroup) {
    val pass = this as? RenderPassEncoder ?: return setBindGroup(index, group)
    pass.handler.setBindGroup(index.toInt().toJsNumber(), (group as BindGroup).handler)
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPURenderPassEncoder

internal actual fun GPURenderPassEncoder.bindGroup(index: UInt, group: GPUBindGroup) = setBindGroup(index, group)

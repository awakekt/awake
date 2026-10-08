/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPURenderPassDescriptor
import io.ygdrasil.webgpu.GPURenderPassEncoder

internal actual fun GPUCommandEncoder.beginTimestampedRenderPass(descriptor: GPURenderPassDescriptor): GPURenderPassEncoder =
    beginRenderPass(descriptor)

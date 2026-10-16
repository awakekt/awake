/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPURenderPassEncoder

/**
 * Sets [group] at [index], with no dynamic offsets. In a browser, wgpu4k's `setBindGroup` hands a
 * new JS object to Kotlin each call, and Kotlin/Wasm remembers every such object in a WeakMap: tens
 * of thousands of entries a minute, which made each full garbage collection take seconds. The
 * browser actual calls WebGPU directly.
 */
internal expect fun GPURenderPassEncoder.bindGroup(index: UInt, group: GPUBindGroup)

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.material

import io.ygdrasil.webgpu.GPUBindGroup

/**
 * Drops every cached group [Material] built before the device's sampler generation moved on: one may
 * hold a sampler replaced for a new anisotropy (#1660), and a `GPUBindGroup` cannot be rewritten. With
 * no change it compares one integer.
 */
internal fun Material.refreshSamplers() {
    val current = samplerDevice.samplerGeneration
    if (current == samplerGeneration) return
    samplerGeneration = current
    bindGroups.values.forEach(GPUBindGroup::close)
    bindGroups.clear()
    drawBindGroups.values.forEach { groups -> groups.values.forEach(GPUBindGroup::close) }
    drawBindGroups.clear()
    texture?.let { previewSampler = it.sampler }
    samplerRebuilds++
}

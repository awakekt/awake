/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.ResourceBinding
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.SamplerType
import com.awakekt.awake.render.pipeline.ShaderStage
import com.awakekt.awake.render.pipeline.TextureSampleType
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPUSamplerBindingType
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUTextureSampleType
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.SamplerBindingLayout
import io.ygdrasil.webgpu.TextureBindingLayout

/** Creates a contiguous explicit layout from the shared shader binding ABI. */
internal fun GPUDevice.createAwakePipelineLayout(groups: Map<Int, GroupBindings>): GPUPipelineLayout {
    val maxGroup = groups.keys.maxOrNull() ?: return createPipelineLayout(PipelineLayoutDescriptor(emptyList()))
    val layouts = (0..maxGroup).map { group ->
        createBindGroupLayout(
            BindGroupLayoutDescriptor(
                entries = groups[group]?.entries.orEmpty().map(ResourceBinding::toWebGpuLayoutEntry),
            ),
        )
    }
    return createPipelineLayout(PipelineLayoutDescriptor(layouts))
}

private fun ResourceBinding.toWebGpuLayoutEntry() = BindGroupLayoutEntry(
    binding = binding.toUInt(),
    visibility = stages.fold(GPUShaderStage.None) { mask, stage ->
        mask or when (stage) {
            ShaderStage.Vertex -> GPUShaderStage.Vertex
            ShaderStage.Fragment -> GPUShaderStage.Fragment
        }
    },
    buffer = when (kind) {
        ResourceKind.UniformBuffer -> BufferBindingLayout(GPUBufferBindingType.Uniform)
        ResourceKind.StorageBuffer -> BufferBindingLayout(GPUBufferBindingType.ReadOnlyStorage)
        else -> null
    },
    sampler = if (kind == ResourceKind.Sampler) {
        SamplerBindingLayout(
            when (samplerType) {
                SamplerType.Filtering -> GPUSamplerBindingType.Filtering
                SamplerType.NonFiltering -> GPUSamplerBindingType.NonFiltering
                SamplerType.Comparison -> GPUSamplerBindingType.Comparison
            },
        )
    } else {
        null
    },
    texture = if (kind == ResourceKind.SampledTexture) {
        TextureBindingLayout(
            sampleType = when (textureSampleType) {
                TextureSampleType.Float -> GPUTextureSampleType.Float
                TextureSampleType.Depth -> GPUTextureSampleType.Depth
                TextureSampleType.Sint -> GPUTextureSampleType.Sint
                TextureSampleType.Uint -> GPUTextureSampleType.Uint
            },
            viewDimension = if (arrayed) GPUTextureViewDimension.TwoDArray else GPUTextureViewDimension.TwoD,
        )
    } else {
        null
    },
)

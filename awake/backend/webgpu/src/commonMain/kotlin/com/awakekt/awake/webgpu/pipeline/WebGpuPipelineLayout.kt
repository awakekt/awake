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
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
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

/**
 * Creates a contiguous explicit layout from the shared shader binding ABI, taking [groupZero] as
 * group 0 when given so a bind group built against that layout binds here too.
 *
 * The group layouts this creates are released once the pipeline layout holds them; [groupZero]
 * stays the caller's. The caller closes the returned layout once no pipeline is still to be built
 * from it.
 */
internal fun GPUDevice.createAwakePipelineLayout(
    groups: Map<Int, GroupBindings>,
    groupZero: GPUBindGroupLayout? = null,
): GPUPipelineLayout {
    val maxGroup = groups.keys.maxOrNull() ?: return createPipelineLayout(PipelineLayoutDescriptor(emptyList()))
    val created = mutableListOf<GPUBindGroupLayout>()
    try {
        val layouts = (0..maxGroup).map { group ->
            groupZero?.takeIf { group == 0 } ?: createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    entries = groups[group]?.entries.orEmpty().map(ResourceBinding::toWebGpuLayoutEntry),
                ),
            ).also(created::add)
        }
        return createPipelineLayout(PipelineLayoutDescriptor(layouts))
    } finally {
        created.forEach(GPUBindGroupLayout::close)
    }
}

/**
 * Creates the bind group [descriptor] describes and releases its layout. Use it when the layout
 * came from `getBindGroupLayout`: every call of that hands out a new native reference, which the
 * bind group does not need once it exists.
 */
internal fun GPUDevice.createBindGroupReleasingLayout(descriptor: BindGroupDescriptor): GPUBindGroup =
    try {
        createBindGroup(descriptor)
    } finally {
        descriptor.layout.close()
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
            viewDimension = when {
                cubemap -> GPUTextureViewDimension.Cube
                arrayed -> GPUTextureViewDimension.TwoDArray
                else -> GPUTextureViewDimension.TwoD
            },
        )
    } else {
        null
    },
)

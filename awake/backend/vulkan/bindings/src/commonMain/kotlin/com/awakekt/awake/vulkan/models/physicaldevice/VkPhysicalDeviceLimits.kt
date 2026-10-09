/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.physicaldevice

import com.awakekt.awake.vulkan.VkBool32
import com.awakekt.awake.vulkan.VkConstArray
import com.awakekt.awake.vulkan.VkDeviceSize
import com.awakekt.awake.vulkan.VkMutator
import com.awakekt.awake.vulkan.enums.VkSampleCountFlags
import kotlin.jvm.JvmOverloads

/**
 * The numeric limits of a physical device (`VkPhysicalDeviceLimits`).
 *
 * @property maxImageDimension1D The largest width, height or depth supported for 1D images.
 * @property maxImageDimension2D The largest width, height or depth supported for 2D images.
 * @property maxImageDimension3D The largest width, height or depth supported for 3D images.
 * @property maxImageDimensionCube The largest width and height supported for cube images.
 * @property maxImageArrayLayers The largest number of layers an image can have.
 * @property maxTexelBufferElements The largest number of texels a buffer view can address.
 * @property maxUniformBufferRange The largest range, in bytes, of a uniform buffer descriptor.
 * @property maxStorageBufferRange The largest range, in bytes, of a storage buffer descriptor.
 * @property maxPushConstantsSize The largest total size, in bytes, of push constants.
 * @property maxMemoryAllocationCount The largest number of device memory allocations that can exist
 * at once.
 * @property maxSamplerAllocationCount The largest number of sampler objects that can exist at once.
 * @property bufferImageGranularity The granularity, in bytes, at which linear and optimal resources
 * must be separated in memory.
 * @property sparseAddressSpaceSize The total address space, in bytes, available for sparse
 * allocations.
 * @property maxBoundDescriptorSets The largest number of descriptor sets bound at once.
 * @property maxPerStageDescriptorSamplers The largest number of samplers one shader stage can
 * access.
 * @property maxPerStageDescriptorUniformBuffers The largest number of uniform buffers one shader
 * stage can access.
 * @property maxPerStageDescriptorStorageBuffers The largest number of storage buffers one shader
 * stage can access.
 * @property maxPerStageDescriptorSampledImages The largest number of sampled images one shader
 * stage can access.
 * @property maxPerStageDescriptorStorageImages The largest number of storage images one shader
 * stage can access.
 * @property maxPerStageDescriptorInputAttachments The largest number of input attachments one
 * shader stage can access.
 * @property maxPerStageResources The largest total number of resources one shader stage can access.
 * @property maxDescriptorSetSamplers The largest number of samplers in a descriptor set, across all
 * stages.
 * @property maxDescriptorSetUniformBuffers The largest number of uniform buffers in a descriptor
 * set, across all stages.
 * @property maxDescriptorSetUniformBuffersDynamic The largest number of dynamic uniform buffers in
 * a descriptor set, across all stages.
 * @property maxDescriptorSetStorageBuffers The largest number of storage buffers in a descriptor
 * set, across all stages.
 * @property maxDescriptorSetStorageBuffersDynamic The largest number of dynamic storage buffers in
 * a descriptor set, across all stages.
 * @property maxDescriptorSetSampledImages The largest number of sampled images in a descriptor set,
 * across all stages.
 * @property maxDescriptorSetStorageImages The largest number of storage images in a descriptor set,
 * across all stages.
 * @property maxDescriptorSetInputAttachments The largest number of input attachments in a
 * descriptor set, across all stages.
 * @property maxVertexInputAttributes The largest number of vertex input attributes.
 * @property maxVertexInputBindings The largest number of vertex input bindings.
 * @property maxVertexInputAttributeOffset The largest offset, in bytes, of a vertex attribute
 * within its binding's element.
 * @property maxVertexInputBindingStride The largest stride, in bytes, of a vertex input binding.
 * @property maxVertexOutputComponents The largest number of components of output variables of the
 * vertex stage.
 * @property maxTessellationGenerationLevel The largest tessellation level the fixed-function
 * generator supports.
 * @property maxTessellationPatchSize The largest number of control points per patch.
 * @property maxTessellationControlPerVertexInputComponents The largest number of input components
 * per vertex of the tessellation control stage.
 * @property maxTessellationControlPerVertexOutputComponents The largest number of output components
 * per vertex of the tessellation control stage.
 * @property maxTessellationControlPerPatchOutputComponents The largest number of per-patch output
 * components of the tessellation control stage.
 * @property maxTessellationControlTotalOutputComponents The largest total number of output
 * components of the tessellation control stage, per-vertex and per-patch together.
 * @property maxTessellationEvaluationInputComponents The largest number of input components of the
 * tessellation evaluation stage.
 * @property maxTessellationEvaluationOutputComponents The largest number of output components of
 * the tessellation evaluation stage.
 * @property maxGeometryShaderInvocations The largest number of invocations of a geometry shader per
 * primitive.
 * @property maxGeometryInputComponents The largest number of input components of the geometry
 * stage.
 * @property maxGeometryOutputComponents The largest number of output components of the geometry
 * stage.
 * @property maxGeometryOutputVertices The largest number of vertices a geometry shader can emit.
 * @property maxGeometryTotalOutputComponents The largest total number of components of all vertices
 * a geometry shader emits.
 * @property maxFragmentInputComponents The largest number of input components of the fragment
 * stage.
 * @property maxFragmentOutputAttachments The largest number of colour attachments a fragment shader
 * can write.
 * @property maxFragmentDualSrcAttachments The largest number of colour attachments a fragment
 * shader can write with dual-source blending.
 * @property maxFragmentCombinedOutputResources The largest total number of storage buffers, storage
 * images and output attachments a fragment shader can use.
 * @property maxComputeSharedMemorySize The largest total size, in bytes, of shared memory in a
 * compute workgroup.
 * @property maxComputeWorkGroupCount The largest workgroup count per dimension of a compute
 * dispatch, as three values.
 * @property maxComputeWorkGroupInvocations The largest total number of invocations in a compute
 * workgroup.
 * @property maxComputeWorkGroupSize The largest workgroup size per dimension, as three values.
 * @property subPixelPrecisionBits The number of bits of sub-pixel precision in framebuffer
 * coordinates.
 * @property subTexelPrecisionBits The number of bits of sub-texel precision in texture lookups.
 * @property mipmapPrecisionBits The number of bits of precision in the mipmap level of detail.
 * @property maxDrawIndexedIndexValue The largest index value an indexed draw can use.
 * @property maxDrawIndirectCount The largest draw count of a multi-draw indirect command.
 * @property maxSamplerLodBias The largest absolute LOD bias of a sampler.
 * @property maxSamplerAnisotropy The largest anisotropy a sampler can use.
 * @property maxViewports The largest number of active viewports.
 * @property maxViewportDimensions The largest viewport width and height, as two values.
 * @property viewportBoundsRange The range, as a minimum and a maximum, that viewport bounds must
 * fit within.
 * @property viewportSubPixelBits The number of bits of sub-pixel precision for viewport bounds.
 * @property minMemoryMapAlignment The minimum alignment, in bytes, of mapped memory pointers.
 * @property minTexelBufferOffsetAlignment The minimum alignment, in bytes, for the offset of a
 * texel buffer view.
 * @property minUniformBufferOffsetAlignment The minimum alignment, in bytes, for the offset of a
 * uniform buffer descriptor.
 * @property minStorageBufferOffsetAlignment The minimum alignment, in bytes, for the offset of a
 * storage buffer descriptor.
 * @property minTexelOffset The smallest texel offset an image sample operation can use.
 * @property maxTexelOffset The largest texel offset an image sample operation can use.
 * @property minTexelGatherOffset The smallest texel offset an image gather operation can use.
 * @property maxTexelGatherOffset The largest texel offset an image gather operation can use.
 * @property minInterpolationOffset The smallest offset an interpolation instruction can use.
 * @property maxInterpolationOffset The largest offset an interpolation instruction can use.
 * @property subPixelInterpolationOffsetBits The number of bits of sub-pixel precision for
 * interpolation offsets.
 * @property maxFramebufferWidth The largest framebuffer width.
 * @property maxFramebufferHeight The largest framebuffer height.
 * @property maxFramebufferLayers The largest number of framebuffer layers.
 * @property framebufferColorSampleCounts A mask of the sample counts supported for framebuffers
 * with colour attachments.
 * @property framebufferDepthSampleCounts A mask of the sample counts supported for framebuffers
 * with depth attachments.
 * @property framebufferStencilSampleCounts A mask of the sample counts supported for framebuffers
 * with stencil attachments.
 * @property framebufferNoAttachmentsSampleCounts A mask of the sample counts supported for
 * framebuffers with no attachments.
 * @property maxColorAttachments The largest number of colour attachments of a subpass.
 * @property sampledImageColorSampleCounts A mask of the sample counts supported for sampled
 * non-integer colour images.
 * @property sampledImageIntegerSampleCounts A mask of the sample counts supported for sampled
 * integer colour images.
 * @property sampledImageDepthSampleCounts A mask of the sample counts supported for sampled depth
 * images.
 * @property sampledImageStencilSampleCounts A mask of the sample counts supported for sampled
 * stencil images.
 * @property storageImageSampleCounts A mask of the sample counts supported for storage images.
 * @property maxSampleMaskWords The largest number of words in a sample mask.
 * @property timestampComputeAndGraphics Whether timestamps are supported on every graphics and
 * compute queue.
 * @property timestampPeriod The number of nanoseconds per timestamp tick.
 * @property maxClipDistances The largest number of clip distances.
 * @property maxCullDistances The largest number of cull distances.
 * @property maxCombinedClipAndCullDistances The largest total number of clip and cull distances.
 * @property discreteQueuePriorities The number of distinct queue priorities that can be told apart.
 * @property pointSizeRange The range, as a minimum and a maximum, of supported point sizes.
 * @property lineWidthRange The range, as a minimum and a maximum, of supported line widths.
 * @property pointSizeGranularity The step between supported point sizes.
 * @property lineWidthGranularity The step between supported line widths.
 * @property strictLines Whether lines are rasterized strictly following the specification.
 * @property standardSampleLocations Whether rasterization uses the standard sample locations.
 * @property optimalBufferCopyOffsetAlignment The optimal alignment, in bytes, of buffer offsets in
 * copies to and from images.
 * @property optimalBufferCopyRowPitchAlignment The optimal alignment, in bytes, of buffer row pitch
 * in copies to and from images.
 * @property nonCoherentAtomSize The size and alignment, in bytes, of flushes and invalidations of
 * non-coherent mapped memory.
 */
@VkMutator
class VkPhysicalDeviceLimits @JvmOverloads constructor(
    val maxImageDimension1D: UInt = 0u,
    val maxImageDimension2D: UInt = 0u,
    val maxImageDimension3D: UInt = 0u,
    val maxImageDimensionCube: UInt = 0u,
    val maxImageArrayLayers: UInt = 0u,
    val maxTexelBufferElements: UInt = 0u,
    val maxUniformBufferRange: UInt = 0u,
    val maxStorageBufferRange: UInt = 0u,
    val maxPushConstantsSize: UInt = 0u,
    val maxMemoryAllocationCount: UInt = 0u,
    val maxSamplerAllocationCount: UInt = 0u,
    val bufferImageGranularity: VkDeviceSize = 0,
    val sparseAddressSpaceSize: VkDeviceSize = 0,
    val maxBoundDescriptorSets: UInt = 0u,
    val maxPerStageDescriptorSamplers: UInt = 0u,
    val maxPerStageDescriptorUniformBuffers: UInt = 0u,
    val maxPerStageDescriptorStorageBuffers: UInt = 0u,
    val maxPerStageDescriptorSampledImages: UInt = 0u,
    val maxPerStageDescriptorStorageImages: UInt = 0u,
    val maxPerStageDescriptorInputAttachments: UInt = 0u,
    val maxPerStageResources: UInt = 0u,
    val maxDescriptorSetSamplers: UInt = 0u,
    val maxDescriptorSetUniformBuffers: UInt = 0u,
    val maxDescriptorSetUniformBuffersDynamic: UInt = 0u,
    val maxDescriptorSetStorageBuffers: UInt = 0u,
    val maxDescriptorSetStorageBuffersDynamic: UInt = 0u,
    val maxDescriptorSetSampledImages: UInt = 0u,
    val maxDescriptorSetStorageImages: UInt = 0u,
    val maxDescriptorSetInputAttachments: UInt = 0u,
    val maxVertexInputAttributes: UInt = 0u,
    val maxVertexInputBindings: UInt = 0u,
    val maxVertexInputAttributeOffset: UInt = 0u,
    val maxVertexInputBindingStride: UInt = 0u,
    val maxVertexOutputComponents: UInt = 0u,
    val maxTessellationGenerationLevel: UInt = 0u,
    val maxTessellationPatchSize: UInt = 0u,
    val maxTessellationControlPerVertexInputComponents: UInt = 0u,
    val maxTessellationControlPerVertexOutputComponents: UInt = 0u,
    val maxTessellationControlPerPatchOutputComponents: UInt = 0u,
    val maxTessellationControlTotalOutputComponents: UInt = 0u,
    val maxTessellationEvaluationInputComponents: UInt = 0u,
    val maxTessellationEvaluationOutputComponents: UInt = 0u,
    val maxGeometryShaderInvocations: UInt = 0u,
    val maxGeometryInputComponents: UInt = 0u,
    val maxGeometryOutputComponents: UInt = 0u,
    val maxGeometryOutputVertices: UInt = 0u,
    val maxGeometryTotalOutputComponents: UInt = 0u,
    val maxFragmentInputComponents: UInt = 0u,
    val maxFragmentOutputAttachments: UInt = 0u,
    val maxFragmentDualSrcAttachments: UInt = 0u,
    val maxFragmentCombinedOutputResources: UInt = 0u,
    val maxComputeSharedMemorySize: UInt = 0u,
    @VkConstArray("3")
    val maxComputeWorkGroupCount: IntArray = IntArray(3), // max 3
    val maxComputeWorkGroupInvocations: UInt = 0u,
    @VkConstArray("3")
    val maxComputeWorkGroupSize: IntArray = IntArray(3), // max 3
    val subPixelPrecisionBits: UInt = 0u,
    val subTexelPrecisionBits: UInt = 0u,
    val mipmapPrecisionBits: UInt = 0u,
    val maxDrawIndexedIndexValue: UInt = 0u,
    val maxDrawIndirectCount: UInt = 0u,
    val maxSamplerLodBias: Float = 0f,
    val maxSamplerAnisotropy: Float = 0f,
    val maxViewports: UInt = 0u,
    @VkConstArray("2")
    val maxViewportDimensions: IntArray = IntArray(2), // max 2
    @VkConstArray("2")
    val viewportBoundsRange: FloatArray = FloatArray(2), // max 2
    val viewportSubPixelBits: UInt = 0u,
    val minMemoryMapAlignment: ULong = 0u,
    val minTexelBufferOffsetAlignment: VkDeviceSize = 0,
    val minUniformBufferOffsetAlignment: VkDeviceSize = 0,
    val minStorageBufferOffsetAlignment: VkDeviceSize = 0,
    val minTexelOffset: Int = 0,
    val maxTexelOffset: UInt = 0u,
    val minTexelGatherOffset: Int = 0,
    val maxTexelGatherOffset: UInt = 0u,
    val minInterpolationOffset: Float = 0f,
    val maxInterpolationOffset: Float = 0f,
    val subPixelInterpolationOffsetBits: UInt = 0u,
    val maxFramebufferWidth: UInt = 0u,
    val maxFramebufferHeight: UInt = 0u,
    val maxFramebufferLayers: UInt = 0u,
    val framebufferColorSampleCounts: VkSampleCountFlags = 0,
    val framebufferDepthSampleCounts: VkSampleCountFlags = 0,
    val framebufferStencilSampleCounts: VkSampleCountFlags = 0,
    val framebufferNoAttachmentsSampleCounts: VkSampleCountFlags = 0,
    val maxColorAttachments: UInt = 0u,
    val sampledImageColorSampleCounts: VkSampleCountFlags = 0,
    val sampledImageIntegerSampleCounts: VkSampleCountFlags = 0,
    val sampledImageDepthSampleCounts: VkSampleCountFlags = 0,
    val sampledImageStencilSampleCounts: VkSampleCountFlags = 0,
    val storageImageSampleCounts: VkSampleCountFlags = 0,
    val maxSampleMaskWords: UInt = 0u,
    val timestampComputeAndGraphics: VkBool32 = false,
    val timestampPeriod: Float = 0f,
    val maxClipDistances: UInt = 0u,
    val maxCullDistances: UInt = 0u,
    val maxCombinedClipAndCullDistances: UInt = 0u,
    val discreteQueuePriorities: UInt = 0u,
    @VkConstArray("2")
    val pointSizeRange: FloatArray = FloatArray(2), // max size 2
    @VkConstArray("2")
    val lineWidthRange: FloatArray = FloatArray(2), // max size 2
    val pointSizeGranularity: Float = 0f,
    val lineWidthGranularity: Float = 0f,
    val strictLines: VkBool32 = false,
    val standardSampleLocations: VkBool32 = false,
    val optimalBufferCopyOffsetAlignment: VkDeviceSize = 0,
    val optimalBufferCopyRowPitchAlignment: VkDeviceSize = 0,
    val nonCoherentAtomSize: VkDeviceSize = 0,
)

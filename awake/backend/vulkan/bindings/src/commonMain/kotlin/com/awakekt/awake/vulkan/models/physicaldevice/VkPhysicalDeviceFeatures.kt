/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.physicaldevice

import com.awakekt.awake.vulkan.VkBool32
import com.awakekt.awake.vulkan.VkMutator
import kotlin.jvm.JvmOverloads

/**
 * The optional features a physical device supports (`VkPhysicalDeviceFeatures`); enable the ones a
 * device needs when creating it.
 *
 * @property robustBufferAccess Whether buffer accesses are bounds-checked against the range of the
 * buffer descriptor.
 * @property fullDrawIndexUint32 Whether the full 32-bit range of indices is supported for indexed
 * draws.
 * @property imageCubeArray Whether cube array image views can be created.
 * @property independentBlend Whether each colour attachment can use different blend state.
 * @property geometryShader Whether geometry shaders are supported.
 * @property tessellationShader Whether tessellation control and evaluation shaders are supported.
 * @property sampleRateShading Whether per-sample shading and interpolation are supported.
 * @property dualSrcBlend Whether blend factors that use the fragment shader's second colour output
 * are supported.
 * @property logicOp Whether logic operations on colour attachments are supported.
 * @property multiDrawIndirect Whether one indirect draw command can issue more than one draw.
 * @property drawIndirectFirstInstance Whether indirect draw commands may use a non-zero first
 * instance.
 * @property depthClamp Whether depth clamping is supported.
 * @property depthBiasClamp Whether the depth bias clamp is supported.
 * @property fillModeNonSolid Whether polygons can be drawn as lines or points.
 * @property depthBounds Whether the depth bounds test is supported.
 * @property wideLines Whether lines wider than 1.0 are supported.
 * @property largePoints Whether points larger than 1.0 are supported.
 * @property alphaToOne Whether alpha-to-one is supported.
 * @property multiViewport Whether more than one viewport can be used.
 * @property samplerAnisotropy Whether anisotropic filtering is supported.
 * @property textureCompressionETC2 Whether the ETC2 and EAC compressed formats are supported.
 * @property textureCompressionASTC_LDR Whether the ASTC LDR compressed formats are supported.
 * @property textureCompressionBC Whether the BC compressed formats are supported.
 * @property occlusionQueryPrecise Whether occlusion queries can return exact sample counts.
 * @property pipelineStatisticsQuery Whether pipeline statistics queries are supported.
 * @property vertexPipelineStoresAndAtomics Whether vertex, tessellation and geometry shaders can
 * write storage resources and use atomics.
 * @property fragmentStoresAndAtomics Whether fragment shaders can write storage resources and use
 * atomics.
 * @property shaderTessellationAndGeometryPointSize Whether tessellation and geometry shaders can
 * write the point size.
 * @property shaderImageGatherExtended Whether image gather accepts extended offsets, including
 * non-constant ones.
 * @property shaderStorageImageExtendedFormats Whether the extended set of storage image formats is
 * supported.
 * @property shaderStorageImageMultisample Whether multisampled storage images are supported.
 * @property shaderStorageImageReadWithoutFormat Whether storage images can be read without
 * declaring a format.
 * @property shaderStorageImageWriteWithoutFormat Whether storage images can be written without
 * declaring a format.
 * @property shaderUniformBufferArrayDynamicIndexing Whether arrays of uniform buffers can be
 * indexed with dynamically uniform values.
 * @property shaderSampledImageArrayDynamicIndexing Whether arrays of samplers or sampled images can
 * be indexed with dynamically uniform values.
 * @property shaderStorageBufferArrayDynamicIndexing Whether arrays of storage buffers can be
 * indexed with dynamically uniform values.
 * @property shaderStorageImageArrayDynamicIndexing Whether arrays of storage images can be indexed
 * with dynamically uniform values.
 * @property shaderClipDistance Whether shaders can write clip distances.
 * @property shaderCullDistance Whether shaders can write cull distances.
 * @property shaderFloat64 Whether 64-bit floats are supported in shaders.
 * @property shaderInt64 Whether 64-bit integers are supported in shaders.
 * @property shaderInt16 Whether 16-bit integers are supported in shaders.
 * @property shaderResourceResidency Whether image operations that return sparse residency
 * information are supported in shaders.
 * @property shaderResourceMinLod Whether image operations that take a minimum LOD are supported in
 * shaders.
 * @property sparseBinding Whether resource memory can be managed in opaque sparse blocks.
 * @property sparseResidencyBuffer Whether partially resident sparse buffers are supported.
 * @property sparseResidencyImage2D Whether partially resident sparse 2D images with one sample are
 * supported.
 * @property sparseResidencyImage3D Whether partially resident sparse 3D images are supported.
 * @property sparseResidency2Samples Whether partially resident sparse 2D images with 2 samples are
 * supported.
 * @property sparseResidency4Samples Whether partially resident sparse 2D images with 4 samples are
 * supported.
 * @property sparseResidency8Samples Whether partially resident sparse 2D images with 8 samples are
 * supported.
 * @property sparseResidency16Samples Whether partially resident sparse 2D images with 16 samples
 * are supported.
 * @property sparseResidencyAliased Whether the device can access data aliased into several
 * locations of sparse resources.
 * @property variableMultisampleRate Whether pipelines used in a subpass without attachments may
 * have different sample counts.
 * @property inheritedQueries Whether a secondary command buffer can run while the primary has an
 * active occlusion query.
 */
@VkMutator
data class VkPhysicalDeviceFeatures @JvmOverloads constructor(
    val robustBufferAccess: VkBool32 = false,
    val fullDrawIndexUint32: VkBool32 = false,
    val imageCubeArray: VkBool32 = false,
    val independentBlend: VkBool32 = false,
    val geometryShader: VkBool32 = false,
    val tessellationShader: VkBool32 = false,
    val sampleRateShading: VkBool32 = false,
    val dualSrcBlend: VkBool32 = false,
    val logicOp: VkBool32 = false,
    val multiDrawIndirect: VkBool32 = false,
    val drawIndirectFirstInstance: VkBool32 = false,
    val depthClamp: VkBool32 = false,
    val depthBiasClamp: VkBool32 = false,
    val fillModeNonSolid: VkBool32 = false,
    val depthBounds: VkBool32 = false,
    val wideLines: VkBool32 = false,
    val largePoints: VkBool32 = false,
    val alphaToOne: VkBool32 = false,
    val multiViewport: VkBool32 = false,
    val samplerAnisotropy: VkBool32 = false,
    val textureCompressionETC2: VkBool32 = false,
    val textureCompressionASTC_LDR: VkBool32 = false,
    val textureCompressionBC: VkBool32 = false,
    val occlusionQueryPrecise: VkBool32 = false,
    val pipelineStatisticsQuery: VkBool32 = false,
    val vertexPipelineStoresAndAtomics: VkBool32 = false,
    val fragmentStoresAndAtomics: VkBool32 = false,
    val shaderTessellationAndGeometryPointSize: VkBool32 = false,
    val shaderImageGatherExtended: VkBool32 = false,
    val shaderStorageImageExtendedFormats: VkBool32 = false,
    val shaderStorageImageMultisample: VkBool32 = false,
    val shaderStorageImageReadWithoutFormat: VkBool32 = false,
    val shaderStorageImageWriteWithoutFormat: VkBool32 = false,
    val shaderUniformBufferArrayDynamicIndexing: VkBool32 = false,
    val shaderSampledImageArrayDynamicIndexing: VkBool32 = false,
    val shaderStorageBufferArrayDynamicIndexing: VkBool32 = false,
    val shaderStorageImageArrayDynamicIndexing: VkBool32 = false,
    val shaderClipDistance: VkBool32 = false,
    val shaderCullDistance: VkBool32 = false,
    val shaderFloat64: VkBool32 = false,
    val shaderInt64: VkBool32 = false,
    val shaderInt16: VkBool32 = false,
    val shaderResourceResidency: VkBool32 = false,
    val shaderResourceMinLod: VkBool32 = false,
    val sparseBinding: VkBool32 = false,
    val sparseResidencyBuffer: VkBool32 = false,
    val sparseResidencyImage2D: VkBool32 = false,
    val sparseResidencyImage3D: VkBool32 = false,
    val sparseResidency2Samples: VkBool32 = false,
    val sparseResidency4Samples: VkBool32 = false,
    val sparseResidency8Samples: VkBool32 = false,
    val sparseResidency16Samples: VkBool32 = false,
    val sparseResidencyAliased: VkBool32 = false,
    val variableMultisampleRate: VkBool32 = false,
    val inheritedQueries: VkBool32 = false,
)

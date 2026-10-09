/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkBool32
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.VkPointer
import com.awakekt.awake.vulkan.VkSampleMask
import com.awakekt.awake.vulkan.enums.VkSampleCountFlagBits
import com.awakekt.awake.vulkan.enums.VkStructureType

/**
 * The multisampling state of a graphics pipeline (`VkPipelineMultisampleStateCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags Reserved for future use; 0.
 * @property rasterizationSamples The number of samples per pixel used in rasterization.
 * @property sampleShadingEnable Whether sample shading is on.
 * @property minSampleShading The minimum fraction of samples that are shaded individually, when
 * sample shading is on.
 * @property pSampleMask A mask of the samples that stay enabled.
 * @property alphaToCoverageEnable Whether the fragment's alpha is converted into a coverage mask.
 * @property alphaToOneEnable Whether the fragment's alpha is set to one.
 */
class VkPipelineMultisampleStateCreateInfo(
    var sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO,
    var pNext: Any? = null,
    var flags: VkPipelineMultisampleStateCreateFlags = 0,
    var rasterizationSamples: VkSampleCountFlagBits = VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT,
    var sampleShadingEnable: VkBool32 = false,
    var minSampleShading: Float = 1.0f, // Optional
    @VkPointer
    var pSampleMask: VkSampleMask = 0, // Optional
    var alphaToCoverageEnable: VkBool32 = false, // Optional
    var alphaToOneEnable: VkBool32 = false, // Optional
)

typealias VkPipelineMultisampleStateCreateFlags = VkFlags

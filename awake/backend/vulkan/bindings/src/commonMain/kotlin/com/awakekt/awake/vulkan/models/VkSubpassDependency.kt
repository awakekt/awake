/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

import com.awakekt.awake.vulkan.enums.flags.VkAccessFlags
import com.awakekt.awake.vulkan.enums.flags.VkDependencyFlags
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlags

/**
 * An ordering and memory dependency between two subpasses, or between a subpass and outside the
 * render pass (`VkSubpassDependency`).
 *
 * @property srcSubpass The index of the first subpass in the dependency, or `VK_SUBPASS_EXTERNAL`
 * for work before the render pass.
 * @property dstSubpass The index of the second subpass in the dependency, or `VK_SUBPASS_EXTERNAL`
 * for work after the render pass.
 * @property srcStageMask The pipeline stages of the first scope that must finish before the
 * dependent work starts.
 * @property dstStageMask The pipeline stages of the second scope that must wait.
 * @property srcAccessMask The memory accesses of the first scope that are made available.
 * @property dstAccessMask The memory accesses of the second scope that the available writes are
 * made visible to.
 * @property dependencyFlags A mask of dependency flags, such as by-region.
 */
data class VkSubpassDependency(
    val srcSubpass: Int = 0,
    val dstSubpass: Int = 0,
    val srcStageMask: VkPipelineStageFlags = 0,
    val dstStageMask: VkPipelineStageFlags = 0,
    val srcAccessMask: VkAccessFlags = 0,
    val dstAccessMask: VkAccessFlags = 0,
    val dependencyFlags: VkDependencyFlags = 0,
)

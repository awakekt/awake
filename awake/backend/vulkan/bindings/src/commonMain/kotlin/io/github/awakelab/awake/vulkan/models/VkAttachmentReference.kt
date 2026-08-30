/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.models

import io.github.awakelab.awake.vulkan.enums.VkImageLayout

data class VkAttachmentReference(
    val attachment: Int = 0,
    val layout: VkImageLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED,
)

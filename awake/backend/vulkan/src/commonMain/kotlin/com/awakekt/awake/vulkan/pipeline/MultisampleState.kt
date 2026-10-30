/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.vulkan.enums.VkSampleCountFlagBits

/** The Vulkan bit value equals its sample count for the supported power-of-two counts. */
internal fun Int.toVkSampleCount(): VkSampleCountFlagBits =
    VkSampleCountFlagBits.entries.first { it.value == this }

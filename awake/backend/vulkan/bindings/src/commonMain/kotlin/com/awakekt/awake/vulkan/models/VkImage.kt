/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

/**
 * A `VkImage` handle with the type information a plain `Long` lacks.
 *
 * @property image The raw `VkImage` handle.
 */
data class VkImage(val image: Long)

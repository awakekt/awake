/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums.flags

// Plain Int constants (bitmask), not an enum class -- consistent with how other
// Vulkan *FlagBits are modeled when their real values aren't sequential ordinals.
/**
 * Properties of a memory type (`VkMemoryPropertyFlagBits`), kept as plain `Int` constants so they
 * combine as a mask.
 */
object VkMemoryPropertyFlagBits {
    /** The memory is most efficient for device access. */
    const val VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT = 0x00000001

    /** The host can map the memory. */
    const val VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT = 0x00000002

    /** Host and device writes are visible to each other without explicit flushes or invalidates. */
    const val VK_MEMORY_PROPERTY_HOST_COHERENT_BIT = 0x00000004

    /** The memory is cached on the host, so host reads are faster but may need invalidating. */
    const val VK_MEMORY_PROPERTY_HOST_CACHED_BIT = 0x00000008

    /** The device may allocate the memory lazily; only valid for transient attachments. */
    const val VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT = 0x00000010
}

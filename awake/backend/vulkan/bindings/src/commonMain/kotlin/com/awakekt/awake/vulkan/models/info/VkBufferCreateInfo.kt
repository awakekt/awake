/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkDeviceSize
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkSharingMode

/**
 * Marshalled by jni-binding-generator (not the legacy awake-vulkan-generator), so unlike
 * other *CreateInfo classes this deliberately omits `sType`/`pNext`: they are a compile-time
 * constant per struct type and are set directly in the hand-written native body instead of
 * being passed across JNI. This also sidesteps a real hazard: jni-binding-generator marshals
 * enum fields via ordinal position, and `VkStructureType`'s ordinal only matches its real
 * Vulkan value up to entry 48 (extension types jump to values like 1000094000) — see the
 * Phase 1d note in docs/mvp-plan.md. `sharingMode` is safe here because `VkSharingMode` has
 * exactly two entries whose ordinal matches its value (0/1) and the Vulkan spec has never
 * extended it.
 *
 * @property size The size of the buffer in bytes.
 * @property usage A mask of the buffer usage constants the buffer is created for.
 * @property flags Buffer creation flags; 0 for none.
 * @property sharingMode Whether the buffer belongs to one queue family at a time or is shared
 * between several.
 */
class VkBufferCreateInfo(
    val size: VkDeviceSize,
    val usage: VkBufferUsageFlags,
    val flags: VkBufferCreateFlags = 0,
    val sharingMode: VkSharingMode = VkSharingMode.VK_SHARING_MODE_EXCLUSIVE,
)

typealias VkBufferCreateFlags = VkFlags
typealias VkBufferUsageFlags = VkFlags

/**
 * Buffer usage flags as plain `Int` constants that combine with `or` into a mask
 * (`VkBufferUsageFlagBits`).
 */
object VkBufferUsageFlagBits {
    /** The buffer can be the source of a transfer command. */
    const val VK_BUFFER_USAGE_TRANSFER_SRC_BIT = 0x00000001

    /** The buffer can be the destination of a transfer command. */
    const val VK_BUFFER_USAGE_TRANSFER_DST_BIT = 0x00000002

    /** The buffer can hold vertex data. */
    const val VK_BUFFER_USAGE_VERTEX_BUFFER_BIT = 0x00000080

    /** The buffer can hold index data. */
    const val VK_BUFFER_USAGE_INDEX_BUFFER_BIT = 0x00000040

    /** The buffer can back a uniform buffer descriptor. */
    const val VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT = 0x00000010

    /** The buffer can back a storage buffer descriptor. */
    const val VK_BUFFER_USAGE_STORAGE_BUFFER_BIT = 0x00000020
}

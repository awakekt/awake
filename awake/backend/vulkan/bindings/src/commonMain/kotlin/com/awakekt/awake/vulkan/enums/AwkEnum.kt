/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

/**
 * A Vulkan enum whose constants carry their raw integer value, so flag helpers can combine
 * constants of different enums.
 */
interface VkEnum {
    /** The raw integer value Vulkan uses for this constant. */
    val value: Int
}

/**
 * Combines this constant's value with the raw mask [other] using bitwise OR.
 *
 * @param other The mask to combine with.
 * @return The combined mask.
 */
infix fun VkEnum.or(other: Int): Int = this.value or other

/**
 * Combines this constant's value with [other]'s using bitwise OR.
 *
 * @param other The constant to combine with.
 * @return The combined mask.
 */
infix fun VkEnum.or(other: VkEnum): Int = this.value or other.value

/**
 * Returns whether this constant shares any bit with [bit].
 *
 * @param bit The flag to look for.
 * @return `true` when this value and [bit]'s value have a bit in common.
 */
infix fun VkEnum.has(bit: VkEnum): Boolean = this.value and bit.value != 0

/**
 * Returns the bits this constant has in common with [other].
 *
 * @param other The constant to intersect with.
 * @return The shared bits as a mask.
 */
infix fun VkEnum.and(other: VkEnum): Int = this.value and other.value

/**
 * Returns this constant's value with [bit]'s bits added.
 *
 * @param bit The flag to add.
 * @return The combined mask.
 */
infix fun VkEnum.set(bit: VkEnum): VkFlags = this.value or bit.value

/**
 * Returns this constant's value with [bit]'s bits removed. The helper is specific to sample-count
 * flags.
 *
 * @param bit The sample-count flag to remove.
 * @return The mask without those bits.
 */
infix fun VkEnum.clear(bit: VkSampleCountFlagBits): VkFlags = this.value and bit.value.inv()

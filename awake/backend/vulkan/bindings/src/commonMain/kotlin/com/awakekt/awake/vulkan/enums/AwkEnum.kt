/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

interface VkEnum {
    val value: Int
}

infix fun VkEnum.or(other: Int): Int = this.value or other

infix fun VkEnum.or(other: VkEnum): Int = this.value or other.value

infix fun VkEnum.has(bit: VkEnum): Boolean = this.value and bit.value != 0

infix fun VkEnum.and(other: VkEnum): Int = this.value and other.value

infix fun VkEnum.set(bit: VkEnum): VkFlags = this.value or bit.value

infix fun VkEnum.clear(bit: VkSampleCountFlagBits): VkFlags = this.value and bit.value.inv()

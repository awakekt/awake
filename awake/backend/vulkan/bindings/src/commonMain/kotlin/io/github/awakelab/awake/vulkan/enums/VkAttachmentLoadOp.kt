/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.enums

enum class VkAttachmentLoadOp(val value: Int) {
    LOAD(0),
    CLEAR(1),
    DONT_CARE(2),
    NONE_EXT(1000400000),
    MAX_ENUM(0x7FFFFFFF),
}

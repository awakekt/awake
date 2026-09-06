/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

enum class VkAttachmentStoreOp(val value: Int) {
    STORE(0),
    DONT_CARE(1),
    NONE(1000301000),
    NONE_KHR(NONE.value),
    NONE_QCOM(NONE.value),
    NONE_EXT(NONE.value),
    MAX_ENUM(0x7FFFFFFF),
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * The bitwise operation applied between source and destination colour when logic ops are enabled
 * (`VkLogicOp`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkLogicOp(val value: Int) {
    /** Result is 0. */
    VK_LOGIC_OP_CLEAR(0),

    /** Result is source AND destination. */
    VK_LOGIC_OP_AND(1),

    /** Result is source AND NOT destination. */
    VK_LOGIC_OP_AND_REVERSE(2),

    /** Result is the source. */
    VK_LOGIC_OP_COPY(3),

    /** Result is NOT source AND destination. */
    VK_LOGIC_OP_AND_INVERTED(4),

    /** Result is the destination, unchanged. */
    VK_LOGIC_OP_NO_OP(5),

    /** Result is source XOR destination. */
    VK_LOGIC_OP_XOR(6),

    /** Result is source OR destination. */
    VK_LOGIC_OP_OR(7),

    /** Result is NOT (source OR destination). */
    VK_LOGIC_OP_NOR(8),

    /** Result is NOT (source XOR destination). */
    VK_LOGIC_OP_EQUIVALENT(9),

    /** Result is NOT destination. */
    VK_LOGIC_OP_INVERT(10),

    /** Result is source OR NOT destination. */
    VK_LOGIC_OP_OR_REVERSE(11),

    /** Result is NOT source. */
    VK_LOGIC_OP_COPY_INVERTED(12),

    /** Result is NOT source OR destination. */
    VK_LOGIC_OP_OR_INVERTED(13),

    /** Result is NOT (source AND destination). */
    VK_LOGIC_OP_NAND(14),

    /** Result is all ones. */
    VK_LOGIC_OP_SET(15),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_LOGIC_OP_MAX_ENUM(0x7FFFFFFF),
}

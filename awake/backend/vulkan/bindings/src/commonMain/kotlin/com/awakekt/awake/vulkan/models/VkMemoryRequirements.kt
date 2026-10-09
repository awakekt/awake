/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

/**
 * Marshalled by jni-binding-generator — see docs/decisions/D10-codegen-derisk-findings.md.
 *
 * @property size The size in bytes of the memory the resource needs.
 * @property alignment The alignment, in bytes, the resource's offset within the allocation must
 * have.
 * @property memoryTypeBits A mask in which bit `i` is set when memory type `i` can back the
 * resource.
 */
data class VkMemoryRequirements(
    val size: Long = 0,
    val alignment: Long = 0,
    val memoryTypeBits: Int = 0,
)

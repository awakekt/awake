/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.models

/** Marshalled by jni-binding-generator — see docs/decisions/D10-codegen-derisk-findings.md. */
data class VkMemoryRequirements(
    val size: Long = 0,
    val alignment: Long = 0,
    val memoryTypeBits: Int = 0,
)

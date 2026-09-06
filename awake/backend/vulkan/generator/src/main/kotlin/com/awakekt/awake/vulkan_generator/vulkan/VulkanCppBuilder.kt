/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan_generator.vulkan

inline fun <reified T : Any> generateJavaToVulkanCpp() {
    val clazz = T::class.java
    createVulkanAccessor(clazz)
    createVulkanMutator(clazz)
}

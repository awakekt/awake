/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import com.awakekt.awake.vulkan.JniNative

/** Base-level region copies. The generated wrapper delegates to the hand-authored native body. */
@Suppress("LongParameterList") // Primitive arguments mirror the native region-copy boundary.
expect object VulkanTextureRegions {
    @JniNative("awake_vulkan_images_copy_region")
    fun copy(commandBuffer: Long, buffer: Long, image: Long, layer: Int, x: Int, y: Int, width: Int, height: Int)
}

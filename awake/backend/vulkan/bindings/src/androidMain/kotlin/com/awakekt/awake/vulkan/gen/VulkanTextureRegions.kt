/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import com.awakekt.awake.vulkan.JniNative

/**
 * Android actual of [VulkanTextureRegions]: a JNI `external` declaration backed by a hand-written
 * native body in the `awake-vulkan` library, which [com.awakekt.awake.vulkan.Vulkan] loads.
 */
@Suppress("LongParameterList") // Primitive arguments mirror the native region-copy boundary.
actual object VulkanTextureRegions {
    /**
     * Records a copy of tightly packed RGBA texels from a buffer into a rectangle of one array
     * layer's base mip level. The image must be in `TRANSFER_DST_OPTIMAL` layout.
     */
    @JniNative("awake_vulkan_images_copy_region")
    actual external fun copy(commandBuffer: Long, buffer: Long, image: Long, layer: Int, x: Int, y: Int, width: Int, height: Int)
}

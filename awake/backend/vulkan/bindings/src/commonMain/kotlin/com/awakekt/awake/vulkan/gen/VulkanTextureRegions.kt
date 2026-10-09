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
    /**
     * Records a copy of tightly packed RGBA texels from a buffer into a rectangle of one array
     * layer's base mip level. The image must be in `TRANSFER_DST_OPTIMAL` layout.
     *
     * @param commandBuffer The command buffer being recorded.
     * @param buffer The buffer holding the texels, read from offset 0.
     * @param image The destination image.
     * @param layer The array layer to write.
     * @param x Left edge of the destination rectangle, in texels.
     * @param y Top edge of the destination rectangle, in texels.
     * @param width Width of the rectangle, in texels.
     * @param height Height of the rectangle, in texels.
     * @throws IllegalArgumentException If a handle is zero, the layer or origin is negative, or the
     * size is not positive.
     */
    @JniNative("awake_vulkan_images_copy_region")
    fun copy(commandBuffer: Long, buffer: Long, image: Long, layer: Int, x: Int, y: Int, width: Int, height: Int)
}
